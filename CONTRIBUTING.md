# IRIR Project — Team Collaboration & Contribution Guidelines

This guide outlines our Git workflow, module ownership, and strategies to ensure all 8 team members can contribute maximally while minimizing merge conflicts.

---

## 1. Team Module Ownership & Review Matrix

Based on our project plan, here is the breakdown of who is building what, and who is responsible for reviewing their code. **Stick to your assigned modules** to avoid stepping on each other's toes.

| Section | Module | Primary Owner(s) | Support / Review |
| :---: | :--- | :--- | :--- |
| **2.1** | Project Setup & Architecture | Team Lead (Osborn) | All Members |
| **2.2** | Database & JPA Entities | Brian / Nancy | Team Lead Review |
| **2.3** | Security & Authentication | Denis / Praise | Team Lead Review |
| **2.4** | File Upload & Apache Tika | John / Arnold | Brian Support |
| **2.5** | Similarity Detection (Lucene) | Osborn / Ronald | Denis Support |
| **2.6** | Supervisor Dashboard | Praise / Nancy | John Review |
| **2.7** | Collaborator Recommendation | Arnold / Ronald | Osborn Review |
| **2.8** | Analytics Dashboard | Brian / Denis | Nancy Review |
| **2.9** | Student Portal & Search | Nancy / John | Arnold Support |
| **2.10** | Admin Panel | Ronald / Praise | Osborn Review |
| **3.1** | Integration Testing | All Members | Team Lead Merge |
| **3.2** | Deployment & Checklist | Osborn / Denis | All Members |

---

## 2. Git Branching Strategy

We are using a **Feature Branch Workflow**. Direct commits to the `main` or `develop` branches are strictly prohibited.

*   `main`: Stable, production-ready code.
*   `develop`: The active integration branch. All features are merged here first.
*   **Feature Branches**: Where you do your work. Name them descriptively based on your module:
    *   Format: `feature/<developer-name>-<module-name>`
    *   Example: `feature/brian-jpa-entities` or `feature/denis-security-setup`

---

## 3. Step-by-Step Development Workflow

Whenever you start working on your assigned module, follow these exact steps:

1.  **Sync your local repository:**
    ```bash
    git checkout develop
    git pull origin develop
    ```
2.  **Create your feature branch:**
    ```bash
    git checkout -b feature/<your-name>-<module-name>
    ```
3.  **Write code and commit frequently:**
    Use descriptive commit messages so the team knows what changed.
    ```bash
    git add .
    git commit -m "feat(security): added BCrypt encoder and RBAC rules"
    ```
4.  **Push your branch to GitHub:**
    ```bash
    git push -u origin feature/<your-name>-<module-name>
    ```

---

## 4. Pull Request (PR) & Code Review Process

When you are done with a feature (or a significant chunk of it), you must open a Pull Request (PR) to merge your code into `develop`.

1.  **Open a PR** on GitHub from your `feature` branch to the `develop` branch.
2.  **Assign Reviewers** based on the Ownership Matrix above.
    *   *Example:* If Praise and Nancy finish the **Supervisor Dashboard**, they must assign **John** as the reviewer.
    *   *Example:* If Denis and Praise finish **Security**, they must assign **Osborn (Team Lead)** as the reviewer.
3.  **Address Feedback**: The reviewer may ask for changes. Make the changes locally, commit, and push. The PR will update automatically.
4.  **Merge**: Once approved, the reviewer or Team Lead will merge the PR into `develop`.

---

## 5. Strategies to Prevent Merge Conflicts

With 8 people coding simultaneously, merge conflicts are inevitable if we aren't careful. Follow these golden rules:

### 🚨 Stay In Your Lane
*   Only edit files related to your assigned module.
*   If you are building the *Admin Panel*, you should only be touching `AdminController`, `admin.html`, and related services. You should not be editing `SecurityConfig` unless you coordinate with Denis/Praise first.

### 🔄 Pull Daily
Before you start coding for the day, ALWAYS pull the latest changes from `develop` into your feature branch so you are working with the newest code:
```bash
git checkout feature/your-branch
git fetch origin
git merge origin/develop
```
*(Resolve any conflicts immediately locally before continuing to code).*

### ⚠️ Shared Files Protocol
Some files will need to be touched by multiple people (e.g., `pom.xml`, `application.properties`, or core models like `Project.java`).
*   **Communicate first!** Drop a message in your WhatsApp/Slack group: *"Hey team, I'm adding a new dependency to pom.xml for Apache Tika."*
*   **Entities (Database team):** Brian and Nancy own the JPA entities. If File Upload (John/Arnold) needs a new field in the database, ask Brian/Nancy to add it to the entity first, merge it, and then pull their changes.

### 🤝 Interface-Driven Development
If your module depends on someone else's unfinished module, agree on the method signatures (Interfaces) first.
*   *Example:* Ronald (Similarity Detection) needs to use John's (File Upload) extracted text. They should agree on a method like `String extractText(File file)` so Ronald can use a dummy method until John finishes the real implementation.

---

## 6. Emergency Conflict Resolution

If you get a merge conflict during a PR:
1.  **Do not panic.** Do not force push (`push -f`).
2.  Pull `develop` into your feature branch locally.
3.  Open the conflicting files in your IDE (IntelliJ/VS Code). The IDE will highlight the conflicts.
4.  Sit down with the person whose code conflicts with yours (via Discord/Zoom or in person) and manually combine the logic.
5.  Commit the resolved file and push it.
