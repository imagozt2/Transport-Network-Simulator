package com.transport.simulator.service.deviceevent;

import com.transport.simulator.entity.Device;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
class SimulatedDeviceEventGenerator {

    private final Clock clock;

    public SimulatedDeviceEventGenerator(Clock clock) {
        this.clock = clock;
    }

    public DeviceEvent generateOperationalActivity(Device device) {
        EventDefinition definition = selectOperationalDefinition(device);
        return createEvent(device, definition);
    }

    public DeviceEvent generateServiceState(Device device, boolean serviceOpen) {
        EventDefinition definition = serviceOpen
                ? new EventDefinition(
                        DeviceEventType.DEVICE_ONLINE,
                        LogSeverity.INFO,
                        "Conexión simulada iniciada en "
                )
                : new EventDefinition(
                        DeviceEventType.DEVICE_OFFLINE,
                        LogSeverity.INFO,
                        "Conexión simulada finalizada en "
                );
        return createEvent(device, definition);
    }

    private DeviceEvent createEvent(Device device, EventDefinition definition) {
        LocalDateTime occurredAt = LocalDateTime.now(clock);

        return new DeviceEvent(
                device.getCode(),
                LogOrigin.DEVICE_SIMULATION,
                definition.type(),
                definition.severity(),
                definition.messagePrefix() + device.getName() + ".",
                occurredAt,
                null,
                buildPayload(device)
        );
    }

    private EventDefinition selectOperationalDefinition(Device device) {
        int option = ThreadLocalRandom.current().nextInt(100);

        if (device.getType() == DeviceType.TICKET_MACHINE) {
            if (option < 70) {
                return new EventDefinition(
                        DeviceEventType.TICKET_PURCHASE_COMPLETED,
                        LogSeverity.INFO,
                        "Compra simulada completada en "
                );
            }
            if (option < 95) {
                return new EventDefinition(
                        DeviceEventType.TICKET_PURCHASE_REQUESTED,
                        LogSeverity.INFO,
                        "Solicitud de compra simulada recibida en "
                );
            }
            return new EventDefinition(
                    DeviceEventType.QR_TICKET_GENERATED,
                    LogSeverity.INFO,
                    "Billete QR simulado generado en "
            );
        }

        if (option < 75) {
            return new EventDefinition(
                    DeviceEventType.VALIDATION_ACCEPTED,
                    LogSeverity.INFO,
                    "Validación simulada aceptada en "
            );
        }
        if (option < 95) {
            return new EventDefinition(
                    DeviceEventType.VALIDATION_REJECTED,
                    LogSeverity.WARNING,
                    "Validación simulada rechazada en "
            );
        }
        return new EventDefinition(
                DeviceEventType.VALIDATION_REQUESTED,
                LogSeverity.INFO,
                "Solicitud de validación simulada recibida en "
        );
    }

    private String buildPayload(Device device) {
        return """
                {"simulation":true,"deviceCode":"%s","deviceType":"%s","stationCode":"%s"}
                """.formatted(
                escape(device.getCode()),
                device.getType(),
                escape(device.getStation().getCode())
        ).strip();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record EventDefinition(
            DeviceEventType type,
            LogSeverity severity,
            String messagePrefix
    ) {
    }
}
