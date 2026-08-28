#pragma once

#include <QJsonObject>
#include <QString>

#include <optional>

struct ValidationResult
{
    QString validationReference;
    QString decision;
    QString reasonCode;
    QString ticketCode;
    std::optional<double> fareAmount;
    std::optional<double> remainingBalance;
    std::optional<int> consumedTrips;
    std::optional<int> remainingTrips;
    QString validUntil;

    [[nodiscard]] static std::optional<ValidationResult> fromPayload(
        const QJsonObject &payload,
        QString *error = nullptr);
    [[nodiscard]] bool isAccepted() const;
    [[nodiscard]] QString title() const;
    [[nodiscard]] QString detail() const;
};
