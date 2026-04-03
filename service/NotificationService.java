package com.chuka.irir.service;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final boolean failOnError;

    public NotificationService(JavaMailSender mailSender,
            @Value("${app.notification.fail-on-error:false}") boolean failOnError) {
        this.mailSender = mailSender;
        this.failOnError = failOnError;
    }

    public void sendApprovalEmail(User student, Project project) {
        sendHtmlMessage(
                resolveRecipient(student),
                "Project Approved",
                buildEmailBody(student, project, "Your project has been approved for the next stage.", null));
    }

    public void sendRejectionEmail(User student, Project project, String reason) {
        sendHtmlMessage(
                resolveRecipient(student),
                "Project Rejection Notice",
                buildEmailBody(student, project, "Your supervisor requested changes before approval.", reason));
    }

    public void sendIncubationEmail(User student, Project project) {
        sendHtmlMessage(
                resolveRecipient(student),
                "Project Forwarded to Incubation",
                buildEmailBody(student, project, "Your project has been recommended for incubation review.", null));
    }

    private void sendHtmlMessage(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            logger.error("Failed to send notification email to {} with subject '{}'", to, subject, e);
            if (failOnError) {
                throw new IllegalStateException("Failed to send notification email.", e);
            }
        }
    }

    private String buildEmailBody(User student, Project project, String summary, String reason) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:Arial,sans-serif;line-height:1.6;\">")
                .append("<h2>IRIR Notification</h2>")
                .append("<p>Hello ").append(escapeHtml(student == null ? "" : student.getFullName())).append(",</p>")
                .append("<p>").append(escapeHtml(summary)).append("</p>")
                .append("<p><strong>Project:</strong> ")
                .append(escapeHtml(project == null ? "" : project.getTitle()))
                .append("</p>");
        if (reason != null && !reason.isBlank()) {
            html.append("<p><strong>Feedback:</strong> ").append(escapeHtml(reason)).append("</p>");
        }
        html.append("<p>Please log in to IRIR for the latest status and next steps.</p>")
                .append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String value) {
        return value == null ? ""
                : value
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
    }

    private String resolveRecipient(User student) {
        if (student == null || student.getEmail() == null || student.getEmail().isBlank()) {
            throw new IllegalArgumentException("Student email is required for notifications.");
        }
        return student.getEmail().trim();
    }
}
