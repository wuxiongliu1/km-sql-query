package com.kisf.sqlquery.core.engine;

import com.kisf.sqlquery.core.entity.SqlConfig;
import com.kisf.sqlquery.core.repo.SqlConfigRepository;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SqlExecutor {

    private final SqlConfigRepository configRepo;
    private final DataSourceCache dataSourceCache;
    private final MyBatisScriptEngine scriptEngine;

    public SqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                       MyBatisScriptEngine scriptEngine) {
        this.configRepo = configRepo;
        this.dataSourceCache = dataSourceCache;
        this.scriptEngine = scriptEngine;
    }

    public ExecuteResult execute(String sqlPath, Map<String, Object> params) {
        long start = System.currentTimeMillis();

        SqlConfig config = configRepo.findBySqlPath(sqlPath)
                .orElseThrow(() -> new IllegalArgumentException("sqlPath not found: " + sqlPath));

        if (!config.getEnabled()) {
            throw new IllegalArgumentException("sqlPath is disabled: " + sqlPath);
        }

        SqlSessionFactory ssf = dataSourceCache.getOrCreate(config.getDatasourceId());
        Configuration cfg = ssf.getConfiguration();

        SqlCommandType cmdType = scriptEngine.detectCommandType(config.getSqlTemplate());
        SqlSource sqlSource = scriptEngine.parse(sqlPath, config.getSqlTemplate(), cfg);

        // Always re-register to pick up hot-reloaded templates.
        // parse() uses computeIfAbsent, so SqlSource is only re-created on cache miss.
        registerMappedStatement(cfg, sqlPath, sqlSource, cmdType);

        try (SqlSession session = ssf.openSession()) {
            try {
                Object result;
                switch (cmdType) {
                    case SELECT:
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> rows = session.selectList(sqlPath, params);
                        result = rows;
                        break;
                    case INSERT:
                        result = session.insert(sqlPath, params);
                        session.commit();
                        break;
                    case UPDATE:
                        result = session.update(sqlPath, params);
                        session.commit();
                        break;
                    case DELETE:
                        result = session.delete(sqlPath, params);
                        session.commit();
                        break;
                    default:
                        throw new ScriptParseException("Unsupported command type: " + cmdType);
                }

                long elapsed = System.currentTimeMillis() - start;
                return new ExecuteResult(result, elapsed);
            } catch (RuntimeException | Error e) {
                session.rollback();
                throw e;
            }
        }
    }

    public void invalidate(String sqlPath) {
        scriptEngine.invalidate(sqlPath);
        // Remove MappedStatement from all datasource configurations (hot-reload support)
        SqlConfig config = configRepo.findBySqlPath(sqlPath).orElse(null);
        if (config != null) {
            try {
                SqlSessionFactory ssf = dataSourceCache.getOrCreate(config.getDatasourceId());
                removeMappedStatement(ssf.getConfiguration(), sqlPath);
            } catch (Exception ignored) {
                // best-effort removal
            }
        }
    }

    /**
     * Register a parsed SqlSource as a MyBatis MappedStatement so that
     * {@code session.selectList/insert/update/delete(statementId, params)} can locate it.
     */
    private void registerMappedStatement(Configuration cfg, String id,
                                         SqlSource sqlSource, SqlCommandType cmdType) {
        // Remove stale entry first (no-op if absent)
        removeMappedStatement(cfg, id);

        MappedStatement.Builder msBuilder = new MappedStatement.Builder(cfg, id, sqlSource, cmdType);
        if (cmdType == SqlCommandType.SELECT) {
            ResultMap resultMap = new ResultMap.Builder(cfg, id + "-InlineResultMap",
                    Map.class, new ArrayList<ResultMapping>()).build();
            msBuilder.resultMaps(Collections.singletonList(resultMap));
        }
        MappedStatement ms = msBuilder.build();
        cfg.addMappedStatement(ms);
    }

    /**
     * Remove a MappedStatement from a MyBatis Configuration by its id.
     * Uses reflection because {@code mappedStatements} is a protected StrictMap
     * with no public remove method.
     */
    @SuppressWarnings("unchecked")
    private void removeMappedStatement(Configuration cfg, String id) {
        try {
            Field field = Configuration.class.getDeclaredField("mappedStatements");
            field.setAccessible(true);
            Map<String, MappedStatement> map = (Map<String, MappedStatement>) field.get(cfg);
            map.remove(id);
        } catch (Exception e) {
            // Statement not present — no-op
        }
    }

    public static class ExecuteResult {
        private final Object data;
        private final long elapsed;

        public ExecuteResult(Object data, long elapsed) {
            this.data = data;
            this.elapsed = elapsed;
        }

        public Object getData() { return data; }
        public long getElapsed() { return elapsed; }
        public boolean isList() { return data instanceof List; }
        @SuppressWarnings("rawtypes")
        public int getTotal() { return data instanceof List ? ((List) data).size() : 0; }
    }

    public static class ScriptParseException extends RuntimeException {
        public ScriptParseException(String message) {
            super(message);
        }
    }
}
