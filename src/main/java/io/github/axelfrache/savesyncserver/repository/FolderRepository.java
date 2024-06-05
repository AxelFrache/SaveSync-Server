package io.github.axelfrache.savesyncserver.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.axelfrache.savesyncserver.model.Folder;

public interface FolderRepository extends JpaRepository<Folder, Long> {
	List<Folder> findByParentFolder(Folder parentFolder);
}
