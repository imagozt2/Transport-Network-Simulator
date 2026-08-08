package com.transport.simulator.config;

import com.transport.simulator.security.RestAccessDeniedHandler;
import com.transport.simulator.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        return http
                .cors(cors -> {
                })
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/rmm-app/v1/**")
                        .csrfTokenRepository(csrfRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/csrf",
                                "/api/auth/login",
                                "/api/rmm-app/v1/auth/register"
                        ).permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/admin/passenger-users/**"
                        ).hasRole("ADMINISTRATOR")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/admin/passenger-users"
                        ).hasRole("ADMINISTRATOR")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/admin/passenger-users/**"
                        ).hasRole("ADMINISTRATOR")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .build();
    }
}
