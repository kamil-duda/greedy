package org.kduda.greedy.characterization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.kduda.greedy.golden.GoldenFiles;
import org.kduda.greedy.integration.SpringIntegrationTest;
import org.kduda.greedy.model.FileModel;
import org.kduda.greedy.repository.data.FileRepository;
import org.kduda.greedy.repository.rules.RulesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden cases through the real app: HTTP upload, synchronous exploration, download, on a real MongoDB 4.4.
 * Pins what the golden generator cannot see: stored file names, download headers, and that the stored content is the
 * golden output. Golden files come from {@code make legacy-golden}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EndToEndGoldenIT extends SpringIntegrationTest {

	@Autowired private TestRestTemplate rest;
	@Autowired private FileRepository fileRepository;
	@Autowired private RulesRepository rulesRepository;

	/** The f1 table has a most-common-decision tie (registry #2); every run so far, and the 2017 fixture, resolve it alike. */
	@Test
	public void paperSampleInformationSystemRses() throws IOException {
		check("fixtures/paper-sample", "paper-sample.csv", "m", "is", "rses", "is-m.rul", "paper-sample-is-rules-m.rul");
	}

	@Test
	public void complexSampleCsvWithMetadata() throws IOException {
		check("fixtures/complex-sample", "complex-sample.csv", "maxCov", "dt", "csvm", "dt-maxCov-wmetadata.csv",
			"complex-sample-dt-rules-maxCov-wmetadata.csv");
	}

	@Test
	public void lensesUnderDottedUploadName() throws IOException {
		check("public/lenses", "my.data.v2.csv", "poly", "dt", "csv", "dt-poly.csv", "my-dt-rules-poly.csv");
	}

	@Test
	public void quotedValuesInformationSystemRses() throws IOException {
		check("edge/quoted-values", "quoted-values.csv", "log", "is", "rses", "is-log.rul", "quoted-values-is-rules-log.rul");
	}

	private void check(String input, String uploadName, String heuristics, String type, String output,
					   String goldenFile, String storedName) throws IOException {
		String dataId = upload(new File("golden/inputs/" + input + ".csv"), uploadName);
		try {
			HttpStatus explored = explore(dataId, heuristics, type, output);
			List<FileModel> rules = rulesRepository.listByParent(dataId);

			if (failsInGoldenRun(input, type, heuristics, output)) {
				assertThat(explored).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
				assertThat(rules).isEmpty();
				return;
			}

			assertThat(explored).isEqualTo(HttpStatus.FOUND);
			assertThat(rules).extracting("name").containsExactly(storedName);

			ResponseEntity<byte[]> download = download(rules.get(0).getId());
			assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
				.isEqualTo("attachment; filename=\"" + storedName + "\"");
			// legacy finding: rule files are served as JSON
			assertThat(download.getHeaders().getContentType().toString()).startsWith("application/json");

			String golden = FileUtils.readFileToString(new File("golden/outputs/" + input + "/" + goldenFile), StandardCharsets.UTF_8);
			assertThat(GoldenFiles.normalize(goldenFile, new String(download.getBody(), StandardCharsets.UTF_8)))
				.isEqualTo(GoldenFiles.normalize(goldenFile, golden));
		} finally {
			rulesRepository.deleteByParent(dataId);
			fileRepository.deleteById(dataId);
		}
	}

	/** Uploads through POST /file and returns the new data file's id (other files with the same name are left alone). */
	private String upload(File file, String uploadName) throws IOException {
		Set<String> before = idsNamed(uploadName);
		ByteArrayResource content = new ByteArrayResource(FileUtils.readFileToByteArray(file)) {
			@Override
			public String getFilename() {
				return uploadName;
			}
		};
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", content);
		assertThat(rest.postForEntity("/file", form, String.class).getStatusCode()).isEqualTo(HttpStatus.FOUND);

		Set<String> created = idsNamed(uploadName);
		created.removeAll(before);
		assertThat(created).hasSize(1);
		return created.iterator().next();
	}

	private Set<String> idsNamed(String name) {
		Set<String> ids = new HashSet<>();
		for (FileModel file : fileRepository.listAll()) {
			if (file.getName().equals(name)) {
				ids.add(file.getId());
			}
		}
		return ids;
	}

	/**
	 * Posts the explore form as a browser does. Not through TestRestTemplate: its form converter sets Content-Length and
	 * HttpURLConnection adds a second one, which Tomcat 8.5 rejects with a 500 before the request reaches the app.
	 */
	private HttpStatus explore(String dataId, String heuristics, String type, String output) throws IOException {
		URL url = rest.getRestTemplate().getUriTemplateHandler().expand("/explore/" + dataId).toURL();
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setInstanceFollowRedirects(false);
		connection.setDoOutput(true);
		connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
		try (OutputStream body = connection.getOutputStream()) {
			body.write(("heuristics=" + heuristics + "&type=" + type + "&output=" + output).getBytes(StandardCharsets.UTF_8));
		}
		return HttpStatus.valueOf(connection.getResponseCode());
	}

	private ResponseEntity<byte[]> download(String ruleId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.ALL));
		return rest.exchange("/file/" + ruleId, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
	}

	/** True when golden/outputs/manifest.json records this case (or this format) as failing in the legacy pipeline. */
	private static boolean failsInGoldenRun(String input, String mode, String heuristic, String format) throws IOException {
		JsonNode manifest = new ObjectMapper().readTree(new File("golden/outputs/manifest.json"));
		for (JsonNode node : manifest.get("cases")) {
			if (node.get("input").asText().equals(input) && node.get("mode").asText().equals(mode)
				&& node.get("heuristic").asText().equals(heuristic)) {
				return !node.get("error").isNull() || node.get("formatErrors").has(format);
			}
		}
		throw new AssertionError("case not in golden manifest: " + input + " " + mode + " " + heuristic);
	}
}
