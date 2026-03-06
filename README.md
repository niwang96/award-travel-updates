# Award Travel Updates

Award Travel Updates aggregates award-flight news from Reddit (r/awardtravel, r/churning) and travel blogs (Doctor of Credit, Frequent Miler), summarizes posts with an LLM, and surfaces award flight deals parsed from Roame email alerts — all on a single-page React app.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3, Gradle, H2 (embedded DB), Spring Data JPA |
| LLM | Groq API — llama-3.3-70b-versatile |
| Frontend | React 19, React Router 7, Vite 7 |
| External APIs | Reddit JSON API, RSS (Rome), Gmail API, Google OAuth2 |

## Project Structure

```
award-travel-updates/
├── backend/src/main/java/com/awardtravelupdates/
│   ├── config/         # Spring beans: RestClients, CORS, JPA converters, secrets config
│   ├── constants/      # Reddit and blog constants (URLs, limits, staleness thresholds)
│   ├── controller/     # REST endpoints (Blog, Subreddit, EmailDeals, Health)
│   ├── agent/          # LLM summarization agents (one per source)
│   ├── service/        # Orchestration services (caching, summary coordination)
│   ├── accessor/       # External API clients (Reddit, RSS, Gmail, Groq)
│   ├── model/          # Domain records + JPA entities
│   └── repository/     # Spring Data JPA repositories
└── frontend/src/
    ├── pages/          # Home, FlightDeals, RecentNews
    ├── components/     # Nav
    └── hooks/          # useFetch
```

## Architecture Overview

The system uses a two-level cache to minimize LLM calls and external API requests.

- **Level 1 (summary cache):** `BlogSummary`/`SubredditSummary`/`EmailDealsSummary` DB rows, keyed by source ID, TTL 3 hours. A fresh entry is returned immediately without fetching from external APIs.
- **Level 2 (per-post LLM cache):** `PostSummaryCache` DB rows, keyed by post URL, permanent. When regenerating an L1 summary, individual posts that were already summarized reuse their stored LLM output instead of calling Groq again.

**Reddit/Blog flow** (via `AbstractCachingSummaryService` + Agent):
```
Request → Controller
           → AbstractCachingSummaryService
               ├─ L1 cache hit? → return immediately
               └─ L1 miss/stale:
                   ├─ *Accessor fetches posts (Reddit/RSS)   [parallel]
                   └─ Agent.summarize(posts)
                       ├─ L2 cache hit per post → use stored text
                       └─ L2 miss → GroqAccessor (LLM) → save to L2
                   → save to L1
                   → return
```

**Email deals flow** (via `EmailDealsSummaryService`, no LLM step):
```
Request → EmailDealsController
           → EmailDealsSummaryService
               ├─ L1 cache hit? → return immediately
               └─ L1 miss/stale:
                   → GmailAccessor.fetchRecentDeals()
                   → save to L1
                   → return
```

### Accessor classes

| Class | Description |
|---|---|
| `RedditAccessor` | Reddit JSON API — no auth required |
| `BlogAccessor` | RSS feeds via Rome library |
| `GmailAccessor` | Gmail API with Google OAuth2 refresh-token flow |
| `GroqAccessor` | Groq LLM API, with 3-retry exponential backoff on rate limits |

All sources are fetched in parallel via a virtual thread pool; LLM calls are sequential to avoid Groq rate limits.

### Agent classes

| Class | Description |
|---|---|
| `AbstractSummaryAgent` | Shared caching + LLM call infrastructure |
| `AbstractRedditSummaryAgent` | Base class for Reddit sources |
| `AbstractBlogSummaryAgent` | Base class for RSS blog sources |
| `RAwardTravelSummaryAgent` | r/awardtravel — top posts, 1-month window |
| `RChurningSummaryAgent` | r/churning — new posts + top comments from "news and updates" threads |
| `DoCBlogSummaryAgent` | Doctor of Credit RSS — posts from last 3 days |
| `FMBlogSummaryAgent` | Frequent Miler RSS — posts from last 3 days |

## Running Locally

```bash
# Backend
cd backend && ./gradlew bootRun
# → http://localhost:8080/api

# Frontend (separate terminal)
cd frontend && npm install && npm run dev
# → http://localhost:5173
```

Vite proxies `/api/*` requests to `localhost:8080` automatically — no CORS issues during development.

## Configuration

Set the following in `backend/src/main/resources/application.properties`:

| Property | Description |
|---|---|
| `groq.api-key` | Groq API key |
| `google.client-id` | Google OAuth2 client ID |
| `google.client-secret` | Google OAuth2 client secret |
| `google.gmail-refresh-token` | Gmail OAuth2 refresh token (one-time auth) |

## API Endpoints

| Method | Path | Description | Response |
|---|---|---|---|
| GET | `/api/health` | Health check | `{"status":"ok"}` |
| GET | `/api/subreddit-summaries` | All subreddit summaries | `Map<String, SummaryResult>` |
| GET | `/api/subreddit-summaries/{subreddit}` | Single subreddit summary | `SummaryResult` |
| GET | `/api/blog-summaries` | All blog summaries | `Map<String, SummaryResult>` |
| GET | `/api/blog-summaries/{blogId}` | Single blog summary | `SummaryResult` |
| GET | `/api/email-deals` | Roame award flight deals from Gmail | `List<SummaryUpdate>` |

**Response shapes:**

```json
SummaryResult  { "updates": [...], "stale": false }
SummaryUpdate  { "text": "...", "source": "https://...", "timestamp": 1234567890 }
```

## External Services

| Service | Used for |
|---|---|
| Reddit JSON API | r/awardtravel (top/month), r/churning (new + comments) — no auth needed |
| RSS (Rome library) | Doctor of Credit and Frequent Miler blog feeds |
| Groq API | LLM summarization (llama-3.3-70b-versatile), 3-retry exponential backoff |
| Gmail API + Google OAuth2 | Parses Roame flight deal alert emails |
