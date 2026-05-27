# KM-SQL-Query Online SQL Execution Platform — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot 2.x platform that exposes a unified HTTP endpoint to execute pre-configured MyBatis dynamic SQL templates against multiple relational databases, with hot-reload and JDK 8 compatibility.

**Architecture:** Four Maven modules — `admin` (JPA entities, repos, services, CRUD controllers, hot-reload triggers), `core` (MyBatis script parsing, DataSource/HikariCP caching, SQL execution engine), `api` (unified `/api/sqlQuery` and `/api/refresh/*` endpoints, global error handling), `starter` (Spring Boot auto-configuration). Dependency chain: `api → core → admin`.

**Tech Stack:** Spring Boot 2.7.x, MyBatis 3.5.x + mybatis-spring 2.x, Spring Data JPA (Hibernate 5.x), SQLite for config store, HikariCP for business datasources, JDK 8.

---

## File Structure

```
km-sql-query/
├── pom.xml
├── admin/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/kisf/sqlquery/admin/
│       │   ├── entity/SqlConfig.java
│       │   ├── entity/DatasourceConfig.java
│       │   ├── repo/SqlConfigRepository.java
│       │   ├── repo/DatasourceConfigRepository.java
│       │   ├── service/SqlConfigService.java
│       │   ├── service/DatasourceConfigService.java
│       │   ├── service/impl/SqlConfigServiceImpl.java
│       │   ├── service/impl/DatasourceConfigServiceImpl.java
│       │   ├── controller/SqlConfigController.java
│       │   ├── controller/DatasourceConfigController.java
│       │   ├── util/AesUtils.java
│       │   └── dialect/SQLiteDialect.java
│       ├── main/resources/application-admin.yml
│       └── test/java/com/kisf/sqlquery/admin/
│           ├── repo/SqlConfigRepositoryTest.java
│           └── repo/DatasourceConfigRepositoryTest.java
├── core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/kisf/sqlquery/core/
│       │   ├── cache/DataSourceCache.java
│       │   ├── engine/MyBatisScriptEngine.java
│       │   └── engine/SqlExecutor.java
│       └── test/java/com/kisf/sqlquery/core/
│           ├── engine/MyBatisScriptEngineTest.java
│           └── engine/SqlExecutorTest.java
├── api/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/kisf/sqlquery/api/
│       │   ├── model/SqlQueryRequest.java
│       │   ├── model/ApiResponse.java
│       │   ├── controller/SqlQueryController.java
│       │   ├── controller/RefreshController.java
│       │   └── exception/GlobalExceptionHandler.java
│       └── test/java/com/kisf/sqlquery/api/
│           └── controller/SqlQueryControllerTest.java
└── starter/
    ├── pom.xml
    └── src/main/
        ├── java/com/kisf/sqlquery/starter/
        │   └── SqlQueryAutoConfiguration.java
        └── resources/META-INF/spring.factories
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `pom.xml` (parent)
- Create: `admin/pom.xml`
- Create: `core/pom.xml`
- Create: `api/pom.xml`
- Create: `starter/pom.xml`

- [ ] **Step 1: Create parent pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.kisf</groupId>
    <artifactId>km-sql-query</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
    </parent>

    <modules>
        <module>admin</module>
        <module>core</module>
        <module>api</module>
        <module>starter</module>
    </modules>

    <properties>
        <java.version>1.8</java.version>
        <mybatis.version>3.5.15</mybatis.version>
        <mybatis-spring.version>2.1.2</mybatis-spring.version>
        <sqlite.version>3.42.0.1</sqlite.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.kisf</groupId>
                <artifactId>admin</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.kisf</groupId>
                <artifactId>core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.kisf</groupId>
                <artifactId>api</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: Create admin/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.kisf</groupId>
        <artifactId>km-sql-query</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>admin</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>${sqlite.version}</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Create core/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.kisf</groupId>
        <artifactId>km-sql-query</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>core</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.kisf</groupId>
            <artifactId>admin</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis</artifactId>
            <version>${mybatis.version}</version>
        </dependency>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis-spring</artifactId>
            <version>${mybatis-spring.version}</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: Create api/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.kisf</groupId>
        <artifactId>km-sql-query</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>api</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.kisf</groupId>
            <artifactId>core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: Create starter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.kisf</groupId>
        <artifactId>km-sql-query</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>starter</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.kisf</groupId>
            <artifactId>api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 6: Verify build compiles (empty src)**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -q
```

Expected: BUILD SUCCESS (no source files yet, but POMs parse correctly)

- [ ] **Step 7: Commit**

```bash
git add pom.xml admin/pom.xml core/pom.xml api/pom.xml starter/pom.xml
git commit -m "chore: scaffold project module structure with pom files"
```

---

### Task 2: Admin — JPA Entities & Repositories

**Files:**
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/entity/SqlConfig.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/entity/DatasourceConfig.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/repo/SqlConfigRepository.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/repo/DatasourceConfigRepository.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/dialect/SQLiteDialect.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/util/AesUtils.java`
- Create: `admin/src/main/resources/application-admin.yml`
- Create: `admin/src/test/java/com/kisf/sqlquery/admin/repo/SqlConfigRepositoryTest.java`

- [ ] **Step 1: Write SQLiteDialect**

```java
package com.kisf.sqlquery.admin.dialect;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;

import java.sql.Types;

public class SQLiteDialect extends Dialect {

    public SQLiteDialect() {
        registerColumnType(Types.BIT, "integer");
        registerColumnType(Types.TINYINT, "tinyint");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.FLOAT, "float");
        registerColumnType(Types.DOUBLE, "double");
        registerColumnType(Types.DECIMAL, "decimal");
        registerColumnType(Types.VARCHAR, "varchar");
        registerColumnType(Types.CLOB, "clob");
        registerColumnType(Types.BLOB, "blob");
        registerColumnType(Types.TIMESTAMP, "datetime");
        registerColumnType(Types.BOOLEAN, "integer");
    }

    @Override
    public boolean supportsIdentityColumns() {
        return true;
    }

    @Override
    public boolean hasDataTypeInIdentityColumn() {
        return false;
    }

    @Override
    public String getIdentityColumnString(int type) {
        return "integer";
    }

    @Override
    public String getIdentitySelectString(String table, String column, int type) {
        return "select last_insert_rowid()";
    }

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new SQLiteIdentityColumnSupport();
    }

    private static class SQLiteIdentityColumnSupport extends IdentityColumnSupportImpl {
        @Override
        public boolean supportsIdentityColumns() {
            return true;
        }

        @Override
        public String getIdentitySelectString(String table, String column, int type) {
            return "select last_insert_rowid()";
        }

        @Override
        public String getIdentityColumnString(int type) {
            return "integer";
        }
    }
}
```

- [ ] **Step 2: Write SqlConfig entity**

```java
package com.kisf.sqlquery.admin.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sql_config")
public class SqlConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 255)
    private String sqlPath;

    @Column(nullable = false, columnDefinition = "clob")
    private String sqlTemplate;

    @Column(nullable = false, length = 100)
    private String datasourceId;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSqlPath() { return sqlPath; }
    public void setSqlPath(String sqlPath) { this.sqlPath = sqlPath; }

    public String getSqlTemplate() { return sqlTemplate; }
    public void setSqlTemplate(String sqlTemplate) { this.sqlTemplate = sqlTemplate; }

    public String getDatasourceId() { return datasourceId; }
    public void setDatasourceId(String datasourceId) { this.datasourceId = datasourceId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: Write DatasourceConfig entity**

```java
package com.kisf.sqlquery.admin.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "datasource_config")
public class DatasourceConfig {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 255)
    private String driverClass;

    @Column(nullable = false, length = 500)
    private String jdbcUrl;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false)
    private Integer poolSize = 10;

    @Column(columnDefinition = "clob")
    private String extra;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDriverClass() { return driverClass; }
    public void setDriverClass(String driverClass) { this.driverClass = driverClass; }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Integer getPoolSize() { return poolSize; }
    public void setPoolSize(Integer poolSize) { this.poolSize = poolSize; }

    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: Write AesUtils**

```java
package com.kisf.sqlquery.admin.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AesUtils {

    private static final String ALGORITHM = "AES";
    private static final byte[] DEFAULT_KEY = "KmSqlQuery@2026!" .getBytes(); // 16 bytes for AES-128

    public static String encrypt(String plainText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(DEFAULT_KEY, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    public static String decrypt(String encryptedText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(DEFAULT_KEY, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            return new String(cipher.doFinal(decoded), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed", e);
        }
    }
}
```

- [ ] **Step 5: Write SqlConfigRepository**

```java
package com.kisf.sqlquery.admin.repo;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SqlConfigRepository extends JpaRepository<SqlConfig, Long>,
        JpaSpecificationExecutor<SqlConfig> {

    Optional<SqlConfig> findBySqlPath(String sqlPath);

    boolean existsBySqlPath(String sqlPath);
}
```

- [ ] **Step 6: Write DatasourceConfigRepository**

```java
package com.kisf.sqlquery.admin.repo;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceConfigRepository extends JpaRepository<DatasourceConfig, String> {
}
```

- [ ] **Step 7: Write admin application config for SQLite**

`admin/src/main/resources/application-admin.yml`:
```yaml
spring:
  datasource:
    url: jdbc:sqlite:km-sql-query.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: com.kisf.sqlquery.admin.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
    show-sql: false
```

- [ ] **Step 8: Write repository test**

```java
package com.kisf.sqlquery.admin.repo;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SqlConfigRepositoryTest {

    @Autowired
    private SqlConfigRepository repo;

    @Test
    void shouldSaveAndFindBySqlPath() {
        SqlConfig config = new SqlConfig();
        config.setSqlPath("/order/detail");
        config.setSqlTemplate("<select id=\"q\">SELECT * FROM orders WHERE id = #{orderId}</select>");
        config.setDatasourceId("order_db");
        config.setEnabled(true);
        repo.save(config);

        Optional<SqlConfig> found = repo.findBySqlPath("/order/detail");
        assertThat(found).isPresent();
        assertThat(found.get().getDatasourceId()).isEqualTo("order_db");
    }
}
```

- [ ] **Step 9: Run test**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn test -pl admin -Dtest=SqlConfigRepositoryTest -q
```

Expected: PASS — 1 test, saves and finds by sqlPath

- [ ] **Step 10: Commit**

```bash
git add admin/
git commit -m "feat(admin): add JPA entities, repositories, SQLite dialect, and AES utils"
```

---

### Task 3: Admin — Services with Hot-Reload Trigger

**Files:**
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/service/SqlConfigService.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/service/DatasourceConfigService.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/service/impl/DatasourceConfigServiceImpl.java`

- [ ] **Step 1: Write SqlConfigService interface**

```java
package com.kisf.sqlquery.admin.service;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface SqlConfigService {

    SqlConfig save(SqlConfig config);

    SqlConfig update(Long id, SqlConfig config);

    void delete(Long id);

    Optional<SqlConfig> findById(Long id);

    Optional<SqlConfig> findBySqlPath(String sqlPath);

    Page<SqlConfig> list(String sqlPath, String datasourceId, Pageable pageable);
}
```

- [ ] **Step 2: Write SqlConfigServiceImpl**

```java
package com.kisf.sqlquery.admin.service.impl;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import com.kisf.sqlquery.admin.repo.SqlConfigRepository;
import com.kisf.sqlquery.admin.service.SqlConfigService;
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

    public SqlConfigServiceImpl(SqlConfigRepository repo) {
        this.repo = repo;
        this.onConfigChange = () -> {};
    }

    public SqlConfigServiceImpl(SqlConfigRepository repo, Runnable onConfigChange) {
        this.repo = repo;
        this.onConfigChange = onConfigChange;
    }

    @Override
    @Transactional
    public SqlConfig save(SqlConfig config) {
        SqlConfig saved = repo.save(config);
        onConfigChange.run();
        return saved;
    }

    @Override
    @Transactional
    public SqlConfig update(Long id, SqlConfig config) {
        SqlConfig existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SqlConfig not found: " + id));
        existing.setSqlPath(config.getSqlPath());
        existing.setSqlTemplate(config.getSqlTemplate());
        existing.setDatasourceId(config.getDatasourceId());
        existing.setDescription(config.getDescription());
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
    public Page<SqlConfig> list(String sqlPath, String datasourceId, Pageable pageable) {
        return repo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sqlPath != null && !sqlPath.isEmpty()) {
                predicates.add(cb.like(root.get("sqlPath"), "%" + sqlPath + "%"));
            }
            if (datasourceId != null && !datasourceId.isEmpty()) {
                predicates.add(cb.equal(root.get("datasourceId"), datasourceId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }
}
```

- [ ] **Step 3: Write DatasourceConfigService interface**

```java
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
```

- [ ] **Step 4: Write DatasourceConfigServiceImpl**

```java
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
```

- [ ] **Step 5: Verify admin module builds**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -pl admin -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add admin/src/main/java/com/kisf/sqlquery/admin/service/
git commit -m "feat(admin): add SqlConfigService and DatasourceConfigService with AES encrypt"
```

---

### Task 4: Admin — REST Controllers

**Files:**
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/controller/SqlConfigController.java`
- Create: `admin/src/main/java/com/kisf/sqlquery/admin/controller/DatasourceConfigController.java`

- [ ] **Step 1: Write SqlConfigController**

```java
package com.kisf.sqlquery.admin.controller;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import com.kisf.sqlquery.admin.service.SqlConfigService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/sqlConfig")
public class SqlConfigController {

    private final SqlConfigService service;

    public SqlConfigController(SqlConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SqlConfig> create(@RequestBody SqlConfig config) {
        return ResponseEntity.ok(service.save(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SqlConfig> update(@PathVariable Long id, @RequestBody SqlConfig config) {
        return ResponseEntity.ok(service.update(id, config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SqlConfig> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public ResponseEntity<Page<SqlConfig>> list(
            @RequestParam(required = false) String sqlPath,
            @RequestParam(required = false) String datasourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(sqlPath, datasourceId, PageRequest.of(page, size)));
    }
}
```

- [ ] **Step 2: Write DatasourceConfigController**

```java
package com.kisf.sqlquery.admin.controller;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import com.kisf.sqlquery.admin.service.DatasourceConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/datasource")
public class DatasourceConfigController {

    private final DatasourceConfigService service;

    public DatasourceConfigController(DatasourceConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DatasourceConfig> create(@RequestBody DatasourceConfig config) {
        return ResponseEntity.ok(service.save(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DatasourceConfig> update(@PathVariable String id, @RequestBody DatasourceConfig config) {
        return ResponseEntity.ok(service.update(id, config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasourceConfig> getById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public ResponseEntity<List<DatasourceConfig>> list() {
        return ResponseEntity.ok(service.list());
    }
}
```

- [ ] **Step 3: Verify admin module compiles**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -pl admin -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add admin/src/main/java/com/kisf/sqlquery/admin/controller/
git commit -m "feat(admin): add admin REST controllers for SqlConfig and DatasourceConfig CRUD"
```

---

### Task 5: Core — DataSource Cache

**Files:**
- Create: `core/src/main/java/com/kisf/sqlquery/core/cache/DataSourceCache.java`
- Create: `core/src/test/java/com/kisf/sqlquery/core/cache/DataSourceCacheTest.java`

- [ ] **Step 1: Write DataSourceCache**

```java
package com.kisf.sqlquery.core.cache;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import com.kisf.sqlquery.admin.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.admin.util.AesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataSourceCache {

    private final DatasourceConfigRepository repo;
    private final Map<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, SqlSessionFactory> factoryMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataSourceCache(DatasourceConfigRepository repo) {
        this.repo = repo;
    }

    public SqlSessionFactory getOrCreate(String datasourceId) {
        return factoryMap.computeIfAbsent(datasourceId, id -> {
            DatasourceConfig config = repo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + id));
            if (!config.getEnabled()) {
                throw new IllegalArgumentException("Datasource is disabled: " + id);
            }
            DataSource ds = createDataSource(config);
            return createSqlSessionFactory(ds);
        });
    }

    public void invalidate(String datasourceId) {
        SqlSessionFactory oldFactory = factoryMap.remove(datasourceId);
        HikariDataSource oldDs = dataSourceMap.remove(datasourceId);
        if (oldDs != null && !oldDs.isClosed()) {
            oldDs.close();
        }
    }

    public void invalidateAll() {
        for (String id : factoryMap.keySet()) {
            invalidate(id);
        }
    }

    private HikariDataSource createDataSource(DatasourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(config.getDriverClass());
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(AesUtils.decrypt(config.getPassword()));
        hikariConfig.setMaximumPoolSize(config.getPoolSize());

        if (config.getExtra() != null && !config.getExtra().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> extra = objectMapper.readValue(config.getExtra(), Map.class);
                if (extra.containsKey("connectionTimeout")) {
                    hikariConfig.setConnectionTimeout(((Number) extra.get("connectionTimeout")).longValue());
                }
                if (extra.containsKey("maxLifetime")) {
                    hikariConfig.setMaxLifetime(((Number) extra.get("maxLifetime")).longValue());
                }
                if (extra.containsKey("idleTimeout")) {
                    hikariConfig.setIdleTimeout(((Number) extra.get("idleTimeout")).longValue());
                }
            } catch (Exception ignored) {
            }
        }

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        dataSourceMap.put(config.getId(), ds);
        return ds;
    }

    private SqlSessionFactory createSqlSessionFactory(DataSource dataSource) {
        try {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            return factoryBean.getObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SqlSessionFactory", e);
        }
    }
}
```

- [ ] **Step 2: Write DataSourceCacheTest**

```java
package com.kisf.sqlquery.core.cache;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import com.kisf.sqlquery.admin.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.admin.util.AesUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DataSourceCacheTest {

    private DatasourceConfigRepository repo;
    private DataSourceCache cache;

    @BeforeEach
    void setUp() {
        repo = mock(DatasourceConfigRepository.class);
        cache = new DataSourceCache(repo);
    }

    @Test
    void shouldThrowWhenDatasourceNotFound() {
        when(repo.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cache.getOrCreate("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Datasource not found");
    }

    @Test
    void shouldThrowWhenDatasourceDisabled() {
        DatasourceConfig config = new DatasourceConfig();
        config.setId("test_db");
        config.setEnabled(false);
        when(repo.findById("test_db")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> cache.getOrCreate("test_db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldCacheAndReturnSameFactory() {
        DatasourceConfig config = new DatasourceConfig();
        config.setId("h2_db");
        config.setDriverClass("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:mem:test_cache;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword(AesUtils.encrypt(""));
        config.setPoolSize(5);
        config.setEnabled(true);
        when(repo.findById("h2_db")).thenReturn(Optional.of(config));

        SqlSessionFactory f1 = cache.getOrCreate("h2_db");
        SqlSessionFactory f2 = cache.getOrCreate("h2_db");

        assertThat(f1).isSameAs(f2);
    }
}
```

- [ ] **Step 3: Run test**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn test -pl core -Dtest=DataSourceCacheTest -q
```

Expected: PASS — 3 tests

- [ ] **Step 4: Commit**

```bash
git add core/
git commit -m "feat(core): add DataSourceCache with HikariCP pool per datasource"
```

---

### Task 6: Core — MyBatis Script Engine & SQL Executor

**Files:**
- Create: `core/src/main/java/com/kisf/sqlquery/core/engine/MyBatisScriptEngine.java`
- Create: `core/src/main/java/com/kisf/sqlquery/core/engine/SqlExecutor.java`
- Create: `core/src/test/java/com/kisf/sqlquery/core/engine/MyBatisScriptEngineTest.java`
- Create: `core/src/test/java/com/kisf/sqlquery/core/engine/SqlExecutorTest.java`

- [ ] **Step 1: Write MyBatisScriptEngine**

```java
package com.kisf.sqlquery.core.engine;

import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MyBatisScriptEngine {

    private final Map<String, SqlSource> sqlSourceCache = new ConcurrentHashMap<>();

    public SqlSource parse(String sqlPath, String sqlTemplate, Configuration configuration) {
        return sqlSourceCache.computeIfAbsent(sqlPath, k -> {
            String script = "<script>" + sqlTemplate + "</script>";
            XMLLanguageDriver driver = new XMLLanguageDriver();
            return driver.createSqlSource(configuration, script, Map.class);
        });
    }

    public SqlCommandType detectCommandType(String sqlTemplate) {
        String trimmed = sqlTemplate.trim().toLowerCase();
        if (trimmed.startsWith("<select")) return SqlCommandType.SELECT;
        if (trimmed.startsWith("<insert")) return SqlCommandType.INSERT;
        if (trimmed.startsWith("<update")) return SqlCommandType.UPDATE;
        if (trimmed.startsWith("<delete")) return SqlCommandType.DELETE;
        throw new ScriptParseException("SQL模板必须以 <select>, <insert>, <update> 或 <delete> 开头");
    }

    public void invalidate(String sqlPath) {
        sqlSourceCache.remove(sqlPath);
    }

    public void invalidateAll() {
        sqlSourceCache.clear();
    }
}
```

- [ ] **Step 2: Write SqlExecutor**

```java
package com.kisf.sqlquery.core.engine;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import com.kisf.sqlquery.admin.repo.SqlConfigRepository;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Field;
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

        try (SqlSession session = ssf.openSession()) {
            Object result;
            switch (cmdType) {
                case SELECT:
                    List<Map<String, Object>> rows = session.selectList(sqlPath, params,
                            null, null, null);
                    result = rows;
                    break;
                case INSERT:
                    result = session.insert(sqlPath, params);
                    break;
                case UPDATE:
                    result = session.update(sqlPath, params);
                    break;
                case DELETE:
                    result = session.delete(sqlPath, params);
                    break;
                default:
                    throw new ScriptParseException("Unsupported command type: " + cmdType);
            }

            long elapsed = System.currentTimeMillis() - start;
            return new ExecuteResult(result, elapsed);
        }
    }

    public void invalidate(String sqlPath) {
        scriptEngine.invalidate(sqlPath);
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
```

- [ ] **Step 3: Write MyBatisScriptEngineTest**

```java
package com.kisf.sqlquery.core.engine;

import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MyBatisScriptEngineTest {

    private MyBatisScriptEngine engine;
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        engine = new MyBatisScriptEngine();
        configuration = new Configuration();
        configuration.addLoadedResource("test");
    }

    @Test
    void shouldDetectSelectCommand() {
        assertThat(engine.detectCommandType("<select id=\"q\">SELECT 1</select>"))
                .isEqualTo(SqlCommandType.SELECT);
    }

    @Test
    void shouldDetectInsertCommand() {
        assertThat(engine.detectCommandType("<insert id=\"q\">INSERT INTO t VALUES(1)</insert>"))
                .isEqualTo(SqlCommandType.INSERT);
    }

    @Test
    void shouldDetectUpdateCommand() {
        assertThat(engine.detectCommandType("<update id=\"q\">UPDATE t SET x=1</update>"))
                .isEqualTo(SqlCommandType.UPDATE);
    }

    @Test
    void shouldDetectDeleteCommand() {
        assertThat(engine.detectCommandType("<delete id=\"q\">DELETE FROM t</delete>"))
                .isEqualTo(SqlCommandType.DELETE);
    }

    @Test
    void shouldThrowOnInvalidTemplate() {
        assertThatThrownBy(() -> engine.detectCommandType("SELECT * FROM t"))
                .isInstanceOf(SqlExecutor.ScriptParseException.class)
                .hasMessageContaining("SQL模板必须以");
    }

    @Test
    void shouldParseSelectTemplate() {
        String template = "<select id=\"q\">SELECT * FROM orders WHERE id = #{orderId}</select>";
        assertThat(engine.parse("/test", template, configuration)).isNotNull();
    }

    @Test
    void shouldParseTemplateWithIfTag() {
        String template = "<select id=\"q\">SELECT * FROM orders " +
                "<where><if test=\"status != null\">AND status = #{status}</if></where></select>";
        assertThat(engine.parse("/test2", template, configuration))
                .isInstanceOf(DynamicSqlSource.class);
    }

    @Test
    void shouldParseTemplateWithForEach() {
        String template = "<select id=\"q\">SELECT * FROM orders WHERE id IN " +
                "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach></select>";
        assertThat(engine.parse("/test3", template, configuration)).isNotNull();
    }

    @Test
    void shouldCacheSqlSource() {
        String template = "<select id=\"q\">SELECT 1</select>";
        Object s1 = engine.parse("/cached", template, configuration);
        Object s2 = engine.parse("/cached", template, configuration);
        assertThat(s1).isSameAs(s2);
    }

    @Test
    void shouldInvalidateCache() {
        String template = "<select id=\"q\">SELECT 1</select>";
        engine.parse("/inval", template, configuration);
        engine.invalidate("/inval");
        // next parse should create fresh
        assertThat((Object) engine.sqlSourceCache).matches(m -> {
            @SuppressWarnings("unchecked")
            java.util.Map<String, ?> map = (java.util.Map<String, ?>) ((Object) m);
            return !map.containsKey("/inval");
        });
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn test -pl core -Dtest=MyBatisScriptEngineTest -q
```

Expected: PASS — 9 tests

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/kisf/sqlquery/core/engine/ core/src/test/
git commit -m "feat(core): add MyBatis script engine with dynamic SQL parsing and SqlExecutor"
```

---

### Task 7: API — Request/Response Models & Global Error Handler

**Files:**
- Create: `api/src/main/java/com/kisf/sqlquery/api/model/SqlQueryRequest.java`
- Create: `api/src/main/java/com/kisf/sqlquery/api/model/ApiResponse.java`
- Create: `api/src/main/java/com/kisf/sqlquery/api/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Write SqlQueryRequest**

```java
package com.kisf.sqlquery.api.model;

import java.util.Map;

public class SqlQueryRequest {

    private String sqlPath;
    private Map<String, Object> params;

    public String getSqlPath() { return sqlPath; }
    public void setSqlPath(String sqlPath) { this.sqlPath = sqlPath; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
```

- [ ] **Step 2: Write ApiResponse**

```java
package com.kisf.sqlquery.api.model;

import java.util.List;
import java.util.Map;

public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private Integer total;
    private Long elapsed;

    public static ApiResponse<List<Map<String, Object>>> okList(List<Map<String, Object>> data, long elapsed) {
        ApiResponse<List<Map<String, Object>>> resp = new ApiResponse<>();
        resp.code = 0;
        resp.message = "ok";
        resp.data = data;
        resp.total = data.size();
        resp.elapsed = elapsed;
        return resp;
    }

    public static ApiResponse<Map<String, Object>> okUpdate(Map<String, Object> data, long elapsed) {
        ApiResponse<Map<String, Object>> resp = new ApiResponse<>();
        resp.code = 0;
        resp.message = "ok";
        resp.data = data;
        resp.elapsed = elapsed;
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = code;
        resp.message = message;
        return resp;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public Long getElapsed() { return elapsed; }
    public void setElapsed(Long elapsed) { this.elapsed = elapsed; }
}
```

- [ ] **Step 3: Write GlobalExceptionHandler**

```java
package com.kisf.sqlquery.api.exception;

import com.kisf.sqlquery.api.model.ApiResponse;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "参数校验失败";
        int code = -1;
        if (msg.contains("not found")) {
            code = -2;
        } else if (msg.contains("Datasource")) {
            code = -3;
        } else if (msg.contains("disabled")) {
            code = -2;
        }
        return ApiResponse.error(code, msg);
    }

    @ExceptionHandler(SqlExecutor.ScriptParseException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleScriptParse(SqlExecutor.ScriptParseException e) {
        return ApiResponse.error(-5, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ApiResponse.error(-4, "SQL执行异常: " + e.getMessage());
    }
}
```

- [ ] **Step 4: Verify api module compiles**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -pl api -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/kisf/sqlquery/api/model/ api/src/main/java/com/kisf/sqlquery/api/exception/
git commit -m "feat(api): add request/response models and global exception handler"
```

---

### Task 8: API — SqlQueryController & RefreshController

**Files:**
- Create: `api/src/main/java/com/kisf/sqlquery/api/controller/SqlQueryController.java`
- Create: `api/src/main/java/com/kisf/sqlquery/api/controller/RefreshController.java`

- [ ] **Step 1: Write SqlQueryController**

```java
package com.kisf.sqlquery.api.controller;

import com.kisf.sqlquery.api.model.ApiResponse;
import com.kisf.sqlquery.api.model.SqlQueryRequest;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SqlQueryController {

    private final SqlExecutor sqlExecutor;

    public SqlQueryController(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @PostMapping("/api/sqlQuery")
    public ApiResponse<?> execute(@RequestBody SqlQueryRequest request) {
        SqlExecutor.ExecuteResult result = sqlExecutor.execute(
                request.getSqlPath(), request.getParams());

        if (result.isList()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getData();
            return ApiResponse.okList(rows, result.getElapsed());
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> updateResult = Map.of("affectedRows", result.getData());
            return ApiResponse.okUpdate(updateResult, result.getElapsed());
        }
    }
}
```

- [ ] **Step 2: Write RefreshController**

```java
package com.kisf.sqlquery.api.controller;

import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/refresh")
public class RefreshController {

    private final SqlExecutor sqlExecutor;

    public RefreshController(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @PostMapping("/{sqlPath}")
    public Map<String, Object> refresh(@PathVariable String sqlPath) {
        sqlExecutor.invalidate(sqlPath);
        return Map.of("success", true, "sqlPath", sqlPath);
    }
}
```

- [ ] **Step 3: Verify api module compiles**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -pl api -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/kisf/sqlquery/api/controller/
git commit -m "feat(api): add SqlQueryController and RefreshController"
```

---

### Task 9: Starter — Auto-Configuration

**Files:**
- Create: `starter/src/main/java/com/kisf/sqlquery/starter/SqlQueryAutoConfiguration.java`
- Create: `starter/src/main/resources/META-INF/spring.factories`

- [ ] **Step 1: Write SqlQueryAutoConfiguration**

```java
package com.kisf.sqlquery.starter;

import com.kisf.sqlquery.admin.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.admin.repo.SqlConfigRepository;
import com.kisf.sqlquery.admin.service.DatasourceConfigService;
import com.kisf.sqlquery.admin.service.SqlConfigService;
import com.kisf.sqlquery.admin.service.impl.DatasourceConfigServiceImpl;
import com.kisf.sqlquery.admin.service.impl.SqlConfigServiceImpl;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import com.kisf.sqlquery.core.engine.MyBatisScriptEngine;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = {
        "com.kisf.sqlquery.admin.controller",
        "com.kisf.sqlquery.api.controller",
        "com.kisf.sqlquery.api.exception"
})
@EntityScan("com.kisf.sqlquery.admin.entity")
@EnableJpaRepositories("com.kisf.sqlquery.admin.repo")
public class SqlQueryAutoConfiguration {

    @Bean
    public MyBatisScriptEngine myBatisScriptEngine() {
        return new MyBatisScriptEngine();
    }

    @Bean
    public DatasourceConfigService datasourceConfigService(DatasourceConfigRepository repo) {
        return new DatasourceConfigServiceImpl(repo);
    }

    @Bean
    public SqlConfigService sqlConfigService(SqlConfigRepository repo, MyBatisScriptEngine scriptEngine) {
        return new SqlConfigServiceImpl(repo, scriptEngine::invalidateAll);
    }

    @Bean
    public DataSourceCache dataSourceCache(DatasourceConfigRepository repo) {
        return new DataSourceCache(repo);
    }

    @Bean
    public SqlExecutor sqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                                   MyBatisScriptEngine scriptEngine) {
        return new SqlExecutor(configRepo, dataSourceCache, scriptEngine);
    }
}
```

- [ ] **Step 2: Write spring.factories**

`starter/src/main/resources/META-INF/spring.factories`:
```
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.kisf.sqlquery.starter.SqlQueryAutoConfiguration
```

- [ ] **Step 3: Verify full project builds**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add starter/
git commit -m "feat(starter): add Spring Boot auto-configuration"
```

---

### Task 10: Application Entry Point & Integration Test

**Files:**
- Create: `src/main/java/com/kisf/sqlquery/SqlQueryApplication.java` (root, outside modules — or use a separate app module)
- Create: `src/main/resources/application.yml`

Note: Since the main application class needs all modules and the starter already pulls in everything, we add it at the root (not in a module). Alternatively, create an `app` module. For simplicity, we use the root `src` as the application and add the starter dependency in the parent POM's dependency management as an optional dependency for the consuming project. The starter is the entry point — a consuming Spring Boot project adds `starter` as a dependency and creates its own `@SpringBootApplication` class. Let's provide a sample.

- [ ] **Step 1: Add starter dependency to parent POM for consumption**

Add to parent `pom.xml` `<dependencyManagement>` section before the closing `</dependencyManagement>` tag:
```xml
    <dependency>
        <groupId>com.kisf</groupId>
        <artifactId>starter</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencyManagement>
```

- [ ] **Step 2: Create sample application.yml for consuming project**

`src/main/resources/application.yml` (sample to be used by consuming project):
```yaml
spring:
  datasource:
    url: jdbc:sqlite:km-sql-query.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: com.kisf.sqlquery.admin.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
    show-sql: false

server:
  port: 8080

spring.servlet.multipart.max-request-size: 10MB
```

- [ ] **Step 3: Write Full Integration Test**

Place in `api/src/test/java/com/kisf/sqlquery/api/controller/SqlQueryControllerTest.java`:

```java
package com.kisf.sqlquery.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import com.kisf.sqlquery.admin.entity.SqlConfig;
import com.kisf.sqlquery.admin.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.admin.repo.SqlConfigRepository;
import com.kisf.sqlquery.admin.util.AesUtils;
import com.kisf.sqlquery.starter.SqlQueryAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SqlQueryAutoConfiguration.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testint;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "server.servlet.multipart.max-request-size=10MB"
})
class SqlQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SqlConfigRepository sqlConfigRepo;

    @Autowired
    private DatasourceConfigRepository dsConfigRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sqlConfigRepo.deleteAll();
        dsConfigRepo.deleteAll();

        DatasourceConfig ds = new DatasourceConfig();
        ds.setId("h2_test");
        ds.setDriverClass("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:testexec;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword(AesUtils.encrypt(""));
        ds.setPoolSize(5);
        ds.setEnabled(true);
        dsConfigRepo.save(ds);

        // Create test table on H2
        SqlConfig ddl = new SqlConfig();
        ddl.setSqlPath("/test/ddl");
        ddl.setSqlTemplate("<update id=\"ddl\">CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, name VARCHAR(100), amount DECIMAL(10,2))</update>");
        ddl.setDatasourceId("h2_test");
        ddl.setEnabled(true);
        sqlConfigRepo.save(ddl);
    }

    @Test
    void shouldExecuteSelectQuery() throws Exception {
        SqlConfig insert = new SqlConfig();
        insert.setSqlPath("/test/insert");
        insert.setSqlTemplate("<insert id=\"ins\">INSERT INTO orders(id, name, amount) VALUES(1, 'Test', 99.9)</insert>");
        insert.setDatasourceId("h2_test");
        insert.setEnabled(true);
        sqlConfigRepo.save(insert);

        SqlConfig select = new SqlConfig();
        select.setSqlPath("/test/select");
        select.setSqlTemplate("<select id=\"sel\">SELECT * FROM orders WHERE id = #{id}</select>");
        select.setDatasourceId("h2_test");
        select.setEnabled(true);
        sqlConfigRepo.save(select);

        // Execute DDL
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("sqlPath", "/test/ddl", "params", Map.of()))))
                .andExpect(status().isOk());

        // Insert
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("sqlPath", "/test/insert", "params", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Select
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "sqlPath", "/test/select",
                        "params", Map.of("id", 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].NAME").value("Test"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elapsed").isNumber());
    }

    @Test
    void shouldReturnErrorForUnknownSqlPath() throws Exception {
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "sqlPath", "/nonexistent",
                        "params", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-2));
    }

    @Test
    void shouldExecuteSelectWithDynamicSql() throws Exception {
        SqlConfig select = new SqlConfig();
        select.setSqlPath("/test/dynamic");
        select.setSqlTemplate(
                "<select id=\"dyn\">SELECT * FROM orders " +
                "<where><if test=\"name != null\">AND name = #{name}</if></where></select>");
        select.setDatasourceId("h2_test");
        select.setEnabled(true);
        sqlConfigRepo.save(select);

        // Execute DDL
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("sqlPath", "/test/ddl", "params", Map.of()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "sqlPath", "/test/dynamic",
                        "params", Map.of("name", "Test")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRefreshSqlPath() throws Exception {
        SqlConfig config = new SqlConfig();
        config.setSqlPath("/test/refreshme");
        config.setSqlTemplate("<select id=\"r\">SELECT 1 AS val</select>");
        config.setDatasourceId("h2_test");
        config.setEnabled(true);
        sqlConfigRepo.save(config);

        mockMvc.perform(post("/api/refresh//test/refreshme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
```

- [ ] **Step 4: Run integration tests**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn test -pl api -Dtest=SqlQueryControllerTest -q
```

Expected: PASS — 4 integration tests

- [ ] **Step 5: Run full test suite**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn test -q
```

Expected: All tests pass across all modules

- [ ] **Step 6: Commit**

```bash
git add src/ api/src/test/
git commit -m "test(api): add full-stack integration tests for sqlQuery and refresh endpoints"
```

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] Module structure (admin/core/api/starter) — Tasks 1-9
   - [x] Entity design (SqlConfig, DatasourceConfig) — Task 2
   - [x] Unified API (POST /api/sqlQuery) — Task 8
   - [x] Error codes (-1 to -5) — Task 7
   - [x] MyBatis dynamic SQL parsing — Task 6
   - [x] DataSource pool caching — Task 5
   - [x] Hot-reload (admin save triggers invalidate, refresh endpoint) — Tasks 3, 8
   - [x] AES password encryption — Tasks 2, 3
   - [x] Admin CRUD APIs — Task 4
   - [x] Auto-configuration — Task 9
   - [x] Test strategy (DAO, engine, API layers) — Tasks 2, 6, 10
   - [x] Request body size limit (10MB) — Task 10 (application.yml)
   - [x] SQL execution timeout (30s) — Not explicitly implemented in plan; noted as gap

2. **Placeholder scan:** No TBD, TODO, or vague instructions found. All code is concrete.

3. **Type consistency:**
   - `SqlExecutor.ExecuteResult` used consistently in Tasks 6 and 8 ✓
   - `SqlExecutor.ScriptParseException` used in Tasks 6 and 7 ✓
   - `ApiResponse` static factories used consistently in Tasks 7 and 8 ✓
   - `DataSourceCache.getOrCreate(String)` → `SqlSessionFactory` used consistently ✓
   - `MyBatisScriptEngine.parse(sqlPath, template, configuration)` signature consistent ✓

4. **Gap identified:** Execution timeout (30s) from spec section 9 is not implemented. This can be added as a HikariCP `validationTimeout` or a Spring `@Transactional(timeout=30)` wrapper. Not blocking — can be a follow-up enhancement.
