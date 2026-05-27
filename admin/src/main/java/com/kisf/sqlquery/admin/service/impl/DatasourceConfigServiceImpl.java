package com.kisf.sqlquery.admin.service.impl;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import com.kisf.sqlquery.admin.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.admin.service.DatasourceConfigService;
import com.kisf.sqlquery.admin.util.AesUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DatasourceConfigServiceImpl implements DatasourceConfigService {

    private final DatasourceConfigRepository repo;

    public DatasourceConfigServiceImpl(DatasourceConfigRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public DatasourceConfig save(DatasourceConfig config) {
        config.setPassword(AesUtils.encrypt(config.getPassword()));
        return repo.save(config);
    }

    @Override
    @Transactional
    public DatasourceConfig update(String id, DatasourceConfig config) {
        DatasourceConfig existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DatasourceConfig not found: " + id));
        existing.setDriverClass(config.getDriverClass());
        existing.setJdbcUrl(config.getJdbcUrl());
        existing.setUsername(config.getUsername());
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            existing.setPassword(AesUtils.encrypt(config.getPassword()));
        }
        existing.setPoolSize(config.getPoolSize());
        existing.setExtra(config.getExtra());
        existing.setEnabled(config.getEnabled());
        return repo.save(existing);
    }

    @Override
    @Transactional
    public void delete(String id) {
        repo.deleteById(id);
    }

    @Override
    public Optional<DatasourceConfig> findById(String id) {
        return repo.findById(id);
    }

    @Override
    public List<DatasourceConfig> list() {
        return repo.findAll();
    }
}
