#include "validatorfeedback.h"

#include <QtTest>

class ValidatorFeedbackTest final : public QObject
{
    Q_OBJECT

private slots:
    void activatesCameraOnlyWhileWaiting();
    void representsEveryValidationState();
    void usesDistinctAcousticPatterns();
    void keepsAcceptedAndRejectedResultsForTheirConfiguredDuration();
    void returnsToAReusableWaitingStateAfterEveryResult();
};

void ValidatorFeedbackTest::activatesCameraOnlyWhileWaiting()
{
    QVERIFY(validatorFeedbackPolicy(ValidatorFeedbackState::Waiting).cameraActive);
    QVERIFY(!validatorFeedbackPolicy(ValidatorFeedbackState::Processing).cameraActive);
    QVERIFY(!validatorFeedbackPolicy(ValidatorFeedbackState::Accepted).cameraActive);
    QVERIFY(!validatorFeedbackPolicy(ValidatorFeedbackState::Rejected).cameraActive);
}

void ValidatorFeedbackTest::representsEveryValidationState()
{
    const auto waiting = validatorFeedbackPolicy(ValidatorFeedbackState::Waiting);
    const auto processing = validatorFeedbackPolicy(ValidatorFeedbackState::Processing);
    const auto accepted = validatorFeedbackPolicy(ValidatorFeedbackState::Accepted);
    const auto rejected = validatorFeedbackPolicy(ValidatorFeedbackState::Rejected);

    QCOMPARE(waiting.code, QStringLiteral("waiting"));
    QCOMPARE(processing.code, QStringLiteral("processing"));
    QCOMPARE(accepted.code, QStringLiteral("accepted"));
    QCOMPARE(rejected.code, QStringLiteral("rejected"));
    QVERIFY(!waiting.gateOpen);
    QVERIFY(!processing.gateOpen);
    QVERIFY(accepted.gateOpen);
    QVERIFY(!rejected.gateOpen);
    QCOMPARE(accepted.icon, QStringLiteral("✓"));
    QCOMPARE(rejected.icon, QStringLiteral("×"));
}

void ValidatorFeedbackTest::usesDistinctAcousticPatterns()
{
    QCOMPARE(validatorFeedbackPolicy(ValidatorFeedbackState::Waiting).beepCount, 0);
    QCOMPARE(validatorFeedbackPolicy(ValidatorFeedbackState::Processing).beepCount, 0);
    QCOMPARE(validatorFeedbackPolicy(ValidatorFeedbackState::Accepted).beepCount, 1);
    QCOMPARE(validatorFeedbackPolicy(ValidatorFeedbackState::Rejected).beepCount, 3);
}

void ValidatorFeedbackTest::keepsAcceptedAndRejectedResultsForTheirConfiguredDuration()
{
    QCOMPARE(validatorFeedbackPolicy(ValidatorFeedbackState::Accepted)
                 .resetDelayMilliseconds, 5000);
    QCOMPARE(validatorFeedbackPolicy(ValidatorFeedbackState::Rejected)
                 .resetDelayMilliseconds, 3000);
}

void ValidatorFeedbackTest::returnsToAReusableWaitingStateAfterEveryResult()
{
    for (const auto result : {ValidatorFeedbackState::Accepted,
                              ValidatorFeedbackState::Rejected}) {
        const auto completed = validatorFeedbackPolicy(result);
        QVERIFY(completed.resetDelayMilliseconds > 0);
        QVERIFY(!completed.cameraActive);

        const auto recovered = validatorFeedbackPolicy(ValidatorFeedbackState::Waiting);
        QVERIFY(recovered.cameraActive);
        QVERIFY(!recovered.gateOpen);
        QCOMPARE(recovered.beepCount, 0);
        QCOMPARE(recovered.resetDelayMilliseconds, 0);
    }
}

QTEST_MAIN(ValidatorFeedbackTest)
#include "validatorfeedbacktest.moc"
