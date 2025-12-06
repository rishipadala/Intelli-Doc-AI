import streamlit as st
import requests
import time
import re

# --- CONFIGURATION ---
st.set_page_config(page_title="IntelliDoc AI", page_icon="🤖", layout="wide")
API_BASE_URL = "http://localhost:8080/api"

# --- SESSION STATE INITIALIZATION ---
if 'token' not in st.session_state:
    st.session_state['token'] = None
if 'user_email' not in st.session_state:
    st.session_state['user_email'] = None
if 'my_repos' not in st.session_state:
    st.session_state['my_repos'] = []
if 'current_repo_id' not in st.session_state:
    st.session_state['current_repo_id'] = None

# --- AUTHENTICATION FUNCTIONS ---
def login(email, password):
    try:
        with st.spinner("Authenticating..."):
            response = requests.post(f"{API_BASE_URL}/auth/login", json={
                "email": email,
                "password": password
            })
            
            if response.status_code == 200:
                data = response.json()
                st.session_state['token'] = data['token']
                st.session_state['user_email'] = data.get('email', email) 
                st.success("✅ Login successful!")
                time.sleep(0.5)
                st.rerun()
            else:
                st.error(f"❌ Login failed: {response.text}")
    except Exception as e:
        st.error(f"Connection error: {e}")

def signup(username, email, password):
    try:
        with st.spinner("Creating account..."):
            response = requests.post(f"{API_BASE_URL}/auth/signup", json={
                "username": username,
                "email": email,
                "password": password,
                "roles": ["user"]
            })
            
            if response.status_code == 200:
                st.success("✅ Account created! Please Login.")
            else:
                st.error(f"❌ Signup failed: {response.text}")
    except Exception as e:
        st.error(f"Connection error: {e}")

def logout():
    st.session_state['token'] = None
    st.session_state['user_email'] = None
    st.session_state['my_repos'] = []
    st.session_state['current_repo_id'] = None
    st.rerun()

# --- HELPER FUNCTIONS ---

def clean_markdown(text):
    """
    Fixes the 'Bad Text' issue.
    Removes ```markdown wrapping and ensures clean rendering.
    """
    if not text: return ""
    # Remove starting ```markdown or ```
    text = re.sub(r"^```[a-zA-Z]*\n", "", text)
    # Remove ending ```
    text = re.sub(r"\n```$", "", text)
    return text.strip()

def poll_status(repository_id, headers, status_container):
    """
    Polls the backend and updates the progress bar LIVE.
    Returns the FINAL status string.
    """
    progress_bar = status_container.progress(0, text="Waiting for worker...")
    
    while True:
        time.sleep(2)
        try:
            res = requests.get(f"{API_BASE_URL}/repositories/{repository_id}/status", headers=headers)
            if res.status_code == 200:
                status = res.json().get("status")
                
                if status == "QUEUED":
                    progress_bar.progress(10, text="⏳ Queued...")
                elif status == "ANALYZING_CODE":
                    progress_bar.progress(40, text="⚙️ AI is reading code files...")
                elif status == "ANALYSIS_COMPLETED":
                    progress_bar.progress(60, text="✅ Code Analysis Done! Ready for Readme.")
                    return "ANALYSIS_COMPLETED"
                elif status == "GENERATING_README":
                    progress_bar.progress(80, text="📝 Writing Master README...")
                elif status == "COMPLETED":
                    progress_bar.progress(100, text="✅ All Done!")
                    return "COMPLETED"
                elif status == "FAILED":
                    status_container.error("❌ Job Failed.")
                    return "FAILED"
        except:
            break
    return "UNKNOWN"

def trigger_readme_generation(repo_id, headers):
    try:
        res = requests.post(f"{API_BASE_URL}/repositories/{repo_id}/generate-readme", headers=headers)
        if res.status_code == 202:
            st.toast("✅ Readme Generation Started!", icon="📝")
            return True
        else:
            st.error(f"Failed: {res.text}")
            return False
    except Exception as e:
        st.error(str(e))
        return False

def fetch_repos(headers):
    try:
        res = requests.get(f"{API_BASE_URL}/repositories/my-repos", headers=headers)
        if res.status_code == 200:
            st.session_state['my_repos'] = res.json()
    except: pass

def view_docs(repo_id, headers):
    try:
        res = requests.get(f"{API_BASE_URL}/repositories/{repo_id}/documentation", headers=headers)
        if res.status_code == 200:
            docs = res.json()
            if not docs:
                st.info("No documentation found. Try running analysis first.")
                return

            # Sort: README first, then alphabetical
            docs.sort(key=lambda x: (0 if "README_GENERATED" in x['filePath'] else 1, x['filePath']))
            
            st.markdown("### 📄 Documentation Viewer")
            
            for doc in docs:
                display_path = doc['filePath'].replace("\\", "/").split("/")[-1]
                # CLEAN THE TEXT BEFORE RENDERING
                clean_content = clean_markdown(doc['content'])
                
                # Highlight the Master Readme
                if "README_GENERATED.md" in doc['filePath']:
                    st.success("🌟 PROJECT MASTER README")
                    with st.expander(f"📖 {display_path}", expanded=True):
                        st.markdown(clean_content)
                else:
                    with st.expander(f"📄 {display_path}"):
                        st.markdown(clean_content)
        else:
            st.error("Could not load docs.")
    except Exception as e:
        st.error(str(e))

# --- MAIN APP ---
def main_app():
    # Sidebar
    with st.sidebar:
        st.title("🤖 Intelli-Doc AI")
        st.write(f"User: **{st.session_state['user_email']}**")
        st.markdown("---")
        if st.button("🚪 Logout", use_container_width=True):
            logout()

    auth_headers = {"Authorization": f"Bearer {st.session_state['token']}"}

    # === HEADER RESTORED ===
    col1, col2 = st.columns([0.1, 0.9])
    with col1:
        st.write("# 🤖")
    with col2:
        st.title("Intelli-Doc AI")
        st.caption("An AI Powered Code Documentation Platform")
    
    st.divider()

    tab1, tab2 = st.tabs(["🚀 New Analysis", "📂 My Repositories"])

    # === TAB 1: SUBMIT NEW REPO ===
    with tab1:
        st.subheader("Analyze a new GitHub Repository")
        
        col_input, col_btn = st.columns([4, 1])
        with col_input:
            repo_url = st.text_input("Git Repository URL", placeholder="[https://github.com/username/repo.git](https://github.com/username/repo.git)", label_visibility="collapsed")
        
        with col_btn:
            submit_btn = st.button("Start Analysis", type="primary", use_container_width=True)

        if submit_btn:
            if not repo_url:
                st.warning("Please enter a URL.")
                return

            status_placeholder = st.empty()
            
            try:
                with status_placeholder.container():
                    st.info("Submitting job...")
                    payload = {"url": repo_url}
                    
                    response = requests.post(
                        f"{API_BASE_URL}/repositories", 
                        json=payload, 
                        headers=auth_headers
                    )

                    if response.status_code == 202:
                        data = response.json()
                        repository_id = data.get("id")
                        st.session_state['current_repo_id'] = repository_id # Save for flow
                        st.success(f"✅ Job Queued! ID: `{repository_id}`")
                    else:
                        st.error(f"❌ Submission Failed: {response.text}")
                        return

            except Exception as e:
                st.error(f"Error submitting job: {e}")
                return

        # === THE NEW CONTINUOUS FLOW ===
        # If we have a current repo ID in session, show its status in this tab
        if st.session_state['current_repo_id']:
            repo_id = st.session_state['current_repo_id']
            
            st.write("---")
            st.write("#### ⏳ Analysis Progress")
            status_container = st.empty()
            
            # Poll and get result
            final_status = poll_status(repo_id, auth_headers, status_container)
            
            # 1. IF CODE ANALYSIS IS DONE -> SHOW "GEN README" BUTTON
            if final_status == "ANALYSIS_COMPLETED":
                st.success("✅ Code Analysis Complete! Files have been indexed.")
                
                st.info("👇 Now, generate the Master README for the whole project.")
                if st.button("📝 Generate Master README", type="primary", use_container_width=True):
                    if trigger_readme_generation(repo_id, auth_headers):
                        st.rerun() # Rerun to start polling for the readme status

            # 2. IF README IS DONE -> SHOW SUCCESS
            elif final_status == "COMPLETED":
                st.balloons()
                st.success("🎉 Full Project Documentation Generated Successfully!")
                if st.button("📂 Open Documentation Viewer"):
                    st.session_state[f"active_doc_{repo_id}"] = True
                    view_docs(repo_id, auth_headers)

    # === TAB 2: MY REPOS ===
    with tab2:
        col_header, col_refresh = st.columns([4, 1])
        with col_header:
            st.subheader("Your Repository History")
        with col_refresh:
            if st.button("🔄 Refresh", use_container_width=True):
                fetch_repos(auth_headers)
        
        if not st.session_state['my_repos']:
            fetch_repos(auth_headers)
            
        if st.session_state['my_repos']:
            for repo in st.session_state['my_repos']:
                status = repo.get('status', 'UNKNOWN')
                status_icon = "🟢" if status == "COMPLETED" else "🟠" if "ANALYSIS" in status else "🔵"
                
                with st.expander(f"{status_icon} {repo['name']}  —  [{status}]"):
                    st.write(f"**URL:** `{repo['url']}`")
                    st.write(f"**Last Update:** {repo.get('lastAnalyzedAt', 'N/A')}")
                    
                    act_col1, act_col2 = st.columns(2)
                    
                    with act_col1:
                        if st.button("📂 View Docs", key=f"view_{repo['id']}", use_container_width=True):
                            st.session_state[f"active_doc_{repo['id']}"] = True

                    with act_col2:
                        btn_label = "📝 Gen Readme"
                        if status == "QUEUED" or status == "PROCESSING": 
                            btn_label = "⏳ Processing..."
                        
                        # Disable if queued, but ENABLE if 'ANALYSIS_COMPLETED' or 'COMPLETED' (to re-gen)
                        is_disabled = (status == "QUEUED" or status == "PROCESSING" or status == "GENERATING_README")
                        
                        if st.button(btn_label, key=f"readme_{repo['id']}", use_container_width=True, disabled=is_disabled):
                            trigger_readme_generation(repo['id'], auth_headers)
                            
                    if st.session_state.get(f"active_doc_{repo['id']}"):
                        st.divider()
                        view_docs(repo['id'], auth_headers)
                        if st.button("Close Docs", key=f"close_{repo['id']}"):
                            st.session_state[f"active_doc_{repo['id']}"] = False
                            st.rerun()
        else:
            st.info("No repositories found.")

# --- ENTRY POINT ---
if not st.session_state['token']:
    st.title("🔒 IntelliDoc AI - Login")
    tab_login, tab_signup = st.tabs(["Login", "Sign Up"])
    with tab_login:
        email = st.text_input("Email", key="login_email")
        password = st.text_input("Password", type="password", key="login_pass")
        if st.button("Login", use_container_width=True):
            login(email, password)
    with tab_signup:
        new_user = st.text_input("Username", key="signup_user")
        new_email = st.text_input("Email", key="signup_email")
        new_pass = st.text_input("Password", type="password", key="signup_pass")
        if st.button("Sign Up", use_container_width=True):
            signup(new_user, new_email, new_pass)
else:
    main_app()