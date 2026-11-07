package org.kduda.greedy.characterization;

import org.apache.commons.io.IOUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.Test;
import org.kduda.greedy.characterization.support.LegacyPipeline;
import org.kduda.greedy.characterization.support.LegacyPipeline.Format;
import org.kduda.greedy.characterization.support.LegacyPipeline.Heuristic;
import org.kduda.greedy.characterization.support.LegacyPipeline.Mode;
import org.kduda.greedy.golden.GoldenFiles;
import org.kduda.greedy.model.ExploreRequestModel;
import org.kduda.greedy.model.FileModel;
import org.kduda.greedy.repository.data.FileRepository;
import org.kduda.greedy.repository.rules.RulesRepository;
import org.kduda.greedy.service.exploration.DataExplorationService;
import org.kduda.greedy.spark.reader.mongo.SparkMongoService;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The golden generator uses LegacyPipeline instead of the service (no MongoDB). This proves the two produce the
 * same rules files, so golden outputs are what the legacy app would have stored.
 */
public class LegacyPipelineMatchesServiceTest {

	@Test
	public void pipelineProducesWhatTheServiceStores() throws IOException {
		File paperSample = new ClassPathResource("/files/paper-sample.csv").getFile();
		for (Heuristic heuristic : Heuristic.values()) {
			for (Mode mode : Mode.values()) {
				for (Format format : Format.values()) {
					assertSame(paperSample, heuristic, mode, format);
				}
			}
		}

		File complexSample = new ClassPathResource("/files/complex-sample.csv").getFile();
		for (Heuristic heuristic : Heuristic.values()) {
			assertSame(complexSample, heuristic, Mode.DT, Format.CSV);
		}
	}

	/** Same normalized content, or the same exception class when the legacy code fails (e.g. CSV with metadata, no rules). */
	private static void assertSame(File file, Heuristic heuristic, Mode mode, Format format) {
		Dataset<Row> csv = LegacyPipeline.readCsv(file);
		String fileName = "case" + format.suffix;

		String byService = outcome(fileName, () -> storedByService(csv, heuristic, mode, format));
		String byPipeline = outcome(fileName, () -> LegacyPipeline.format(LegacyPipeline.induce(csv, mode, heuristic), format));

		assertThat(byService).as("%s %s %s %s", file.getName(), heuristic, mode, format).isEqualTo(byPipeline);
	}

	private interface Producer {
		String produce() throws Exception;
	}

	private static String outcome(String fileName, Producer producer) {
		try {
			return GoldenFiles.normalize(fileName, producer.produce()).toString();
		} catch (Exception e) {
			return "error: " + e.getClass().getName();
		}
	}

	private static String storedByService(Dataset<Row> csv, Heuristic heuristic, Mode mode, Format format) throws IOException {
		SparkMongoService sparkMongo = mock(SparkMongoService.class);
		RulesRepository rulesRepository = mock(RulesRepository.class);
		FileRepository fileRepository = mock(FileRepository.class);
		when(sparkMongo.readCsvById("id1")).thenReturn(csv);
		when(fileRepository.listById("id1")).thenReturn(new FileModel("input.csv", "id1"));
		ExploreRequestModel request = new ExploreRequestModel();
		request.setHeuristics(heuristic.formValue);
		request.setType(mode.formValue);
		request.setOutput(format.formValue);

		new DataExplorationService(sparkMongo, rulesRepository, fileRepository).exploreAndSave("id1", request);

		ArgumentCaptor<InputStream> content = ArgumentCaptor.forClass(InputStream.class);
		ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> contentType = ArgumentCaptor.forClass(String.class);
		verify(rulesRepository).store(content.capture(), name.capture(), eq("id1"), contentType.capture());
		return IOUtils.toString(content.getValue(), StandardCharsets.UTF_8);
	}
}
