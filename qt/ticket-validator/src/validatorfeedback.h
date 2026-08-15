#pragma once

#include <QString>

enum class ValidatorFeedbackState
{
    Waiting,
    Processing,
    Accepted,
    Rejected,
};

struct ValidatorFeedbackPolicy
{
    QString code;
    QString icon;
    int beepCount;
    int resetDelayMilliseconds;
    bool cameraActive;
    bool gateOpen;
};

[[nodiscard]] ValidatorFeedbackPolicy validatorFeedbackPolicy(
    ValidatorFeedbackState state);
