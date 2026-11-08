package org.kduda.greedy.characterization;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.kduda.greedy.characterization.support.LegacyTables.table;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how DataExplorationService names, types and fills the rules file it stores. Storage and the Mongo-backed
 * CSV read are mocked; the Spark pipeline and formatters run for real.
 */
public class ExplorationServiceTest {

	private SparkMongoService sparkMongo;
	private RulesRepository rulesRepository;
	private FileRepository fileRepository;
	private DataExplorationService service;

	@Before
	public void setUp() {
		sparkMongo = mock(SparkMongoService.class);
		rulesRepository = mock(RulesRepository.class);
		fileRepository = mock(FileRepository.class);
		service = new DataExplorationService(sparkMongo, rulesRepository, fileRepository);
		when(sparkMongo.readCsvById("id1")).thenReturn(input());
	}

	// every column has two values, so both "dt" and "is" produce rules (a constant decision column yields none,
	// and CSV with metadata then fails: see OutputFormatTest.csvWithMetadataFailsWhenThereAreNoRules)
	private static org.apache.spark.sql.Dataset<org.apache.spark.sql.Row> input() {
		return table("a,b,d", "0,0,y", "0,1,y", "1,0,n", "1,1,n");
	}

	private Stored explore(String fileName, String heuristics, String type, String output) throws IOException {
		when(fileRepository.listById("id1")).thenReturn(new FileModel(fileName, "id1"));
		ExploreRequestModel request = new ExploreRequestModel();
		request.setHeuristics(heuristics);
		request.setType(type);
		request.setOutput(output);

		service.exploreAndSave("id1", request);

		ArgumentCaptor<InputStream> content = ArgumentCaptor.forClass(InputStream.class);
		ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> contentType = ArgumentCaptor.forClass(String.class);
		verify(rulesRepository).store(content.capture(), name.capture(), eq("id1"), contentType.capture());
		return new Stored(name.getValue(), contentType.getValue(), IOUtils.toString(content.getValue(), StandardCharsets.UTF_8));
	}

	private static String expectedContent(Mode mode, Heuristic heuristic, Format format) {
		return LegacyPipeline.format(LegacyPipeline.induce(input(), mode, heuristic), format);
	}

	@Test
	public void storesCsvUnderTruncatedNameAndTextCsv() throws IOException {
		Stored stored = explore("my.data.v2.csv", "m", "dt", "csv");

		assertThat(stored.name).isEqualTo("my-dt-rules-m.csv");
		assertThat(stored.contentType).isEqualTo("text/csv");
		assertThat(GoldenFiles.normalize(stored.name, stored.content))
			.isEqualTo(GoldenFiles.normalize(stored.name, expectedContent(Mode.DT, Heuristic.M, Format.CSV)));
	}

	@Test
	public void storesRsesAsRulFileAndTextPlain() throws IOException {
		Stored stored = explore("paper.csv", "maxCov", "is", "rses");

		assertThat(stored.name).isEqualTo("paper-is-rules-maxCov.rul");
		assertThat(stored.contentType).isEqualTo("text/plain");
		assertThat(GoldenFiles.normalize(stored.name, stored.content))
			.isEqualTo(GoldenFiles.normalize(stored.name, expectedContent(Mode.IS, Heuristic.MAX_COV, Format.RSES)));
	}

	@Test
	public void storesCsvWithMetadataSuffix() throws IOException {
		Stored stored = explore("paper.csv", "poly", "dt", "csvm");

		assertThat(stored.name).isEqualTo("paper-dt-rules-poly-wmetadata.csv");
		assertThat(stored.contentType).isEqualTo("text/csv");
		assertThat(GoldenFiles.normalize(stored.name, stored.content))
			.isEqualTo(GoldenFiles.normalize(stored.name, expectedContent(Mode.DT, Heuristic.POLY, Format.CSVM)));
	}

	@Test
	public void unknownHeuristicAndOutputFallBackToMAndCsv() throws IOException {
		Stored stored = explore("paper.csv", "xyz", "dt", "xyz");

		assertThat(stored.name).isEqualTo("paper-dt-rules-m.csv");
		assertThat(stored.contentType).isEqualTo("text/csv");
	}

	@Test
	public void missingTypeIsTreatedAsDecisionTableAndNamedNull() throws IOException {
		assertThat(explore("paper.csv", "m", null, "csv").name).isEqualTo("paper-null-rules-m.csv");
	}

	@Test
	public void nameWithoutDotIsKeptWhole() throws IOException {
		assertThat(explore("data", "m", "dt", "csv").name).isEqualTo("data-dt-rules-m.csv");
	}

	@Test
	public void nameStartingWithDotLosesEverything() throws IOException {
		assertThat(explore(".hidden.csv", "m", "dt", "csv").name).isEqualTo("-dt-rules-m.csv");
	}

	@Test
	public void missingHeuristicFailsWithNullPointer() {
		assertThat(catchThrowable(() -> explore("paper.csv", null, "dt", "csv"))).isInstanceOf(NullPointerException.class);
		verify(rulesRepository, never()).store(any(InputStream.class), anyString(), anyString(), anyString());
	}

	@Test
	public void missingOutputFailsWithNullPointer() {
		assertThat(catchThrowable(() -> explore("paper.csv", "m", "dt", null))).isInstanceOf(NullPointerException.class);
		verify(rulesRepository, never()).store(any(InputStream.class), anyString(), anyString(), anyString());
	}

	private static final class Stored {
		final String name;
		final String contentType;
		final String content;

		Stored(String name, String contentType, String content) {
			this.name = name;
			this.contentType = contentType;
			this.content = content;
		}
	}
}
