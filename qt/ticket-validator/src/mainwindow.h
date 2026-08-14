#pragma once

#include "validatorconfiguration.h"
#include "validationresult.h"

#include <QMainWindow>

class QLabel;
class QPlainTextEdit;
class QPushButton;
class ValidatorMqttClient;

class MainWindow final : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);
    void showValidationResult(const ValidationResult &result);

private:
    [[nodiscard]] QWidget *createHeader();
    [[nodiscard]] QWidget *createTurnstilePanel();
    [[nodiscard]] QWidget *createScannerPanel();
    [[nodiscard]] QWidget *createDevicePanel();
    [[nodiscard]] QWidget *createFooter();
    void configureWindow();
    void readQrCode();
    void updateQrInputState();
    void setValidationState(const QString &state, const QString &title,
                            const QString &detail, bool gateOpen);

    QLabel *m_connectionState = nullptr;
    QLabel *m_validationState = nullptr;
    QLabel *m_validationDetail = nullptr;
    QLabel *m_gateState = nullptr;
    QLabel *m_qrCounter = nullptr;
    QPlainTextEdit *m_qrInput = nullptr;
    QPushButton *m_scanButton = nullptr;
    ValidatorMqttClient *m_validationClient = nullptr;
    ValidatorConfiguration m_configuration;
    QString m_lastQrValue;
    bool m_connected = false;
};
