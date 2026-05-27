# DML 安全防护设计

## 背景

当前 SQL 平台支持 DELETE、INSERT、UPDATE、SELECT 四种操作。开发人员可以自由创建和执行任意 SQL 模板，存在误操作导致全表删除/更新的风险。

## 目标

**在模板保存阶段拦截危险的 DELETE/UPDATE 操作**——缺少 WHERE 条件的 DELETE/UPDATE 模板将被拒绝保存。

## 范围

- DELETE 和 UPDATE 模板：**必须**包含 WHERE 条件
- INSERT 模板：**不限制**（天生无 WHERE）
- SELECT 模板：**不限制**

## 设计方案

### 核心组件

新增 `DmlSafetyValidator`，放在 `core` 模块的 `engine` 包中，与 `SqlExecutor`、`MyBatisScriptEngine` 同级：

```
core/src/main/java/com/kisf/sqlquery/core/engine/DmlSafetyValidator.java
```

### 校验逻辑

```
validate(sqlTemplate):
    1. 检测 SQL 标签类型（复用 existing detectCommandType 逻辑）
    2. 如果是 SELECT 或 INSERT → 直接放行
    3. 如果是 DELETE 或 UPDATE:
       a. 调用 extractInnerSql(sqlTemplate) 提取内层 SQL 文本
       b. 对内层 SQL 做 case-insensitive 检查是否包含 "where"
       c. 不包含 → 抛出 DmlSafetyException
       d. 包含 → 放行
```

`extractInnerSql` 会将 MyBatis 动态标签序列化回 XML 文本，因此：
- 静态 WHERE：`WHERE id=#{id}` → 含 "where" → 通过
- 动态 WHERE：`<where>...</where>` → 展开后含 "where" → 通过
- `<trim prefix="WHERE">` → 展开后含 "where" → 通过
- 无 WHERE 的裸 SQL → 不含 "where" → 拒绝

### 集成点

`SqlConfigServiceImpl.save()` 和 `update()` 方法在持久化前调用：

```java
dmlSafetyValidator.validate(config.getSqlTemplate());
```

校验不通过则抛出 `DmlSafetyException`（继承 `RuntimeException`），事务回滚，模板不落库。

### 异常处理

`GlobalExceptionHandler` 捕获 `DmlSafetyException`，返回 HTTP 400 和中文提示："DELETE/UPDATE 操作必须包含 WHERE 条件，不允许全表操作"。

### 边界情况

| 情况 | 行为 | 说明 |
|------|------|------|
| `WHERE 1=1` | 通过 | 含 WHERE 关键字，本需求不检测条件质量 |
| 列名含 "where" | 通过（误判安全） | 极罕见，偏严方向无害 |
| 注释含 "WHERE" | 通过（误判安全） | 同上 |

## 测试策略

| 模板 | 预期 |
|------|------|
| `<delete>DELETE FROM t WHERE id=#{id}</delete>` | 通过 |
| `<delete>DELETE FROM t</delete>` | 拒绝 |
| `<delete>DELETE FROM t <where><if test="id!=null">id=#{id}</if></where></delete>` | 通过 |
| `<update>UPDATE t SET name=#{n} WHERE id=#{id}</update>` | 通过 |
| `<update>UPDATE t SET name=#{n}</update>` | 拒绝 |
| `<insert>INSERT INTO t VALUES (#{v})</insert>` | 通过 |
| `<select>SELECT * FROM t</select>` | 通过 |

## 影响范围

- **新增**：`DmlSafetyValidator.java` + `DmlSafetyException.java`
- **修改**：`SqlConfigServiceImpl.java`（注入 validator，save/update 中调用）
- **修改**：`GlobalExceptionHandler.java`（捕获新异常）
- 不涉及 API 接口变更，向前兼容
