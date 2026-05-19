# CLAUDE.md

FlightCompare — Android 机票全平台比价 App

## 项目结构

```
E:/flightcompare/
├── backend/         Python FastAPI 后端 (爬虫 + API)
├── android/         Kotlin Jetpack Compose Android 客户端
└── CLAUDE.md
```

## 后端 (backend/)

- **框架:** FastAPI + Uvicorn
- **数据库:** SQLite (async, SQLAlchemy + aiosqlite)
- **爬虫:** Playwright (Google Flights)
- **启动:** `cd backend && uvicorn app.main:app --reload --port 8000`
- **种子数据:** `cd backend && python scripts/seed_demo.py`
- **API 文档:** 启动后访问 http://localhost:8000/docs

### 后端目录

```
app/
├── main.py           FastAPI 入口, lifespan (启动/停止 browser + scheduler)
├── config.py         pydantic-settings, 读 .env
├── models/           SQLAlchemy ORM: Flight, Offer, PriceSnapshot, Bookmark, Alert, SearchHistory
├── schemas/          Pydantic 请求/响应 schema
├── routers/          flights, bookmarks, alerts, events(SSE)
├── services/         flight_service, bookmark_service, alert_service, monitor_service
├── scraper/          google_flights (Playwright), engine, rate_limiter, cache, user_agents
├── sse/              EventManager (SSE 广播)
└── db/               session.py (async engine)
```

### API 端点

| Method | Path | 说明 |
|--------|------|------|
| POST | /api/v1/flights/search | 搜索航班 |
| GET | /api/v1/flights/search/{id} | 轮询搜索状态 |
| GET | /api/v1/flights/{id} | 航班详情 + 最低价 |
| GET | /api/v1/flights/{id}/prices | 价格历史 |
| POST/GET/DELETE | /api/v1/bookmarks/ | 收藏 CRUD |
| POST/GET/PUT/DELETE | /api/v1/alerts/ | 提醒 CRUD |
| GET | /api/v1/events/stream | SSE 推送 |
| GET | /api/v1/health | 健康检查 |

## Android (android/)

- **语言:** Kotlin 2.1.0
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **网络:** Retrofit + OkHttp (含 SSE)
- **数据库:** Room
- **最低 SDK:** 26, **目标 SDK:** 35
- **BASE_URL:** `http://10.0.2.2:8000/api/v1/` (模拟器指向宿主机 localhost)

### Android 目录

```
app/src/main/java/com/flightcompare/
├── FlightCompareApp.kt    @HiltAndroidApp
├── MainActivity.kt        Compose 入口
├── navigation/            NavGraph, Routes (BottomNav 三标签)
├── ui/theme/              Material 3 主题 (动态取色)
├── ui/screens/            search, results, detail, history, bookmarks, alerts
├── ui/components/         FlightCard, PriceTag, LoadingOverlay, ErrorBanner, EmptyState
├── data/remote/           ApiService (Retrofit), SseClient, DTOs, Mapper
├── data/local/            Room DB, DAOs, Entities
├── data/repository/       FlightRepository
├── domain/model/          领域模型
└── di/                    Hilt Module (App + Network)
```

### 页面流

```
BottomNav: [搜索] [收藏] [提醒]
搜索 → Results → Detail → History
收藏 → Detail
提醒 → Detail
```

## 开发命令

```bash
# 后端
cd E:/flightcompare/backend
uvicorn app.main:app --reload --port 8000

# 种子数据（免爬虫测试）
python scripts/seed_demo.py

# Android
# 用 Android Studio 打开 E:/flightcompare/android/
# 或在 android/ 下运行: ./gradlew assembleDebug
```

## Git 操作

- 已授权：commit/push/PR 无需逐次确认
- 仓库: https://github.com/Chami537/flightcompare (private)

## 注意事项

- 爬虫依赖 Playwright 浏览器: `pip install playwright && playwright install chromium`
- Google Flights 爬虫有反检测措施（stealth JS, UA 轮换, rate limiter）
- 搜索结果缓存 30 分钟，避免频繁爬取被封
- Android 用 `10.0.2.2` 访问宿主机（模拟器）, 真机需改 `BASE_URL`
