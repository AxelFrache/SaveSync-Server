package io.github.axelfrache.savesyncserver.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import io.github.axelfrache.savesyncserver.model.FileInfo;
import io.github.axelfrache.savesyncserver.model.Folder;
import io.github.axelfrache.savesyncserver.repository.FileInfoRepository;
import io.github.axelfrache.savesyncserver.repository.FolderRepository;
import io.github.axelfrache.savesyncserver.response.DeleteResponse;
import io.github.axelfrache.savesyncserver.response.UploadResponse;
import io.github.axelfrache.savesyncserver.service.FileStorageService;

@RestController
@RequestMapping("/api/savesync")
public class FileInfoController {

	@Autowired
	FileStorageService storageService;

	@Autowired
	FolderRepository folderRepository;

	@Autowired
	FileInfoRepository fileInfoRepository;

	@PostMapping("/upload")
	public ResponseEntity<List<UploadResponse>> uploadFiles(@RequestPart("files") MultipartFile[] files,
			@RequestParam(required = false) Long parentFolderId) {
		String backupId = String.valueOf(System.currentTimeMillis());
		Folder parentFolder = null;

		if (parentFolderId != null) {
			parentFolder = folderRepository.findById(parentFolderId).orElse(null);
		}

		List<UploadResponse> responses = new ArrayList<>();

		for (MultipartFile file : files) {
			try {
				String relativePath = backupId + "/" + file.getOriginalFilename();
				storageService.saveAll(file, relativePath, parentFolder);

				String downloadUrl = MvcUriComponentsBuilder
						.fromMethodName(FileInfoController.class, "getFile", relativePath).build().toUri().toString();

				responses.add(new UploadResponse(file.getOriginalFilename(), downloadUrl, file.getContentType(), file.getSize()));
			} catch (Exception e) {
				e.printStackTrace();
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
			}
		}

		return ResponseEntity.ok(responses);
	}

	@GetMapping("/files")
	public ResponseEntity<List<FileInfo>> getListFiles(@RequestParam(required = false) Long parentFolderId) {
		List<FileInfo> fileInfos;
		if (parentFolderId == null) {
			fileInfos = storageService.readAll().collect(Collectors.toList());
		} else {
			Folder parentFolder = folderRepository.findById(parentFolderId).orElse(null);
			fileInfos = storageService.getFilesByParentFolder(parentFolder);
		}

		return ResponseEntity.status(HttpStatus.OK).body(fileInfos);
	}

	@GetMapping("/files/{fileName:.+}")
	public ResponseEntity<Resource> getFile(@PathVariable String fileName) {
		Resource file = storageService.read(fileName);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"").body(file);
	}

	@GetMapping("/backups")
	public ResponseEntity<List<String>> getBackups() {
		List<String> backups = fileInfoRepository.findAll()
				.stream()
				.map(FileInfo::getBackupId)
				.distinct()
				.collect(Collectors.toList());
		System.out.println("Backups: " + backups); // Ajout de journalisation
		return ResponseEntity.status(HttpStatus.OK).body(backups);
	}

	@GetMapping("/backups/{backupId}/files")
	public ResponseEntity<List<String>> getBackupFiles(@PathVariable String backupId) {
		List<String> backupFiles = fileInfoRepository.findAllByBackupId(backupId)
				.stream()
				.map(FileInfo::getFileName)
				.collect(Collectors.toList());
		System.out.println("Files in backup " + backupId + ": " + backupFiles); // Ajout de journalisation
		return ResponseEntity.status(HttpStatus.OK).body(backupFiles);
	}

	@PostMapping("/files/delete")
	public ResponseEntity<DeleteResponse> deleteFileFromBackup(@RequestParam String backupId, @RequestParam String filePath) {
		try {
			String decodedFileName = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
			System.out.println("Received request to delete file: " + decodedFileName + " from backup: " + backupId);
			boolean existed = storageService.deleteFromBackup(backupId, decodedFileName);
			System.out.println("Deletion status: " + existed);

			if (existed) {
				// Après la suppression réussie, actualisez la liste des fichiers pour cette sauvegarde
				List<String> updatedBackupFiles = fileInfoRepository.findAllByBackupId(backupId)
						.stream()
						.map(FileInfo::getFileName)
						.collect(Collectors.toList());
				System.out.println("Updated files in backup " + backupId + ": " + updatedBackupFiles); // Journalisation de la liste mise à jour

				return ResponseEntity.status(HttpStatus.OK)
						.body(new DeleteResponse("File " + decodedFileName + " from backup " + backupId + " has been deleted successfully"));
			}

			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new DeleteResponse("File " + decodedFileName + " from backup " + backupId + " not found"));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new DeleteResponse("Failed to delete " + filePath + " from backup " + backupId + ": " + e.getMessage()));
		}
	}

	@PostMapping("/backups/delete")
	public ResponseEntity<DeleteResponse> deleteBackup(@RequestParam String backupId) {
		try {
			boolean existed = storageService.deleteBackup(backupId);

			if (existed) {
				return ResponseEntity.status(HttpStatus.OK).body(new DeleteResponse("Backup " + backupId + " has been deleted successfully"));
			}

			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new DeleteResponse("Backup " + backupId + " not found"));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new DeleteResponse("Failed to delete " + backupId + ": " + e.getMessage()));
		}
	}

	@GetMapping("/backups/{version}")
	public ResponseEntity<List<FileInfo>> getFilesFromBackup(@PathVariable String version) {
		List<FileInfo> fileInfos = storageService.readAllFromVersion(version).collect(Collectors.toList());
		return ResponseEntity.status(HttpStatus.OK).body(fileInfos);
	}
}
