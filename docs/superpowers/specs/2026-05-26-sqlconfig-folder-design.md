# SqlConfig 新增 folder 字段

## 背景

当前 SqlConfig 通过 `sqlPath`（如 `/order/detail`）标识接口，缺乏分类维度。新增 `folder` 字段让每个 sqlPath 归属到某个文件夹，仅支持一级目录，方便分类管理。

## 数据模型

### SqlConfig 实体变更

- 新增字段 `folder`，`VARCHAR(100)`，可空（兼容已有数据）
- `update` 方法中同步增加 `folder` 的赋值

## 接口变更

### 1. 列表查询 — 增加 folder 筛选参数

`GET /api/admin/sqlConfig/list` 新增可选参数 `folder`（精确匹配）。

- `SqlConfigController.list`：增加 `@RequestParam(required = false) String folder`
- `SqlConfigService.list`：方法签名增加 `String folder` 参数
- `SqlConfigServiceImpl.list`：动态查询中增加 `folder` 的 `equal` 条件

### 2. 获取 folder 列表

新增接口 `GET /api/admin/sqlConfig/folders`，返回 `List<String>`，用于前端筛选下拉框。

## 不影响

- 核心执行链路（sqlPath 路由 → SqlExecutor → MyBatis）不变
- `sqlPath` 唯一性约束不变
- 现有 API 兼容
