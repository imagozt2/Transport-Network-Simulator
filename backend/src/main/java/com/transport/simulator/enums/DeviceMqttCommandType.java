package com.transport.simulator.enums;

public enum DeviceMqttCommandType {
    TICKET_ISSUE("ticket.issue-command"),
    CONFIGURATION_REFRESH("device.configuration-refresh-command"),
    STATUS_REQUEST("device.status-request-command"),
    RESTART("device.restart-command");

    private final String messageType;

    DeviceMqttCommandType(String messageType) { this.messageType = messageType; }
    public String messageType() { return messageType; }
}
