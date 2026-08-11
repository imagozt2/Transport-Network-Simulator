package com.transport.simulator.service;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.entity.TicketValidation;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.TicketQrValidationType;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketValidationRepository;
import com.transport.simulator.service.model.TicketSnapshot;
import com.transport.simulator.service.model.TicketValidationDecision;
import com.transport.simulator.service.model.TicketValidationRequest;
import com.transport.simulator.ticketing.qr.TicketQrReferenceReuseException;
import com.transport.simulator.ticketing.qr.TicketQrUseClaimOutcome;
import com.transport.simulator.ticketing.qr.TicketQrUseGuard;
import com.transport.simulator.ticketing.qr.TicketQrVerificationException;
import com.transport.simulator.ticketing.qr.TicketQrVerificationFailure;
import com.transport.simulator.ticketing.qr.TicketQrVerifier;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TicketValidationService {

    private final DeviceRepository deviceRepository;
    private final StationRepository stationRepository;
    private final TicketValidationRepository validationRepository;
    private final TicketQrVerifier qrVerifier;
    private final TicketQrUseGuard useGuard;
    private final TicketEntryValidationService entryValidationService;
    private final TicketExitValidationService exitValidationService;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public TicketValidationService(DeviceRepository deviceRepository,
            StationRepository stationRepository, TicketValidationRepository validationRepository,
            TicketQrVerifier qrVerifier, TicketQrUseGuard useGuard,
            TicketEntryValidationService entryValidationService,
            TicketExitValidationService exitValidationService,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.stationRepository = stationRepository;
        this.validationRepository = validationRepository;
        this.qrVerifier = qrVerifier;
        this.useGuard = useGuard;
        this.entryValidationService = entryValidationService;
        this.exitValidationService = exitValidationService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public TicketValidationDecision validate(long authenticatedDeviceId,
            TicketValidationRequest request) {
        Objects.requireNonNull(request, "request is required");
        String reference = required(request.validationReference(), "validationReference");
        return validationRepository.findByExternalReference(reference)
                .map(TicketValidationDecision::from)
                .orElseGet(() -> decide(authenticatedDeviceId, normalized(request, reference)));
    }

    private TicketValidationDecision decide(long deviceId, TicketValidationRequest request) {
        try {
            return Objects.requireNonNull(transactions.execute(status -> accepted(deviceId, request)));
        } catch (TicketQrVerificationException exception) {
            return rejected(deviceId, request, qrReason(exception.getFailure()), exception.getMessage());
        } catch (TicketQrReferenceReuseException exception) {
            return rejected(deviceId, request, "DUPLICATE_REFERENCE", exception.getMessage());
        } catch (TicketValidationRejectionException exception) {
            return rejected(deviceId, request, exception.getReasonCode(), exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return rejected(deviceId, request, domainReason(exception), exception.getMessage());
        }
    }

    private TicketValidationDecision accepted(long deviceId, TicketValidationRequest request) {
        Device device = requiredDevice(deviceId, request);
        Station station = requiredStation(request.stationCode());
        var verified = qrVerifier.verify(request.qrValue());
        var claim = useGuard.claim(verified, request.validationReference(), request.direction(),
                device.getCode(), station.getCode());
        if (claim.outcome() == TicketQrUseClaimOutcome.IDEMPOTENT_RETRY) {
            return validationRepository.findByExternalReference(request.validationReference())
                    .map(TicketValidationDecision::from)
                    .orElseThrow(() -> new IllegalStateException(
                            "The validation retry has no persisted decision"));
        }

        Ticket ticket = verified.credential().getTicket();
        TicketSnapshot before = TicketSnapshot.from(ticket);
        TicketJourney journey = apply(ticket, request.direction(), station.getCode());
        TicketSnapshot after = TicketSnapshot.from(ticket);
        TicketValidation validation = validationRepository.save(TicketValidation.accepted(
                uniqueCode(), request.validationReference(), request.direction(), ticket, journey,
                station, device, verified.credential().getCredentialId().toString(), before, after,
                LocalDateTime.now(clock)));
        if (request.direction() == TicketQrValidationType.ENTRY) {
            journey.attachEntryValidation(validation);
        } else {
            journey.attachExitValidation(validation);
        }
        useGuard.complete(request.validationReference());
        return TicketValidationDecision.from(validation);
    }

    private TicketValidationDecision rejected(long deviceId, TicketValidationRequest request,
            String reason, String message) {
        return Objects.requireNonNull(transactions.execute(status ->
                validationRepository.findByExternalReference(request.validationReference())
                        .map(TicketValidationDecision::from)
                        .orElseGet(() -> {
                            Device device = requiredDevice(deviceId, request);
                            Station station = requiredStation(request.stationCode());
                            TicketValidation validation = validationRepository.save(
                                    TicketValidation.rejected(uniqueCode(), request.validationReference(),
                                            request.direction(), station, device, reason,
                                            safeMessage(message), LocalDateTime.now(clock)));
                            return TicketValidationDecision.from(validation);
                        })));
    }

    private TicketJourney apply(Ticket ticket, TicketQrValidationType direction,
            String stationCode) {
        if (direction == TicketQrValidationType.ENTRY) {
            return entryValidationService.enter(ticket, stationCode);
        }
        return exitValidationService.exit(ticket, stationCode);
    }

    private Device requiredDevice(long id, TicketValidationRequest request) {
        Device device = deviceRepository.findById(id)
                .filter(Device::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Validator device not found"));
        DeviceType expected = request.direction() == TicketQrValidationType.ENTRY
                ? DeviceType.ENTRY_VALIDATOR : DeviceType.EXIT_VALIDATOR;
        if (device.getType() != expected || device.getStation() == null
                || !device.getStation().getCode().equals(request.stationCode())) {
            throw new IllegalArgumentException("Validation context does not match the device");
        }
        return device;
    }

    private Station requiredStation(String code) {
        return stationRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new IllegalArgumentException("Active station not found"));
    }

    private TicketValidationRequest normalized(TicketValidationRequest request, String reference) {
        return new TicketValidationRequest(reference,
                Objects.requireNonNull(request.direction(), "direction is required"),
                required(request.stationCode(), "stationCode").toUpperCase(Locale.ROOT),
                required(request.qrValue(), "qrValue"));
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private String qrReason(TicketQrVerificationFailure failure) {
        return switch (failure) {
            case EXPIRED -> "EXPIRED";
            case CREDENTIAL_NOT_FOUND -> "UNKNOWN_TICKET";
            case CREDENTIAL_REVOKED, CREDENTIAL_SUPERSEDED -> "INACTIVE";
            case VERIFICATION_NOT_CONFIGURED -> "SERVICE_UNAVAILABLE";
            default -> "INVALID_SIGNATURE";
        };
    }

    private String domainReason(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("already has an open journey")) return "ENTRY_ALREADY_OPEN";
        if (message.contains("no open journey")) return "ENTRY_REQUIRED";
        if (message.contains("insufficient balance")) return "INSUFFICIENT_BALANCE";
        if (message.contains("validity") || message.contains("expired")) return "EXPIRED";
        if (message.contains("station") || message.contains("origin") || message.contains("destination")) {
            return "WRONG_STATION";
        }
        if (message.contains("no trip") || message.contains("exhausted")) return "EXHAUSTED";
        if (message.contains("device") || message.contains("context")) return "WRONG_DEVICE";
        return "INACTIVE";
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) return "Validation rejected";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String uniqueCode() {
        return "RMM-VAL-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }
}
