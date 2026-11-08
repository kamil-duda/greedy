package org.kduda.greedy.unit.storage;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kduda.greedy.repository.data.FileRepository;
import org.kduda.greedy.unit.SpringUnitTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.any;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FileStorageTest extends SpringUnitTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@MockBean
	private FileRepository storageService;

	@LocalServerPort
	private int port;

	@Test
	public void shouldUploadFile() throws Exception {
		ClassPathResource resource = new ClassPathResource("/files/storage-test-file.txt", getClass());

		MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
		map.add("file", resource);
		ResponseEntity<String> response = restTemplate.postForEntity("/file", map, String.class);

		assertThat(response.getStatusCode()).isEqualByComparingTo(HttpStatus.FOUND);
		assertThat(response.getHeaders().getLocation().toString()).startsWith("http://localhost" + "/");
		then(storageService).should().store(any(MultipartFile.class));
	}

	@Test
	public void shouldDownloadFile() throws Exception {
		ClassPathResource resource = new ClassPathResource("/files/storage-test-file.txt", getClass());
		given(storageService.loadResourceById("10"))
			.willReturn(Pair.of(resource.getFilename(), resource));

		ResponseEntity<String> response = restTemplate.getForEntity("/file/{id}", String.class,
																	"10");

		assertThat(response.getStatusCodeValue()).isEqualTo(200);
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
			.isEqualTo("attachment; filename=\"storage-test-file.txt\"");
		assertThat(response.getBody()).isEqualTo("Storage Integration Tests");
	}
}
