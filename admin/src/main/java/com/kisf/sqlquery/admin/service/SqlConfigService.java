package com.kisf.sqlquery.admin.service;

import com.kisf.sqlquery.core.entity.SqlConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SqlConfigService {

    SqlConfig save(SqlConfig config);

    SqlConfig update(Long id, SqlConfig config);

    void delete(Long id);

    Optional<SqlConfig> findById(Long id);

    Optional<SqlConfig> findBySqlPath(String sqlPath);

    Page<SqlConfig> list(String sqlPath, String datasourceId, String folder, Pageable pageable);

    List<String> getFolders();
}
