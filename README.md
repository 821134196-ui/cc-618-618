# 城市 AED 设备巡检与急救取用回补系统

面向公众的城市 AED（自动体外除颤器）查询平台 + 面向维护人员的巡检/回补工作台。

- **公众**：地图 + 可搜索文字列表查找附近 AED，看到的设备状态实时可信——巡检过期、耗材过期、箱体故障的设备会明确标出停用原因；紧急取用可一键模拟报警。
- **维护人员**：逐项巡检登记（电池 / 电极片 / 箱体，含实物耗材批次），处理取用后的耗材回补与复检，核对补回耗材的实际批次。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21 · Quarkus 3.15（REST + Hibernate ORM） |
| 数据库 | SQLite（`backend/aed.db`，首次启动自动建表并写入演示数据） |
| 前端 | Svelte 4 + Vite（构建产物由 Quarkus 静态托管） |
| 地图/定位/报警 | 本地模拟（SVG 街区图 + 模拟定位点 + 模拟报警按钮，无外部依赖） |

## 一键启动

```bash
./start.sh
```

启动后访问：

- 公众查询页： http://localhost:8080/
- 维护后台：   http://localhost:8080/#/admin

> 脚本会自动定位系统 JDK/Maven 或 `~/tools` 下的便携版；首次执行会下载依赖，耗时较长。

## 验证

```bash
./verify.sh
```

后端集成测试（`backend/src/test/java/com/cityaed/AedFlowTest.java`）覆盖关键业务约束：

1. **过期设备不能显示可用**：巡检过期 / 耗材过期 / 箱体故障 / 从未巡检的设备均为 `OUT_OF_SERVICE`，并返回具体原因（如“巡检过期”“耗材过期：电池”“箱体故障”）。
2. **取用后必须完成回补和复检**：紧急取用 → `PENDING_REPLENISHMENT`（待回补）→ 回补登记 → 仍为 `PENDING_REPLENISHMENT`（待复检）→ 复检通过 → 事件闭环，设备恢复 `AVAILABLE`。
3. **批次核对**：复检登记的耗材批次必须与回补登记的实际批次一致，否则复检被拒绝（400）。
4. **取用记录不可刷新清除**：设备状态由底层记录实时派生，没有“刷新/直接置为可用”的入口；反复查询、甚至补一条合格例行巡检，都无法让存在未闭环取用事件的设备恢复可用。
5. **信息隔离**：公众 API 的 DTO 不含内部维修电话（响应体中断言无 `maintenancePhone` 字段）；维护端 API 可见。
6. **地图与列表一致**：前端地图与文字列表渲染同一个数据源（同一 Svelte store / 同一接口），测试验证列表、详情、搜索过滤三种视图状态一致。

## 业务规则

### 设备状态（实时派生，不落库）

| 状态 | 含义 | 判定 |
| --- | --- | --- |
| `AVAILABLE` 可用 | 可放心取用 | 无未闭环取用事件，且最近一次巡检在 90 天有效期内、三项检查（电池/电极片/箱体）全部通过、耗材未过期 |
| `PENDING_REPLENISHMENT` 待回补 | 紧急取用后等待处理 | 存在未闭环取用事件（待回补 / 已回补待复检） |
| `OUT_OF_SERVICE` 停用 | 不可取用，显示具体原因 | 未巡检 / 巡检过期（>90 天）/ 箱体故障 / 耗材过期（电池、电极片）/ 检查项未通过 |

### 取用-回补-复检状态机

```
公众紧急取用 ──> OPEN(待回补)
                    │ 维护人员登记回补（录入实际耗材批次+有效期，过期耗材拒绝）
                    ▼
              REPLENISHED(待复检)
                    │ 复检：逐项复查 + 核对批次（与回补登记不一致则拒绝）
                    ├─ 通过 ─> CLOSED（事件闭环，设备状态重新派生，满足条件即恢复可用）
                    └─ 未通过 ─> 保持 REPLENISHED，整改后再次复检
```

例行巡检（维护后台「巡检登记」）只更新巡检记录，**不会**清除未闭环的取用事件。

## API 概览

公众端（不输出内部字段）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/public/aeds?query=&status=` | 设备列表（地图与文字列表共用的唯一数据源） |
| GET | `/api/public/aeds/{id}` | 设备详情 |
| POST | `/api/public/aeds/{id}/use` | 紧急取用（模拟报警），设备立即转待回补 |

维护端：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/api/admin/aeds` | 设备列表（含内部维修电话）/ 新增设备 |
| GET | `/api/admin/aeds/{id}` | 设备详情（巡检历史 + 取用事件历史） |
| POST | `/api/admin/aeds/{id}/inspections` | 例行巡检登记（逐项 + 耗材批次） |
| GET | `/api/admin/usage-events?status=` | 取用事件列表 |
| POST | `/api/admin/usage-events/{id}/replenish` | 回补登记（实际批次，OPEN→REPLENISHED） |
| POST | `/api/admin/usage-events/{id}/recheck` | 复检（核对批次，通过则 CLOSED） |

## 目录结构

```
├── start.sh               # 一键启动
├── verify.sh              # 测试 + 构建验证
├── backend/               # Quarkus 后端
│   └── src/main/java/com/cityaed/
│       ├── model/         # AedDevice / Inspection / UsageEvent / Replenishment
│       ├── service/       # DeviceStatusService（状态派生，核心）
│       ├── api/           # 公众端 + 维护端 REST 资源、DTO
│       └── SeedData.java  # 演示数据（8 台设备覆盖各状态）
└── frontend/              # Svelte 前端
    └── src/components/    # 公众页（地图/列表/详情）+ 维护后台（事件/巡检/设备）
```

## 演示数据

首次启动写入 8 台设备（`AED-0001`~`AED-0008`），覆盖：可用、待回补、待复检（可演示批次核对）、巡检过期、耗材过期、箱体故障、未巡检。删除 `backend/aed.db` 可重置。
