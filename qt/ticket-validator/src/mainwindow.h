#pragma once

#include "validatorconfiguration.h"

#include <QMainWindow>

class QLabel;
class QPushButton;

class MainWindow final : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);

private:
    [[nodiscard]] QWidget *createHeader();
    [[nodiscard]] QWidget *createTurnstilePanel();
    [[nodiscard]] QWidget *createScannerPanel();
    [[nodiscard]] QWidget *createDevicePanel();
    [[nodiscard]] QWidget *createFooter();
    void configureWindow();
    void readQrCode();

    QLabel *m_connectionState = nullptr;
    QLabel *m_validationState = nullptr;
    QLabel *m_gateState = nullptr;
    QPushButton *m_scanButton = nullptr;
    ValidatorConfiguration m_configuration;
    QString m_lastQrValue;
};
