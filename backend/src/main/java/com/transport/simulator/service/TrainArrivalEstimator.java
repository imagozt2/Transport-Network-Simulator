package com.transport.simulator.service;

import com.transport.simulator.entity.LineStation;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.service.model.SimulatedTrainState;
import com.transport.simulator.service.model.TrainArrivalEstimate;
import java.util.List;

public final class TrainArrivalEstimator {

    private TrainArrivalEstimator() {
    }

    public static TrainArrivalEstimate estimate(
            Long targetStationId,
            List<LineStation> route,
            SimulatedTrainState train
    ) {
        if (train.positionState() == TrainPositionState.AT_STATION
                && targetStationId.equals(train.currentStationId())) {
            return new TrainArrivalEstimate(train.direction(), 0, 0);
        }

        int nextIndex = findStationIndex(route, train.nextStationId());
        long secondsUntilArrival = train.secondsUntilNextStation();
        int stationsAway = 1;
        ServiceDirection arrivalDirection = train.direction();
        if (targetStationId.equals(route.get(nextIndex).getStation().getId())) {
            return new TrainArrivalEstimate(arrivalDirection, stationsAway, secondsUntilArrival);
        }

        int currentIndex = nextIndex;
        int maximumSegments = 2 * (route.size() - 1);
        for (int traversedSegments = 1; traversedSegments <= maximumSegments; traversedSegments++) {
            ServiceDirection departureDirection = directionAfterArrival(
                    arrivalDirection,
                    currentIndex,
                    route.size()
            );
            int followingIndex = currentIndex + departureDirection.getValue();
            secondsUntilArrival = Math.addExact(
                    secondsUntilArrival,
                    route.get(currentIndex).getDwellSeconds()
                            + travelSeconds(route, currentIndex, departureDirection)
            );
            stationsAway++;
            arrivalDirection = departureDirection;
            currentIndex = followingIndex;
            if (targetStationId.equals(route.get(currentIndex).getStation().getId())) {
                return new TrainArrivalEstimate(arrivalDirection, stationsAway, secondsUntilArrival);
            }
        }

        throw new ServiceConfigurationException(
                "Unable to estimate arrival of train " + train.trainCode()
                        + " at station " + targetStationId
        );
    }

    private static int findStationIndex(List<LineStation> route, Long stationId) {
        for (int index = 0; index < route.size(); index++) {
            if (route.get(index).getStation().getId().equals(stationId)) {
                return index;
            }
        }
        throw new ServiceConfigurationException("Train position references a station outside its route");
    }

    private static ServiceDirection directionAfterArrival(
            ServiceDirection arrivalDirection,
            int stationIndex,
            int routeSize
    ) {
        if (stationIndex == 0) {
            return ServiceDirection.OUTBOUND;
        }
        if (stationIndex == routeSize - 1) {
            return ServiceDirection.INBOUND;
        }
        return arrivalDirection;
    }

    private static long travelSeconds(
            List<LineStation> route,
            int stationIndex,
            ServiceDirection direction
    ) {
        Integer travelSeconds = direction == ServiceDirection.OUTBOUND
                ? route.get(stationIndex).getTravelSecondsToNext()
                : route.get(stationIndex - 1).getTravelSecondsToNext();
        if (travelSeconds == null || travelSeconds <= 0) {
            throw new ServiceConfigurationException(
                    "Missing travel time on line " + route.getFirst().getLine().getCode()
            );
        }
        return travelSeconds;
    }
}
