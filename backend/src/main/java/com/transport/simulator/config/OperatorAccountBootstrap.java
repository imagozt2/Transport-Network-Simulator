package com.transport.simulator.config;

import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.repository.OperatorAccountRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OperatorAccountBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperatorAccountBootstrap.class);

    private final OperatorAccountRepository operatorAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;

    public OperatorAccountBootstrap(
            OperatorAccountRepository operatorAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.operator-bootstrap.username:}") String username,
            @Value("${app.operator-bootstrap.email:}") String email,
            @Value("${app.operator-bootstrap.password:}") String password,
            @Value("${app.operator-bootstrap.first-name:}") String firstName,
            @Value("${app.operator-bootstrap.last-name:}") String lastName
    ) {
        this.operatorAccountRepository = operatorAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (operatorAccountRepository.count() > 0) {
            return;
        }

        List<String> values = List.of(username, email, password, firstName, lastName);
        if (values.stream().allMatch(String::isBlank)) {
            LOGGER.warn(
                    "No operator account exists. Define OPERATOR_USERNAME, OPERATOR_EMAIL, "
                            + "OPERATOR_PASSWORD, OPERATOR_FIRST_NAME and OPERATOR_LAST_NAME "
                            + "to provision the initial administrator"
            );
            return;
        }
        if (values.stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException(
                    "All initial operator environment variables must be configured together"
            );
        }
        validateBootstrapValues();

        OperatorAccount account = new OperatorAccount(
                username,
                email,
                passwordEncoder.encode(password),
                firstName,
                lastName,
                OperatorRole.ADMINISTRATOR
        );
        operatorAccountRepository.save(account);
        LOGGER.info("Initial administrator account provisioned successfully");
    }

    private void validateBootstrapValues() {
        if (username.trim().length() < 3) {
            throw new IllegalStateException("OPERATOR_USERNAME must contain at least 3 characters");
        }
        if (!email.contains("@")) {
            throw new IllegalStateException("OPERATOR_EMAIL must contain a valid email address");
        }
        if (password.length() < 12) {
            throw new IllegalStateException(
                    "OPERATOR_PASSWORD must contain at least 12 characters"
            );
        }
    }
}
