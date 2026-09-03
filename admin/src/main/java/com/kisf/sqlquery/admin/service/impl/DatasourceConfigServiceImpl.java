package com.kisf.sqlquery.admin.service.impl;

import com.kisf.sqlquery.core.entity.DatasourceConfig;
import com.kisf.sqlquery.core.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.admin.service.DatasourceConfigService;
import com.kisf.sqlquery.core.util.AesUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Service
public class DatasourceConfigServiceImpl implements DatasourceConfigService {

    private final DatasourceConfigRepository repo;
    private final Consumer<String> onConfigChange;
    private final Predicate<String> isReferenced;

    public DatasourceConfigServiceImpl(DatasourceConfigRepository repo) {
        this(repo, id -> {}, id -> false);
    }

    public DatasourceConfigServiceImpl(DatasourceConfigRepository repo, Consumer<String> onConfigChange) {
        this(repo, onConfigChange, id -> false);
    }

    public DatasourceConfigServiceImpl(DatasourceConfigRepository repo, Consumer<String> onConfigChange,
                                       Predicate<String> isReferenced) {
        this.repo = repo;
        this.onConfigChange = onConfigChange;
        this.isReferenced = isReferenced;
    }

    @Override
    @Transactional
    public DatasourceConfig save(DatasourceConfig config) {
        config.setPassword(AesUtils.encrypt(config.getPassword()));
        DatasourceConfig saved = repo.save(config);
        onConfigChange.accept(saved.getId());
        return saved;
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
        DatasourceConfig updated = repo.save(existing);
        onConfigChange.accept(id);
        return updated;
    }

    @Override
    @Transactional
    public void delete(String id) {
        if (isReferenced.test(id)) {
            throw new IllegalArgumentException(
                    "Datasource is referenced by SQL configurations: " + id);
        }
        repo.deleteById(id);
        onConfigChange.accept(id);
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
