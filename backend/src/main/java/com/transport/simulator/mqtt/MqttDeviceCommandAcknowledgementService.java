package com.transport.simulator.mqtt;

import com.transport.simulator.enums.CompensatoryIssuanceStatus;
import com.transport.simulator.enums.DeviceMqttCommandStatus;
import com.transport.simulator.enums.DeviceMqttCommandType;
import com.transport.simulator.repository.CompensatoryTicketIssuanceRepository;
import com.transport.simulator.repository.DeviceMqttCommandRepository;
import com.transport.simulator.service.TicketIssuanceEventRegistrationService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqttDeviceCommandAcknowledgementService {
    private final DeviceMqttCommandRepository commandRepository;
    private final CompensatoryTicketIssuanceRepository issuanceRepository;
    private final TicketIssuanceEventRegistrationService eventService;
    private final Clock clock;

    public MqttDeviceCommandAcknowledgementService(DeviceMqttCommandRepository commandRepository,
            CompensatoryTicketIssuanceRepository issuanceRepository,
            TicketIssuanceEventRegistrationService eventService, Clock clock) {
        this.commandRepository = commandRepository;
        this.issuanceRepository = issuanceRepository;
        this.eventService = eventService;
        this.clock = clock;
    }

    @Transactional
    public void acknowledge(Long deviceId, String commandId, String issuanceCode,
            DeviceMqttCommandStatus status, String resultCode) {
        var command = commandRepository.findByCommandIdForAcknowledgement(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown acknowledged command"));
        if (!command.getDevice().getId().equals(deviceId)) {
            throw new IllegalArgumentException("Acknowledged command belongs to another machine");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        command.acknowledge(status, now, resultCode);
        if (command.getType() != DeviceMqttCommandType.TICKET_ISSUE || issuanceCode == null) return;
        var issuance = issuanceRepository.findByCodeForUpdate(issuanceCode).orElse(null);
        if (issuance == null) return;
        if (status == DeviceMqttCommandStatus.COMPLETED
                && issuance.getStatus() == CompensatoryIssuanceStatus.PROCESSING) {
            issuance.complete(now);
            eventService.registerCompleted(issuance, now);
        } else if ((status == DeviceMqttCommandStatus.FAILED || status == DeviceMqttCommandStatus.REJECTED)
                && (issuance.getStatus() == CompensatoryIssuanceStatus.PROCESSING
                    || issuance.getStatus() == CompensatoryIssuanceStatus.REQUESTED)) {
            issuance.fail(resultCode, now);
            eventService.registerFailed(issuance, now);
        }
    }
}
