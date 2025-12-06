import os
import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv() # Make sure your GOOGLE_API_KEY is in a .env file

try:
    api_key = os.getenv("GOOGLE_API_KEY")
    if not api_key:
        raise ValueError("GOOGLE_API_KEY is not set!")

    genai.configure(api_key=api_key)
    model = genai.GenerativeModel('gemini-2.0-flash')

    print("Model configured successfully. Sending test prompt...")
    response = model.generate_content("Tell me a short, fun fact about software development.")

    print("\nSUCCESS! ✅")
    print(response.text)

except Exception as e:
    print(f"\nFAILED! ❌\nError: {e}")