package com.transport.simulator.service;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.enums.PassengerAccountTokenType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rmm-app.mail.enabled", havingValue = "true")
public class SmtpPassengerAccountTokenDelivery implements PassengerAccountTokenDelivery {

    private final JavaMailSender mailSender;
    private final String sender;
    private final String verificationUri;
    private final String passwordResetUri;

    public SmtpPassengerAccountTokenDelivery(
            JavaMailSender mailSender,
            @Value("${app.rmm-app.mail.sender}") String sender,
            @Value("${app.rmm-app.mail.verification-uri}") String verificationUri,
            @Value("${app.rmm-app.mail.password-reset-uri}") String passwordResetUri
    ) {
        this.mailSender = mailSender;
        this.sender = sender;
        this.verificationUri = verificationUri;
        this.passwordResetUri = passwordResetUri;
    }

    @Override
    public void deliver(PassengerAccount account, PassengerAccountTokenType type, String rawToken) {
        String targetUri = type == PassengerAccountTokenType.EMAIL_VERIFICATION
                ? verificationUri
                : passwordResetUri;
        String action = type == PassengerAccountTokenType.EMAIL_VERIFICATION
                ? "verificar tu cuenta"
                : "restablecer tu contraseña";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(account.getEmail());
        message.setSubject(type == PassengerAccountTokenType.EMAIL_VERIFICATION
                ? "Verifica tu cuenta de RMM App"
                : "Recuperación de tu cuenta de RMM App");
        message.setText("Hola " + account.getFirstName() + ",\n\nUtiliza este enlace para "
                + action + ":\n" + targetUri + URLEncoder.encode(rawToken, StandardCharsets.UTF_8)
                + "\n\nSi no has solicitado esta operación, ignora este mensaje.");
        mailSender.send(message);
    }
}
