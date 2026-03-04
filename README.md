# Award Travel Updates

A webapp that aggregates award travel news from Reddit and online blogs, then surfaces it on a clean website.

## Tech Stack

| Layer     | Technology                  |
|-----------|-----------------------------|
| Backend   | Java 21, Spring Boot 3, Gradle |
| Frontend  | React (Vite)                |
| Repo      | Monorepo (`/backend`, `/frontend`) |

## Project Structure

```
award-travel-updates/
├── backend/          # Spring Boot REST API
│   ├── src/
│   │   └── main/java/com/awardtravelupdates/
│   │       ├── Application.java
│   │       ├── config/CorsConfig.java
│   │       └── controller/HealthController.java
│   ├── build.gradle
│   └── gradlew
├── frontend/         # React SPA (Vite)
│   ├── src/
│   ├── vite.config.js
│   └── package.json
└── .gitignore
```

## Running Locally

### Backend

```bash
cd backend
./gradlew bootRun
# API available at http://localhost:8080/api
```

### Frontend

```bash
cd frontend
npm install
npm run dev
# App available at http://localhost:5173
```

The Vite dev server proxies `/api/*` requests to the Spring Boot backend automatically, so no CORS issues during development.

## API Endpoints

| Method | Path         | Description        |
|--------|--------------|--------------------|
| GET    | /api/health  | Health check       |
