package org.kduda.greedy.controller;

import org.kduda.greedy.model.ExploreRequestModel;
import org.kduda.greedy.repository.data.FileRepository;
import org.kduda.greedy.repository.rules.RulesRepository;
import org.kduda.greedy.service.exploration.ExplorationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ExploreController {

	private final FileRepository fileRepository;
	private final RulesRepository rulesRepository;
	private final ExplorationService explorationService;

	public ExploreController(FileRepository fileRepository, RulesRepository rulesRepository, ExplorationService explorationService) {
		this.fileRepository = fileRepository;
		this.rulesRepository = rulesRepository;
		this.explorationService = explorationService;
	}

	@GetMapping("/explore/{id}")
	public String explore(@PathVariable("id") String id, Model model) {
		model.addAttribute("dataFile", fileRepository.listById(id));
		model.addAttribute("files", rulesRepository.listByParent(id));
		return "explore";
	}

	@PostMapping("/explore/{id}")
	public String handleExploreRequest(@PathVariable("id") String id, @ModelAttribute ExploreRequestModel exploreDetails) {
		explorationService.exploreAndSave(id, exploreDetails);

		return "redirect:/explore/" + id;
	}

	@DeleteMapping("/explore/{parentId}/{id:.+}")
	public String deleteFile(@PathVariable String parentId, @PathVariable String id, RedirectAttributes redirectAttributes) {
		fileRepository.deleteById(id);

		redirectAttributes.addFlashAttribute("message", "Data file deleted successfully!");

		return "redirect:/explore/" + parentId;
	}
}
