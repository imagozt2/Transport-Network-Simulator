#include "mainwindow.h"
#include "qrcodescannerwidget.h"
#include "validatormqttclient.h"

#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QPlainTextEdit>
#include <QPushButton>
#include <QSizePolicy>
#include <QStyle>
#include <QTimer>
#include <QVBoxLayout>
#include <QWidget>

namespace {
constexpr qsizetype maximumQrLength = 4096;
constexpr auto windowStyle = R"(
    QMainWindow { background-color: #f4f7fa; }
    QLabel { color: #0f172a; font-family: "Segoe UI"; }
    QLabel#brandMark {
        min-width: 52px; min-height: 52px; max-width: 52px; max-height: 52px;
        border-radius: 14px; background-color: #2294f2; color: white;
        font-size: 28px; font-weight: 900;
    }
    QLabel#applicationName { font-size: 22px; font-weight: 700; }
    QLabel#applicationContext, QLabel#screenDescription,
    QLabel#detailHint, QLabel#footerText { color: #64748b; font-size: 14px; }
    QLabel#connectionState {
        padding: 7px 12px; border-radius: 14px; background-color: #dcfce7;
        color: #166534; font-size: 12px; font-weight: 700;
    }
    QLabel#connectionState[configurationValid="false"] {
        background-color: #fee2e2; color: #991b1b;
    }
    QFrame#turnstilePanel, QFrame#scannerPanel, QFrame#devicePanel {
        border: 1px solid #dbe3ec; border-radius: 18px; background-color: white;
    }
    QFrame#scannerWell {
        border: 2px solid #bfdbfe; border-radius: 22px; background-color: #eff8ff;
    }
    QPlainTextEdit#qrInput {
        min-height: 118px; border: 2px solid #bfdbfe; border-radius: 14px;
        padding: 14px; background-color: white; color: #0f172a;
        font-family: Consolas; font-size: 13px;
    }
    QPlainTextEdit#qrInput:focus { border-color: #2294f2; }
    QLabel#qrCounter { color: #64748b; font-size: 12px; }
    QLabel#qrCounter[invalid="true"] { color: #b91c1c; font-weight: 700; }
    QLabel#screenTitle { font-size: 28px; font-weight: 800; }
    QLabel#eyebrow, QLabel#detailLabel {
        color: #0060a8; font-size: 12px; font-weight: 700;
    }
    QLabel#detailValue { font-size: 18px; font-weight: 800; }
    QLabel#gateState {
        padding: 12px 16px; border-radius: 12px; background-color: #f1f5f9;
        color: #334155; font-size: 14px; font-weight: 800;
    }
    QLabel#validationState {
        padding: 14px 18px; border-radius: 14px; background-color: #e0f2fe;
        color: #075985; font-size: 15px; font-weight: 800;
    }
    QLabel#validationState[state="read"] {
        background-color: #fef3c7; color: #92400e;
    }
    QLabel#validationState[state="accepted"] {
        background-color: #dcfce7; color: #166534;
    }
    QLabel#validationState[state="rejected"] {
        background-color: #fee2e2; color: #991b1b;
    }
    QLabel#validationDetail {
        color: #475569; font-size: 14px; font-weight: 600;
    }
    QLabel#gateState[state="open"] {
        background-color: #dcfce7; color: #166534;
    }
    QLabel#gateState[state="closed-rejected"] {
        background-color: #fee2e2; color: #991b1b;
    }
    QPushButton#scanAction {
        min-height: 52px; padding: 0 28px; border: 0; border-radius: 14px;
        background-color: #0f172a; color: white; font-family: "Segoe UI";
        font-size: 15px; font-weight: 800;
    }
    QPushButton#scanAction:hover { background-color: #1e293b; }
    QPushButton#scanAction:pressed { background-color: #020617; }
    QFrame#directionBadge {
        border: 0; border-radius: 16px; background-color: #0f172a;
    }
    QFrame#directionBadge[mode="exit"] { background-color: #334155; }
    QLabel#directionIcon { color: white; font-size: 26px; font-weight: 900; }
    QLabel#directionText { color: white; font-size: 14px; font-weight: 800; }
)";
}

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent),
      m_configuration(ValidatorConfiguration::fromEnvironment())
{
    configureWindow();

    auto *centralWidget = new QWidget(this);
    auto *layout = new QVBoxLayout(centralWidget);
    layout->setContentsMargins(30, 24, 30, 20);
    layout->setSpacing(18);
    layout->addWidget(createHeader());
    layout->addWidget(createTurnstilePanel(), 1);
    layout->addWidget(createFooter());

    setCentralWidget(centralWidget);

    m_validationClient = new ValidatorMqttClient(m_configuration, this);
    connect(m_validationClient, &ValidatorMqttClient::connectionStateChanged,
            this, [this](bool connected) {
        m_connected = connected;
        m_connectionState->setProperty("configurationValid", connected);
        m_connectionState->setText(connected ? tr("Preparada") : tr("Sin conexión"));
        updateQrInputState();
        m_connectionState->style()->unpolish(m_connectionState);
        m_connectionState->style()->polish(m_connectionState);
    });
    connect(m_validationClient, &ValidatorMqttClient::validationCompleted,
            this, &MainWindow::showValidationResult);
    connect(m_validationClient, &ValidatorMqttClient::validationFailed,
            this, [this](const QString &reason) {
        const QString detail = reason == QStringLiteral("MQTT_CREDENTIALS_MISSING")
            ? tr("Falta configurar la contraseña MQTT de la validadora")
            : tr("No se ha podido obtener una respuesta del centro de control");
        setValidationState(QStringLiteral("rejected"),
                           tr("Validación no disponible"), detail, false);
        if (reason == QStringLiteral("MQTT_CREDENTIALS_MISSING")) {
            m_connected = false;
        }
        updateQrInputState();
        restartCameraAfterResult();
    });
    if (m_configuration.valid) {
        m_cameraScanner->start();
    }
}

void MainWindow::configureWindow()
{
    setWindowTitle(m_configuration.isEntry()
        ? tr("Validadora de entrada · RMM") : tr("Validadora de salida · RMM"));
    setMinimumSize(760, 620);
    resize(980, 720);
    setStyleSheet(QString::fromUtf8(windowStyle));
}

QWidget *MainWindow::createHeader()
{
    auto *header = new QWidget(this);
    auto *layout = new QHBoxLayout(header);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(14);

    auto *brandMark = new QLabel(QStringLiteral("M"), header);
    brandMark->setObjectName(QStringLiteral("brandMark"));
    brandMark->setAlignment(Qt::AlignCenter);

    auto *identity = new QWidget(header);
    auto *identityLayout = new QVBoxLayout(identity);
    identityLayout->setContentsMargins(0, 0, 0, 0);
    identityLayout->setSpacing(2);
    auto *name = new QLabel(tr("RMM · Máquina validadora"), identity);
    name->setObjectName(QStringLiteral("applicationName"));
    auto *context = new QLabel(tr("Red de Metro de Macegocia"), identity);
    context->setObjectName(QStringLiteral("applicationContext"));
    identityLayout->addWidget(name);
    identityLayout->addWidget(context);

    m_connectionState = new QLabel(
        m_configuration.valid ? tr("Preparada") : tr("Configuración inválida"), header);
    m_connectionState->setObjectName(QStringLiteral("connectionState"));
    m_connectionState->setProperty("configurationValid", m_configuration.valid);
    m_connectionState->setAlignment(Qt::AlignCenter);
    m_connectionState->setAccessibleName(tr("Estado de conexión"));
    if (!m_configuration.valid) {
        m_connectionState->setToolTip(m_configuration.error);
    }

    layout->addWidget(brandMark);
    layout->addWidget(identity);
    layout->addStretch();
    layout->addWidget(m_connectionState);
    return header;
}

QWidget *MainWindow::createTurnstilePanel()
{
    auto *panel = new QFrame(this);
    panel->setObjectName(QStringLiteral("turnstilePanel"));
    auto *layout = new QHBoxLayout(panel);
    layout->setContentsMargins(20, 20, 20, 20);
    layout->setSpacing(18);
    layout->addWidget(createScannerPanel(), 3);
    layout->addWidget(createDevicePanel(), 2);
    return panel;
}

QWidget *MainWindow::createScannerPanel()
{
    auto *panel = new QFrame(this);
    panel->setObjectName(QStringLiteral("scannerPanel"));
    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(34, 30, 34, 30);
    layout->setSpacing(14);

    auto *eyebrow = new QLabel(
        m_configuration.isEntry() ? tr("ACCESO A LA RED") : tr("SALIDA DE LA RED"), panel);
    eyebrow->setObjectName(QStringLiteral("eyebrow"));
    auto *title = new QLabel(tr("Presenta tu billete"), panel);
    title->setObjectName(QStringLiteral("screenTitle"));
    auto *description = new QLabel(
        tr("Acerca el código QR al lector para comprobar el acceso."), panel);
    description->setObjectName(QStringLiteral("screenDescription"));
    description->setWordWrap(true);

    auto *scannerWell = new QFrame(panel);
    scannerWell->setObjectName(QStringLiteral("scannerWell"));
    scannerWell->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    auto *scannerLayout = new QVBoxLayout(scannerWell);
    scannerLayout->setContentsMargins(24, 24, 24, 24);
    scannerLayout->setAlignment(Qt::AlignCenter);
    auto *readerTitle = new QLabel(tr("LECTOR QR"), scannerWell);
    readerTitle->setObjectName(QStringLiteral("eyebrow"));
    readerTitle->setAlignment(Qt::AlignCenter);
    auto *readerHint = new QLabel(
        tr("Sitúa el código completo dentro del objetivo o utiliza la entrada manual."),
        scannerWell);
    readerHint->setObjectName(QStringLiteral("screenDescription"));
    readerHint->setAlignment(Qt::AlignCenter);
    readerHint->setWordWrap(true);
    m_cameraScanner = new QrCodeScannerWidget(scannerWell);
    m_cameraScanner->setSpanish(true);
    m_cameraScanner->setKioskMode(true);
    m_cameraScanner->setAccessibleName(tr("Cámara del lector de códigos QR"));

    auto *manualLabel = new QLabel(tr("ENTRADA MANUAL PARA LA SIMULACIÓN"), scannerWell);
    manualLabel->setObjectName(QStringLiteral("detailLabel"));
    m_qrInput = new QPlainTextEdit(scannerWell);
    m_qrInput->setObjectName(QStringLiteral("qrInput"));
    m_qrInput->setPlaceholderText(QStringLiteral("RMM:TICKET:1:<JWS>"));
    m_qrInput->setAccessibleName(tr("Contenido del código QR"));
    m_qrInput->setAccessibleDescription(
        tr("Campo que recibe el valor del lector físico o pegado para la simulación"));
    m_qrInput->setTabChangesFocus(true);
    m_qrCounter = new QLabel(scannerWell);
    m_qrCounter->setObjectName(QStringLiteral("qrCounter"));
    m_qrCounter->setAlignment(Qt::AlignRight);
    scannerLayout->addWidget(readerTitle);
    scannerLayout->addWidget(readerHint);
    scannerLayout->addWidget(m_cameraScanner, 1);
    scannerLayout->addWidget(manualLabel);
    scannerLayout->addWidget(m_qrInput);
    scannerLayout->addWidget(m_qrCounter);

    m_validationState = new QLabel(tr("Esperando un billete"), panel);
    m_validationState->setObjectName(QStringLiteral("validationState"));
    m_validationState->setAlignment(Qt::AlignCenter);
    m_validationDetail = new QLabel(
        tr("El resultado aparecerá después de comprobar el QR"), panel);
    m_validationDetail->setObjectName(QStringLiteral("validationDetail"));
    m_validationDetail->setAlignment(Qt::AlignCenter);
    m_validationDetail->setWordWrap(true);
    m_validationState->setAccessibleName(tr("Resultado de la validación"));
    m_scanButton = new QPushButton(tr("Validar billete"), panel);
    m_scanButton->setObjectName(QStringLiteral("scanAction"));
    m_scanButton->setCursor(Qt::PointingHandCursor);
    m_scanButton->setAccessibleDescription(
        tr("Envía el código QR introducido al centro de control"));
    m_scanButton->setEnabled(false);
    connect(m_scanButton, &QPushButton::clicked, this, &MainWindow::readQrCode);
    connect(m_qrInput, &QPlainTextEdit::textChanged, this, &MainWindow::updateQrInputState);
    connect(m_cameraScanner, &QrCodeScannerWidget::qrDetected, this,
            [this](const QString &qrValue) {
        m_qrInput->setPlainText(qrValue);
        readQrCode();
    });

    layout->addWidget(eyebrow);
    layout->addWidget(title);
    layout->addWidget(description);
    layout->addWidget(scannerWell, 1);
    layout->addWidget(m_validationState);
    layout->addWidget(m_validationDetail);
    layout->addWidget(m_scanButton);
    updateQrInputState();
    return panel;
}

QWidget *MainWindow::createDevicePanel()
{
    auto *panel = new QFrame(this);
    panel->setObjectName(QStringLiteral("devicePanel"));
    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(28, 28, 28, 28);
    layout->setSpacing(18);

    auto addDetail = [panel, layout](const QString &label, const QString &value,
                                    const QString &hint = QString()) {
        auto *container = new QWidget(panel);
        auto *detailLayout = new QVBoxLayout(container);
        detailLayout->setContentsMargins(0, 0, 0, 0);
        detailLayout->setSpacing(4);
        auto *caption = new QLabel(label, container);
        caption->setObjectName(QStringLiteral("detailLabel"));
        auto *content = new QLabel(value, container);
        content->setObjectName(QStringLiteral("detailValue"));
        detailLayout->addWidget(caption);
        detailLayout->addWidget(content);
        if (!hint.isEmpty()) {
            auto *help = new QLabel(hint, container);
            help->setObjectName(QStringLiteral("detailHint"));
            help->setWordWrap(true);
            detailLayout->addWidget(help);
        }
        layout->addWidget(container);
    };

    auto *eyebrow = new QLabel(tr("CONTEXTO DEL TORNIQUETE"), panel);
    eyebrow->setObjectName(QStringLiteral("eyebrow"));
    layout->addWidget(eyebrow);

    auto *direction = new QFrame(panel);
    direction->setObjectName(QStringLiteral("directionBadge"));
    direction->setProperty("mode", m_configuration.isEntry()
        ? QStringLiteral("entry") : QStringLiteral("exit"));
    auto *directionLayout = new QHBoxLayout(direction);
    directionLayout->setContentsMargins(16, 13, 16, 13);
    auto *directionIcon = new QLabel(
        m_configuration.isEntry() ? QStringLiteral("→") : QStringLiteral("←"), direction);
    directionIcon->setObjectName(QStringLiteral("directionIcon"));
    auto *directionText = new QLabel(
        m_configuration.isEntry() ? tr("VALIDACIÓN DE ENTRADA") : tr("VALIDACIÓN DE SALIDA"),
        direction);
    directionText->setObjectName(QStringLiteral("directionText"));
    directionLayout->addWidget(directionIcon);
    directionLayout->addWidget(directionText);
    directionLayout->addStretch();
    layout->addWidget(direction);

    addDetail(tr("ESTACIÓN"), m_configuration.stationName, m_configuration.stationCode);
    addDetail(tr("DISPOSITIVO"), m_configuration.deviceCode);
    addDetail(tr("SENTIDO DEL PASO"),
              m_configuration.isEntry() ? tr("Entrada a la red") : tr("Salida de la red"));

    layout->addStretch();
    m_gateState = new QLabel(tr("Torniquete cerrado"), panel);
    m_gateState->setObjectName(QStringLiteral("gateState"));
    m_gateState->setAlignment(Qt::AlignCenter);
    m_gateState->setAccessibleName(tr("Estado del torniquete"));
    layout->addWidget(m_gateState);
    return panel;
}

QWidget *MainWindow::createFooter()
{
    auto *footer = new QWidget(this);
    auto *layout = new QHBoxLayout(footer);
    layout->setContentsMargins(4, 0, 4, 0);
    auto *help = new QLabel(
        tr("Mantén el QR dentro del lector hasta recibir el resultado."), footer);
    help->setObjectName(QStringLiteral("footerText"));
    auto *network = new QLabel(tr("Red de Metro de Macegocia · RMM"), footer);
    network->setObjectName(QStringLiteral("footerText"));
    layout->addWidget(help);
    layout->addStretch();
    layout->addWidget(network);
    return footer;
}

void MainWindow::readQrCode()
{
    const QString qrValue = m_qrInput->toPlainText().trimmed();
    if (qrValue.isEmpty() || qrValue.size() > maximumQrLength
            || !m_configuration.valid || !m_connected
            || m_validationClient->hasPendingValidation()) {
        return;
    }

    m_lastQrValue = qrValue;
    m_cameraScanner->stop();
    m_validationState->setProperty("state", QStringLiteral("read"));
    m_validationDetail->setText(tr("Comprobando el billete con el centro de control"));
    m_gateState->setProperty("state", QString());
    m_gateState->setText(tr("Torniquete cerrado"));
    m_scanButton->setEnabled(false);
    m_validationState->setText(tr("Código QR leído · Pendiente de verificar"));
    m_validationState->style()->unpolish(m_validationState);
    m_validationState->style()->polish(m_validationState);
    m_gateState->style()->unpolish(m_gateState);
    m_gateState->style()->polish(m_gateState);
    m_validationState->setFocus(Qt::OtherFocusReason);
    m_validationClient->submit(m_lastQrValue);
}

void MainWindow::updateQrInputState()
{
    if (m_qrInput == nullptr || m_qrCounter == nullptr || m_scanButton == nullptr) {
        return;
    }
    const qsizetype length = m_qrInput->toPlainText().trimmed().size();
    const bool validInput = length > 0 && length <= maximumQrLength;
    const bool pending = m_validationClient != nullptr
        && m_validationClient->hasPendingValidation();
    m_scanButton->setEnabled(validInput && m_configuration.valid && m_connected && !pending);
    m_qrCounter->setText(tr("%1 de %2 caracteres").arg(length).arg(maximumQrLength));
    m_qrCounter->setProperty("invalid", length > maximumQrLength);
    m_qrCounter->style()->unpolish(m_qrCounter);
    m_qrCounter->style()->polish(m_qrCounter);
}

void MainWindow::showValidationResult(const ValidationResult &result)
{
    setValidationState(
        result.isAccepted() ? QStringLiteral("accepted") : QStringLiteral("rejected"),
        result.title(), result.detail(), result.isAccepted());
    restartCameraAfterResult();
}

void MainWindow::restartCameraAfterResult()
{
    QTimer::singleShot(3000, this, [this] {
        if (!m_configuration.valid || (m_validationClient != nullptr
                && m_validationClient->hasPendingValidation())) {
            return;
        }
        m_qrInput->clear();
        m_lastQrValue.clear();
        m_cameraScanner->start();
    });
}

void MainWindow::setValidationState(const QString &state, const QString &title,
                                    const QString &detail, bool gateOpen)
{
    m_validationState->setProperty("state", state);
    m_validationState->setText(title);
    m_validationDetail->setText(detail);
    m_gateState->setProperty(
        "state", gateOpen ? QStringLiteral("open") : QStringLiteral("closed-rejected"));
    m_gateState->setText(gateOpen ? tr("Paso autorizado") : tr("Torniquete cerrado"));
    updateQrInputState();

    m_validationState->style()->unpolish(m_validationState);
    m_validationState->style()->polish(m_validationState);
    m_gateState->style()->unpolish(m_gateState);
    m_gateState->style()->polish(m_gateState);
    m_validationState->setFocus(Qt::OtherFocusReason);
}
