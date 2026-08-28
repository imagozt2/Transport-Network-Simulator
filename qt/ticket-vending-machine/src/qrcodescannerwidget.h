#pragma once

#include <QWidget>

class QCamera;
class QEvent;
class QLabel;
class QPushButton;
class QVideoFrame;
class QVideoWidget;

class QrCodeScannerWidget final : public QWidget
{
    Q_OBJECT

public:
    explicit QrCodeScannerWidget(QWidget *parent = nullptr);

    void start();
    void stop();
    void setSpanish(bool spanish);
    void setKioskMode(bool kioskMode);

signals:
    void cancelled();
    void qrDetected(const QString &qrValue);

private:
    bool eventFilter(QObject *watched, QEvent *event) override;
    void startCamera();
    void releaseCamera();
    void synchronizeCameraWithWindow();
    void processFrame(const QVideoFrame &frame);
    void showUnavailableMessage(const QString &message);
    void retranslateUi();

    QCamera *m_camera = nullptr;
    QVideoWidget *m_videoWidget = nullptr;
    QWidget *m_viewport = nullptr;
    QLabel *m_title = nullptr;
    QLabel *m_instructions = nullptr;
    QLabel *m_status = nullptr;
    QPushButton *m_cancelButton = nullptr;
    QWidget *m_observedWindow = nullptr;
    bool m_spanish = true;
    bool m_decoding = false;
    bool m_cameraRequested = false;
    qint64 m_lastDecodeAt = 0;
};
