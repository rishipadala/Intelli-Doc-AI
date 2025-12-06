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
    You are a Senior Software Architect.
    Analyze this file structure and select the **TOP 10 MOST CRITICAL SOURCE CODE FILES** for documentation.
    
    **SELECTION RULES:**
    1. **PRIORITIZE:** Entry points (app.js, Main.java), Controllers, Services, Core Algorithms, and Models.
    2. **IGNORE:** - Build scripts (Dockerfile, Makefile, .sh, .bat).
       - Config files (.json, .xml, .yaml, .env).
       - Tests (test_*, *Spec.js).
       - Assets (images, css, html).
       - Documentation (LICENSE, README).
    3. **OUTPUT:** Return ONLY a raw JSON list of file paths. Do not use Markdown blocks.
    
    **FILE STRUCTURE:**
    {request.file_structure}
    
    **EXPECTED JSON FORMAT:**
    ["src/main/java/com/app/App.java", "src/services/UserService.java"]
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