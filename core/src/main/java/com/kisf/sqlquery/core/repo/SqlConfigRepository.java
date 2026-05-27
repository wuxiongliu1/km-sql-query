package com.kisf.sqlquery.core.repo;

import com.kisf.sqlquery.core.entity.SqlConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SqlConfigRepository extends JpaRepository<SqlConfig, Long>,
        JpaSpecificationExecutor<SqlConfig> {

    Optional<SqlConfig> findBySqlPath(String sqlPath);

    boolean existsBySqlPath(String sqlPath);

    @Query("SELECT DISTINCT s.folder FROM SqlConfig s WHERE s.folder IS NOT NULL AND s.folder != '' ORDER BY s.folder")
    List<String> findDistinctFolders();
}
