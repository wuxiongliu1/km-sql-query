# DML Safety Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent DELETE/UPDATE SQL templates without WHERE clause from being saved, blocking accidental full-table operations at the template creation/update stage.

**Architecture:** Add a `DmlSafetyValidator` in the `core.engine` package that checks DELETE/UPDATE templates contain a WHERE clause before persisting. The validator is called from `SqlConfigServiceImpl.save/update`. A custom `DmlSafetyException` carries the error to `GlobalExceptionHandler` which returns HTTP 400.

**Tech Stack:** Java 8, Spring Boot 2.7.18, MyBatis, JUnit 5, Mockito, AssertJ

---

### Task 1: DmlSafetyException

**Files:**
- Create: `core/src/main/java/com/kisf/sqlquery/core/engine/DmlSafetyException.java`

- [ ] **Step 1: Write the exception class**

```java
package com.kisf.sqlquery.core.engine;

public class DmlSafetyException extends RuntimeException {
    public DmlSafetyException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/kisf/sqlquery/core/engine/DmlSafetyException.java
git commit -m "feat: add DmlSafetyException for write-operation safety violations"
```

---

### Task 2: DmlSafetyValidator

**Files:**
- Create: `core/src/main/java/com/kisf/sqlquery/core/engine/DmlSafetyValidator.java`

- [ ] **Step 1: Write the validator**

```java
package com.kisf.sqlquery.core.engine;

public class DmlSafetyValidator {

    public void validate(String sqlTemplate) {
        String trimmed = sqlTemplate.trim().toLowerCase();
        if (trimmed.startsWith("<select") || trimmed.startsWith("<insert")) {
            return;
        }
        if (!trimmed.startsWith("<delete") && !trimmed.startsWith("<update")) {
            throw new SqlExecutor.ScriptParseException(
                    "SQL模板必须以 <select>, <insert>, <update> 或 <delete> 开头");
        }

        String innerSql = MyBatisScriptEngine.extractInnerSql(sqlTemplate);
        if (!innerSql.toLowerCase().contains("where")) {
            throw new DmlSafetyException(
                    "DELETE/UPDATE 操作必须包含 WHERE 条件，不允许全表操作");
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/kisf/sqlquery/core/engine/DmlSafetyValidator.java
git commit -m "feat: add DmlSafetyValidator to enforce WHERE clause on DELETE/UPDATE templates"
```

---

### Task 3: DmlSafetyValidator unit tests

**Files:**
- Create: `core/src/test/java/com/kisf/sqlquery/core/engine/DmlSafetyValidatorTest.java`

- [ ] **Step 1: Write the test class**

```java
package com.kisf.sqlquery.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DmlSafetyValidatorTest {

    private DmlSafetyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DmlSafetyValidator();
    }

    @Test
    void shouldAllowDeleteWithStaticWhere() {
        assertThatCode(() -> validator.validate(
                "<delete>DELETE FROM t WHERE id=#{id}</delete>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDeleteWithoutWhere() {
        assertThatThrownBy(() -> validator.validate(
                "<delete>DELETE FROM t</delete>"))
                .isInstanceOf(DmlSafetyException.class)
                .hasMessageContaining("WHERE");
    }

    @Test
    void shouldAllowDeleteWithDynamicWhere() {
        assertThatCode(() -> validator.validate(
                "<delete>DELETE FROM t <where><if test=\"id!=null\">id=#{id}</if></where></delete>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowUpdateWithStaticWhere() {
        assertThatCode(() -> validator.validate(
                "<update>UPDATE t SET name=#{n} WHERE id=#{id}</update>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUpdateWithoutWhere() {
        assertThatThrownBy(() -> validator.validate(
                "<update>UPDATE t SET name=#{n}</update>"))
                .isInstanceOf(DmlSafetyException.class)
                .hasMessageContaining("WHERE");
    }

    @Test
    void shouldAllowInsert() {
        assertThatCode(() -> validator.validate(
                "<insert>INSERT INTO t VALUES (#{v})</insert>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowSelect() {
        assertThatCode(() -> validator.validate(
                "<select>SELECT * FROM t</select>"))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run the tests to verify they pass**

Run: `cd /Users/wuxl/kisf_ai/km-sql-query && mvn -f core/pom.xml test -pl core -Dtest=DmlSafetyValidatorTest -DfailIfNoTests=false -Dmaven.test.failure.ignore=false verify`

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/com/kisf/sqlquery/core/engine/DmlSafetyValidatorTest.java
git commit -m "test: add DmlSafetyValidator unit tests"
```

---

### Task 4: Integrate into SqlConfigServiceImpl

**Files:**
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java`

- [ ] **Step 1: Add validator field and update constructors**

Replace the existing constructor definitions and add the validator field:

The three member variables become:
```java
    private final SqlConfigRepository repo;
    private final Runnable onConfigChange;
    private final DmlSafetyValidator dmlSafetyValidator;
```

Replace both constructors:
```java
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
```

- [ ] **Step 2: Add validation call in save method**

Add at the top of `save()`:
```java
    @Override
    @Transactional
    public SqlConfig save(SqlConfig config) {
        dmlSafetyValidator.validate(config.getSqlTemplate());
        SqlConfig saved = repo.save(config);
        onConfigChange.run();
        return saved;
    }
```

- [ ] **Step 3: Add validation call in update method**

Add at the top of `update()`:
```java
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
```

Add the import:
```java
import com.kisf.sqlquery.core.engine.DmlSafetyValidator;
```

- [ ] **Step 4: Commit**

```bash
git add admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java
git commit -m "feat: integrate DmlSafetyValidator into SqlConfigServiceImpl save/update"
```

---

### Task 5: Wire beans in AutoConfiguration

**Files:**
- Modify: `starter/src/main/java/com/kisf/sqlquery/starter/SqlQueryAutoConfiguration.java`

- [ ] **Step 1: Add DmlSafetyValidator bean and update sqlConfigService bean**

Add before the `sqlConfigService` bean definition:
```java
    @Bean
    public DmlSafetyValidator dmlSafetyValidator() {
        return new DmlSafetyValidator();
    }
```

Update the `sqlConfigService` bean:
```java
    @Bean
    public SqlConfigService sqlConfigService(SqlConfigRepository repo, MyBatisScriptEngine scriptEngine,
                                              DmlSafetyValidator dmlSafetyValidator) {
        return new SqlConfigServiceImpl(repo, scriptEngine::invalidateAll, dmlSafetyValidator);
    }
```

Add the import:
```java
import com.kisf.sqlquery.core.engine.DmlSafetyValidator;
```

- [ ] **Step 2: Commit**

```bash
git add starter/src/main/java/com/kisf/sqlquery/starter/SqlQueryAutoConfiguration.java
git commit -m "feat: register DmlSafetyValidator bean and wire into SqlConfigService"
```

---

### Task 6: Handle exception in GlobalExceptionHandler

**Files:**
- Modify: `api/src/main/java/com/kisf/sqlquery/api/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Add exception handler**

Add after the `handleScriptParse` method:
```java
    @ExceptionHandler(com.kisf.sqlquery.core.engine.DmlSafetyException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleDmlSafety(com.kisf.sqlquery.core.engine.DmlSafetyException e) {
        return ApiResponse.error(-6, e.getMessage());
    }
```

- [ ] **Step 2: Commit**

```bash
git add api/src/main/java/com/kisf/sqlquery/api/exception/GlobalExceptionHandler.java
git commit -m "feat: handle DmlSafetyException in global exception handler"
```

---

### Task 7: Update test config in SqlConfigControllerTest

**Files:**
- Modify: `admin/src/test/java/com/kisf/sqlquery/admin/controller/SqlConfigControllerTest.java`

- [ ] **Step 1: Update the sqlConfigService bean in TestConfig**

Replace:
```java
        @Bean
        public SqlConfigService sqlConfigService(SqlConfigRepository repo) {
            return new SqlConfigServiceImpl(repo);
        }
```

With:
```java
        @Bean
        public DmlSafetyValidator dmlSafetyValidator() {
            return new DmlSafetyValidator();
        }

        @Bean
        public SqlConfigService sqlConfigService(SqlConfigRepository repo, DmlSafetyValidator dmlSafetyValidator) {
            return new SqlConfigServiceImpl(repo, () -> {}, dmlSafetyValidator);
        }
```

Add import:
```java
import com.kisf.sqlquery.core.engine.DmlSafetyValidator;
```

- [ ] **Step 2: Run the existing tests to verify no regression**

Run: `cd /Users/wuxl/kisf_ai/km-sql-query && mvn test -pl admin -Dtest=SqlConfigControllerTest -DfailIfNoTests=false`

Expected: All existing tests pass.

- [ ] **Step 3: Commit**

```bash
git add admin/src/test/java/com/kisf/sqlquery/admin/controller/SqlConfigControllerTest.java
git commit -m "test: wire DmlSafetyValidator into SqlConfigControllerTest config"
```

---

### Task 8: Full build verification

- [ ] **Step 1: Run the full test suite**

Run: `cd /Users/wuxl/kisf_ai/km-sql-query && mvn test -DfailIfNoTests=false`

Expected: All tests pass across all modules.

- [ ] **Step 2: Verify the build**

Run: `cd /Users/wuxl/kisf_ai/km-sql-query && mvn package -DskipTests`

Expected: BUILD SUCCESS.
