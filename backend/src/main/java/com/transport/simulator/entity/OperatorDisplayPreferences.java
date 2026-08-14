package com.transport.simulator.entity;

import com.transport.simulator.enums.OperatorTheme;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "operator_display_preferences")
public class OperatorDisplayPreferences extends AuditableEntity {

    public static final String DEFAULT_TIME_ZONE = "Europe/Madrid";
    public static final OperatorTheme DEFAULT_THEME = OperatorTheme.LIGHT;

    @Id
    @Column(name = "operator_account_id")
    private Long operatorAccountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_account_id", nullable = false)
    private OperatorAccount operatorAccount;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 20)
    private OperatorTheme theme;

    protected OperatorDisplayPreferences() {
    }

    public OperatorDisplayPreferences(OperatorAccount operatorAccount) {
        this.operatorAccount = Objects.requireNonNull(operatorAccount);
        this.timeZone = DEFAULT_TIME_ZONE;
        this.theme = DEFAULT_THEME;
    }

    public void update(String timeZone, OperatorTheme theme) {
        this.timeZone = requireText(timeZone);
        this.theme = Objects.requireNonNull(theme);
    }

    public Long getOperatorAccountId() {
        return operatorAccountId;
    }

    public OperatorAccount getOperatorAccount() {
        return operatorAccount;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public OperatorTheme getTheme() {
        return theme;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Operator time zone cannot be blank");
        }
        return value.trim();
    }
}
