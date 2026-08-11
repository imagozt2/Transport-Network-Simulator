package com.transport.simulator.service;

import com.transport.simulator.dto.response.deviceoperation.DeviceOperationLastEventResponse;
import com.transport.simulator.dto.response.deviceoperation.DeviceConnectivityResponse;
import com.transport.simulator.dto.response.deviceoperation.DeviceOperationResponse;
import com.transport.simulator.dto.response.deviceoperation.DeviceOperationStationResponse;
import com.transport.simulator.dto.response.deviceoperation.DeviceOperationSummaryResponse;
import com.transport.simulator.dto.response.deviceoperation.DeviceOperationsResponse;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.entity.Station;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceConnectivityState;
import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.repository.DeviceEventLogRepository;
import com.transport.simulator.repository.DeviceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeviceOperationsQueryService {

    private final DeviceRepository deviceRepository;
    private final DeviceEventLogRepository eventLogRepository;
    private final Clock serviceClock;

    public DeviceOperationsQueryService(
            DeviceRepository deviceRepository,
            DeviceEventLogRepository eventLogRepository,
            Clock serviceClock
    ) {
        this.deviceRepository = deviceRepository;
        this.eventLogRepository = eventLogRepository;
        this.serviceClock = serviceClock;
    }

    public DeviceOperationsResponse getOperations(
            String search,
            DeviceType type,
            DeviceStatus status,
            String stationCode
    ) {
        List<Device> activeDevices = deviceRepository.findAllByActiveTrueOrderByCodeAsc();
        Map<Long, DeviceEventLog> latestEventsByDevice = eventLogRepository
                .findLatestForEachDevice()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        log -> log.getDevice().getId(),
                        Function.identity(),
                        (first, duplicate) -> {
                            throw new ServiceConfigurationException(
                                    "Device " + first.getDevice().getCode()
                                            + " has multiple latest events"
                            );
                        }
                ));

        String normalizedSearch = normalize(search);
        String normalizedStationCode = normalize(stationCode);

        List<DeviceOperationResponse> filteredDevices = activeDevices.stream()
                .filter(device -> matchesSearch(device, normalizedSearch))
                .filter(device -> type == null || device.getType() == type)
                .filter(device -> status == null || device.getStatus() == status)
                .filter(device -> normalizedStationCode == null
                        || normalize(device.getStation().getCode()).equals(normalizedStationCode))
                .map(device -> toResponse(device, latestEventsByDevice.get(device.getId())))
                .toList();

        return new DeviceOperationsResponse(
                LocalDateTime.now(serviceClock),
                summarize(activeDevices, filteredDevices.size()),
                filteredDevices
        );
    }

    private DeviceOperationResponse toResponse(Device device, DeviceEventLog latestEvent) {
        return new DeviceOperationResponse(
                device.getId(),
                device.getCode(),
                device.getName(),
                device.getType(),
                device.getStatus(),
                device.getLastConnectionAt(),
                toConnectivityResponse(device),
                toStationResponse(device.getStation()),
                latestEvent == null ? null : toEventResponse(latestEvent)
        );
    }

    private DeviceConnectivityResponse toConnectivityResponse(Device device) {
        DeviceConnectivityState state = !device.isMqttManaged()
                ? DeviceConnectivityState.NOT_MONITORED
                : device.getMqttPresence() == DeviceMqttPresence.ONLINE
                        ? DeviceConnectivityState.CONNECTED
                        : DeviceConnectivityState.DISCONNECTED;
        return new DeviceConnectivityResponse(
                state,
                device.getMqttPresence(),
                device.getOperationalState(),
                device.getLastCommunicationAt(),
                device.getLastPresenceAt(),
                device.getLastStatusAt(),
                device.getServiceMode(),
                device.getSoftwareVersion(),
                device.getUptimeSeconds()
        );
    }

    private DeviceOperationStationResponse toStationResponse(Station station) {
        return new DeviceOperationStationResponse(
                station.getId(),
                station.getCode(),
                station.getName()
        );
    }

    private DeviceOperationLastEventResponse toEventResponse(DeviceEventLog event) {
        return new DeviceOperationLastEventResponse(
                event.getId(),
                event.getEventType(),
                event.getSeverity(),
                event.getMessage(),
                event.getOrigin(),
                event.getSource(),
                event.getOccurredAt()
        );
    }

    private DeviceOperationSummaryResponse summarize(
            List<Device> devices,
            long filteredDeviceCount
    ) {
        Map<DeviceType, Long> byType = initializeCounts(DeviceType.class);
        Map<DeviceStatus, Long> byStatus = initializeCounts(DeviceStatus.class);

        devices.forEach(device -> {
            byType.compute(device.getType(), (ignored, count) -> count + 1);
            byStatus.compute(device.getStatus(), (ignored, count) -> count + 1);
        });

        return new DeviceOperationSummaryResponse(
                devices.size(),
                filteredDeviceCount,
                byType,
                byStatus
        );
    }

    private boolean matchesSearch(Device device, String normalizedSearch) {
        if (normalizedSearch == null) {
            return true;
        }

        return normalize(device.getCode()).contains(normalizedSearch)
                || normalize(device.getName()).contains(normalizedSearch)
                || normalize(device.getStation().getCode()).contains(normalizedSearch)
                || normalize(device.getStation().getName()).contains(normalizedSearch);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private <E extends Enum<E>> Map<E, Long> initializeCounts(Class<E> enumType) {
        Map<E, Long> counts = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }
}
