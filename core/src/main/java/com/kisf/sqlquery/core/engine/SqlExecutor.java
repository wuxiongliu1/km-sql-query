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
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class SqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutor.class);
    private static final Field MAPPED_STATEMENTS_FIELD = resolveMappedStatementsField();
    private static final AtomicLong TEST_STATEMENT_SEQUENCE = new AtomicLong();

    private final SqlConfigRepository configRepo;
    private final DataSourceCache dataSourceCache;
    private final MyBatisScriptEngine scriptEngine;
    private final DmlSafetyValidator dmlSafetyValidator;
    private final int queryTimeoutSeconds;
    private final int maxRows;
    private final SqlQueryMetrics metrics;

    public SqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                       MyBatisScriptEngine scriptEngine) {
        this(configRepo, dataSourceCache, scriptEngine, new DmlSafetyValidator(),
                30, 1000, SqlQueryMetrics.noop());
    }

    public SqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                       MyBatisScriptEngine scriptEngine, DmlSafetyValidator dmlSafetyValidator) {
        this(configRepo, dataSourceCache, scriptEngine, dmlSafetyValidator,
                30, 1000, SqlQueryMetrics.noop());
    }

    public SqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                       MyBatisScriptEngine scriptEngine, DmlSafetyValidator dmlSafetyValidator,
                       int queryTimeoutSeconds) {
        this(configRepo, dataSourceCache, scriptEngine, dmlSafetyValidator,
                queryTimeoutSeconds, 1000, SqlQueryMetrics.noop());
    }

    public SqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                       MyBatisScriptEngine scriptEngine, DmlSafetyValidator dmlSafetyValidator,
                       int queryTimeoutSeconds, int maxRows) {
        this(configRepo, dataSourceCache, scriptEngine, dmlSafetyValidator,
                queryTimeoutSeconds, maxRows, SqlQueryMetrics.noop());
    }

    public SqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                       MyBatisScriptEngine scriptEngine, DmlSafetyValidator dmlSafetyValidator,
                       int queryTimeoutSeconds, int maxRows, SqlQueryMetrics metrics) {
        this.configRepo = configRepo;
        this.dataSourceCache = dataSourceCache;
        this.scriptEngine = scriptEngine;
        this.dmlSafetyValidator = dmlSafetyValidator;
        if (queryTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("queryTimeoutSeconds must be greater than zero");
        }
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be greater than zero");
        }
        this.maxRows = maxRows;
        this.metrics = metrics;
    }

    public ExecuteResult execute(String sqlPath, Map<String, Object> params) {
        return execute(sqlPath, params, null, null);
    }

    public ExecuteResult execute(String sqlPath, Map<String, Object> params,
                                 Integer requestedPage, Integer requestedSize) {
        long start = System.currentTimeMillis();
        ExecuteResult result = null;
        boolean success = false;
        try {
            result = doExecute(sqlPath, params, requestedPage, requestedSize, start);
            success = true;
            return result;
        } finally {
            int rows = result == null ? 0 : (result.isList() ? result.getTotal() : affectedRows(result));
            metrics.record(sqlPath, System.currentTimeMillis() - start, success, rows);
        }
    }

    private ExecuteResult doExecute(String sqlPath, Map<String, Object> params,
                                    Integer requestedPage, Integer requestedSize, long start) {
        if (sqlPath == null || sqlPath.trim().isEmpty()) {
            throw new IllegalArgumentException("sqlPath must not be blank");
        }
        Map<String, Object> effectiveParams = params == null ? Collections.emptyMap() : params;
        QueryWindow queryWindow = QueryWindow.create(requestedPage, requestedSize, maxRows);

        SqlConfig config = configRepo.findBySqlPath(sqlPath)
                .orElseThrow(() -> new IllegalArgumentException("sqlPath not found: " + sqlPath));

        if (!config.getEnabled()) {
            throw new IllegalArgumentException("sqlPath is disabled: " + sqlPath);
        }

        SqlSessionFactory ssf = dataSourceCache.getOrCreate(config.getDatasourceId());
        Configuration cfg = ssf.getConfiguration();

        dmlSafetyValidator.validate(config.getSqlTemplate());
        SqlCommandType cmdType = scriptEngine.detectCommandType(config.getSqlTemplate());
        SqlSource sqlSource = scriptEngine.parse(sqlPath, config.getSqlTemplate(), cfg);
        dmlSafetyValidator.validateRenderedSql(cmdType, sqlSource.getBoundSql(effectiveParams).getSql());

        // Reuse the registered statement until template invalidation produces a new SqlSource.
        registerMappedStatement(cfg, sqlPath, sqlSource, cmdType);

        try (SqlSession session = ssf.openSession()) {
            try {
                StatementResult result = executeStatement(
                        session, sqlPath, effectiveParams, cmdType, queryWindow);
                if (cmdType != SqlCommandType.SELECT) {
                    session.commit();
                }
                long elapsed = System.currentTimeMillis() - start;
                return new ExecuteResult(result.data, elapsed, result.page, result.size,
                        result.hasMore, result.truncated);
            } catch (RuntimeException | Error e) {
                rollbackPreservingOriginalFailure(session, e);
                throw e;
            }
        }
    }

    private int affectedRows(ExecuteResult result) {
        return result.getData() instanceof Number ? ((Number) result.getData()).intValue() : 0;
    }

    /**
     * Executes an unpublished template in an always-rollback session. The template
     * is registered under a unique statement id and removed immediately afterward.
     */
    public ExecuteResult testExecute(String datasourceId, String sqlTemplate,
                                     Map<String, Object> params, Integer page, Integer size) {
        if (datasourceId == null || datasourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("datasourceId must not be blank");
        }
        if (sqlTemplate == null || sqlTemplate.trim().isEmpty()) {
            throw new IllegalArgumentException("sqlTemplate must not be blank");
        }

        String statementId = "__km_sql_test_" + TEST_STATEMENT_SEQUENCE.incrementAndGet();
        Map<String, Object> effectiveParams = params == null ? Collections.emptyMap() : params;
        QueryWindow queryWindow = QueryWindow.create(page, size, maxRows);
        SqlSessionFactory factory = dataSourceCache.getOrCreate(datasourceId);
        Configuration configuration = factory.getConfiguration();
        long start = System.currentTimeMillis();

        try {
            dmlSafetyValidator.validate(sqlTemplate);
            SqlCommandType commandType = scriptEngine.detectCommandType(sqlTemplate);
            SqlSource sqlSource = scriptEngine.parse(statementId, sqlTemplate, configuration);
            dmlSafetyValidator.validateRenderedSql(
                    commandType, sqlSource.getBoundSql(effectiveParams).getSql());
            registerMappedStatement(configuration, statementId, sqlSource, commandType);

            try (SqlSession session = factory.openSession()) {
                try {
                    StatementResult result = executeStatement(
                            session, statementId, effectiveParams, commandType, queryWindow);
                    session.rollback();
                    return new ExecuteResult(result.data, System.currentTimeMillis() - start,
                            result.page, result.size, result.hasMore, result.truncated);
                } catch (RuntimeException | Error e) {
                    rollbackPreservingOriginalFailure(session, e);
                    throw e;
                }
            }
        } finally {
            scriptEngine.invalidate(statementId);
            synchronized (configuration) {
                removeMappedStatement(configuration, statementId);
            }
        }
    }

    private StatementResult executeStatement(SqlSession session, String sqlPath,
                                             Map<String, Object> params, SqlCommandType cmdType,
                                             QueryWindow queryWindow) {
        switch (cmdType) {
            case SELECT:
                List<Object> rows = session.selectList(sqlPath, params,
                        new RowBounds(queryWindow.offset, queryWindow.limit + 1));
                boolean hasMore = rows.size() > queryWindow.limit;
                if (hasMore) {
                    rows = new ArrayList<>(rows.subList(0, queryWindow.limit));
                }
                return new StatementResult(rows, queryWindow.page, queryWindow.limit,
                        hasMore, hasMore && !queryWindow.explicitPagination);
            case INSERT:
                return StatementResult.write(session.insert(sqlPath, params));
            case UPDATE:
                return StatementResult.write(session.update(sqlPath, params));
            case DELETE:
                return StatementResult.write(session.delete(sqlPath, params));
            default:
                throw new ScriptParseException("Unsupported command type: " + cmdType);
        }
    }

    private static class QueryWindow {
        private final int page;
        private final int limit;
        private final int offset;
        private final boolean explicitPagination;

        private QueryWindow(int page, int limit, boolean explicitPagination) {
            this.page = page;
            this.limit = limit;
            this.offset = page * limit;
            this.explicitPagination = explicitPagination;
        }

        private static QueryWindow create(Integer requestedPage, Integer requestedSize, int maxRows) {
            boolean explicit = requestedPage != null || requestedSize != null;
            int page = requestedPage == null ? 0 : requestedPage;
            int size = requestedSize == null ? maxRows : requestedSize;
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
            if (size <= 0 || size > maxRows) {
                throw new IllegalArgumentException("size must be between 1 and " + maxRows);
            }
            if (page > Integer.MAX_VALUE / size) {
                throw new IllegalArgumentException("page and size produce an invalid offset");
            }
            return new QueryWindow(page, size, explicit);
        }
    }

    private static class StatementResult {
        private final Object data;
        private final int page;
        private final int size;
        private final boolean hasMore;
        private final boolean truncated;

        private StatementResult(Object data, int page, int size,
                                boolean hasMore, boolean truncated) {
            this.data = data;
            this.page = page;
            this.size = size;
            this.hasMore = hasMore;
            this.truncated = truncated;
        }

        private static StatementResult write(Object data) {
            return new StatementResult(data, 0, 0, false, false);
        }
    }

    private void rollbackPreservingOriginalFailure(SqlSession session, Throwable originalFailure) {
        try {
            session.rollback();
        } catch (RuntimeException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    public void invalidate(String sqlPath) {
        scriptEngine.invalidate(sqlPath);
        // Remove MappedStatement from all datasource configurations (hot-reload support)
        SqlConfig config = configRepo.findBySqlPath(sqlPath).orElse(null);
        if (config != null) {
            try {
                SqlSessionFactory ssf = dataSourceCache.getOrCreate(config.getDatasourceId());
                Configuration cfg = ssf.getConfiguration();
                synchronized (cfg) {
                    removeMappedStatement(cfg, sqlPath);
                }
            } catch (Exception ignored) {
                log.warn("Failed to invalidate mapped statement for sqlPath: {}", sqlPath, ignored);
            }
        }
    }

    /**
     * Register a parsed SqlSource as a MyBatis MappedStatement so that
     * {@code session.selectList/insert/update/delete(statementId, params)} can locate it.
     */
    private void registerMappedStatement(Configuration cfg, String id,
                                         SqlSource sqlSource, SqlCommandType cmdType) {
        synchronized (cfg) {
            if (cfg.hasStatement(id, false)
                    && cfg.getMappedStatement(id, false).getSqlSource() == sqlSource) {
                return;
            }

            removeMappedStatement(cfg, id);
            MappedStatement.Builder msBuilder = new MappedStatement.Builder(cfg, id, sqlSource, cmdType)
                    .timeout(queryTimeoutSeconds);
            if (cmdType == SqlCommandType.SELECT) {
                ResultMap resultMap = new ResultMap.Builder(cfg, id + "-InlineResultMap",
                        Map.class, new ArrayList<ResultMapping>()).build();
                msBuilder.resultMaps(Collections.singletonList(resultMap));
            }
            cfg.addMappedStatement(msBuilder.build());
        }
    }

    /**
     * Remove a MappedStatement from a MyBatis Configuration by its id.
     * Uses reflection because {@code mappedStatements} is a protected StrictMap
     * with no public remove method.
     */
    @SuppressWarnings("unchecked")
    private void removeMappedStatement(Configuration cfg, String id) {
        try {
            Map<String, MappedStatement> map =
                    (Map<String, MappedStatement>) MAPPED_STATEMENTS_FIELD.get(cfg);
            map.remove(id);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access MyBatis mappedStatements registry", e);
        }
    }

    private static Field resolveMappedStatementsField() {
        try {
            Field field = Configuration.class.getDeclaredField("mappedStatements");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static class ExecuteResult {
        private final Object data;
        private final long elapsed;
        private final int page;
        private final int size;
        private final boolean hasMore;
        private final boolean truncated;

        public ExecuteResult(Object data, long elapsed) {
            this(data, elapsed, 0, data instanceof List ? ((List<?>) data).size() : 0,
                    false, false);
        }

        public ExecuteResult(Object data, long elapsed, int page, int size,
                             boolean hasMore, boolean truncated) {
            this.data = data;
            this.elapsed = elapsed;
            this.page = page;
            this.size = size;
            this.hasMore = hasMore;
            this.truncated = truncated;
        }

        public Object getData() { return data; }
        public long getElapsed() { return elapsed; }
        public int getPage() { return page; }
        public int getSize() { return size; }
        public boolean isHasMore() { return hasMore; }
        public boolean isTruncated() { return truncated; }
        public boolean isList() { return data instanceof List; }
        @SuppressWarnings("rawtypes")
        public int getTotal() { return data instanceof List ? ((List) data).size() : 0; }
    }

    public static class ScriptParseException extends RuntimeException {
        public ScriptParseException(String message) {
            super(message);
        }

        public ScriptParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
