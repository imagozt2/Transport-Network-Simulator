package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ticket_products")
public class TicketProduct extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 40)
    private TicketProductType productType;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "price_per_station", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerStation;

    @Column(name = "price_per_trip", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerTrip;

    @Column(name = "price_per_day", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerDay;

    @Column(name = "min_trips")
    private Integer minTrips;

    @Column(name = "max_trips")
    private Integer maxTrips;

    @Column(name = "min_days")
    private Integer minDays;

    @Column(name = "max_days")
    private Integer maxDays;

    @Column(name = "min_recharge_amount", precision = 10, scale = 2)
    private BigDecimal minRechargeAmount;

    @Column(name = "max_recharge_amount", precision = 10, scale = 2)
    private BigDecimal maxRechargeAmount;

    @Column(name = "requires_origin_destination", nullable = false)
    private boolean requiresOriginDestination;

    @Column(name = "uses_trip_balance", nullable = false)
    private boolean usesTripBalance;

    @Column(name = "uses_day_validity", nullable = false)
    private boolean usesDayValidity;

    @Column(name = "uses_money_balance", nullable = false)
    private boolean usesMoneyBalance;

    @Column(nullable = false)
    private boolean rechargeable;

    @Column(nullable = false)
    private boolean active;

    protected TicketProduct() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TicketProductType getProductType() {
        return productType;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public BigDecimal getPricePerStation() {
        return pricePerStation;
    }

    public BigDecimal getPricePerTrip() {
        return pricePerTrip;
    }

    public BigDecimal getPricePerDay() {
        return pricePerDay;
    }

    public Integer getMinTrips() {
        return minTrips;
    }

    public Integer getMaxTrips() {
        return maxTrips;
    }

    public Integer getMinDays() {
        return minDays;
    }

    public Integer getMaxDays() {
        return maxDays;
    }

    public BigDecimal getMinRechargeAmount() {
        return minRechargeAmount;
    }

    public BigDecimal getMaxRechargeAmount() {
        return maxRechargeAmount;
    }

    public boolean isRequiresOriginDestination() {
        return requiresOriginDestination;
    }

    public boolean isUsesTripBalance() {
        return usesTripBalance;
    }

    public boolean isUsesDayValidity() {
        return usesDayValidity;
    }

    public boolean isUsesMoneyBalance() {
        return usesMoneyBalance;
    }

    public boolean isRechargeable() {
        return rechargeable;
    }

    public boolean isActive() {
        return active;
    }
}
