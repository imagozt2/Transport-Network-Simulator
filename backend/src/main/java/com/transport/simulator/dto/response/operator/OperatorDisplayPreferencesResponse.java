package com.transport.simulator.dto.response.operator;

import com.transport.simulator.entity.OperatorDisplayPreferences;
import com.transport.simulator.enums.OperatorTheme;

public record OperatorDisplayPreferencesResponse(
        String timeZone,
        OperatorTheme theme
) {

    public static OperatorDisplayPreferencesResponse from(
            OperatorDisplayPreferences preferences
    ) {
        return new OperatorDisplayPreferencesResponse(
                preferences.getTimeZone(),
                preferences.getTheme()
        );
    }
}
