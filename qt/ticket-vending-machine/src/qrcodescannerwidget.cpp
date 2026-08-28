#include "qrcodescannerwidget.h"

#include <QApplication>
#include <QCamera>
#include <QCameraDevice>
#include <QCameraFormat>
#include <QDateTime>
#include <QEvent>
#include <QFrame>
#include <QHBoxLayout>
#include <QImage>
#include <QLabel>
#include <QMediaCaptureSession>
#include <QMediaDevices>
#include <QPainter>
#include <QPainterPath>
#include <QPermissions>
#include <QPushButton>
#include <QTimer>
#include <QVideoFrame>
#include <QVideoSink>
#include <QVideoWidget>
#include <QVBoxLayout>

#include <limits>

#include <BarcodeFormat.h>
#include <ImageView.h>
#include <ReadBarcode.h>
#include <ReaderOptions.h>

namespace {
constexpr qint64 decodeIntervalMs = 180;
constexpr int preferredCameraWidth = 1280;
constexpr int preferredCameraHeight = 720;

QCameraFormat lowResourceCameraFormat(const QCameraDevice &device)
{
    QCameraFormat selected;
    qint64 bestScore = std::numeric_limits<qint64>::max();
    for (const QCameraFormat &format : device.videoFormats()) {
        const QSize resolution = format.resolution();
        if (resolution.isEmpty()) {
            continue;
        }
        const qint64 resolutionDistance =
            qAbs(resolution.width() - preferredCameraWidth)
            + qAbs(resolution.height() - preferredCameraHeight);
        const qint64 oversizedPenalty =
            resolution.width() > preferredCameraWidth || resolution.height() > preferredCameraHeight
                ? 1000000 : 0;
        const qint64 frameRatePenalty = static_cast<qint64>(
            qMax(0.0f, format.maxFrameRate() - 30.0f) * 1000.0f);
        const qint64 score = oversizedPenalty + resolutionDistance + frameRatePenalty;
        if (score < bestScore) {
            selected = format;
            bestScore = score;
        }
    }
    return selected;
}

class ScannerOverlay final : public QWidget
{
public:
    explicit ScannerOverlay(QWidget *parent)
        : QWidget(parent)
    {
        setAttribute(Qt::WA_TransparentForMouseEvents);
        setAttribute(Qt::WA_NoSystemBackground);
    }

protected:
    void paintEvent(QPaintEvent *) override
    {
        QPainter painter(this);
        painter.setRenderHint(QPainter::Antialiasing);

        const int side = qMin(width(), height()) * 58 / 100;
        const QRect target((width() - side) / 2, (height() - side) / 2, side, side);
        QPainterPath shade;
        shade.addRect(rect());
        QPainterPath opening;
        opening.addRoundedRect(target, 18, 18);
        painter.fillPath(shade.subtracted(opening), QColor(2, 6, 23, 145));

        QPen pen(QColor(QStringLiteral("#38bdf8")), 6, Qt::SolidLine, Qt::RoundCap);
        painter.setPen(pen);
        const int arm = qMax(34, side / 7);
        const auto corner = [&painter, arm](const QPoint &point, int horizontal, int vertical) {
            painter.drawLine(point, point + QPoint(horizontal * arm, 0));
            painter.drawLine(point, point + QPoint(0, vertical * arm));
        };
        corner(target.topLeft(), 1, 1);
        corner(target.topRight(), -1, 1);
        corner(target.bottomLeft(), 1, -1);
        corner(target.bottomRight(), -1, -1);

        painter.setPen(QPen(QColor(255, 255, 255, 170), 1, Qt::DashLine));
        painter.drawLine(target.center().x(), target.top() + 28,
                         target.center().x(), target.bottom() - 28);
        painter.drawLine(target.left() + 28, target.center().y(),
                         target.right() - 28, target.center().y());
    }
};

class CameraViewport final : public QFrame
{
public:
    explicit CameraViewport(QWidget *parent = nullptr)
        : QFrame(parent)
    {
        setObjectName(QStringLiteral("cameraViewport"));
        auto *layout = new QVBoxLayout(this);
        layout->setContentsMargins(0, 0, 0, 0);
        m_video = new QVideoWidget(this);
        m_video->setAspectRatioMode(Qt::KeepAspectRatioByExpanding);
        layout->addWidget(m_video);
        m_overlay = new ScannerOverlay(this);
        m_overlay->raise();
    }

    QVideoWidget *video() const { return m_video; }

protected:
    void resizeEvent(QResizeEvent *event) override
    {
        QFrame::resizeEvent(event);
        m_overlay->setGeometry(rect());
    }

private:
    QVideoWidget *m_video = nullptr;
    ScannerOverlay *m_overlay = nullptr;
};
}

QrCodeScannerWidget::QrCodeScannerWidget(QWidget *parent)
    : QWidget(parent)
{
    auto *layout = new QVBoxLayout(this);
    layout->setContentsMargins(4, 0, 4, 0);
    layout->setSpacing(12);

    auto *heading = new QHBoxLayout;
    auto *copy = new QVBoxLayout;
    m_title = new QLabel(this);
    m_title->setObjectName(QStringLiteral("scannerTitle"));
    m_instructions = new QLabel(this);
    m_instructions->setObjectName(QStringLiteral("scannerInstructions"));
    m_instructions->setWordWrap(true);
    copy->addWidget(m_title);
    copy->addWidget(m_instructions);
    heading->addLayout(copy, 1);
    m_cancelButton = new QPushButton(this);
    m_cancelButton->setObjectName(QStringLiteral("scannerCancel"));
    heading->addWidget(m_cancelButton, 0, Qt::AlignTop);
    layout->addLayout(heading);

    auto *viewport = new CameraViewport(this);
    viewport->setMinimumHeight(390);
    m_viewport = viewport;
    m_videoWidget = viewport->video();
    layout->addWidget(viewport, 1);

    m_status = new QLabel(this);
    m_status->setObjectName(QStringLiteral("scannerStatus"));
    m_status->setAlignment(Qt::AlignCenter);
    layout->addWidget(m_status);

    connect(m_cancelButton, &QPushButton::clicked, this, [this] {
        stop();
        emit cancelled();
    });
    retranslateUi();
}

void QrCodeScannerWidget::start()
{
    m_cameraRequested = true;
    m_decoding = false;
    m_lastDecodeAt = 0;
    m_status->setText(m_spanish ? QStringLiteral("Buscando un código QR…")
                               : QStringLiteral("Looking for a QR code…"));

    QWidget *topLevelWindow = window();
    if (m_observedWindow != topLevelWindow) {
        if (m_observedWindow) {
            m_observedWindow->removeEventFilter(this);
        }
        m_observedWindow = topLevelWindow;
        if (m_observedWindow) {
            m_observedWindow->installEventFilter(this);
        }
    }

    QCameraPermission permission;
    const auto status = qApp->checkPermission(permission);
    if (status == Qt::PermissionStatus::Undetermined) {
        qApp->requestPermission(permission, this, [this](const QPermission &result) {
            if (result.status() == Qt::PermissionStatus::Granted) {
                synchronizeCameraWithWindow();
            } else {
                showUnavailableMessage(m_spanish
                    ? QStringLiteral("Se necesita permiso para utilizar la cámara.")
                    : QStringLiteral("Camera permission is required."));
            }
        });
        return;
    }
    if (status == Qt::PermissionStatus::Denied) {
        showUnavailableMessage(m_spanish
            ? QStringLiteral("El acceso a la cámara está desactivado en el sistema.")
            : QStringLiteral("Camera access is disabled in system settings."));
        return;
    }
    synchronizeCameraWithWindow();
}

void QrCodeScannerWidget::startCamera()
{
    releaseCamera();
    const QCameraDevice device = QMediaDevices::defaultVideoInput();
    if (device.isNull()) {
        showUnavailableMessage(m_spanish ? QStringLiteral("No se ha detectado ninguna cámara.")
                                         : QStringLiteral("No camera was detected."));
        return;
    }

    m_camera = new QCamera(device, this);
    const QCameraFormat format = lowResourceCameraFormat(device);
    if (!format.isNull()) {
        m_camera->setCameraFormat(format);
    }
    auto *captureSession = new QMediaCaptureSession(m_camera);
    captureSession->setCamera(m_camera);
    captureSession->setVideoOutput(m_videoWidget);
    connect(m_videoWidget->videoSink(), &QVideoSink::videoFrameChanged,
            this, &QrCodeScannerWidget::processFrame);
    connect(m_camera, &QCamera::errorOccurred, this,
            [this](QCamera::Error, const QString &description) {
        showUnavailableMessage(description);
    });
    m_camera->start();
}

void QrCodeScannerWidget::stop()
{
    m_cameraRequested = false;
    releaseCamera();
}

void QrCodeScannerWidget::releaseCamera()
{
    if (!m_camera) {
        return;
    }
    m_camera->stop();
    delete m_camera;
    m_camera = nullptr;
}

void QrCodeScannerWidget::synchronizeCameraWithWindow()
{
    const QWidget *topLevelWindow = window();
    const bool canUseCamera = m_cameraRequested && isVisible()
        && topLevelWindow && !topLevelWindow->isMinimized();
    if (!canUseCamera) {
        releaseCamera();
        return;
    }
    if (!m_camera) {
        startCamera();
    }
}

bool QrCodeScannerWidget::eventFilter(QObject *watched, QEvent *event)
{
    if (watched == m_observedWindow
            && (event->type() == QEvent::WindowStateChange
                || event->type() == QEvent::Show
                || event->type() == QEvent::Hide)) {
        QTimer::singleShot(0, this, [this] {
            synchronizeCameraWithWindow();
        });
    }
    return QWidget::eventFilter(watched, event);
}

void QrCodeScannerWidget::setSpanish(bool spanish)
{
    m_spanish = spanish;
    retranslateUi();
}

void QrCodeScannerWidget::setKioskMode(bool kioskMode)
{
    m_title->setVisible(!kioskMode);
    m_instructions->setVisible(!kioskMode);
    m_cancelButton->setVisible(!kioskMode);
    m_viewport->setMinimumHeight(kioskMode ? 230 : 390);
}

void QrCodeScannerWidget::processFrame(const QVideoFrame &frame)
{
    if (m_decoding || !frame.isValid()) {
        return;
    }
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (now - m_lastDecodeAt < decodeIntervalMs) {
        return;
    }
    m_lastDecodeAt = now;
    m_decoding = true;

    const QImage image = frame.toImage().convertToFormat(QImage::Format_Grayscale8);
    if (!image.isNull()) {
        const ZXing::ImageView view(image.constBits(), image.width(), image.height(),
                                    ZXing::ImageFormat::Lum, image.bytesPerLine());
        ZXing::ReaderOptions options;
        options.setFormats(ZXing::BarcodeFormat::QRCode);
        options.setTryHarder(true);
        options.setTryRotate(true);
        options.setTryInvert(true);
        const auto result = ZXing::ReadBarcode(view, options);
        if (result.isValid()) {
            const QString value = QString::fromStdString(result.text());
            if (value.startsWith(QStringLiteral("RMM:TICKET:"))) {
                m_status->setText(m_spanish ? QStringLiteral("Código QR leído correctamente")
                                            : QStringLiteral("QR code read successfully"));
                stop();
                emit qrDetected(value);
                m_decoding = false;
                return;
            }
            m_status->setText(m_spanish ? QStringLiteral("El QR no pertenece a un billete RMM")
                                        : QStringLiteral("This is not an RMM ticket QR code"));
        }
    }
    m_decoding = false;
}

void QrCodeScannerWidget::showUnavailableMessage(const QString &message)
{
    releaseCamera();
    m_status->setText(message);
}

void QrCodeScannerWidget::retranslateUi()
{
    m_title->setText(m_spanish ? QStringLiteral("Escanear billete")
                               : QStringLiteral("Scan ticket"));
    m_instructions->setText(m_spanish
        ? QStringLiteral("Sitúe el código QR completo dentro del objetivo y manténgalo estable.")
        : QStringLiteral("Place the complete QR code inside the target and hold it steady."));
    m_cancelButton->setText(m_spanish ? QStringLiteral("Cancelar") : QStringLiteral("Cancel"));
}
