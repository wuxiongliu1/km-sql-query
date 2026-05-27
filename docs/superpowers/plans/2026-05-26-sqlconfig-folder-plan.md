# SqlConfig 新增 folder 字段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 SqlConfig 新增 folder 字段，支持列表按 folder 筛选，以及获取所有 folder 列表

**Architecture:** 在现有 admin 模块的 SqlConfig 实体、Service、Controller 层逐级添加 folder 字段和筛选逻辑，不涉及 core/api 模块

**Tech Stack:** Spring Boot 2.7.18, JPA/Hibernate, SQLite

---

### Task 1: SqlConfig 实体新增 folder 字段

**Files:**
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/entity/SqlConfig.java`

- [ ] **Step 1: 在 SqlConfig 中增加 folder 字段及 getter/setter**

在 `datasourceId` 字段后面添加 folder 字段，并在 getter/setter 区域添加对应方法：

```java
@Column(length = 100)
private String folder;
```

getter/setter 放在 `datasourceId` 的 getter/setter 之后：

```java
public String getFolder() { return folder; }
public void setFolder(String folder) { this.folder = folder; }
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -pl admin -q
```

- [ ] **Step 3: Commit**

```bash
git add admin/src/main/java/com/kisf/sqlquery/admin/entity/SqlConfig.java
git commit -m "feat(admin): add folder field to SqlConfig entity"
```

---

### Task 2: SqlConfigServiceImpl.update 方法增加 folder 赋值

**Files:**
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java:45-49`

- [ ] **Step 1: 在 update 方法中增加 folder 的赋值**

在 `existing.setDescription(config.getDescription());` 后面添加一行：

```java
existing.setFolder(config.getFolder());
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -pl admin -q
```

- [ ] **Step 3: Commit**

```bash
git add admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java
git commit -m "feat(admin): include folder field in SqlConfig update"
```

---

### Task 3: 列表查询增加 folder 筛选

**Files:**
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/service/SqlConfigService.java:21`
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java:75-90`
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/controller/SqlConfigController.java:44-49`

- [ ] **Step 1: SqlConfigService 接口的 list 方法签名增加 folder 参数**

```java
Page<SqlConfig> list(String sqlPath, String datasourceId, String folder, Pageable pageable);
```

- [ ] **Step 2: SqlConfigServiceImpl.list 方法增加 folder 精确匹配条件**

方法签名改为：

```java
public Page<SqlConfig> list(String sqlPath, String datasourceId, String folder, Pageable pageable) {
```

在 predicates 构建区（`datasourceId` 判断之后）增加：

```java
if (folder != null && !folder.isEmpty()) {
    predicates.add(cb.equal(root.get("folder"), folder));
}
```

- [ ] **Step 3: SqlConfigController.list 方法增加 folder 请求参数**

在方法参数中增加：

```java
@RequestParam(required = false) String folder,
```

调用改为：

```java
return ResponseEntity.ok(PagedResult.of(service.list(sqlPath, datasourceId, folder, PageRequest.of(page, size))));
```

- [ ] **Step 4: 编译验证**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -pl admin -q
```

- [ ] **Step 5: Commit**

```bash
git add admin/src/main/java/com/kisf/sqlquery/admin/service/SqlConfigService.java \
       admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java \
       admin/src/main/java/com/kisf/sqlquery/admin/controller/SqlConfigController.java
git commit -m "feat(admin): add folder filter to SqlConfig list API"
```

---

### Task 4: 新增获取 folder 列表接口

**Files:**
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/repo/SqlConfigRepository.java`
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/service/SqlConfigService.java`
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java`
- Modify: `admin/src/main/java/com/kisf/sqlquery/admin/controller/SqlConfigController.java`

- [ ] **Step 1: SqlConfigRepository 增加查询 distinct folder 的方法**

```java
@Query("SELECT DISTINCT s.folder FROM SqlConfig s WHERE s.folder IS NOT NULL AND s.folder != '' ORDER BY s.folder")
List<String> findDistinctFolders();
```

- [ ] **Step 2: SqlConfigService 接口增加 getFolders 方法**

```java
List<String> getFolders();
```

- [ ] **Step 3: SqlConfigServiceImpl 实现 getFolders**

```java
@Override
public List<String> getFolders() {
    return repo.findDistinctFolders();
}
```

- [ ] **Step 4: SqlConfigController 增加 folders 端点**

```java
@GetMapping("/folders")
public ResponseEntity<List<String>> folders() {
    return ResponseEntity.ok(service.getFolders());
}
```

注意：此方法需放在 `list` 方法之前，避免 `/folders` 被 `/{id}` 误匹配。

- [ ] **Step 5: 编译验证**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn compile -pl admin -q
```

- [ ] **Step 6: 运行测试验证**

```bash
cd /Users/wuxl/kisf_ai/km-sql-query && mvn test -pl admin -q
```

- [ ] **Step 7: Commit**

```bash
git add admin/src/main/java/com/kisf/sqlquery/admin/repo/SqlConfigRepository.java \
       admin/src/main/java/com/kisf/sqlquery/admin/service/SqlConfigService.java \
       admin/src/main/java/com/kisf/sqlquery/admin/service/impl/SqlConfigServiceImpl.java \
       admin/src/main/java/com/kisf/sqlquery/admin/controller/SqlConfigController.java
git commit -m "feat(admin): add GET /api/admin/sqlConfig/folders endpoint"
```
