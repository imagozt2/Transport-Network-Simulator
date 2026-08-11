#include "mainwindow.h"
#include "qrreaderdialog.h"

#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QSizePolicy>
#include <QStyle>
#include <QVBoxLayout>
#include <QWidget>

namespace {
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
    QLabel#scannerMark {
        min-width: 150px; min-height: 150px; max-width: 150px; max-height: 150px;
        border: 4px solid #2294f2; border-radius: 24px; background-color: white;
        color: #0f172a; font-size: 34px; font-weight: 900;
    }
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
    auto *scannerMark = new QLabel(QStringLiteral("QR"), scannerWell);
    scannerMark->setObjectName(QStringLiteral("scannerMark"));
    scannerMark->setAlignment(Qt::AlignCenter);
    scannerMark->setAccessibleName(tr("Zona de lectura del código QR"));
    scannerLayout->addWidget(scannerMark);

    m_validationState = new QLabel(tr("Esperando un billete"), panel);
    m_validationState->setObjectName(QStringLiteral("validationState"));
    m_validationState->setAlignment(Qt::AlignCenter);
    m_validationState->setAccessibleName(tr("Resultado de la validación"));
    m_scanButton = new QPushButton(tr("Escanear código QR"), panel);
    m_scanButton->setObjectName(QStringLiteral("scanAction"));
    m_scanButton->setCursor(Qt::PointingHandCursor);
    m_scanButton->setAccessibleDescription(
        tr("Abre el lector de códigos QR del torniquete"));
    m_scanButton->setEnabled(m_configuration.valid);
    connect(m_scanButton, &QPushButton::clicked, this, &MainWindow::readQrCode);

    layout->addWidget(eyebrow);
    layout->addWidget(title);
    layout->addWidget(description);
    layout->addWidget(scannerWell, 1);
    layout->addWidget(m_validationState);
    layout->addWidget(m_scanButton);
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
    QrReaderDialog reader(this);
    if (reader.exec() != QDialog::Accepted) {
        return;
    }

    m_lastQrValue = reader.qrValue();
    m_validationState->setProperty("state", QStringLiteral("read"));
    m_validationState->setText(tr("Código QR leído · Pendiente de verificar"));
    m_validationState->style()->unpolish(m_validationState);
    m_validationState->style()->polish(m_validationState);
    m_validationState->setFocus(Qt::OtherFocusReason);
}
