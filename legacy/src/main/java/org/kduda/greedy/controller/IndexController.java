package org.kduda.greedy.controller;

import org.kduda.greedy.repository.data.FileRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
public class IndexController {
	private final FileRepository fileRepository;

	public IndexController(FileRepository fileRepository) {
		this.fileRepository = fileRepository;
	}

	@GetMapping("/")
	public String listFiles(Model model) throws IOException {
		model.addAttribute("files", fileRepository.listAll());
		return "index";
	}

}
