package com.transport.simulator.dto.response.networkmap;

import java.util.List;

public record NetworkMapResponse(List<NetworkMapLineResponse> lines) {
}
