package com.transport.simulator.controller;

import com.transport.simulator.dto.response.networkmap.NetworkMapResponse;
import com.transport.simulator.service.NetworkMapQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/network-map")
public class NetworkMapController {

    private final NetworkMapQueryService networkMapQueryService;

    public NetworkMapController(NetworkMapQueryService networkMapQueryService) {
        this.networkMapQueryService = networkMapQueryService;
    }

    @GetMapping
    public NetworkMapResponse getNetworkMap() {
        return networkMapQueryService.getNetworkMap();
    }
}
