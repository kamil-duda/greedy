package org.kduda.greedy.characterization;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kduda.greedy.model.ExploreRequestModel;
import org.kduda.greedy.repository.data.FileRepository;
import org.kduda.greedy.repository.rules.RulesRepository;
import org.kduda.greedy.service.exploration.ExplorationService;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.NestedServletException;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the legacy controllers' HTTP behavior, including the findings from running the app on 2026-10-27:
 * raw file names in Content-Disposition, server errors for unknown ids, and Actuator endpoints open without login.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class WebLayerTest {

	@Autowired private MockMvc mvc;

	@MockBean private FileRepository fileRepository;
	@MockBean private RulesRepository rulesRepository;
	@MockBean private ExplorationService explorationService;

	@Test
	public void downloadPutsRawFileNameIntoContentDisposition() throws Exception {
		Resource resource = new ByteArrayResource("x".getBytes());
		given(fileRepository.loadResourceById("r1")).willReturn(Pair.of("a\"b.rul", resource));

		mvc.perform(get("/file/r1").accept(MediaType.ALL))
		   .andExpect(status().isOk())
		   .andExpect(header().string("Content-Disposition", "attachment; filename=\"a\"b.rul\""));
	}

	@Test
	public void downloadOfUnknownIdIsNotFound() throws Exception {
		given(fileRepository.loadResourceById("r1")).willReturn(null);

		mvc.perform(get("/file/r1")).andExpect(status().isNotFound());
	}

	@Test
	public void uploadRedirectsToIndexWithFlashMessage() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "t.csv", "text/plain", "a,d\n1,y".getBytes());

		mvc.perform(fileUpload("/file").file(file))
		   .andExpect(status().isFound())
		   .andExpect(redirectedUrl("/"))
		   .andExpect(flash().attribute("message", "File: t.csv uploaded successfully!"));
	}

	@Test
	public void deletingDataFileAlsoDeletesItsRules() throws Exception {
		mvc.perform(delete("/file/d1"))
		   .andExpect(status().isFound())
		   .andExpect(redirectedUrl("/"));

		verify(fileRepository).deleteById("d1");
		verify(rulesRepository).deleteByParent("d1");
	}

	@Test
	public void exploringUnknownDataFileIsAServerError() {
		// DataFileRepository.listById calls Optional.get() on a missing file
		given(fileRepository.listById("nope")).willThrow(new NoSuchElementException("No value present"));

		Throwable thrown = catchThrowable(() -> mvc.perform(get("/explore/nope")));

		assertThat(thrown).isInstanceOf(NestedServletException.class).hasCauseInstanceOf(NoSuchElementException.class);
	}

	@Test
	public void postingExploreRunsTheJobBeforeRedirecting() throws Exception {
		mvc.perform(post("/explore/d1").param("heuristics", "m").param("type", "dt").param("output", "csv"))
		   .andExpect(status().isFound())
		   .andExpect(redirectedUrl("/explore/d1"));

		ArgumentCaptor<ExploreRequestModel> request = ArgumentCaptor.forClass(ExploreRequestModel.class);
		verify(explorationService).exploreAndSave(eq("d1"), request.capture());
		assertThat(request.getValue().getHeuristics()).isEqualTo("m");
		assertThat(request.getValue().getType()).isEqualTo("dt");
		assertThat(request.getValue().getOutput()).isEqualTo("csv");
	}

	@Test
	public void deletingRulesFileGoesThroughTheDataRepository() throws Exception {
		mvc.perform(delete("/explore/d1/r1"))
		   .andExpect(status().isFound())
		   .andExpect(redirectedUrl("/explore/d1"));

		verify(fileRepository).deleteById("r1");
	}

	@Test
	public void actuatorEnvAndDumpAreOpenWithoutLogin() throws Exception {
		mvc.perform(get("/env")).andExpect(status().isOk());
		mvc.perform(get("/dump")).andExpect(status().isOk());
	}
}
