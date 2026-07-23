package com.transport.simulator.service.deviceevent;

import com.transport.simulator.enums.DeviceStatus;
import org.springframework.stereotype.Component;

@Component
public class DeviceStatusTransitionPolicy {

    public DeviceStatus resolve(DeviceStatus currentStatus, DeviceEvent event) {
        return switch (event.type()) {
            case DEVICE_ONLINE, DEVICE_MAINTENANCE_FINISHED,
                    TICKET_PURCHASE_REQUESTED, TICKET_PURCHASE_COMPLETED,
                    QR_TICKET_GENERATED, QR_TICKET_SCANNED,
                    VALIDATION_REQUESTED, VALIDATION_ACCEPTED,
                    VALIDATION_REJECTED -> DeviceStatus.ONLINE;

            case DEVICE_OFFLINE -> DeviceStatus.OFFLINE;

            case DEVICE_MAINTENANCE_STARTED -> DeviceStatus.MAINTENANCE;

            case DEVICE_ERROR, TICKET_PURCHASE_FAILED,
                    VALIDATION_FAILED -> DeviceStatus.ERROR;

            case DEVICE_STATUS_CHANGED -> currentStatus;
        };
    }
}
