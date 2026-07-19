package com.transport.simulator.repository.projection;

import com.transport.simulator.enums.DeviceType;

public interface DeviceTypeCountProjection {

    DeviceType getType();

    long getTotal();
}
