package org.kduda.greedy.controller;

import org.apache.commons.lang3.tuple.Pair;
import org.kduda.greedy.exception.StorageFileNotFoundException;
import org.kduda.greedy.repository.data.FileRepository;
import org.kduda.greedy.repository.rules.RulesRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FileController {

	private final FileRepository fileRepository;
	private final RulesRepository rulesRepository;

	public FileController(FileRepository fileRepository, RulesRepository rulesRepository) {
		this.fileRepository = fileRepository;
		this.rulesRepository = rulesRepository;
	}

	@GetMapping("/file/{id:.+}")
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String id) {
		Pair<String, Resource> file = fileRepository.loadResourceById(id);
		if (file != null) {
			return ResponseEntity
				.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getKey() + "\"")
				.body(file.getValue());
		}
		return ResponseEntity.notFound().build();
	}

	@PostMapping("/file")
	public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
		fileRepository.store(file);
		redirectAttributes.addFlashAttribute("message", "File: " + file.getOriginalFilename() + " uploaded successfully!");

		return "redirect:/";
	}

	@DeleteMapping("/file/{id:.+}")
	public String deleteFile(@PathVariable String id, RedirectAttributes redirectAttributes) {
		fileRepository.deleteById(id);
		rulesRepository.deleteByParent(id);

		redirectAttributes.addFlashAttribute("message", "Data file deleted successfully!");

		return "redirect:/";
	}

	@ExceptionHandler(StorageFileNotFoundException.class)
	public ResponseEntity handleStorageFileNotFound(StorageFileNotFoundException ex) {
		return ResponseEntity.notFound().build();
	}

}