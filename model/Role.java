package com.chuka.irir.model;

/**
 * Enumeration of user roles in the IRIR system.
 *
 * Each role maps to a Spring Security granted authority with the "ROLE_" prefix.
 * Role-Based Access Control (RBAC) is enforced throughout the application using
 * these roles to restrict access to controllers, services, and views.
 *
 * <ul>
 *   <li><b>STUDENT</b>     — 4th-year CS student; uploads projects, views analytics</li>
 *   <li><b>SUPERVISOR</b>  — Lecturer; reviews, approves/rejects submissions</li>
 *   <li><b>DIRECTORATE</b> — Research & Extension office; analytics, incubation flagging</li>
 *   <li><b>ADMIN</b>       — System administrator; user management, logs, backups</li>
 * </ul>
 */
public enum Role {
    STUDENT,
    SUPERVISOR,
    DIRECTORATE,
    ADMIN
}
