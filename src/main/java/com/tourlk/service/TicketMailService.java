package com.tourlk.service;

import com.tourlk.entity.SupportTicket;
import com.tourlk.entity.TicketReply;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Best-effort email notifications for the support ticket module, built
 * on the mail config in application.yml (spring.mail.*) that no other
 * module uses yet. Silently does nothing when MAIL_USERNAME/
 * MAIL_PASSWORD aren't set (the local/default case), and never lets a
 * mail failure fail the ticket operation that triggered it — see
 * {@code SupportTicketServiceImpl}, which always calls this after its
 * own transaction-relevant work is done.
 */
@Slf4j
@Service
public class TicketMailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final String fromAddress;
    private final boolean mailConfigured;

    public TicketMailService(JavaMailSender mailSender,
                              UserRepository userRepository,
                              @Value("${spring.mail.username}") String mailUsername,
                              @Value("${spring.mail.password}") String mailPassword) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.fromAddress = mailUsername;
        this.mailConfigured = StringUtils.hasText(mailUsername) && StringUtils.hasText(mailPassword);
    }

    public void notifyAdminsOfNewTicket(SupportTicket ticket) {
        if (!mailConfigured) {
            return;
        }

        List<User> admins = userRepository.findByRole(Role.ADMIN);
        String subject = "New support ticket #" + ticket.getId() + ": " + ticket.getSubject();
        String body = "A new " + ticket.getPriority() + " priority " + ticket.getCategory()
                + " ticket was raised by " + ticket.getRaisedBy().getName() + ".\n\n" + ticket.getSubject();

        for (User admin : admins) {
            send(admin.getEmail(), subject, body);
        }
    }

    public void notifyRaiserOfReply(SupportTicket ticket, TicketReply reply) {
        if (!mailConfigured) {
            return;
        }

        String subject = "New reply on your support ticket #" + ticket.getId() + ": " + ticket.getSubject();
        String body = reply.getAuthor().getName() + " replied to your ticket:\n\n" + reply.getMessage();

        send(ticket.getRaisedBy().getEmail(), subject, body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Failed to send ticket notification email to {}", to, e);
        }
    }

}
