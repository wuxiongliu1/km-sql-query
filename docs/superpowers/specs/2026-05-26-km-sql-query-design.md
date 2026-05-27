# KM-SQL-Query 在线 SQL 执行平台 —— 设计文档

## 项目概述

基于 Spring Boot 开发的内部低代码 SQL 执行平台。核心理念：**SQL 即接口** —— 通过统一 HTTP 端点接收请求，根据 `sqlPath` 路由到预配置的 MyBatis 动态 SQL 模板，绑定多数据源执行，减少重复 CRUD 编码。

## 技术选型

- JDK 1.8
- Spring Boot 2.x
- MyBatis 3.x + mybatis-spring-boot-starter
- Spring Data JPA (Hibernate)
- SQLite（配置库）
- HikariCP（业务数据源连接池）

## 模块结构

```
km-sql-query/
├── core              # 核心引擎：MyBatis 集成、SQL 解析、执行、缓存管理
├── admin             # 管理模块：JPA 实体、配置 CRUD、管理 API、热更新触发
├── api               # 统一入口：POST /api/sqlQuery 路由分发
└── starter           # Spring Boot 自动装配 starter
```

**依赖关系**：`api → core → admin`

## 实体设计

### SqlConfig（SQL 配置）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK | 自增主键 |
| sqlPath | String UNIQUE | 接口路径标识，如 `/order/detail` |
| sqlTemplate | Text/CLOB | 完整 MyBatis XML 片段，必须以 `<select>/<insert>/<update>/<delete>` 标签开头 |
| datasourceId | String FK | 绑定的数据源标识 |
| description | String | 功能描述 |
| enabled | Boolean | 启用/禁用 |
| createdAt | DateTime | 创建时间 |
| updatedAt | DateTime | 更新时间 |

### DatasourceConfig（数据源配置）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String PK | 数据源唯一标识，如 `order_db` |
| driverClass | String | JDBC 驱动类名 |
| jdbcUrl | String | JDBC 连接串 |
| username | String | 用户名 |
| password | String | AES 加密存储的密码 |
| poolSize | Integer | 连接池大小，默认 10 |
| extra | Text/JSON | 扩展配置（连接超时、字符集、最大生命周期等 HikariCP 参数） |
| enabled | Boolean | 启用/禁用 |
| createdAt | DateTime | 创建时间 |
| updatedAt | DateTime | 更新时间 |

## 统一入口 API

### 请求

```json
POST /api/sqlQuery
Content-Type: application/json

{
  "sqlPath": "/order/detail",
  "params": {
    "orderId": "123",
    "status": "PAID"
  }
}
```

### 成功响应（查询）

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "orderId": "123", "amount": 99.9 }
  ],
  "total": 1,
  "elapsed": 15
}
```

### 成功响应（更新）

```json
{
  "code": 0,
  "message": "ok",
  "data": { "affectedRows": 3 },
  "elapsed": 8
}
```

### 错误码

| code | 含义 |
|------|------|
| 0 | 成功 |
| -1 | 参数校验失败 |
| -2 | sqlPath 未找到或已禁用 |
| -3 | 数据源不可用 |
| -4 | SQL 执行异常 |
| -5 | SQL 模板解析失败 |

## 核心执行引擎

### 执行流程

```
POST /api/sqlQuery
  → 解析请求体，取 sqlPath + params
  → 从 SQLite 查 SqlConfig by sqlPath
  → 获取对应 DatasourceConfig
  → 获取或创建 SqlSessionFactory（数据源缓存池）
  → XMLScriptBuilder 解析 sqlTemplate 为 SqlNode
  → 动态构造 MappedStatement 注册到 Configuration
  → SqlSession.selectList / selectOne / insert / update / delete
  → JSON 返回结果
```

### MyBatis 语法支持

- 完整 MyBatis 动态 SQL 标签：`<if>`, `<where>`, `<foreach>`, `<choose>/<when>/<otherwise>`, `<set>`, `<trim>`
- 片段复用：`<sql>` 和 `<include>`
- 参数占位符：`#{xxx}`（预编译安全注入）和 `${xxx}`（直接替换）
- 模板必须以 `<select>/<insert>/<update>/<delete>` 标签开头

### 缓存策略

- **数据源工厂缓存**：`Map<String, SqlSessionFactory>`，按 datasourceId 懒加载
- **MappedStatement 缓存**：`Map<String, MappedStatement>`，按 sqlPath 缓存解析结果
- **缓存粒度为单个 sqlPath**，热更新时仅失效变动的那条

## 热更新机制

1. **管理端保存时自动触发**：`admin` 模块在 SqlConfig JPA save/delete 操作后，同步调用 `core.invalidate(sqlPath)`，当前实例的对应 MappedStatement 缓存立即失效，下次请求重建
2. **多实例手动刷新接口**：`POST /api/refresh/{sqlPath}`，运维调用使指定 sqlPath 在各实例上重新加载

无需 MQ 或消息中间件，纯内存懒加载缓存。

## 数据源连接池管理

- 每个 `DatasourceConfig` 对应一个独立的 `HikariDataSource`
- `Map<String, HikariDataSource>` 按 datasourceId 缓存
- 池大小来自 `poolSize` 字段，`extra` JSON 中的参数可覆盖默认 HikariCP 配置
- 数据源懒加载：首次被使用时创建
- 数据源配置变更（admin 更新 DatasourceConfig）时，关闭旧连接池后重建

## Admin 管理 API

```
POST   /api/admin/sqlConfig             # 创建（保存后自动失效缓存）
PUT    /api/admin/sqlConfig/{id}         # 更新（保存后自动失效缓存）
DELETE /api/admin/sqlConfig/{id}         # 删除（保存后自动失效缓存）
GET    /api/admin/sqlConfig/{id}         # 查询单个
GET    /api/admin/sqlConfig/list         # 分页列表，支持 sqlPath/datasourceId 筛选

POST   /api/admin/datasource             # 创建
PUT    /api/admin/datasource/{id}        # 更新（关闭旧连接池后重建）
DELETE /api/admin/datasource/{id}        # 删除
GET    /api/admin/datasource/{id}        # 查询单个
GET    /api/admin/datasource/list        # 列表
```

密码 AES 加密存储，库中不存明文。

## 安全与异常处理

- SQL 注入防护：`#{}` 走 PreparedStatement 预编译防注入；`${}` 由开发者自行控制
- 连接泄漏：HikariCP 自带超时回收
- 执行超时：单 SQL 最大执行时间 30s，超时抛 `SQLTimeoutException`
- 统一异常兜底：`@RestControllerAdvice` 捕获未处理异常，返回 `{ code: -4, message: "..." }`
- 请求体大小限制 10MB
- 内网部署，无需鉴权

## 测试策略

| 层级 | 内容 | 框架 |
|------|------|------|
| DAO 层 | JPA Repository，SQLite 内存模式 | `@DataJpaTest` |
| Core 引擎 | MyBatis 模板解析、SQL 执行、缓存管理 | JUnit + Mockito |
| API 层 | 完整链路：POST /api/sqlQuery → SQLite → MyBatis → H2 内存数据库 | `@SpringBootTest` + MockMvc |
