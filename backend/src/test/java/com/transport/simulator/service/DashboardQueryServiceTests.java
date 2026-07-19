package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.response.dashboard.DashboardResponse;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DepotRepository;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.repository.projection.DepotOccupancyProjection;
import com.transport.simulator.repository.projection.DeviceStatusCountProjection;
import com.transport.simulator.repository.projection.DeviceTypeCountProjection;
import com.transport.simulator.repository.projection.TrainStatusCountProjection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardQueryServiceTests {

    @Mock
    private StationRepository stationRepository;

    @Mock
    private TransportLineRepository transportLineRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private TrainRepository trainRepository;

    @Mock
    private DepotRepository depotRepository;

    private DashboardQueryService dashboardQueryService;

    @BeforeEach
    void setUp() {
        dashboardQueryService = new DashboardQueryService(
                stationRepository,
                transportLineRepository,
                deviceRepository,
                trainRepository,
                depotRepository
        );
    }

    @Test
    void shouldBuildACompleteDashboardSummary() {
        when(stationRepository.countByActiveTrue()).thenReturn(50L);
        when(transportLineRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(
                new TransportLine("L1", "Línea 1", "Roja"),
                new TransportLine("L2", "Línea 2", "Verde")
        ));

        List<TrainStatusCountProjection> trainStatusCounts = List.of(
                trainStatusCount(TrainStatus.DEPOT, 230),
                trainStatusCount(TrainStatus.RESERVE, 5),
                trainStatusCount(TrainStatus.HISTORIC, 7)
        );
        when(trainRepository.countByActiveTrue()).thenReturn(242L);
        when(trainRepository.countActiveTrainsByStatus()).thenReturn(trainStatusCounts);

        List<DeviceStatusCountProjection> deviceStatusCounts = List.of(
                deviceStatusCount(DeviceStatus.OFFLINE, 622)
        );
        List<DeviceTypeCountProjection> deviceTypeCounts = List.of(
                deviceTypeCount(DeviceType.TICKET_MACHINE, 126),
                deviceTypeCount(DeviceType.ENTRY_VALIDATOR, 248),
                deviceTypeCount(DeviceType.EXIT_VALIDATOR, 248)
        );
        when(deviceRepository.countByActiveTrue()).thenReturn(622L);
        when(deviceRepository.countActiveDevicesByStatus()).thenReturn(deviceStatusCounts);
        when(deviceRepository.countActiveDevicesByType()).thenReturn(deviceTypeCounts);

        List<DepotOccupancyProjection> depotOccupancy = List.of(
                depotOccupancy(1L, "DEP-A", "Cochera A", 30, 29),
                depotOccupancy(2L, "DEP-B", "Cochera B", 20, 18)
        );
        when(depotRepository.findActiveDepotOccupancy()).thenReturn(depotOccupancy);

        DashboardResponse result = dashboardQueryService.getSummary();

        assertThat(result.network().activeStations()).isEqualTo(50);
        assertThat(result.network().activeLines()).isEqualTo(2);
        assertThat(result.lines()).extracting("code").containsExactly("L1", "L2");

        assertThat(result.fleet().activeTrains()).isEqualTo(242);
        assertThat(result.fleet().byStatus().get(TrainStatus.DEPOT)).isEqualTo(230);
        assertThat(result.fleet().byStatus().get(TrainStatus.IN_SERVICE)).isZero();

        assertThat(result.devices().activeDevices()).isEqualTo(622);
        assertThat(result.devices().byStatus().get(DeviceStatus.OFFLINE)).isEqualTo(622);
        assertThat(result.devices().byStatus().get(DeviceStatus.ONLINE)).isZero();
        assertThat(result.devices().byType().get(DeviceType.ENTRY_VALIDATOR)).isEqualTo(248);

        assertThat(result.depots().activeDepots()).isEqualTo(2);
        assertThat(result.depots().totalCapacity()).isEqualTo(50);
        assertThat(result.depots().assignedTrains()).isEqualTo(47);
        assertThat(result.depots().freeSlots()).isEqualTo(3);
        assertThat(result.depots().occupationPercentage()).isEqualTo(94);
        assertThat(result.depots().items()).extracting("freeSlots").containsExactly(1L, 2L);
    }

    private TrainStatusCountProjection trainStatusCount(TrainStatus status, long total) {
        TrainStatusCountProjection projection = mock(TrainStatusCountProjection.class);
        when(projection.getStatus()).thenReturn(status);
        when(projection.getTotal()).thenReturn(total);
        return projection;
    }

    private DeviceStatusCountProjection deviceStatusCount(DeviceStatus status, long total) {
        DeviceStatusCountProjection projection = mock(DeviceStatusCountProjection.class);
        when(projection.getStatus()).thenReturn(status);
        when(projection.getTotal()).thenReturn(total);
        return projection;
    }

    private DeviceTypeCountProjection deviceTypeCount(DeviceType type, long total) {
        DeviceTypeCountProjection projection = mock(DeviceTypeCountProjection.class);
        when(projection.getType()).thenReturn(type);
        when(projection.getTotal()).thenReturn(total);
        return projection;
    }

    private DepotOccupancyProjection depotOccupancy(
            Long id,
            String code,
            String name,
            int capacity,
            long assignedTrains
    ) {
        DepotOccupancyProjection projection = mock(DepotOccupancyProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getCode()).thenReturn(code);
        when(projection.getName()).thenReturn(name);
        when(projection.getCapacity()).thenReturn(capacity);
        when(projection.getAssignedTrains()).thenReturn(assignedTrains);
        return projection;
    }
}
