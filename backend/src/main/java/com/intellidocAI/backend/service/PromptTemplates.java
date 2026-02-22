package com.intellidocAI.backend.service;

/**
 * Centralized location for AI prompt templates used by the Java backend.
 * Keeps large string literals out of service classes for better readability.
 *
 * <p>
 * <b>Architecture Note:</b> Only the README generation prompt lives here
 * because the
 * Java backend constructs and sends it via
 * {@link AiServiceClient#callPythonService}.
 * The per-file documentation and file-selection prompts are defined directly in
 * the Python AI service ({@code intellidoc-ai-service/app.py}), since that
 * service
 * owns and executes those prompts internally via Gemini.
 * </p>
 */
public final class PromptTemplates {

    private PromptTemplates() {
        // Utility class — no instantiation
    }

    // ============================================================================================
    // 📖 README GENERATION PROMPT
    // ============================================================================================

    /**
     * Builds the master README generation prompt.
     */
    public static String buildReadmePrompt(String projectStructure, String summariesContext) {
        return String.format(
                """
                        You are an **Elite Senior Technical Writer** and **Open Source Documentation Architect**.
                        Your task is to transform the following code analysis into a **stunning, professional, production-grade `README.md`** that makes developers excited to explore and contribute to this project.

                        ---

                        ### 1. INPUT DATA
                        **Project Structure:**
                        %s

                        **Code Intelligence (AI-Analyzed File Summaries):**
                        %s

                        ---

                        ### 2. DEEP ANALYSIS INSTRUCTIONS (Internal — Do NOT output this)
                        * **Detect Stack:** Analyze file extensions, directory structure, and config files to identify ALL technologies:
                            * `pom.xml` / `build.gradle` → Java/Spring Boot
                            * `requirements.txt` / `pyproject.toml` → Python
                            * `package.json` → Node.js/React/Vue/Angular
                            * `Dockerfile` / `docker-compose.yml` → Docker
                            * `application.properties/.yml` → Spring Boot config
                            * `.env` files → Environment configuration present
                        * **Infer Build & Run Commands:**
                            * Java/Maven: `./mvnw clean install` + `mvn spring-boot:run`
                            * Java/Gradle: `./gradlew build` + `./gradlew bootRun`
                            * Python/pip: `pip install -r requirements.txt` + `python app.py` or `uvicorn app:app`
                            * Node.js: `npm install` + `npm run dev` or `npm start`
                        * **Infer Architecture Pattern:** Monolith, Microservices, Modular Monolith, Event-Driven, Layered?
                        * **Detect Infrastructure:** Kafka, Redis, MongoDB, PostgreSQL, RabbitMQ, Docker, etc.
                        * **Identify API Style:** REST, GraphQL, gRPC, WebSocket?
                        * **Extract Environment Variables:** From `.env`, `application.properties`, or `docker-compose.yml` references.

                        ### 3. OUTPUT STRUCTURE (Follow this EXACTLY)

                        **HEADER BLOCK**
                        * **Title**: Use a creative H1 like: `# 🚀 ProjectName — One-Line Tagline`
                        * **Badges Row**: Generate 5-7 premium `shields.io` badges using a **two-tone design** with `style=for-the-badge`.
                            * **FORMAT**: `![Label](https://img.shields.io/badge/Label-Value-color?style=for-the-badge&logo=logoname&logoColor=white&labelColor=1a1a2e)`
                            * **Design rules**:
                                * `labelColor=1a1a2e` (dark charcoal background for the label side — gives premium feel)
                                * `color` = brand color for the value side (e.g., Spring=6DB33F, React=61DAFB, Python=3776AB, Java=ED8B00, Docker=2496ED, MongoDB=47A248, Redis=DC382D, Kafka=231F20, TypeScript=3178C6)
                                * `logoColor=white` (white logo icon — pops against dark label background)
                            * Include badges for: Primary Language, Framework(s), Database(s), Message Broker (if any), DevOps tools, License
                            * Use accurate logo names from shields.io (e.g., `logo=springboot`, `logo=react`, `logo=python`, `logo=docker`, `logo=mongodb`, `logo=redis`, `logo=apachekafka`)
                            * **Example**: `![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white&labelColor=1a1a2e)`
                            * ❌ DO NOT use HTML `<img>` tags. Use ONLY Markdown image syntax.
                        * **Elevator Pitch**: 2-3 sentences explaining what this project does, who it's for, and why it matters.

                        **TABLE OF CONTENTS**
                        * Use emoji-prefixed links: `- [📍 Overview](#-overview)` etc.
                        * Include all major sections.

                        **## 📍 Overview**
                        * Explain the project's purpose in depth (3-5 sentences).
                        * Describe the high-level architecture (e.g., "Built on a microservices architecture with Spring Boot handling API orchestration, Kafka for async messaging, and a Python AI service for document generation.").
                        * Mention the problem it solves and the target audience.

                        **## 🏗️ Architecture**
                        * If the project has multiple services/modules, include an ASCII or text-based architecture diagram:
                        ```
                        ┌─────────────┐    ┌──────────┐    ┌────────────┐
                        │   Frontend  │───▶│  Backend │───▶│ AI Service │
                        │  (React)    │    │ (Spring) │    │ (FastAPI)  │
                        └─────────────┘    └──────────┘    └────────────┘
                        ```
                        * Explain the data flow between components.

                        **## 👾 Tech Stack**
                        * Use a **Markdown table** with columns: Category | Technology | Purpose
                        * Categories: Core, Database, Infrastructure, DevTools

                        **## ✨ Key Features**
                        * Extract 4-6 compelling features from the code intelligence.
                        * Use format: `- **🔥 Feature Name** — Brief description of what it does and why it matters.`
                        * Focus on unique/impressive capabilities, not generic ones.

                        **## 🚀 Getting Started**

                        * **Prerequisites**: List with version numbers (e.g., "Java 17+", "Node.js 18+", "Docker")
                        * **Installation**:
                            1. Clone: `git clone <repo-url>`
                            2. Navigate: `cd project-name`
                            3. Install dependencies (use detected commands)
                            4. Configure environment (mention `.env` or config files if detected)
                        * **Running the Application**: Provide exact run commands for each service/component.
                        * **Verify It Works**: Suggest a quick smoke test (e.g., `curl http://localhost:8080/api/health`).

                        **## 🔗 API Endpoints** (ONLY if REST controllers/routes are detected)
                        * Use a table: Method | Endpoint | Description
                        * Extract from controller/route file summaries. Include Auth endpoints, CRUD endpoints, etc.
                        * If no API is detected, SKIP this section entirely.

                        **## 📂 Project Structure**
                        * Show the top-level directory tree in a code block using box-drawing characters:
                        ```
                        ├── src/main/java/      # Backend source code
                        ├── frontend/           # React frontend
                        ├── ai-service/         # Python AI microservice
                        └── docker-compose.yml  # Container orchestration
                        ```
                        * Explain what each top-level folder contains.

                        **## ⚙️ Environment Variables** (ONLY if .env or config files detected)
                        * Table format: Variable | Description | Default
                        * Extract from `.env`, `application.properties`, or `docker-compose.yml` references.
                        * If no env vars detected, SKIP this section.

                        **## 🤝 Contributing**
                        * Brief contributing guidelines:
                            1. Fork the repository
                            2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
                            3. Commit your changes (`git commit -m 'Add AmazingFeature'`)
                            4. Push to the branch (`git push origin feature/AmazingFeature`)
                            5. Open a Pull Request

                        **## 📄 License**
                        * If LICENSE file detected, mention it. Otherwise use: "Distributed under the MIT License."

                        ---

                        ### 4. QUALITY RULES
                        * **Tone**: Professional yet approachable. Write as if onboarding a new team member.
                        * **Depth over breadth**: Better to explain 5 features well than list 20 superficially.
                        * **Code blocks**: Use fenced code blocks (```) for ALL commands, paths, and file names.
                        * **Accuracy**: Only mention technologies you can confirm from the file structure and summaries.
                        * **Badge rule**: ONLY use Markdown `![text](url)` syntax. NEVER use HTML `<img>` tags.
                        * **CRITICAL**: Do NOT wrap the entire output in a markdown code block. Return raw markdown text ONLY.
                        * **Output**: RETURN ONLY THE RAW MARKDOWN CONTENT. NO preamble, NO explanations, NO "Here is your README".
                        """,
                projectStructure, summariesContext);
    }
}
