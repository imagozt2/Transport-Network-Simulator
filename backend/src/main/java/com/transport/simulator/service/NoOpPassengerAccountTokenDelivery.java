package com.transport.simulator.service;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.enums.PassengerAccountTokenType;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(
        name = "app.rmm-app.mail.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpPassengerAccountTokenDelivery implements PassengerAccountTokenDelivery {

    @Override
    public void deliver(PassengerAccount account, PassengerAccountTokenType type, String rawToken) {
        // Delivery is an infrastructure boundary. Tokens are deliberately not logged.
    }
}
