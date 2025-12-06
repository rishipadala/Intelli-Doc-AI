import os
import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.getenv("GOOGLE_API_KEY"))

print("--- Available Models ---")
for m in genai.list_models():
  # Check if the 'generateContent' method is supported
  if 'generateContent' in m.supported_generation_methods:
    print(m.name)