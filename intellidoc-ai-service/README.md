# IntelliDoc AI Service 🤖

AI-powered code documentation generator using **Google Gemini**. This FastAPI microservice is the AI brain of IntelliDoc — it selects important files, generates per-file documentation, and produces a master README for any GitHub repository.

---

## Features

- 🧠 **AI Architect Agent** — intelligently selects the most important source files to document
- 📦 **Batch Documentation** — documents multiple files in a single Gemini API call (60–70% fewer API calls)
- 🔑 **Multi-Key Rotation** — supports up to 9 Gemini API keys in round-robin to maximize throughput
- ⏱️ **Proactive Rate Limiter** — token-bucket limiter prevents hitting Gemini quota limits
- 🔄 **Smart Retry** — distinguishes 503 (server overload) from 429 (rate limit) and applies appropriate backoff

---

## Setup

### 1. Install Dependencies

```bash
pip install -r requirements.txt
```

### 2. Configure Environment Variables

Create a `.env` file in this directory (copy the template below):

```env
# Primary Gemini API key (required)
GOOGLE_API_KEY=your_api_key_here

# Optional: additional keys for rotation (up to GOOGLE_API_KEY_9)
GOOGLE_API_KEY_2=your_second_key_here
GOOGLE_API_KEY_3=your_third_key_here
```

| Variable | Description | Required |
|---|---|---|
| `GOOGLE_API_KEY` | Primary Google Gemini API key | ✅ Yes |
| `GOOGLE_API_KEY_2` … `GOOGLE_API_KEY_9` | Additional keys for round-robin rotation | Optional |

> **Get an API key:** [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)

### 3. Run Locally

```bash
uvicorn app:app --reload --port 8000
```

The service will be available at `http://localhost:8000`.

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Health check — returns service status and number of loaded API keys |
| `POST` | `/generate-docs` | Generate documentation for a **single file** from a raw prompt |
| `POST` | `/generate-docs-batch` | Generate documentation for **multiple files** in one API call |
| `POST` | `/select-files` | AI Architect — select the most important files from a file tree |

### `GET /`
Returns:
```json
{
  "status": "ok",
  "service": "IntelliDoc AI",
  "model": "gemini-2.5-flash-lite",
  "api_keys_loaded": 3
}
```

### `POST /generate-docs`
```json
{
  "prompt": "Document this code: ..."
}
```
Returns: `{ "documentation": "..." }`

### `POST /generate-docs-batch`
```json
{
  "files": [
    { "path": "src/service/UserService.java", "content": "..." },
    { "path": "src/controller/AuthController.java", "content": "..." }
  ],
  "project_context": "src/\n  service/\n    UserService.java\n  ..."
}
```
Returns: `{ "results": [{ "path": "...", "documentation": "..." }] }`

### `POST /select-files`
```json
{
  "file_structure": "src/\n  main/\n    app.py\n  tests/\n    test_app.py\n..."
}
```
Returns: `{ "selected_files": ["src/main/app.py", "..."] }`

---

## Model

This service uses **`gemini-2.5-flash-lite`** — stable, fast, and optimized for the free tier with minimal 503 capacity issues.

---

## Rate Limiting

The service uses a **proactive token-bucket limiter** that sleeps *before* making API calls to stay within Gemini free-tier quotas:

- Default: **12 RPM** with a **4-second minimum interval** between calls
- Tuned for 3 API keys × 5 RPM each = 15 RPM max (12 RPM used for safety margin)
- Configure by editing `RateLimiter(rpm_limit=..., min_interval_seconds=...)` in `app.py`

---

## Multi-Key Support

Add keys as `GOOGLE_API_KEY`, `GOOGLE_API_KEY_2`, `GOOGLE_API_KEY_3`, etc. in your `.env`. The service automatically rotates through all available keys in round-robin order on each API call. This multiplies your effective rate limit (e.g., 3 keys = ~15 RPM on the free tier).

---

## Deployment (Hugging Face Spaces)

This service is configured for **Hugging Face Spaces** using Docker (`sdk: docker` in the YAML front matter).

1. Push this directory to a Hugging Face Space
2. Add all environment variables as **Secrets** in the Space settings:
   - `GOOGLE_API_KEY`, `GOOGLE_API_KEY_2`, etc.
3. The Dockerfile will build and start `uvicorn` automatically

---

## Project Structure

```
intellidoc-ai-service/
├── app.py              # Main FastAPI application — all endpoints and logic
├── requirements.txt    # Pinned Python dependencies
├── Dockerfile          # Container build for HF Spaces / production
├── .env.prod           # Production env var template (do not commit secrets)
├── test.py             # Basic endpoint tests
└── README.md           # This file
```
