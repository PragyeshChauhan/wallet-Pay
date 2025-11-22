package com.ezpay.notificationservice.service;

import com.ezpay.notificationservice.domain.EmailTemplate;
import com.ezpay.notificationservice.dto.UserEvent;
import com.ezpay.notificationservice.repository.EmailTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);


    /**
     * method to send email with template , going forward we will store variables also in the db
     * @param request
     */
//    public void sendEmailWithTemplate(UserEvent request) {
//        log.info("Inside sendEmailWithTemplate()");
//        Map<String, String> variables = new HashMap<>();
//        variables.put("name", request.getName());
//        variables.put("email", request.getEmail());
//        variables.put("userId", request.getUserId() != null ? request.getUserId().toString() : "");
//        variables.put("eventType", request.getEventType());
//        variables.put("date", LocalDate.now().toString());
//
////        EmailTemplate template = emailTemplateRepository.findByTemplateName("registration")
////                .orElseThrow(() -> new RuntimeException("Email template not found: registration"));
//
//        String subject = replacePlaceholders(template.getSubject(), variables);
//        String body = replacePlaceholders(template.getBody(), variables);
//        sendEmail(request.getEmail(), subject, body);
//
//    }

    public void sendEmailWithTemplate(UserEvent request) {
        log.info("Inside sendEmailWithTemplate()");
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", request.getName());
        variables.put("email", request.getEmail());
        variables.put("userId", request.getUserId() != null ? request.getUserId().toString() : "");
        variables.put("eventType", request.getEventType());
        variables.put("date", LocalDate.now().toString());
        variables.put("redirectUrl", request.getRedirectUrl());
        // Render HTML from Thymeleaf template in resources/templates/registration.html
        Context context = new Context();
        context.setVariables(variables);
        String body = templateEngine.process(request.getEventType(), context); // "registration" means registration.html
        String subject = "Welcome to EZPay"; // hardcoded or from properties for now
        sendEmail(request.getEmail(), subject, body);
    }


    /**
     * generic method to send emails
     * @param to
     * @param subject
     * @param body
     */
    public void sendEmail(String to, String subject, String body) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // true = HTML

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Email sending failed", e);
        }
    }


//    public void sendEmailWithTemplate(UserEvent event) {
//        EmailTemplate template = templateRepo.findByTemplateName("user.registered")
//                .orElseThrow(() -> new RuntimeException("Template not found"));
//
//        Context context = new Context();
//        context.setVariable("name", event.getName());
//        context.setVariable("email", event.getEmail());
//        context.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")));
//
//        String body = thymeleaf.process(template.getBodyHtml(), context);
//
//        MimeMessage message = mailSender.createMimeMessage();
//        MimeMessageHelper helper = new MimeMessageHelper(message, true);
//        helper.setTo(event.getEmail());
//        helper.setSubject(template.getSubject());
//        helper.setText(body, true);
//
//        mailSender.send(message);
//    }

    private String replacePlaceholders(String text, Map<String, String> variables) {
        log.info("Inside replacePlaceholders()");
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return text;
    }

    /**
     * method to send email notification
     * @param emailRequest
     */
    public void sendEmailNotification(Map<String, String> emailRequest) {
        log.info("Inside sendEmailNotification()");
        try{
            String to = emailRequest.get("to");
            String subject = emailRequest.get("subject");
            String body  = emailRequest.get("body");
            if(to==null || subject==null || body==null){
                throw new IllegalArgumentException("Some fields are missing");
            }
            sendEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Error while calling sendEmailNotification()");
            e.printStackTrace();
        }
    }
}
