package com.kisf.sqlquery.admin.service.impl;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import com.kisf.sqlquery.admin.repo.SqlConfigRepository;
import com.kisf.sqlquery.admin.service.SqlConfigService;
import com.kisf.sqlquery.core.engine.DmlSafetyValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SqlConfigServiceImpl implements SqlConfigService {

    private final SqlConfigRepository repo;
    private final Runnable onConfigChange;
    private final DmlSafetyValidator dmlSafetyValidator;

    public SqlConfigServiceImpl(SqlConfigRepository repo) {
        this.repo = repo;
        this.onConfigChange = () -> {};
        this.dmlSafetyValidator = new DmlSafetyValidator();
    }

    public SqlConfigServiceImpl(SqlConfigRepository repo, Runnable onConfigChange,
                                 DmlSafetyValidator dmlSafetyValidator) {
        this.repo = repo;
        this.onConfigChange = onConfigChange;
        this.dmlSafetyValidator = dmlSafetyValidator;
    }

    @Override
    @Transactional
    public SqlConfig save(SqlConfig config) {
        dmlSafetyValidator.validate(config.getSqlTemplate());
        SqlConfig saved = repo.save(config);
        onConfigChange.run();
        return saved;
    }

    @Override
    @Transactional
    public SqlConfig update(Long id, SqlConfig config) {
        dmlSafetyValidator.validate(config.getSqlTemplate());
        SqlConfig existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SqlConfig not found: " + id));
        existing.setSqlPath(config.getSqlPath());
        existing.setSqlTemplate(config.getSqlTemplate());
        existing.setDatasourceId(config.getDatasourceId());
        existing.setDescription(config.getDescription());
        existing.setFolder(config.getFolder());
        existing.setEnabled(config.getEnabled());
        SqlConfig updated = repo.save(existing);
        onConfigChange.run();
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repo.findById(id).ifPresent(c -> {
            repo.delete(c);
            onConfigChange.run();
        });
    }

    @Override
    public Optional<SqlConfig> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    public Optional<SqlConfig> findBySqlPath(String sqlPath) {
        return repo.findBySqlPath(sqlPath);
    }

    @Override
    public Page<SqlConfig> list(String sqlPath, String datasourceId, String folder, Pageable pageable) {
        Page<SqlConfig> paged = repo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sqlPath != null && !sqlPath.isEmpty()) {
                predicates.add(cb.like(root.get("sqlPath"), "%" + sqlPath + "%"));
            }
            if (datasourceId != null && !datasourceId.isEmpty()) {
                predicates.add(cb.equal(root.get("datasourceId"), datasourceId));
            }
            if (folder != null && !folder.isEmpty()) {
                predicates.add(cb.equal(root.get("folder"), folder));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
        return paged;
    }

    @Override
    public List<String> getFolders() {
        return repo.findDistinctFolders();
    }
}
