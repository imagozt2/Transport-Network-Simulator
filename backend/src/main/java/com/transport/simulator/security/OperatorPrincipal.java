package com.transport.simulator.security;

import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.enums.OperatorRole;
import java.io.Serializable;

public record OperatorPrincipal(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        OperatorRole role
) implements Serializable {

    public static OperatorPrincipal from(OperatorAccount account) {
        return new OperatorPrincipal(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getRole()
        );
    }
}
