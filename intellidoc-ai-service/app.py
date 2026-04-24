import os
import logging
import json
import time
import random
import threading
from google import genai
from google.genai import types
from fastapi import FastAPI
from pydantic import BaseModel
from dotenv import load_dotenv

load_dotenv()
logging.basicConfig(level=logging.INFO)
app = FastAPI(
    title="IntelliDoc AI Service",
    description="An AI service for generating code documentation using Gemini.",
    version="3.0.0"
)

# ============================================================================================
# 🔑 API KEY ROTATION (Round-Robin across multiple Google Cloud projects)
# ============================================================================================
# Supports: GOOGLE_API_KEY, GOOGLE_API_KEY_2, GOOGLE_API_KEY_3, etc.
API_KEYS = []
primary_key = os.getenv("GOOGLE_API_KEY")
if primary_key:
    API_KEYS.append(primary_key)
for i in range(2, 10):  # Support up to 9 keys
    key = os.getenv(f"GOOGLE_API_KEY_{i}")
    if key:
        API_KEYS.append(key)

if not API_KEYS:
    logging.error("No API keys found! Set GOOGLE_API_KEY in .env")

_key_lock = threading.Lock()
_current_key_index = 0

# Using gemini-2.5-flash-lite — stable, fast, no 503 capacity issues on free tier
MODEL_NAME = "gemini-2.5-flash-lite"

def get_client():
    """Get a Gemini client configured with the next API key in rotation."""
    global _current_key_index
    if not API_KEYS:
        return None
    with _key_lock:
        key = API_KEYS[_current_key_index % len(API_KEYS)]
        _current_key_index += 1
    return genai.Client(api_key=key)

# Initialize first client for health checks
try:
    client = genai.Client(api_key=API_KEYS[0])
    logging.info(f"Gemini 2.5 Flash initialized with {len(API_KEYS)} API key(s).")
except Exception as e:
    logging.error(f"Error configuring Gemini: {e}")
    client = None

# ============================================================================================
# ⏱️ PROACTIVE RATE LIMITER (Token Bucket per API key)
# ============================================================================================
class RateLimiter:
    """Proactive rate limiter that pauses BEFORE hitting Gemini limits."""
    
    def __init__(self, rpm_limit=8, min_interval_seconds=7.0):
        """
        Args:
            rpm_limit: Max requests per minute (set below actual limit for safety margin)
            min_interval_seconds: Minimum seconds between requests
        """
        self._lock = threading.Lock()
        self._timestamps = []  # Rolling window of request timestamps
        self._rpm_limit = rpm_limit
        self._min_interval = min_interval_seconds
    
    def wait_if_needed(self):
        """Block until it's safe to make the next API call."""
        with self._lock:
            now = time.time()
            
            # Clean old timestamps (older than 60s)
            self._timestamps = [t for t in self._timestamps if now - t < 60]
            
            # Check minimum interval
            if self._timestamps:
                elapsed = now - self._timestamps[-1]
                if elapsed < self._min_interval:
                    wait_time = self._min_interval - elapsed
                    logging.info(f"Rate limiter: waiting {wait_time:.1f}s (min interval)")
                    time.sleep(wait_time)
                    now = time.time()
            
            # Check RPM limit
            if len(self._timestamps) >= self._rpm_limit:
                oldest_in_window = self._timestamps[0]
                wait_time = 60 - (now - oldest_in_window) + 1  # +1s safety
                if wait_time > 0:
                    logging.info(f"Rate limiter: waiting {wait_time:.1f}s (RPM limit)")
                    time.sleep(wait_time)
                    now = time.time()
                    self._timestamps = [t for t in self._timestamps if now - t < 60]
            
            self._timestamps.append(now)

# Tuned for 3 API keys × 5 RPM each = 15 RPM max. Using 12 RPM with 4s interval for safety.
rate_limiter = RateLimiter(rpm_limit=12, min_interval_seconds=4.0)

# ============================================================================================
# 🔄 SMART RETRY (503 vs 429 aware)
# ============================================================================================
def smart_retry(api_call_fn, max_retries=5, label="API"):
    """
    On 503 (server overload): short delay + rotate key.
    On 429 (rate limit): longer exponential backoff.
    """
    for attempt in range(max_retries):
        try:
            rate_limiter.wait_if_needed()
            current_client = get_client()
            return api_call_fn(current_client, MODEL_NAME)

        except Exception as e:
            error_str = str(e).lower()
            is_503 = "503" in error_str or "unavailable" in error_str or "overloaded" in error_str
            is_429 = "429" in error_str or "quota" in error_str or "rate" in error_str or "resource" in error_str

            if not (is_503 or is_429) or attempt >= max_retries - 1:
                logging.error(f"{label} failed (attempt {attempt + 1}/{max_retries}): {e}")
                raise

            if is_503:
                wait_time = 3 * (attempt + 1) + random.uniform(0.5, 1.5)
                logging.warning(f"{label}: 503 overload (attempt {attempt + 1}/{max_retries}). "
                               f"Rotating key, retrying in {wait_time:.1f}s...")
            else:
                wait_time = 8 * (2 ** attempt) + random.uniform(1.0, 3.0)
                logging.warning(f"{label}: 429 rate limited (attempt {attempt + 1}/{max_retries}). "
                               f"Retrying in {wait_time:.1f}s...")

            time.sleep(wait_time)

            time.sleep(wait_time)

# ============================================================================================
# 📦 REQUEST MODELS
# ============================================================================================
class APIRequest(BaseModel):
    prompt: str
    
class SelectionRequest(BaseModel):
    file_structure: str

class BatchDocRequest(BaseModel):
    """Request model for batch documentation generation."""
    files: list  # List of { "path": str, "content": str }
    project_context: str

# ============================================================================================
# 🏥 ENDPOINT: Health Check (fixes 404 on GET /)
# ============================================================================================
@app.get("/")
def health_check():
    """Health check endpoint for Hugging Face Spaces and monitoring."""
    return {
        "status": "ok",
        "service": "IntelliDoc AI",
        "model": MODEL_NAME,
        "api_keys_loaded": len(API_KEYS)
    }

# ============================================================================================
# 📝 ENDPOINT: Single File Documentation (kept for backward compatibility)
# ============================================================================================
@app.post("/generate-docs")
def generate_docs(request: APIRequest): 
    if not API_KEYS:
        return {"error": "AI model is not available."}

    logging.info("Received request to generate content from prompt.")
    
    try:
        def call_fn(client, model):
            response = client.models.generate_content(
                model=model,
                contents=request.prompt
            )
            return response
        
        response = smart_retry(call_fn, label="SingleDoc")
        generated_text = response.text
        # Cleanup: Remove markdown code block wrappers while preserving content
        if generated_text.startswith("```"):
            lines = generated_text.split('\n')
            if lines and lines[0].strip().startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            generated_text = "\n".join(lines)
        
        generated_text = generated_text.strip()
        logging.info("Content generated successfully by Gemini.")
        return {"documentation": generated_text}
        
    except Exception as e:
        logging.error(f"Error during Gemini API call: {e}")
        return {"error": f"Failed to generate documentation: {e}"}   

# ============================================================================================
# 📦 ENDPOINT: Batch File Documentation (NEW - reduces API calls by 60-70%)
# ============================================================================================
@app.post("/generate-docs-batch")
def generate_docs_batch(request: BatchDocRequest):
    """Generate documentation for multiple files in a single API call."""
    if not API_KEYS:
        return {"error": "AI model is not available."}

    if not request.files:
        return {"results": []}

    logging.info(f"Batch request: generating docs for {len(request.files)} files in one call.")

    # Build a combined prompt for all files in this batch
    combined_prompt = build_batch_prompt(request.files, request.project_context)

    try:
        def call_fn(client, model):
            return client.models.generate_content(
                model=model,
                contents=combined_prompt,
                config=types.GenerateContentConfig(temperature=0.3)
            )
        
        response = smart_retry(call_fn, label="BatchDoc")
        raw_text = response.text.strip()
        
        # Parse the batch response — each file's doc is separated by a delimiter
        results = parse_batch_response(raw_text, request.files)
        
        logging.info(f"Batch documentation generated for {len(results)} files.")
        return {"results": results}

    except Exception as e:
        logging.error(f"Batch generation failed: {e}")
        return {"error": f"Failed to generate batch documentation: {e}"}


def build_batch_prompt(files: list, project_context: str) -> str:
    """Build a single prompt that asks for documentation of multiple files."""
    
    files_section = ""
    for i, file_info in enumerate(files):
        files_section += f"""
---
**FILE {i+1}: `{file_info['path']}`**
```
{file_info['content']}
```
"""

    return f"""You are a **Senior Staff Engineer** writing internal technical documentation for your team.
Analyze the following source files with the depth and precision of a thorough code review.

**PROJECT CONTEXT (File Tree):**
```
{project_context}
```

{files_section}

---

### DOCUMENTATION REQUIREMENTS

For **EACH FILE**, produce a comprehensive Markdown document with these sections:

**### 🎯 Purpose**
* What does this file/class/module do? (2-3 sentences)
* Where does it fit in the overall architecture?
* What problem does it solve?

**### 🏗️ Architecture & Design**
* Design patterns used
* How it interacts with other components
* Class hierarchy or module structure

**### 📦 Key Components**
* Use a **Markdown table** with columns: Component | Type | Description
* List every important class, method, or function

**### ⚙️ Internal Logic & Flow**
* Main execution flow step-by-step (numbered list)
* Algorithms or complex logic
* Include relevant code snippets (2-4 lines max each)

**### 🔗 Dependencies & Integration**
* Internal and external dependencies
* Data flow and integration points

**### 🛡️ Error Handling & Edge Cases**
* Error handling approach, retry mechanisms, fallbacks

**### ⚡ Configuration & Constants**
* Configurable values, environment variables

---

### OUTPUT FORMAT (CRITICAL)
* For each file, start with a clear delimiter line: `===FILE: <filepath>===`
* Then write the full documentation for that file in Markdown
* After the documentation, add another delimiter: `===END_FILE===`
* Be specific: reference actual class names, method names from the code
* Aim for 300-600 words per file
* Do NOT wrap in markdown code blocks. Return raw markdown only.
* RETURN ONLY THE RAW MARKDOWN. No preamble.
"""


def parse_batch_response(raw_text: str, files: list) -> list:
    """Parse the batch response into individual file documentation."""
    results = []
    
    # Try delimiter-based parsing first
    if "===FILE:" in raw_text:
        parts = raw_text.split("===FILE:")
        for part in parts:
            part = part.strip()
            if not part:
                continue
            
            # Extract filepath from delimiter
            if "===" in part:
                header_end = part.index("===")
                filepath = part[:header_end].strip()
                content = part[header_end + 3:].strip()
                
                # Remove END_FILE delimiter if present
                if "===END_FILE===" in content:
                    content = content.replace("===END_FILE===", "").strip()
                
                results.append({"path": filepath, "documentation": content})
    
    # Fallback: if delimiters weren't used, try splitting by file headers
    if not results and len(files) == 1:
        # Single file — just return the whole response
        results.append({"path": files[0]["path"], "documentation": raw_text})
    elif not results:
        # Multiple files but no delimiters — split evenly as last resort
        logging.warning("Batch response missing delimiters, returning full text for first file.")
        results.append({"path": files[0]["path"], "documentation": raw_text})
    
    return results


# ============================================================================================
# 🧠 ENDPOINT: Architect Agent (File Selection)
# ============================================================================================
@app.post("/select-files")
def select_important_files(request: SelectionRequest):
    if not API_KEYS:
        return {"selected_files": []}
    
    prompt = f"""
    You are a Senior Software Architect specializing in code analysis and documentation planning. Your task is to identify the MOST IMPORTANT source files that capture the core business logic and architecture of this project.
    
    **YOUR GOAL:** Select files that, when documented, will give a developer a complete understanding of how this software works.
    
    **WHAT IS "IMPORTANT" CODE?**
    Files that contain:
    - Business rules and domain logic (the "brain" of the application)
    - Core algorithms and data processing pipelines
    - Application workflow, orchestration, and state management
    - Service layer implementations with actual business operations
    - Controllers/Handlers that orchestrate complex workflows
    - Middleware/interceptors with significant business logic (auth, validation, rate limiting)
    
    **SELECTION PRIORITY (in order):**
    1. **Entry Points & Application Core:**
       - Main application files (app.py, main.py, App.java, Program.cs, index.js, main.go)
       - Application initialization, bootstrap, and configuration classes
       - Core application wiring (dependency injection, module setup)
    
    2. **Business Logic Layer (HIGHEST PRIORITY):**
       - Service classes with business operations (UserService, OrderService, PaymentProcessor)
       - Workers, Processors, Handlers (e.g., KafkaListener workers, job processors)
       - Core algorithms, calculations, and data transformations
       - Business rule engines and validators
       - Domain models WITH methods (not plain DTOs)
    
    3. **Application Layer:**
       - Controllers/Handlers that orchestrate multi-step business logic
       - Use cases / Interactors / Application services
       - API routes with non-trivial request processing
       - WebSocket handlers, GraphQL resolvers
    
    4. **Infrastructure with Logic:**
       - Custom middleware with business rules (auth, rate limiting, caching)
       - Database repository classes with custom queries
       - External API integration clients
       - Event publishers/subscribers with processing logic
    
    **STRICT EXCLUSIONS (DO NOT SELECT):**
    - ❌ Test files: *test*, *spec*, *Test*, *Spec*, __tests__, tests/, spec/
    - ❌ Configuration files: *.json, *.yaml, *.yml, *.xml, *.properties, *.env, *.config, *.ini, *.toml
    - ❌ Build/Deploy scripts: Dockerfile, Makefile, *.sh, *.bat, *.ps1, pom.xml, package.json, requirements.txt
    - ❌ Static assets: *.css, *.scss, *.html, *.jpg, *.png, *.svg, *.ico, *.woff, *.ttf
    - ❌ Documentation: README*, LICENSE*, CHANGELOG*, *.md, docs/
    - ❌ Generated code: *generated*, *Generated, target/, build/, dist/, node_modules/, .venv/, __pycache__/
    - ❌ DTOs/VOs without logic: Pure data transfer objects, simple getters/setters only (POJO, dataclass with no methods)
    - ❌ Simple utilities: String formatters, basic validators, constants-only files
    - ❌ Framework boilerplate: Auto-generated routes, scaffolded controllers without custom logic
    - ❌ UI Components: React/Vue/Angular components (unless they contain significant state management logic)
    **REASONING:** We detect the tech stack from the file tree structure separately. Selecting config/static files wastes AI analysis budget.
    
    **ANALYSIS STRATEGY:**
    1. Identify the project structure pattern (MVC, Layered, Clean Architecture, Microservices, Monorepo)
    2. For MONOREPOS: Treat each service/package as a sub-project and select core files from EACH
    3. Find the entry point(s) of the application
    4. Trace from entry points to identify core business logic flow
    5. Select files that contain actual business rules, not just data passing
    6. Prefer files with multiple methods/functions over single-purpose utilities
    7. If multiple similar files exist (e.g., many controllers), choose the ones with the most complex logic
    
    **OUTPUT REQUIREMENTS:**
    - Return ONLY a raw JSON array of file paths
    - Maximum 8 files (prioritize quality and coverage over quantity)
    - Include full relative paths from project root
    - Do NOT use Markdown code blocks
    - Do NOT include explanations or comments
    
    **FILE STRUCTURE TO ANALYZE:**
    {request.file_structure}
    
    **EXPECTED JSON FORMAT:**
    ["src/main/java/com/app/service/CoreService.java", "src/main/java/com/app/worker/ProcessingWorker.java", "src/controllers/OrderController.java"]
    """

    try:
        def call_fn(client, model):
            return client.models.generate_content(
                model=model,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    temperature=0.2
                )
            )
        
        response = smart_retry(call_fn, label="Architect")
        text_response = response.text.strip()
        if text_response.startswith("```json"):
            text_response = text_response.replace("```json", "").replace("```", "")
            
        file_list = json.loads(text_response)
        
        if isinstance(file_list, dict):
            file_list = list(file_list.values())[0]
            
        logging.info(f"Architect selected {len(file_list)} files.")
        return {"selected_files": file_list}
        
    except Exception as e:
        logging.error(f"Architect Agent Failed: {e}")
        return {"selected_files": []}