package io.github.axelfrache.savesyncserver.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.axelfrache.savesyncserver.model.FileInfo;
import io.github.axelfrache.savesyncserver.model.Folder;
import jakarta.transaction.Transactional;

public interface FileInfoRepository extends JpaRepository<FileInfo, Long> {
	List<FileInfo> findByBackupId(String backupId);

	List<FileInfo> findByParentFolder(Folder parentFolder);

	@Transactional
	void deleteByBackupIdAndFileName(String backupId, String fileName);

	void deleteByBackupId(String backupId);

	List<FileInfo> findAllByBackupId(String backupId);

	@Transactional
	void deleteByBackupIdAndFilePath(String backupId, String filePath);

}
