package com.transport.simulator.repository.projection;

public interface DepotOccupancyProjection {

    Long getId();

    String getCode();

    String getName();

    int getCapacity();

    long getAssignedTrains();
}
