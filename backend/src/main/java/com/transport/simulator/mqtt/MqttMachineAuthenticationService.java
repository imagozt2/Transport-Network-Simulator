package com.transport.simulator.mqtt;

import com.transport.simulator.entity.DeviceMqttIdentity;
import com.transport.simulator.repository.DeviceMqttIdentityRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqttMachineAuthenticationService {
    private static final String TOPIC_PREFIX = "rmm/v1/devices/";
    private final DeviceMqttIdentityRepository repository;
    private final Clock clock;

    public MqttMachineAuthenticationService(DeviceMqttIdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public AuthenticatedMqttMachine authenticate(String topic, String claimedDeviceCode) {
        if (claimedDeviceCode == null) {
            throw rejected("MQTT payload does not declare a machine identity");
        }
        return authenticateResolved(topic, claimedDeviceCode.trim());
    }

    @Transactional
    public AuthenticatedMqttMachine authenticatePresence(String topic) {
        return authenticateResolved(topic, deviceCode(topic));
    }

    private AuthenticatedMqttMachine authenticateResolved(String topic, String claimedDeviceCode) {
        String topicDeviceCode = deviceCode(topic);
        if (!topicDeviceCode.equals(claimedDeviceCode)) {
            throw rejected("MQTT topic and payload identities do not match");
        }
        DeviceMqttIdentity identity = repository
                .findByClientIdForAuthentication(topicDeviceCode)
                .orElseThrow(() -> rejected("Unknown MQTT machine identity"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!identity.getDevice().getCode().equals(topicDeviceCode)
                || !identity.canAuthenticate(now)) {
            throw rejected("Inactive MQTT machine identity");
        }
        identity.recordAuthentication(now);
        return new AuthenticatedMqttMachine(identity.getDevice().getId(),
                identity.getDevice().getCode(), identity.getDevice().getType(),
                identity.getDevice().getStation().getCode(),
                identity.getInstanceId(), identity.getMqttClientId());
    }

    private String deviceCode(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) throw rejected("Invalid MQTT topic");
        String remainder = topic.substring(TOPIC_PREFIX.length());
        int separator = remainder.indexOf('/');
        if (separator <= 0 || separator == remainder.length() - 1) throw rejected("Invalid MQTT topic");
        return remainder.substring(0, separator);
    }

    private MqttMachineAuthenticationException rejected(String reason) {
        return new MqttMachineAuthenticationException(reason);
    }
}
