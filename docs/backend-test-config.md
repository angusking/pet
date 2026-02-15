# 后端测试配置说明

## 默认行为

本项目使用 `D:\pet\backend\.mvn\maven.config` 控制测试执行。

当前默认值：

- `-DskipTests=false`（执行测试）
- `-Dtest.exclude.tags=integration`（跳过容器类测试）

## 执行全部测试（包含 Testcontainers）

把 `D:\pet\backend\.mvn\maven.config` 修改为：

```
-DskipTests=false
-Dtest.exclude.tags=
```

## 跳过全部测试

把 `D:\pet\backend\.mvn\maven.config` 修改为：

```
-DskipTests=true
```
