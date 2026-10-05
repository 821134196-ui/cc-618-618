# 城市 AED 设备巡检与急救取用回补系统

面向公众的 AED（自动体外除颤器）查询平台 + 面向维护人员的巡检/回补工作台。

- **后端**：Java 21 + Quarkus（REST + Hibernate ORM/Panache），SQLite 持久化
- **前端**：Svelte + Vite，构建后由 Quarkus 直接托管，单端口运行
- **定位与报警**：本地模拟（SVG 城区地图 + 报警事件记录）

## 一键启动

```bash
./start.sh
```

启动后访问：

| 页面 | 地址 |
|---|---|
| 公众查询（地图 + 可搜索列表） | http://localhost:8080/ |
| 维护工作台 | http://localhost:8080/#/admin |

首次启动自动建表并写入 8 台演示设备，覆盖全部状态（可用 / 待回补 / 巡检过期 / 耗材过期 / 箱体故障）。

运行验证测试：

```bash
./start.sh --test        # 或 cd backend && mvn test
```

## 业务规则

**设备状态不存库、不落缓存**，每次由事实记录实时推导（`DeviceStatusService`）：

1. 存在未闭环的取用事件 → **待回补**
2. 箱体故障（巡检未通过）→ **停用：箱体故障**
3. 电池/电极片未通过巡检 → **停用**
4. 电池或电极片超过有效期 → **停用：耗材过期**
5. 超过下次应巡检日期（巡检有效期 90 天）→ **停用：巡检过期**
6. 否则 → **可用**

由此保证：

- **过期设备不可能显示可用** —— 状态由日期与故障标志实时计算，无人工置"可用"的入口。
- **取用后必须完成回补和复检** —— 紧急取用创建不可删除的 `RetrievalEvent`；只有回补登记（记录实际耗材批次，过期耗材会被拒绝）且复检全部通过，事件才闭环、设备才恢复可用。仅回补不复检，设备仍是待回补。
- **刷新不能清除取用记录** —— 没有"重置/刷新状态"接口；重复取用同一设备返回 409。
- **地图与列表一致** —— 前端两个视图渲染同一个 store（同一份 `/api/public/devices` 数据），后端也有一致性测试。
- **公众页不展示内部维修电话与耗材批次** —— 公众 DTO 在结构上就不含这些字段（有测试断言）；维护端台账与设备记录页可核对补回耗材的实际批次。

## 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/public/devices` | 公众设备列表（地图与列表共用，无内部信息） |
| GET | `/api/public/devices/{id}` | 公众设备详情 |
| POST | `/api/public/devices/{id}/retrieve` | 紧急取用上报（触发本地模拟报警） |
| GET | `/api/admin/devices` | 台账（含内部电话、耗材批次） |
| POST | `/api/admin/devices/{id}/inspections` | 巡检录入（电池/电极片/箱体逐项 + 实物批次） |
| GET | `/api/admin/retrievals?open=true` | 取用事件（可只看未闭环） |
| POST | `/api/admin/retrievals/{id}/restock` | 回补登记（核对实际批次，过期拒绝） |
| POST | `/api/admin/retrievals/{id}/recheck` | 复检（全部通过才闭环） |
| GET | `/api/admin/alarms` | 报警记录 |

## 目录结构

```
├── start.sh               # 一键启动（--test 运行测试）
├── backend/               # Quarkus + Hibernate + SQLite
│   └── src/main/java/com/cityaed/
│       ├── model/         # Device / Inspection / RetrievalEvent / AlarmEvent
│       ├── service/       # DeviceStatusService（状态推导）/ MaintenanceService / DataSeeder
│       ├── dto/           # 公众 DTO（无内部字段）与维护 DTO
│       └── web/           # PublicResource / AdminResource
│   └── src/test/          # 12 个验证测试
└── frontend/              # Svelte + Vite
    └── src/components/    # 公众页（地图/列表/详情取用）与维护工作台
```

## 验证测试覆盖（`backend/src/test`）

- 巡检过期 / 耗材过期 / 箱体故障的设备均显示停用且带具体原因
- 取用 → 待回补；未回补先复检被拒；回补后仍待复检；复检通过才恢复可用
- 复检不通过事件不闭环；过期耗材不能用于回补
- 反复刷新（重复查询）状态与取用记录不变；同一设备重复取用返回 409
- 公众接口响应不含内部维修电话与耗材批次；维护接口包含
- 地图与列表两次取数的状态映射完全一致；停用设备必带原因
- 取用触发本地模拟报警记录
