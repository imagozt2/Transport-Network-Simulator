package com.transport.simulator.repository.projection;

import com.transport.simulator.enums.DeviceStatus;

public interface DeviceStatusCountProjection {

    DeviceStatus getStatus();

    long getTotal();
}
