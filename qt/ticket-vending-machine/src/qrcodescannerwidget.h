#pragma once

#include <QWidget>

class QCamera;
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

signals:
    void cancelled();
    void qrDetected(const QString &qrValue);

private:
    void startCamera();
    void processFrame(const QVideoFrame &frame);
    void showUnavailableMessage(const QString &message);
    void retranslateUi();

    QCamera *m_camera = nullptr;
    QVideoWidget *m_videoWidget = nullptr;
    QLabel *m_title = nullptr;
    QLabel *m_instructions = nullptr;
    QLabel *m_status = nullptr;
    QPushButton *m_cancelButton = nullptr;
    bool m_spanish = true;
    bool m_decoding = false;
    qint64 m_lastDecodeAt = 0;
};
