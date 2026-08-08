package com.transport.simulator.service;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.enums.PassengerAccountTokenType;

public interface PassengerAccountTokenDelivery {

    void deliver(PassengerAccount account, PassengerAccountTokenType type, String rawToken);
}
