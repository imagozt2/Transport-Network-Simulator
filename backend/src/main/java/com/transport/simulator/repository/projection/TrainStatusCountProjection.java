package com.transport.simulator.repository.projection;

import com.transport.simulator.enums.TrainStatus;

public interface TrainStatusCountProjection {

    TrainStatus getStatus();

    long getTotal();
}
