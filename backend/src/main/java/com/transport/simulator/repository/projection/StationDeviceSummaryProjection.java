package com.transport.simulator.repository.projection;

import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;

public interface StationDeviceSummaryProjection {

    Long getStationId();

    DeviceType getType();

    DeviceStatus getStatus();

    long getTotal();
}
