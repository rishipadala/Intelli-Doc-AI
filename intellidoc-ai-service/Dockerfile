# ============================================================
# IntelliDoc AI - FastAPI AI Service Dockerfile
# For deployment on Hugging Face Spaces (port 7860)
# ============================================================

FROM python:3.11-slim

WORKDIR /app

# Install dependencies first (cached layer)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app.py .

EXPOSE 7860

# Start FastAPI with uvicorn on port 7860 (HF Spaces default)
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "7860"]
