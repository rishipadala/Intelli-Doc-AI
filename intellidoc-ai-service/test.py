import os
from google import genai
from dotenv import load_dotenv

load_dotenv()  # Make sure your GOOGLE_API_KEY is in a .env file

try:
    api_key = os.getenv("GOOGLE_API_KEY")
    if not api_key:
        raise ValueError("GOOGLE_API_KEY is not set!")

    client = genai.Client(api_key=api_key)

    print("Client configured successfully. Sending test prompt...")
    response = client.models.generate_content(
        model='gemini-2.5-flash',
        contents="Tell me a short, fun fact about software development."
    )

    print("\nSUCCESS! ✅")
    print(response.text)

except Exception as e:
    print(f"\nFAILED! ❌\nError: {e}")