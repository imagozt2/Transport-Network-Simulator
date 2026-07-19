package com.transport.simulator.service;

import com.transport.simulator.dto.response.HealthResponse;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthResponse check() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return new HealthResponse("UP", "UP", Instant.now());
        } catch (DataAccessException exception) {
            return new HealthResponse("DEGRADED", "DOWN", Instant.now());
        }
    }
}
