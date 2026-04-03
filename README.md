# UniSubmit | University Submission & Innovation Repository

![Status](https://img.shields.io/badge/status-active--dev-0b7285?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-0b7285?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17-0b7285?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/Spring_Boot-3.2.x-0b7285?style=for-the-badge&logo=springboot&logoColor=white)

**UniSubmit** is a specialized digital repository and submission system designed for university departments to manage the entire lifecycle of student research projects. It streamlines project uploads, supervisor feedback, and final archival, ensuring academic integrity and fostering innovation.

### 🚀 Vision & Purpose
Initially built for project progress tracking and lecturer remarks, UniSubmit is expanding to support **fourth-year final projects**. It serves as:
*   **A Research Hub:** Students can discover past projects to spark new ideas and avoid duplicating work.
*   **An Integrity Shield:** Automated similarity reports prevent plagiarism by comparing new submissions against the repository's history.
*   **A Feedback Portal:** Lecturers can monitor student progress and provide actionable remarks in real-time.

---

## 🚀 Vision & Purpose

In many academic environments, final year projects are often lost in physical archives or scattered digital folders. **UniSubmit** centralizes this knowledge, allowing:
*   **Students** to draw inspiration from past work and identify gaps in current research.
*   **Departments** to maintain high academic standards through automated integrity checks.
*   **Supervisors** to efficiently manage the review and approval lifecycle of student submissions.

Soon, **UniSubmit** will serve as the primary submission portal for **fourth-year final projects**, ensuring that every graduate's contribution is preserved and accessible to the next generation of innovators.

---

## ✨ Key Features

### 🛡️ Plagiarism Prevention & Integrity
*   **Automated Similarity Reports:** Each submission is automatically analyzed against the existing repository using document extraction (PDF/DOCX) to identify potential overlaps.
*   **Flagging System:** High similarity scores trigger automated flags for supervisor review, ensuring academic honesty.

### 🔍 Project Discovery & Innovation
*   **Keyword Intelligence:** Projects are indexed with searchable keywords, helping students find relevant past research to build upon.
*   **Public/Incubation Stages:** Projects that reach the "Incubation" stage are showcased for their innovation and potential for further development.

---

## 🚀 How to Run UniSubmit

Getting the application running is simple. Follow these steps for your preferred environment:

### Method 1: Local Development (H2 In-Memory)
This is the fastest way to preview the app without a full MySQL setup.
1.  **Clone the Repo:**
    ```bash
    git clone https://github.com/your-username/unisubmit.git
    cd unisubmit
    ```
2.  **Build and Run:**
    ```bash
    mvn spring-boot:run -Dspring-boot.run.profiles=h2
    ```
3.  **Access:**
    *   App: `http://localhost:8080`
    *   H2 Console: `http://localhost:8080/h2-console` (JDBC: `jdbc:h2:mem:irir_db`)
    *   **Default Admin:** `admin@chuka.ac.ke` / `Admin@2024`

### Method 2: Production/Full Setup (MySQL)
1.  **Database Initialisation:** Create a database named `irir_db`.
    ```sql
    CREATE DATABASE IF NOT EXISTS irir_db;
    ```
2.  **Env Config:** Set your environment variables (optional) or update `application.properties`:
    ```bash
    export IRIR_DB_PASSWORD='your-password'
    ```
3.  **Run:**
    ```bash
    mvn spring-boot:run
    ```

---

## 🛠️ Tech Stack

*   **Backend:** Java 17, Spring Boot 3.2.x
*   **Persistence:** Spring Data JPA + Hibernate
*   **Security:** Spring Security (RBAC & BCrypt)
*   **Frontend:** Thymeleaf + Bootstrap 5
*   **Analytics:** Apache Tika/PDFBox (Text extraction for similarity analysis)

---

## 🗺️ Future Roadmap

*   **Final Year Project Portal:** Dedicated workflows specifically tailored for fourth-year project defenses and final submissions.
*   **Collaboration Hub:** Tools for students to request collaboration on existing "Incubation" projects.
*   **Advanced AI Analysis:** Enhancing the similarity engine with semantic understanding beyond simple text matching.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Developed for Chuka University - Empowering Academic Innovation.*

