package com.tourlk.service;

import com.tourlk.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sends the password reset link. When {@code app.mail.enabled} is false
 * (the default — see {@code TicketMailService} for the same convention),
 * no SMTP server is required to run the app locally: the link is simply
 * logged so it can be copied out of the console during development.
 */
@Slf4j
@Service
public class PasswordResetMailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean mailConfigured;

    public PasswordResetMailService(JavaMailSender mailSender,
                                     @Value("${app.mail.enabled:false}") boolean mailEnabled,
                                     @Value("${spring.mail.username}") String mailUsername,
                                     @Value("${spring.mail.password}") String mailPassword) {
        this.mailSender = mailSender;
        this.fromAddress = mailUsername;
        this.mailConfigured = mailEnabled && StringUtils.hasText(mailUsername) && StringUtils.hasText(mailPassword);
    }

    public void sendResetLink(User user, String resetLink) {
        if (!mailConfigured) {
            log.info("[dev] Password reset link for {}: {}", user.getEmail(), resetLink);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("Reset your TourLK password");
            message.setText("Hi " + user.getName() + ",\n\n"
                    + "We received a request to reset your password. Click the link below to choose a new one. "
                    + "This link expires in 30 minutes.\n\n" + resetLink + "\n\n"
                    + "If you did not request this, you can safely ignore this email.");
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Failed to send password reset email to {}", user.getEmail(), e);
        }
    }

}
