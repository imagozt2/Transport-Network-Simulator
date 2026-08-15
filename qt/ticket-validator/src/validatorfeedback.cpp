#include "validatorfeedback.h"

ValidatorFeedbackPolicy validatorFeedbackPolicy(ValidatorFeedbackState state)
{
    switch (state) {
    case ValidatorFeedbackState::Waiting:
        return {QStringLiteral("waiting"), QStringLiteral("…"), 0, 0, true, false};
    case ValidatorFeedbackState::Processing:
        return {QStringLiteral("processing"), QStringLiteral("…"), 0, 0, false, false};
    case ValidatorFeedbackState::Accepted:
        return {QStringLiteral("accepted"), QStringLiteral("✓"), 1, 5000, false, true};
    case ValidatorFeedbackState::Rejected:
        return {QStringLiteral("rejected"), QStringLiteral("×"), 3, 3000, false, false};
    }
    return {QStringLiteral("waiting"), QStringLiteral("…"), 0, 0, true, false};
}
