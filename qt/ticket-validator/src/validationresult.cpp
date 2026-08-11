#include "validationresult.h"

#include <QDateTime>
#include <QLocale>

namespace {

QString rejectionMessage(const QString &reasonCode)
{
    const QString code = reasonCode.trimmed().toUpper();
    if (code == QStringLiteral("EXPIRED")) return QObject::tr("El billete ha caducado");
    if (code == QStringLiteral("EXHAUSTED")) return QObject::tr("El billete no dispone de saldo o viajes");
    if (code == QStringLiteral("INSUFFICIENT_BALANCE")) return QObject::tr("Saldo insuficiente para completar el trayecto");
    if (code == QStringLiteral("ENTRY_ALREADY_OPEN")) return QObject::tr("El billete ya tiene un trayecto abierto");
    if (code == QStringLiteral("ENTRY_REQUIRED")) return QObject::tr("No existe una validación de entrada");
    if (code == QStringLiteral("WRONG_STATION")) return QObject::tr("El billete no es válido en esta estación");
    if (code == QStringLiteral("WRONG_DEVICE")) return QObject::tr("Utiliza el torniquete correspondiente");
    if (code == QStringLiteral("BLOCKED")) return QObject::tr("El billete está bloqueado");
    if (code == QStringLiteral("INACTIVE")) return QObject::tr("El billete no está activo");
    if (code == QStringLiteral("UNKNOWN_TICKET")) return QObject::tr("No se reconoce el billete");
    if (code == QStringLiteral("DUPLICATE_REFERENCE")) return QObject::tr("La lectura ya ha sido procesada");
    if (code == QStringLiteral("SERVICE_UNAVAILABLE")) return QObject::tr("Servicio temporalmente no disponible");
    return QObject::tr("El código QR no es válido");
}

std::optional<double> optionalNumber(const QJsonObject &payload, const QString &name)
{
    const QJsonValue value = payload.value(name);
    return value.isDouble() ? std::optional<double>(value.toDouble()) : std::nullopt;
}

std::optional<int> optionalInteger(const QJsonObject &payload, const QString &name)
{
    const QJsonValue value = payload.value(name);
    return value.isDouble() ? std::optional<int>(value.toInt()) : std::nullopt;
}

} // namespace

std::optional<ValidationResult> ValidationResult::fromPayload(
    const QJsonObject &payload,
    QString *error)
{
    ValidationResult result;
    result.validationReference = payload.value(QStringLiteral("validationReference")).toString().trimmed();
    result.decision = payload.value(QStringLiteral("decision")).toString().trimmed().toUpper();
    result.reasonCode = payload.value(QStringLiteral("reasonCode")).toString().trimmed().toUpper();
    result.ticketCode = payload.value(QStringLiteral("ticketCode")).toString().trimmed();
    result.fareAmount = optionalNumber(payload, QStringLiteral("fareAmount"));
    result.remainingBalance = optionalNumber(payload, QStringLiteral("remainingBalance"));
    result.consumedTrips = optionalInteger(payload, QStringLiteral("consumedTrips"));
    result.remainingTrips = optionalInteger(payload, QStringLiteral("remainingTrips"));
    result.validUntil = payload.value(QStringLiteral("validUntil")).toString().trimmed();

    if (result.validationReference.isEmpty()
        || (result.decision != QStringLiteral("ACCEPTED")
            && result.decision != QStringLiteral("REJECTED"))) {
        if (error != nullptr) {
            *error = QObject::tr("La respuesta de validación no tiene un formato válido");
        }
        return std::nullopt;
    }
    return result;
}

bool ValidationResult::isAccepted() const
{
    return decision == QStringLiteral("ACCEPTED");
}

QString ValidationResult::title() const
{
    return isAccepted() ? QObject::tr("Validación aceptada")
                        : QObject::tr("Validación rechazada");
}

QString ValidationResult::detail() const
{
    if (!isAccepted()) {
        return rejectionMessage(reasonCode);
    }
    if (remainingTrips.has_value()) {
        return QObject::tr("Viajes restantes: %1").arg(*remainingTrips);
    }
    if (remainingBalance.has_value()) {
        return QObject::tr("Saldo restante: %1 €")
            .arg(QLocale(QLocale::Spanish, QLocale::Spain).toString(*remainingBalance, 'f', 2));
    }
    if (!validUntil.isEmpty()) {
        const QDateTime until = QDateTime::fromString(validUntil, Qt::ISODate);
        return until.isValid()
            ? QObject::tr("Válido hasta %1").arg(QLocale().toString(until.toLocalTime(), QLocale::ShortFormat))
            : QObject::tr("Abono temporal válido");
    }
    return QObject::tr("Puedes continuar");
}
