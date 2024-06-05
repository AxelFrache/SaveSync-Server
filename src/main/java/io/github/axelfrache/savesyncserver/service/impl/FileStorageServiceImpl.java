package io.github.axelfrache.savesyncserver.service.impl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import io.github.axelfrache.savesyncserver.model.FileInfo;
import io.github.axelfrache.savesyncserver.model.Folder;
import io.github.axelfrache.savesyncserver.repository.FileInfoRepository;
import io.github.axelfrache.savesyncserver.repository.FolderRepository;
import io.github.axelfrache.savesyncserver.service.FileStorageService;
import jakarta.transaction.Transactional;

@Service
public class FileStorageServiceImpl implements FileStorageService {
	private final Path root = Paths.get("storage");

	@Autowired
	private FileInfoRepository fileInfoRepository;

	@Autowired
	private FolderRepository folderRepository;

	@Override
	public void init() {
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			throw new IllegalStateException("Could not initialize storage", e);
		}
	}

	@Override
	public void save(MultipartFile file) {
		throw new UnsupportedOperationException("Single file save is not supported. Use saveAll instead.");
	}

	@Override
	public void saveAll(MultipartFile file, String relativePath, Folder parentFolder) {
		try {
			Path destinationPath = this.root.resolve(relativePath).normalize().toAbsolutePath();

			// Créer les répertoires parents si nécessaire
			Files.createDirectories(destinationPath.getParent());

			if (!destinationPath.startsWith(this.root.toAbsolutePath())) {
				throw new IllegalStateException("Cannot store file outside current directory.");
			}
			Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

			// Enregistrer les informations du fichier et du dossier dans la base de données
			FileInfo fileInfo = new FileInfo();
			fileInfo.setFileName(file.getOriginalFilename());
			fileInfo.setFilePath(relativePath);
			fileInfo.setBackupId(relativePath.split("/")[0]);
			fileInfo.setParentFolder(parentFolder);
			fileInfoRepository.save(fileInfo);

		} catch (IOException e) {
			throw new IllegalStateException("Failed to store file.", e);
		}
	}

	@Override
	public Resource read(String fileName) {
		try {
			Path file = root.resolve(fileName).normalize().toAbsolutePath();
			if (!file.startsWith(root.toAbsolutePath())) {
				throw new IllegalStateException("Resolution of the path is outside the storage directory");
			}
			Resource resource = new UrlResource(file.toUri());
			if (resource.exists() || resource.isReadable()) {
				return resource;
			} else {
				throw new IllegalStateException("File not found or not readable");
			}
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Failed to read file", e);
		}
	}

	@Override
	public boolean delete(String fileName) {
		try {
			Path file = root.resolve(fileName);
			return Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to delete file", e);
		}
	}

	@Override
	@Transactional
	public boolean deleteFromBackup(String backupId, String fileName) {
		try {
			Path fileToDelete = this.root.resolve(backupId).resolve(fileName);
			Files.deleteIfExists(fileToDelete);

			fileInfoRepository.deleteByBackupIdAndFilePath(backupId, fileName);

			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	@Transactional
	public void deleteAll() {
		FileSystemUtils.deleteRecursively(root.toFile());
	}

	@Override
	public Stream<FileInfo> readAll() {
		return fileInfoRepository.findAll().stream();
	}

	@Override
	public Stream<FileInfo> readAllFromVersion(String version) {
		return fileInfoRepository.findByBackupId(version).stream();
	}

	@Override
	@Transactional
	public boolean deleteBackup(String backupId) {
		try {
			Path backupPath = root.resolve(backupId).normalize().toAbsolutePath();
			FileSystemUtils.deleteRecursively(backupPath);
			boolean exists = !Files.exists(backupPath);

			if (exists) {
				fileInfoRepository.deleteByBackupId(backupId);
			}

			return exists;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to delete backup", e);
		}
	}

	@Override
	public List<FileInfo> getFilesByParentFolder(Folder parentFolder) {
		return fileInfoRepository.findByParentFolder(parentFolder);
	}
}
