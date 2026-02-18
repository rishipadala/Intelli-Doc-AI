import os
import logging
import json
import google.generativeai as genai
from fastapi import FastAPI
from pydantic import BaseModel
from dotenv import load_dotenv

load_dotenv()
logging.basicConfig(level=logging.INFO)
app = FastAPI(
    title="IntelliDoc AI Service",
    description="An AI service for generating code documentation using Gemini.",
    version="2.0.0"
)

try:
    api_key = os.getenv("GOOGLE_API_KEY")
    if not api_key:
        raise ValueError("GOOGLE_API_KEY environment variable not set.")
    genai.configure(api_key=api_key)
    model = genai.GenerativeModel('gemini-flash-latest')
    logging.info("Gemini Flash (latest) model initialized successfully.")
except Exception as e:
    logging.error(f"Error configuring Gemini: {e}")
    model = None

class APIRequest(BaseModel):
    prompt: str
    
class SelectionRequest(BaseModel):
    file_structure: str

@app.post("/generate-docs")
def generate_docs(request: APIRequest): 
    if model is None:
        return {"error": "AI model is not available."}

    logging.info("Received request to generate content from prompt.")
    
    try:
        response = model.generate_content(request.prompt)
        generated_text = response.text
        # 🧹 CLEANUP LOGIC - Preserve badges and HTML formatting
        if generated_text.startswith("```"):
            # Remove markdown code block wrappers while preserving content
            lines = generated_text.split('\n')
            # Remove opening code block (```markdown, ```md, or just ```)
            if lines and lines[0].strip().startswith("```"):
                lines = lines[1:]
            # Remove closing code block (```)
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            generated_text = "\n".join(lines)
        
        # Ensure we preserve HTML tags for badges (don't strip them)
        # Just clean up any extra whitespace at start/end
        generated_text = generated_text.strip()
        
        logging.info("Content generated successfully by Gemini.")
        
        return {"documentation": generated_text.strip()}
    except Exception as e:
        logging.error(f"Error during Gemini API call: {e}")
        return {"error": "Failed to generate documentation from Gemini API."}   
    
# 🧠 THE ARCHITECT AGENT
@app.post("/select-files")
def select_important_files(request: SelectionRequest):
    if not model: return {"selected_files": []}
    
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
    - Maximum 15 files (prioritize quality and coverage over quantity)
    - Include full relative paths from project root
    - Do NOT use Markdown code blocks
    - Do NOT include explanations or comments
    
    **FILE STRUCTURE TO ANALYZE:**
    {request.file_structure}
    
    **EXPECTED JSON FORMAT:**
    ["src/main/java/com/app/service/CoreService.java", "src/main/java/com/app/worker/ProcessingWorker.java", "src/controllers/OrderController.java"]
    """

    try:
        # Force JSON response logic
        response = model.generate_content(
            prompt,
            generation_config={"response_mime_type": "application/json"}
        )
        
        # Parse result
        text_response = response.text.strip()
        # Clean potential markdown wrappers if the model ignores instruction
        if text_response.startswith("```json"):
            text_response = text_response.replace("```json", "").replace("```", "")
            
        file_list = json.loads(text_response)
        
        # Ensure it's a flat list
        if isinstance(file_list, dict):
            # Sometimes AI returns {"files": [...]}
            file_list = list(file_list.values())[0]
            
        logging.info(f"Architect selected {len(file_list)} files.")
        return {"selected_files": file_list}
        
    except Exception as e:
        logging.error(f"Architect Agent Failed: {e}")
        return {"selected_files": []} # Java will handle empty list