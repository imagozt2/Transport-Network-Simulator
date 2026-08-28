package com.transport.simulator.security;

import com.transport.simulator.entity.PassengerSession;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.PassengerSessionRepository;
import com.transport.simulator.service.PassengerSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PassengerBearerAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final PassengerSessionRepository sessionRepository;
    private final PassengerSessionService sessionService;
    private final Clock clock;

    public PassengerBearerAuthenticationFilter(
            PassengerSessionRepository sessionRepository,
            PassengerSessionService sessionService,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && authorization != null
                && authorization.startsWith(BEARER_PREFIX)) {
            authenticate(authorization.substring(BEARER_PREFIX.length()).trim());
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String accessToken) {
        sessionRepository.findByAccessTokenHash(sessionService.hash(accessToken))
                .filter(session -> session.canUseAccessToken(LocalDateTime.ofInstant(
                        clock.instant(), ZoneOffset.UTC
                )))
                .filter(session -> session.getPassengerAccount().getStatus()
                        == PassengerAccountStatus.ACTIVE)
                .ifPresent(this::setAuthentication);
    }

    private void setAuthentication(PassengerSession session) {
        PassengerPrincipal principal = new PassengerPrincipal(
                session.getPassengerAccount().getId(),
                session.getPassengerAccount().getPublicId(),
                session.getId(),
                session.getInstallationId()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PASSENGER"))
                )
        );
    }
}
