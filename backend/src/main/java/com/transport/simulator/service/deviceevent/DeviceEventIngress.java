package com.transport.simulator.service.deviceevent;

public interface DeviceEventIngress {

    DeviceEventReceipt receive(DeviceEventMessage message);
}
