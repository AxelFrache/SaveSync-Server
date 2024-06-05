package io.github.axelfrache.savesyncserver.service;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import io.github.axelfrache.savesyncserver.model.FileInfo;
import io.github.axelfrache.savesyncserver.model.Folder;

public interface FileStorageService {
	void init();

	void save(MultipartFile file);

	void saveAll(MultipartFile file, String relativePath, Folder parentFolder);

	Resource read(String fileName);

	boolean delete(String fileName);

	boolean deleteFromBackup(String backupId, String fileName);

	void deleteAll();

	Stream<FileInfo> readAll();

	Stream<FileInfo> readAllFromVersion(String version);

	boolean deleteBackup(String backupId);

	List<FileInfo> getFilesByParentFolder(Folder parentFolder);
}
