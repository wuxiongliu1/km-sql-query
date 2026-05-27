package com.kisf.sqlquery.admin.service;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;

import java.util.List;
import java.util.Optional;

public interface DatasourceConfigService {

    DatasourceConfig save(DatasourceConfig config);

    DatasourceConfig update(String id, DatasourceConfig config);

    void delete(String id);

    Optional<DatasourceConfig> findById(String id);

    List<DatasourceConfig> list();
}
