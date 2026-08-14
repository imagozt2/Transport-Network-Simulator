#include "mainwindow.h"
#include "ticketmachineconfiguration.h"
#include "qrcodescannerwidget.h"

#include <algorithm>
#include <QFrame>
#include <QComboBox>
#include <QCompleter>
#include <QDialog>
#include <QDoubleSpinBox>
#include <QGridLayout>
#include <QHBoxLayout>
#include <QLocale>
#include <QLabel>
#include <QLineEdit>
#include <QMessageBox>
#include <QPushButton>
#include <QProcessEnvironment>
#include <QPixmap>
#include <QSizePolicy>
#include <QSettings>
#include <QStackedWidget>
#include <QSpinBox>
#include <QTimer>
#include <QtMath>
#include <QVBoxLayout>
#include <QWidget>

namespace {
constexpr auto windowStyle = R"(
    QMainWindow {
        background-color: #eef3f8;
    }
    QWidget {
        font-family: "Segoe UI";
        color: #0f172a;
    }
    QFrame#header {
        background-color: #ffffff;
        border: 1px solid #dbe3ec;
        border-radius: 18px;
    }
    QLabel#brandMark {
        min-width: 58px;
        min-height: 58px;
        max-width: 58px;
        max-height: 58px;
        border-radius: 16px;
        background-color: #2294f2;
        color: white;
        font-size: 31px;
        font-weight: 900;
    }
    QLabel#applicationName {
        font-size: 23px;
        font-weight: 800;
    }
    QLabel#applicationContext, QLabel#screenHint, QLabel#footerContext {
        color: #64748b;
        font-size: 14px;
    }
    QLabel#connectionState {
        padding: 9px 15px;
        border-radius: 16px;
        background-color: #e2e8f0;
        color: #334155;
        font-size: 13px;
        font-weight: 700;
    }
    QPushButton#languageFlag {
        min-width: 52px;
        min-height: 42px;
        padding: 2px 6px;
        border: 2px solid transparent;
        border-radius: 12px;
        background-color: transparent;
        font-size: 26px;
    }
    QPushButton#languageFlag:hover, QPushButton#languageFlag:focus {
        border-color: #93c5fd;
        background-color: #eff6ff;
    }
    QLabel#scannerTitle {
        font-size: 25px;
        font-weight: 900;
    }
    QLabel#scannerInstructions {
        color: #64748b;
        font-size: 15px;
    }
    QPushButton#scannerCancel {
        min-width: 120px;
        min-height: 44px;
        border: 0;
        border-radius: 12px;
        background-color: #0f172a;
        color: white;
        font-weight: 800;
    }
    QFrame#cameraViewport {
        background-color: #020617;
        border: 2px solid #cbd5e1;
        border-radius: 20px;
    }
    QLabel#scannerStatus {
        min-height: 24px;
        color: #334155;
        font-size: 14px;
        font-weight: 700;
    }
    QPushButton#languageFlag:checked {
        border-color: #2294f2;
        background-color: #eaf5fe;
    }
    QFrame#mainPanel {
        background-color: #ffffff;
        border: 1px solid #dbe3ec;
        border-radius: 22px;
    }
    QFrame#purchaseFlowPanel {
        background-color: #ffffff;
        border: 1px solid #dbe3ec;
        border-radius: 22px;
    }
    QLabel#eyebrow {
        color: #0875c1;
        font-size: 14px;
        font-weight: 800;
    }
    QLabel#screenTitle {
        font-size: 36px;
        font-weight: 900;
    }
    QPushButton#primaryAction, QPushButton#secondaryAction {
        min-height: 172px;
        padding: 24px;
        border-radius: 20px;
        text-align: left;
        font-size: 22px;
        font-weight: 800;
    }
    QPushButton#primaryAction {
        border: 2px solid #0875c1;
        background-color: #2294f2;
        color: #ffffff;
    }
    QPushButton#primaryAction:hover, QPushButton#primaryAction:focus {
        background-color: #0875c1;
        border-color: #005b9f;
    }
    QPushButton#primaryAction:pressed {
        background-color: #00518e;
    }
    QPushButton#secondaryAction {
        border: 2px solid #cbd5e1;
        background-color: #f8fafc;
        color: #0f172a;
    }
    QPushButton#secondaryAction:hover, QPushButton#secondaryAction:focus {
        border-color: #2294f2;
        background-color: #eaf5fe;
    }
    QPushButton#secondaryAction:pressed {
        background-color: #dceefe;
    }
    QLabel#dialogTitle {
        font-size: 25px;
        font-weight: 800;
    }
    QPushButton#backAction, QPushButton#retryAction {
        min-height: 48px;
        padding: 8px 18px;
        border: 1px solid #cbd5e1;
        border-radius: 14px;
        background-color: #ffffff;
        font-size: 15px;
        font-weight: 700;
    }
    QPushButton#retryAction {
        background-color: #0f172a;
        color: #ffffff;
    }
    QFrame#productCard {
        background-color: #f8fafc;
        border: 1px solid #dbe3ec;
        border-radius: 16px;
    }
    QLabel#productName {
        font-size: 20px;
        font-weight: 800;
    }
    QLabel#productCode, QLabel#productRules {
        color: #64748b;
        font-size: 13px;
    }
    QLabel#productTariff {
        color: #0875c1;
        font-size: 18px;
        font-weight: 800;
    }
    QPushButton#selectProduct, QPushButton#confirmAction {
        min-height: 48px;
        padding: 8px 20px;
        border: 0;
        border-radius: 14px;
        background-color: #0f172a;
        color: #ffffff;
        font-size: 15px;
        font-weight: 800;
    }
    QPushButton#selectProduct:hover, QPushButton#confirmAction:hover,
    QPushButton#selectProduct:focus, QPushButton#confirmAction:focus {
        background-color: #2294f2;
    }
    QPushButton#selectProduct:disabled {
        background-color: #cbd5e1;
        color: #64748b;
    }
    QPushButton#stepAction {
        min-width: 58px;
        min-height: 58px;
        max-width: 58px;
        max-height: 58px;
        border: 0;
        border-radius: 16px;
        background-color: #e2e8f0;
        color: #0f172a;
        font-size: 28px;
        font-weight: 900;
    }
    QPushButton#stepAction:hover, QPushButton#stepAction:focus {
        background-color: #bfdbfe;
        color: #075985;
    }
    QPushButton#stepAction:disabled {
        background-color: #f1f5f9;
        color: #94a3b8;
    }
    QPushButton#swapStationsAction {
        min-height: 44px;
        padding: 6px 16px;
        border: 1px solid #cbd5e1;
        border-radius: 13px;
        background-color: #f8fafc;
        font-size: 14px;
        font-weight: 800;
    }
    QPushButton#swapStationsAction:hover, QPushButton#swapStationsAction:focus {
        border-color: #2294f2;
        background-color: #eaf5fe;
    }
    QDialog#configurationDialog {
        background-color: #ffffff;
    }
    QLabel#fieldLabel {
        color: #475569;
        font-size: 14px;
        font-weight: 700;
    }
    QLabel#paymentAmount {
        color: #0875c1;
        font-size: 34px;
        font-weight: 900;
    }
    QLabel#paymentApproved {
        padding: 14px;
        border-radius: 12px;
        background-color: #dcfce7;
        color: #166534;
        font-size: 16px;
        font-weight: 800;
    }
    QComboBox, QSpinBox, QDoubleSpinBox {
        min-height: 54px;
        padding: 0 14px;
        border: 2px solid #cbd5e1;
        border-radius: 14px;
        background-color: #f8fafc;
        font-size: 17px;
        font-weight: 700;
    }
    QComboBox:focus, QSpinBox:focus, QDoubleSpinBox:focus {
        border-color: #2294f2;
    }
    QSpinBox#quantityValue {
        min-width: 150px;
        font-size: 26px;
        font-weight: 900;
    }
)";
}

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
{
    configureWindow();

    auto *centralWidget = new QWidget(this);
    m_catalogClient = new TicketCatalogClient(this);
    m_stationClient = new StationCatalogClient(this);
    m_journeyClient = new JourneyQuoteClient(this);
    m_issuanceClient = new TicketIssuanceRequestClient(this);
    m_rechargeLookupClient = new TicketRechargeLookupClient(this);
    const auto machineConfiguration = TicketMachineConfiguration::fromEnvironment(
        QProcessEnvironment::systemEnvironment());
    m_machineStationCode = machineConfiguration.stationCode;
    auto *layout = new QVBoxLayout(centralWidget);
    layout->setContentsMargins(28, 24, 28, 22);
    layout->setSpacing(18);
    layout->addWidget(createHeader());
    m_contentStack = new QStackedWidget(centralWidget);
    m_homePanel = createMainPanel();
    m_catalogPanel = createCatalogPanel();
    m_contentStack->addWidget(m_homePanel);
    m_contentStack->addWidget(m_catalogPanel);
    m_rechargeScanner = new QrCodeScannerWidget(m_contentStack);
    m_contentStack->addWidget(m_rechargeScanner);
    layout->addWidget(m_contentStack, 1);
    layout->addWidget(createFooter());

    setCentralWidget(centralWidget);
    const QSettings settings;
    m_language = settings.value(QStringLiteral("interface/language"), QStringLiteral("es"))
                         .toString() == QStringLiteral("en")
        ? UiLanguage::English
        : UiLanguage::Spanish;
    connect(this, &MainWindow::purchaseRequested, this, &MainWindow::showCatalog);
    connect(this, &MainWindow::rechargeRequested, this, &MainWindow::showRechargeScanner);
    connect(m_rechargeScanner, &QrCodeScannerWidget::cancelled, this, &MainWindow::showHome);
    connect(m_rechargeScanner, &QrCodeScannerWidget::qrDetected, this,
            [this](const QString &qrValue) {
        emit rechargeQrScanned(qrValue);
        showRechargeLookupProgress();
        m_rechargeLookupClient->lookup(qrValue);
    });
    connect(m_rechargeLookupClient, &TicketRechargeLookupClient::loaded, this,
            [this](const RechargeableTicket &ticket) {
        m_pendingRecharge = ticket;
        showRechargeOptions(ticket);
        if (ticket.requiresOriginDestination && m_stations.isEmpty()) {
            m_stationLoadFailed = false;
            m_stationClient->load();
        }
    });
    connect(m_rechargeLookupClient, &TicketRechargeLookupClient::failed, this,
            [this](const QString &) {
        m_pendingRecharge.reset();
        const bool spanish = m_language == UiLanguage::Spanish;
        QMessageBox::warning(
            this,
            spanish ? QStringLiteral("Billete no disponible")
                    : QStringLiteral("Ticket unavailable"),
            spanish
                ? QStringLiteral("No se ha podido identificar un billete recargable con ese código QR.")
                : QStringLiteral("No rechargeable ticket could be identified from that QR code."));
        showRechargeScanner();
    });
    connect(this, &MainWindow::configurationSelected, this, &MainWindow::preparePayment);
    connect(this, &MainWindow::paymentApproved, this,
            [this](const QString &productCode, const QString &originStationCode,
                   const QString &destinationStationCode, int quantity,
                   double rechargeAmount, double paidAmount) {
        m_connectionState->setText(
            m_language == UiLanguage::Spanish ? QStringLiteral("Solicitando emisión…")
                                              : QStringLiteral("Requesting issuance…"));
        m_issuanceClient->submit(TicketIssuanceRequest{
            .productCode = productCode,
            .originStationCode = originStationCode,
            .destinationStationCode = destinationStationCode,
            .quantity = quantity,
            .rechargeAmount = rechargeAmount,
            .paidAmount = paidAmount,
        });
    });
    connect(m_issuanceClient, &TicketIssuanceRequestClient::submitted, this,
            [this](const QString &reference) {
        m_connectionState->setText(
            m_language == UiLanguage::Spanish ? QStringLiteral("Solicitud enviada")
                                              : QStringLiteral("Request sent"));
        m_connectionState->setToolTip(reference);
    });
    connect(m_issuanceClient, &TicketIssuanceRequestClient::connectionStateChanged, this,
            [this](bool connected, int retryDelaySeconds) {
        if (connected) {
            m_connectionState->setText(
                m_language == UiLanguage::Spanish ? QStringLiteral("Servicios conectados")
                                                  : QStringLiteral("Services connected"));
            m_connectionState->setToolTip(QString());
            return;
        }
        m_connectionState->setText(
            m_language == UiLanguage::Spanish ? QStringLiteral("Reconectando serviciosâ€¦")
                                              : QStringLiteral("Reconnecting servicesâ€¦"));
        m_connectionState->setToolTip(
            retryDelaySeconds > 0
                ? (m_language == UiLanguage::Spanish
                    ? QStringLiteral("Nuevo intento en %1 s").arg(retryDelaySeconds)
                    : QStringLiteral("Retrying in %1 s").arg(retryDelaySeconds))
                : QString());
    });
    connect(m_issuanceClient, &TicketIssuanceRequestClient::ticketIssued, this,
            [this](const QString &ticketCode, const QByteArray &qrPng,
                   const QString &qrValue, const QString &linkingCode,
                   const QString &purchaseReference) {
        showIssuedTicketWindow(ticketCode, qrPng, qrValue, linkingCode, purchaseReference);
    });
    connect(m_issuanceClient, &TicketIssuanceRequestClient::compensatoryTicketIssued, this,
            [this](const QString &commandId, const QString &issuanceCode,
                   const QString &ticketCode, const QByteArray &qrPng,
                   const QString &, const QString &linkingCode) {
        QPixmap qr;
        if (!qr.loadFromData(qrPng, "PNG")) return;
        const bool spanish = m_language == UiLanguage::Spanish;
        QDialog dialog(this);
        dialog.setObjectName(QStringLiteral("configurationDialog"));
        dialog.setModal(true);
        dialog.setMinimumWidth(560);
        dialog.setWindowFlag(Qt::WindowCloseButtonHint, false);
        dialog.setWindowTitle(spanish ? QStringLiteral("Emisión compensatoria")
                                      : QStringLiteral("Compensatory issuance"));
        auto *layout = new QVBoxLayout(&dialog);
        layout->setContentsMargins(30, 26, 30, 26);
        layout->setSpacing(12);
        auto *title = new QLabel(
            spanish ? QStringLiteral("Billete compensatorio preparado")
                    : QStringLiteral("Compensatory ticket ready"), &dialog);
        title->setObjectName(QStringLiteral("dialogTitle"));
        auto *hint = new QLabel(
            spanish
                ? QStringLiteral("El centro de control ha autorizado esta emisión gratuita. Presenta el soporte una sola vez.")
                : QStringLiteral("The control centre authorised this free issuance. Present the support only once."),
            &dialog);
        hint->setObjectName(QStringLiteral("screenHint"));
        hint->setWordWrap(true);
        auto *qrLabel = new QLabel(&dialog);
        qrLabel->setAlignment(Qt::AlignCenter);
        qrLabel->setPixmap(qr);
        qrLabel->setAccessibleName(spanish ? QStringLiteral("QR del billete compensatorio")
                                          : QStringLiteral("Compensatory ticket QR"));
        auto *code = new QLabel(ticketCode, &dialog);
        code->setObjectName(QStringLiteral("productCode"));
        code->setAlignment(Qt::AlignCenter);
        auto *link = new QLabel(
            spanish ? QStringLiteral("Código de vinculación: %1").arg(linkingCode)
                    : QStringLiteral("Linking code: %1").arg(linkingCode), &dialog);
        link->setObjectName(QStringLiteral("productName"));
        link->setAlignment(Qt::AlignCenter);
        auto *finish = new QPushButton(spanish ? QStringLiteral("Confirmar entrega")
                                              : QStringLiteral("Confirm delivery"), &dialog);
        finish->setObjectName(QStringLiteral("confirmAction"));
        connect(finish, &QPushButton::clicked, &dialog, [&, this] {
            m_issuanceClient->completeCompensatoryIssuance(commandId, issuanceCode);
            dialog.accept();
        });
        layout->addWidget(title);
        layout->addWidget(hint);
        layout->addWidget(qrLabel, 0, Qt::AlignCenter);
        layout->addWidget(code);
        layout->addWidget(link);
        layout->addWidget(finish, 0, Qt::AlignRight);
        m_connectionState->setText(spanish ? QStringLiteral("Orden compensatoria recibida")
                                          : QStringLiteral("Compensatory order received"));
        const int result = dialog.exec();
        m_connectionState->setText(
            result == QDialog::Accepted
                ? (spanish ? QStringLiteral("Entrega confirmada")
                           : QStringLiteral("Delivery confirmed"))
                : (spanish ? QStringLiteral("Entrega compensatoria pendiente")
                           : QStringLiteral("Compensatory delivery pending")));
    });
    connect(m_issuanceClient, &TicketIssuanceRequestClient::failed, this,
            [this](const QString &reason) {
        m_connectionState->setText(
            m_language == UiLanguage::Spanish ? QStringLiteral("Conexión no disponible")
                                              : QStringLiteral("Connection unavailable"));
        const bool missingCredentials = reason == QStringLiteral("MQTT_CREDENTIALS_MISSING");
        QMessageBox::warning(
            this,
            m_language == UiLanguage::Spanish ? QStringLiteral("No se pudo solicitar la emisión")
                                              : QStringLiteral("Issuance could not be requested"),
            m_language == UiLanguage::Spanish
                ? (missingCredentials
                    ? QStringLiteral("Falta configurar la credencial MQTT local de esta máquina.")
                    : QStringLiteral("No se ha podido contactar con el backend. No se ha emitido ningún billete."))
                : (missingCredentials
                    ? QStringLiteral("This machine's local MQTT credential is not configured.")
                    : QStringLiteral("The backend could not be reached. No ticket has been issued.")));
        if (m_pendingPayment) {
            m_pendingPayment.reset();
            leavePurchaseFlow(m_catalogPanel);
        }
    });
    connect(m_catalogClient, &TicketCatalogClient::loaded, this, [this](const auto &products) {
        m_products = products;
        renderCatalog();
    });
    connect(m_catalogClient, &TicketCatalogClient::failed, this, [this] {
        m_catalogState->setText(
            m_language == UiLanguage::Spanish
                ? QStringLiteral("No se han podido consultar los títulos y tarifas.")
                : QStringLiteral("Ticket products and fares could not be loaded."));
        m_catalogState->show();
        m_catalogRetryButton->show();
    });
    connect(m_stationClient, &StationCatalogClient::loaded, this, [this](const auto &stations) {
        m_stations = stations;
        m_stationLoadFailed = false;
        if (!m_products.isEmpty()) {
            renderCatalog();
        }
        if (m_pendingRecharge && m_pendingRecharge->requiresOriginDestination) {
            showRechargeOptions(*m_pendingRecharge);
        }
    });
    connect(m_stationClient, &StationCatalogClient::failed, this, [this] {
        m_stations.clear();
        m_stationLoadFailed = true;
        if (!m_products.isEmpty()) {
            renderCatalog();
        }
        if (m_pendingRecharge && m_pendingRecharge->requiresOriginDestination) {
            showRechargeOptions(*m_pendingRecharge);
        }
    });
    connect(m_journeyClient, &JourneyQuoteClient::loaded, this, [this](int stationCount) {
        m_catalogPanel->setEnabled(true);
        m_catalogState->hide();
        if (!m_pendingPayment || !m_pendingPayment->product.basePrice
            || !m_pendingPayment->product.pricePerStation) {
            return;
        }
        const double amount = *m_pendingPayment->product.basePrice
            + (*m_pendingPayment->product.pricePerStation * stationCount);
        showPaymentScreen(m_pendingPayment->product, amount);
    });
    connect(m_journeyClient, &JourneyQuoteClient::failed, this, [this] {
        m_catalogPanel->setEnabled(true);
        m_catalogState->hide();
        QMessageBox::warning(
            this,
            m_language == UiLanguage::Spanish ? QStringLiteral("Trayecto no disponible")
                                              : QStringLiteral("Journey unavailable"),
            m_language == UiLanguage::Spanish
                ? QStringLiteral("No se ha podido calcular el trayecto y su precio. Inténtalo de nuevo.")
                : QStringLiteral("The journey and its fare could not be calculated. Please try again."));
        m_pendingPayment.reset();
    });
    retranslateUi();
}

void MainWindow::configureWindow()
{
    setObjectName(QStringLiteral("ticketVendingMainWindow"));
    setMinimumSize(900, 620);
    resize(1180, 760);
    setStyleSheet(QString::fromUtf8(windowStyle));
}

QWidget *MainWindow::createHeader()
{
    auto *header = new QFrame(this);
    header->setObjectName(QStringLiteral("header"));
    auto *layout = new QHBoxLayout(header);
    layout->setContentsMargins(20, 16, 20, 16);
    layout->setSpacing(16);

    m_brandMark = new QLabel(QStringLiteral("M"), header);
    m_brandMark->setObjectName(QStringLiteral("brandMark"));
    m_brandMark->setAlignment(Qt::AlignCenter);

    auto *identity = new QWidget(header);
    auto *identityLayout = new QVBoxLayout(identity);
    identityLayout->setContentsMargins(0, 0, 0, 0);
    identityLayout->setSpacing(2);

    m_applicationName = new QLabel(identity);
    m_applicationName->setObjectName(QStringLiteral("applicationName"));
    m_applicationContext = new QLabel(identity);
    m_applicationContext->setObjectName(QStringLiteral("applicationContext"));
    identityLayout->addWidget(m_applicationName);
    identityLayout->addWidget(m_applicationContext);

    m_spanishLanguageButton = new QPushButton(QStringLiteral("🇪🇸"), header);
    m_spanishLanguageButton->setObjectName(QStringLiteral("languageFlag"));
    m_spanishLanguageButton->setCheckable(true);
    m_spanishLanguageButton->setAutoExclusive(true);
    m_spanishLanguageButton->setCursor(Qt::PointingHandCursor);

    m_englishLanguageButton = new QPushButton(QStringLiteral("🇬🇧"), header);
    m_englishLanguageButton->setObjectName(QStringLiteral("languageFlag"));
    m_englishLanguageButton->setCheckable(true);
    m_englishLanguageButton->setAutoExclusive(true);
    m_englishLanguageButton->setCursor(Qt::PointingHandCursor);

    connect(m_spanishLanguageButton, &QPushButton::clicked, this, [this] {
        setLanguage(UiLanguage::Spanish);
    });
    connect(m_englishLanguageButton, &QPushButton::clicked, this, [this] {
        setLanguage(UiLanguage::English);
    });

    layout->addWidget(m_brandMark);
    layout->addWidget(identity);
    layout->addStretch();
    layout->addWidget(m_spanishLanguageButton);
    layout->addWidget(m_englishLanguageButton);

    return header;
}

QWidget *MainWindow::createMainPanel()
{
    auto *panel = new QFrame(this);
    panel->setObjectName(QStringLiteral("mainPanel"));

    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(42, 34, 42, 38);
    layout->setSpacing(14);

    m_eyebrow = new QLabel(panel);
    m_eyebrow->setObjectName(QStringLiteral("eyebrow"));
    m_screenTitle = new QLabel(panel);
    m_screenTitle->setObjectName(QStringLiteral("screenTitle"));
    m_screenHint = new QLabel(panel);
    m_screenHint->setObjectName(QStringLiteral("screenHint"));
    m_screenHint->setWordWrap(true);

    auto *actions = new QWidget(panel);
    auto *actionsLayout = new QHBoxLayout(actions);
    actionsLayout->setContentsMargins(0, 16, 0, 0);
    actionsLayout->setSpacing(20);

    m_purchaseButton = new QPushButton(actions);
    m_purchaseButton->setObjectName(QStringLiteral("primaryAction"));
    m_purchaseButton->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    m_purchaseButton->setCursor(Qt::PointingHandCursor);

    m_rechargeButton = new QPushButton(actions);
    m_rechargeButton->setObjectName(QStringLiteral("secondaryAction"));
    m_rechargeButton->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    m_rechargeButton->setCursor(Qt::PointingHandCursor);

    connect(m_purchaseButton, &QPushButton::clicked, this, &MainWindow::purchaseRequested);
    connect(m_rechargeButton, &QPushButton::clicked, this, &MainWindow::rechargeRequested);

    actionsLayout->addWidget(m_purchaseButton, 1);
    actionsLayout->addWidget(m_rechargeButton, 1);

    layout->addWidget(m_eyebrow);
    layout->addWidget(m_screenTitle);
    layout->addWidget(m_screenHint);
    layout->addWidget(actions, 1);

    return panel;
}

QWidget *MainWindow::createFooter()
{
    auto *footer = new QWidget(this);
    auto *layout = new QHBoxLayout(footer);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(10);

    m_footerContext = new QLabel(footer);
    m_footerContext->setObjectName(QStringLiteral("footerContext"));

    m_connectionState = new QLabel(footer);
    m_connectionState->setObjectName(QStringLiteral("connectionState"));
    m_connectionState->setAlignment(Qt::AlignCenter);

    layout->addWidget(m_footerContext);
    layout->addStretch();
    layout->addWidget(m_connectionState);

    return footer;
}

QWidget *MainWindow::createCatalogPanel()
{
    auto *panel = new QFrame(this);
    panel->setObjectName(QStringLiteral("mainPanel"));
    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(34, 28, 34, 30);
    layout->setSpacing(14);

    auto *heading = new QWidget(panel);
    auto *headingLayout = new QHBoxLayout(heading);
    headingLayout->setContentsMargins(0, 0, 0, 0);
    m_catalogBackButton = new QPushButton(heading);
    m_catalogBackButton->setObjectName(QStringLiteral("backAction"));
    m_catalogBackButton->setCursor(Qt::PointingHandCursor);
    auto *titles = new QWidget(heading);
    auto *titlesLayout = new QVBoxLayout(titles);
    titlesLayout->setContentsMargins(0, 0, 0, 0);
    m_catalogTitle = new QLabel(titles);
    m_catalogTitle->setObjectName(QStringLiteral("screenTitle"));
    m_catalogHint = new QLabel(titles);
    m_catalogHint->setObjectName(QStringLiteral("screenHint"));
    m_catalogHint->setWordWrap(true);
    titlesLayout->addWidget(m_catalogTitle);
    titlesLayout->addWidget(m_catalogHint);
    headingLayout->addWidget(m_catalogBackButton, 0, Qt::AlignTop);
    headingLayout->addWidget(titles, 1);

    m_catalogState = new QLabel(panel);
    m_catalogState->setObjectName(QStringLiteral("screenHint"));
    m_catalogState->setAlignment(Qt::AlignCenter);
    m_catalogRetryButton = new QPushButton(panel);
    m_catalogRetryButton->setObjectName(QStringLiteral("retryAction"));
    m_catalogRetryButton->setCursor(Qt::PointingHandCursor);
    m_catalogRetryButton->hide();

    auto *catalogContent = new QWidget(panel);
    m_catalogGrid = new QGridLayout(catalogContent);
    m_catalogGrid->setContentsMargins(0, 0, 0, 0);
    m_catalogGrid->setHorizontalSpacing(14);
    m_catalogGrid->setVerticalSpacing(14);
    m_catalogGrid->setColumnStretch(0, 1);
    m_catalogGrid->setColumnStretch(1, 1);
    m_catalogGrid->setRowStretch(0, 1);
    m_catalogGrid->setRowStretch(1, 1);

    connect(m_catalogBackButton, &QPushButton::clicked, this, &MainWindow::showHome);
    connect(m_catalogRetryButton, &QPushButton::clicked, m_catalogClient, [this] {
        m_catalogRetryButton->hide();
        m_catalogState->show();
        m_catalogState->setText(
            m_language == UiLanguage::Spanish ? QStringLiteral("Consultando tarifas…")
                                              : QStringLiteral("Loading fares…"));
        m_catalogClient->load();
    });

    layout->addWidget(heading);
    layout->addWidget(m_catalogState);
    layout->addWidget(m_catalogRetryButton, 0, Qt::AlignCenter);
    layout->addWidget(catalogContent, 1);
    return panel;
}

void MainWindow::setLanguage(UiLanguage language)
{
    m_spanishLanguageButton->setChecked(language == UiLanguage::Spanish);
    m_englishLanguageButton->setChecked(language == UiLanguage::English);
    if (m_language == language) return;
    m_language = language;
    QSettings settings;
    settings.setValue(
        QStringLiteral("interface/language"),
        language == UiLanguage::Spanish ? QStringLiteral("es") : QStringLiteral("en"));
    retranslateUi();
}

void MainWindow::showCatalog()
{
    leavePurchaseFlow(m_catalogPanel);
    m_products.clear();
    renderCatalog();
    m_catalogState->setText(
        m_language == UiLanguage::Spanish ? QStringLiteral("Consultando tarifas…")
                                          : QStringLiteral("Loading fares…"));
    m_catalogState->show();
    m_catalogRetryButton->hide();
    m_catalogClient->load();
    m_stationLoadFailed = false;
    m_stationClient->load();
}

void MainWindow::showHome()
{
    if (m_rechargeScanner) {
        m_rechargeScanner->stop();
    }
    m_pendingPayment.reset();
    m_pendingRecharge.reset();
    leavePurchaseFlow(m_homePanel);
}

void MainWindow::showRechargeScanner()
{
    if (m_purchaseFlowPanel) {
        leavePurchaseFlow(m_rechargeScanner);
    } else {
        m_contentStack->setCurrentWidget(m_rechargeScanner);
    }
    m_rechargeScanner->setSpanish(m_language == UiLanguage::Spanish);
    m_rechargeScanner->start();
}

void MainWindow::showRechargeLookupProgress()
{
    const bool spanish = m_language == UiLanguage::Spanish;
    auto *panel = new QFrame(m_contentStack);
    panel->setObjectName(QStringLiteral("purchaseFlowPanel"));
    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(36, 30, 36, 30);
    auto *title = new QLabel(spanish ? QStringLiteral("Consultando billete…")
                                    : QStringLiteral("Looking up ticket…"), panel);
    title->setObjectName(QStringLiteral("dialogTitle"));
    auto *hint = new QLabel(
        spanish ? QStringLiteral("Estamos comprobando el título y sus opciones de recarga compatibles.")
                : QStringLiteral("We are checking the ticket and its compatible recharge options."), panel);
    hint->setObjectName(QStringLiteral("screenHint"));
    hint->setWordWrap(true);
    layout->addStretch();
    layout->addWidget(title, 0, Qt::AlignCenter);
    layout->addWidget(hint, 0, Qt::AlignCenter);
    layout->addStretch();
    showPurchaseFlowPanel(panel);
}

void MainWindow::showRechargeOptions(const RechargeableTicket &ticket)
{
    const bool spanish = m_language == UiLanguage::Spanish;
    auto *panel = new QFrame(m_contentStack);
    panel->setObjectName(QStringLiteral("purchaseFlowPanel"));
    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(36, 26, 36, 26);
    layout->setSpacing(12);

    auto *title = new QLabel(spanish ? QStringLiteral("Opciones de recarga")
                                    : QStringLiteral("Recharge options"), panel);
    title->setObjectName(QStringLiteral("dialogTitle"));
    auto *ticketIdentity = new QLabel(
        QStringLiteral("%1 · %2").arg(ticket.productName, ticket.ticketCode), panel);
    ticketIdentity->setObjectName(QStringLiteral("productName"));
    auto *hint = new QLabel(panel);
    hint->setObjectName(QStringLiteral("screenHint"));
    hint->setWordWrap(true);
    layout->addWidget(title);
    layout->addWidget(ticketIdentity);

    QComboBox *origin = nullptr;
    QComboBox *destination = nullptr;
    QComboBox *quantity = nullptr;
    QDoubleSpinBox *amount = nullptr;

    if (ticket.productType == QStringLiteral("SINGLE_TRIP")) {
        hint->setText(spanish ? QStringLiteral("Configura un nuevo trayecto para este billete sencillo.")
                              : QStringLiteral("Configure a new journey for this single ticket."));
        layout->addWidget(hint);
        const auto addStationSelector = [&](const QString &labelText) {
            auto *label = new QLabel(labelText, panel);
            label->setObjectName(QStringLiteral("fieldLabel"));
            auto *selector = new QComboBox(panel);
            selector->setEditable(true);
            selector->setInsertPolicy(QComboBox::NoInsert);
            selector->setAccessibleName(labelText);
            for (const auto &station : m_stations) {
                selector->addItem(QStringLiteral("%1 · %2").arg(station.name, station.code), station.code);
            }
            selector->setCurrentIndex(-1);
            selector->setEnabled(!m_stations.isEmpty());
            if (selector->lineEdit()) {
                selector->lineEdit()->setPlaceholderText(
                    spanish ? QStringLiteral("Busca por nombre o código")
                            : QStringLiteral("Search by name or code"));
            }
            if (selector->completer()) {
                selector->completer()->setCaseSensitivity(Qt::CaseInsensitive);
                selector->completer()->setFilterMode(Qt::MatchContains);
            }
            layout->addWidget(label);
            layout->addWidget(selector);
            return selector;
        };
        origin = addStationSelector(spanish ? QStringLiteral("Estación de origen")
                                             : QStringLiteral("Origin station"));
        destination = addStationSelector(spanish ? QStringLiteral("Estación de destino")
                                                  : QStringLiteral("Destination station"));
        if (m_stations.isEmpty()) {
            hint->setText(m_stationLoadFailed
                ? (spanish ? QStringLiteral("No se han podido cargar las estaciones.")
                           : QStringLiteral("Stations could not be loaded."))
                : (spanish ? QStringLiteral("Cargando las estaciones compatibles…")
                           : QStringLiteral("Loading compatible stations…")));
        }
    } else if (ticket.productType == QStringLiteral("MULTI_TRIP")) {
        hint->setText(spanish
            ? QStringLiteral("Elige cuántos viajes quieres añadir. Saldo actual: %1 viajes.")
                  .arg(ticket.remainingTrips.value_or(0))
            : QStringLiteral("Choose how many trips to add. Current balance: %1 trips.")
                  .arg(ticket.remainingTrips.value_or(0)));
        layout->addWidget(hint);
        auto *label = new QLabel(spanish ? QStringLiteral("Viajes que se añadirán")
                                        : QStringLiteral("Trips to add"), panel);
        label->setObjectName(QStringLiteral("fieldLabel"));
        quantity = new QComboBox(panel);
        for (const int option : ticket.tripOptions) {
            quantity->addItem(spanish ? QStringLiteral("%1 viajes").arg(option)
                                      : QStringLiteral("%1 trips").arg(option), option);
        }
        layout->addWidget(label);
        layout->addWidget(quantity);
    } else if (ticket.productType == QStringLiteral("TIME_PASS")) {
        hint->setText(spanish ? QStringLiteral("Elige la nueva duración del abono.")
                              : QStringLiteral("Choose the new pass duration."));
        layout->addWidget(hint);
        auto *label = new QLabel(spanish ? QStringLiteral("Días de validez")
                                        : QStringLiteral("Validity days"), panel);
        label->setObjectName(QStringLiteral("fieldLabel"));
        quantity = new QComboBox(panel);
        for (const int option : ticket.dayOptions) {
            quantity->addItem(spanish ? QStringLiteral("%1 días").arg(option)
                                      : QStringLiteral("%1 days").arg(option), option);
        }
        layout->addWidget(label);
        layout->addWidget(quantity);
    } else if (ticket.productType == QStringLiteral("SMART_BALANCE")) {
        const QLocale locale = spanish ? QLocale(QLocale::Spanish, QLocale::Spain)
                                       : QLocale(QLocale::English, QLocale::UnitedKingdom);
        hint->setText(spanish
            ? QStringLiteral("Elige el importe que quieres añadir. Saldo actual: %1.")
                  .arg(locale.toCurrencyString(ticket.balanceAmount.value_or(0.0), ticket.currency))
            : QStringLiteral("Choose the amount to add. Current balance: %1.")
                  .arg(locale.toCurrencyString(ticket.balanceAmount.value_or(0.0), ticket.currency)));
        layout->addWidget(hint);
        auto *label = new QLabel(spanish ? QStringLiteral("Importe de la recarga")
                                        : QStringLiteral("Recharge amount"), panel);
        label->setObjectName(QStringLiteral("fieldLabel"));
        amount = new QDoubleSpinBox(panel);
        amount->setDecimals(2);
        amount->setSingleStep(1.0);
        amount->setRange(ticket.minRechargeAmount.value_or(1.0),
                         ticket.maxRechargeAmount.value_or(100.0));
        amount->setSuffix(QStringLiteral(" €"));
        layout->addWidget(label);
        layout->addWidget(amount);
    }

    auto *validation = new QLabel(panel);
    validation->setObjectName(QStringLiteral("screenHint"));
    validation->hide();
    layout->addWidget(validation);
    layout->addStretch();
    auto *actions = new QHBoxLayout;
    auto *back = new QPushButton(spanish ? QStringLiteral("Escanear otro billete")
                                        : QStringLiteral("Scan another ticket"), panel);
    back->setObjectName(QStringLiteral("backAction"));
    auto *confirm = new QPushButton(spanish ? QStringLiteral("Continuar")
                                           : QStringLiteral("Continue"), panel);
    confirm->setObjectName(QStringLiteral("confirmAction"));
    const bool hasOptions = ticket.productType == QStringLiteral("SINGLE_TRIP")
        ? !m_stations.isEmpty()
        : ticket.productType == QStringLiteral("SMART_BALANCE")
            || (quantity && quantity->count() > 0);
    confirm->setEnabled(hasOptions);
    actions->addStretch();
    actions->addWidget(back);
    actions->addWidget(confirm);
    layout->addLayout(actions);

    connect(back, &QPushButton::clicked, this, [this] {
        m_pendingRecharge.reset();
        showRechargeScanner();
    });
    connect(confirm, &QPushButton::clicked, panel,
            [this, ticket, origin, destination, quantity, amount, validation, spanish] {
        QString originCode;
        QString destinationCode;
        if (origin && destination) {
            originCode = origin->currentData().toString();
            destinationCode = destination->currentData().toString();
            if (originCode.isEmpty() || destinationCode.isEmpty() || originCode == destinationCode) {
                validation->setText(spanish ? QStringLiteral("Selecciona dos estaciones diferentes.")
                                            : QStringLiteral("Choose two different stations."));
                validation->show();
                return;
            }
        }
        const int option = quantity ? quantity->currentData().toInt() : 0;
        emit rechargeConfigurationSelected(
            ticket.qrValue, originCode, destinationCode,
            ticket.productType == QStringLiteral("MULTI_TRIP") ? option : 0,
            ticket.productType == QStringLiteral("TIME_PASS") ? option : 0,
            amount ? amount->value() : 0.0);
    });
    showPurchaseFlowPanel(panel);
}

void MainWindow::showPurchaseFlowPanel(QWidget *panel)
{
    QWidget *previousPanel = m_purchaseFlowPanel;
    m_purchaseFlowPanel = panel;
    m_contentStack->addWidget(panel);
    m_contentStack->setCurrentWidget(panel);
    if (previousPanel) {
        m_contentStack->removeWidget(previousPanel);
        previousPanel->deleteLater();
    }
}

void MainWindow::leavePurchaseFlow(QWidget *destination)
{
    QWidget *previousPanel = m_purchaseFlowPanel;
    m_purchaseFlowPanel = nullptr;
    m_contentStack->setCurrentWidget(destination);
    if (previousPanel) {
        m_contentStack->removeWidget(previousPanel);
        previousPanel->deleteLater();
    }
}

void MainWindow::renderCatalog()
{
    while (auto *item = m_catalogGrid->takeAt(0)) {
        delete item->widget();
        delete item;
    }
    if (m_products.isEmpty()) {
        m_catalogState->setText(
            m_language == UiLanguage::Spanish ? QStringLiteral("No hay títulos disponibles.")
                                              : QStringLiteral("No ticket products are available."));
        m_catalogState->show();
        return;
    }

    m_catalogState->hide();
    m_catalogRetryButton->hide();
    for (qsizetype index = 0; index < m_products.size(); ++index) {
        const auto &product = m_products.at(index);
        auto *card = new QFrame(m_catalogPanel);
        card->setObjectName(QStringLiteral("productCard"));
        card->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
        auto *layout = new QVBoxLayout(card);
        layout->setContentsMargins(20, 16, 20, 16);
        layout->setSpacing(5);
        auto *header = new QWidget(card);
        auto *headerLayout = new QHBoxLayout(header);
        headerLayout->setContentsMargins(0, 0, 0, 0);
        auto *name = new QLabel(productName(product), header);
        name->setObjectName(QStringLiteral("productName"));
        auto *code = new QLabel(product.code, header);
        code->setObjectName(QStringLiteral("productCode"));
        headerLayout->addWidget(name);
        headerLayout->addStretch();
        headerLayout->addWidget(code);
        auto *tariff = new QLabel(productTariff(product), card);
        tariff->setObjectName(QStringLiteral("productTariff"));
        auto *rules = new QLabel(productRules(product), card);
        rules->setObjectName(QStringLiteral("productRules"));
        rules->setWordWrap(true);
        layout->addWidget(header);
        layout->addWidget(tariff);
        layout->addWidget(rules);
        layout->addStretch();
        auto *selectButton = new QPushButton(card);
        selectButton->setObjectName(QStringLiteral("selectProduct"));
        selectButton->setCursor(Qt::PointingHandCursor);
        const bool stationProduct = product.type == QStringLiteral("SINGLE_TRIP");
        const bool stationSelectionAvailable = !stationProduct || !m_stations.isEmpty();
        selectButton->setEnabled(stationSelectionAvailable || m_stationLoadFailed);
        if (stationProduct && m_stations.isEmpty()) {
            selectButton->setText(
                m_language == UiLanguage::Spanish
                    ? (m_stationLoadFailed ? QStringLiteral("Estaciones no disponibles")
                                           : QStringLiteral("Cargando estaciones…"))
                    : (m_stationLoadFailed ? QStringLiteral("Stations unavailable")
                                           : QStringLiteral("Loading stations…")));
        } else {
            selectButton->setText(
                m_language == UiLanguage::Spanish ? QStringLiteral("Seleccionar")
                                                  : QStringLiteral("Select"));
        }
        connect(selectButton, &QPushButton::clicked, this, [this, product, stationSelectionAvailable] {
            if (!stationSelectionAvailable) {
                m_stationLoadFailed = false;
                renderCatalog();
                m_stationClient->load();
                return;
            }
            showProductConfiguration(product);
        });
        layout->addWidget(selectButton, 0, Qt::AlignRight);
        m_catalogGrid->addWidget(card, static_cast<int>(index / 2),
                                 static_cast<int>(index % 2));
    }
}

void MainWindow::showProductConfiguration(const TicketProduct &product)
{

    const bool spanish = m_language == UiLanguage::Spanish;
    auto *panel = new QFrame(m_contentStack);
    panel->setObjectName(QStringLiteral("purchaseFlowPanel"));
    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(36, 30, 36, 30);
    layout->setSpacing(14);

    auto *title = new QLabel(productName(product), panel);
    title->setObjectName(QStringLiteral("dialogTitle"));
    auto *hint = new QLabel(productRules(product), panel);
    hint->setObjectName(QStringLiteral("screenHint"));
    hint->setWordWrap(true);
    layout->addWidget(title);
    layout->addWidget(hint);

    QComboBox *origin = nullptr;
    QComboBox *destination = nullptr;
    QSpinBox *quantity = nullptr;
    QDoubleSpinBox *amount = nullptr;
    const auto addQuantityField = [&](const QString &labelText, const QString &rangeText,
                                      const QString &decreaseName, const QString &increaseName,
                                      int minimum, int maximum) {
        auto *label = new QLabel(labelText, panel);
        label->setObjectName(QStringLiteral("fieldLabel"));
        auto *range = new QLabel(rangeText, panel);
        range->setObjectName(QStringLiteral("screenHint"));
        auto *selector = new QSpinBox(panel);
        selector->setObjectName(QStringLiteral("quantityValue"));
        selector->setRange(minimum, maximum);
        selector->setButtonSymbols(QAbstractSpinBox::NoButtons);
        selector->setAlignment(Qt::AlignCenter);
        selector->setReadOnly(true);
        auto *decrease = new QPushButton(QStringLiteral("−"), panel);
        auto *increase = new QPushButton(QStringLiteral("+"), panel);
        decrease->setObjectName(QStringLiteral("stepAction"));
        increase->setObjectName(QStringLiteral("stepAction"));
        decrease->setCursor(Qt::PointingHandCursor);
        increase->setCursor(Qt::PointingHandCursor);
        decrease->setAccessibleName(decreaseName);
        increase->setAccessibleName(increaseName);
        auto *selectorLayout = new QHBoxLayout();
        selectorLayout->addStretch();
        selectorLayout->addWidget(decrease);
        selectorLayout->addWidget(selector);
        selectorLayout->addWidget(increase);
        selectorLayout->addStretch();
        connect(decrease, &QPushButton::clicked, selector,
                [selector] { selector->setValue(selector->value() - 1); });
        connect(increase, &QPushButton::clicked, selector,
                [selector] { selector->setValue(selector->value() + 1); });
        connect(selector, &QSpinBox::valueChanged, panel,
                [selector, decrease, increase](int value) {
            decrease->setEnabled(value > selector->minimum());
            increase->setEnabled(value < selector->maximum());
        });
        decrease->setEnabled(false);
        layout->addWidget(label);
        layout->addWidget(range);
        layout->addLayout(selectorLayout);
        return selector;
    };

    if (product.type == QStringLiteral("SINGLE_TRIP")) {
        const auto addStationField = [&](const QString &labelText) {
            auto *label = new QLabel(labelText, panel);
            label->setObjectName(QStringLiteral("fieldLabel"));
            auto *selector = new QComboBox(panel);
            selector->setEditable(true);
            selector->setInsertPolicy(QComboBox::NoInsert);
            selector->setAccessibleName(labelText);
            for (const auto &station : m_stations) {
                selector->addItem(QStringLiteral("%1 · %2").arg(station.name, station.code),
                                  station.code);
            }
            selector->setCurrentIndex(-1);
            selector->lineEdit()->setPlaceholderText(
                spanish ? QStringLiteral("Busca por nombre o código")
                        : QStringLiteral("Search by name or code"));
            selector->completer()->setCaseSensitivity(Qt::CaseInsensitive);
            selector->completer()->setFilterMode(Qt::MatchContains);
            selector->completer()->setCompletionMode(QCompleter::PopupCompletion);
            layout->addWidget(label);
            layout->addWidget(selector);
            return selector;
        };
        origin = addStationField(spanish ? QStringLiteral("Estación de origen")
                                         : QStringLiteral("Origin station"));
        destination = addStationField(spanish ? QStringLiteral("Estación de destino")
                                              : QStringLiteral("Destination station"));
        auto *swapStations = new QPushButton(
            spanish ? QStringLiteral("⇅ Intercambiar estaciones")
                    : QStringLiteral("⇅ Swap stations"), panel);
        swapStations->setObjectName(QStringLiteral("swapStationsAction"));
        swapStations->setCursor(Qt::PointingHandCursor);
        swapStations->setAccessibleName(
            spanish ? QStringLiteral("Intercambiar origen y destino")
                    : QStringLiteral("Swap origin and destination"));
        connect(swapStations, &QPushButton::clicked, panel, [origin, destination] {
            const int originIndex = origin->currentIndex();
            origin->setCurrentIndex(destination->currentIndex());
            destination->setCurrentIndex(originIndex);
        });
        layout->addWidget(swapStations, 0, Qt::AlignRight);
    } else if (product.type == QStringLiteral("MULTI_TRIP")) {
        const int minimum = product.minTrips.value_or(2);
        const int maximum = product.maxTrips.value_or(30);
        quantity = addQuantityField(
            spanish ? QStringLiteral("Número de viajes") : QStringLiteral("Number of trips"),
            spanish ? QStringLiteral("Selecciona entre %1 y %2 viajes").arg(minimum).arg(maximum)
                    : QStringLiteral("Choose between %1 and %2 trips").arg(minimum).arg(maximum),
            spanish ? QStringLiteral("Quitar un viaje") : QStringLiteral("Remove one trip"),
            spanish ? QStringLiteral("Añadir un viaje") : QStringLiteral("Add one trip"),
            minimum, maximum);
    } else if (product.type == QStringLiteral("TIME_PASS")) {
        const int minimum = product.minDays.value_or(2);
        const int maximum = product.maxDays.value_or(30);
        quantity = addQuantityField(
            spanish ? QStringLiteral("Número de días") : QStringLiteral("Number of days"),
            spanish ? QStringLiteral("Selecciona entre %1 y %2 días").arg(minimum).arg(maximum)
                    : QStringLiteral("Choose between %1 and %2 days").arg(minimum).arg(maximum),
            spanish ? QStringLiteral("Quitar un día") : QStringLiteral("Remove one day"),
            spanish ? QStringLiteral("Añadir un día") : QStringLiteral("Add one day"),
            minimum, maximum);
    } else if (product.type == QStringLiteral("SMART_BALANCE")) {
        auto *label = new QLabel(spanish ? QStringLiteral("Importe de la recarga")
                                        : QStringLiteral("Recharge amount"), panel);
        label->setObjectName(QStringLiteral("fieldLabel"));
        amount = new QDoubleSpinBox(panel);
        amount->setDecimals(2);
        amount->setSingleStep(1.0);
        amount->setRange(product.minRechargeAmount.value_or(1.0),
                         product.maxRechargeAmount.value_or(100.0));
        amount->setSuffix(QStringLiteral(" €"));
        layout->addWidget(label);
        layout->addWidget(amount);
    }

    if (m_pendingPayment && m_pendingPayment->product.code == product.code) {
        if (origin && destination) {
            origin->setCurrentIndex(origin->findData(m_pendingPayment->originStationCode));
            destination->setCurrentIndex(
                destination->findData(m_pendingPayment->destinationStationCode));
        }
        if (quantity) quantity->setValue(m_pendingPayment->quantity);
        if (amount) amount->setValue(m_pendingPayment->rechargeAmount);
    } else if (origin && !m_machineStationCode.isEmpty()) {
        const int machineStationIndex = origin->findData(m_machineStationCode);
        if (machineStationIndex >= 0) origin->setCurrentIndex(machineStationIndex);
    }

    auto *validation = new QLabel(panel);
    validation->setObjectName(QStringLiteral("screenHint"));
    validation->hide();
    layout->addWidget(validation);
    layout->addStretch();
    auto *actions = new QHBoxLayout();
    auto *back = new QPushButton(spanish ? QStringLiteral("Volver al catálogo")
                                        : QStringLiteral("Back to catalogue"), panel);
    back->setObjectName(QStringLiteral("backAction"));
    auto *confirm = new QPushButton(spanish ? QStringLiteral("Continuar")
                                           : QStringLiteral("Continue"), panel);
    confirm->setObjectName(QStringLiteral("confirmAction"));
    actions->addStretch();
    actions->addWidget(back);
    actions->addWidget(confirm);
    layout->addLayout(actions);

    connect(back, &QPushButton::clicked, panel, [this] {
        m_pendingPayment.reset();
        leavePurchaseFlow(m_catalogPanel);
    });
    connect(confirm, &QPushButton::clicked, panel,
            [this, product, origin, destination, quantity, amount, validation, spanish] {
        QString originCode;
        QString destinationCode;
        if (origin && destination) {
            originCode = origin->currentData().toString();
            destinationCode = destination->currentData().toString();
            if (originCode.isEmpty() || destinationCode.isEmpty()
                || originCode == destinationCode) {
                validation->setText(
                    spanish ? QStringLiteral("Selecciona dos estaciones diferentes.")
                            : QStringLiteral("Choose two different stations."));
                validation->show();
                return;
            }
        }
        const int selectedQuantity = quantity ? quantity->value() : 0;
        const double selectedAmount = amount ? amount->value() : 0.0;
        leavePurchaseFlow(m_catalogPanel);
        emit configurationSelected(product.code, originCode, destinationCode,
                                   selectedQuantity, selectedAmount);
    });
    showPurchaseFlowPanel(panel);
}

void MainWindow::preparePayment(
    const QString &productCode,
    const QString &originStationCode,
    const QString &destinationStationCode,
    int quantity,
    double rechargeAmount)
{
    const auto product = std::find_if(m_products.cbegin(), m_products.cend(), [&](const auto &item) {
        return item.code == productCode;
    });
    if (product == m_products.cend()) {
        return;
    }

    m_pendingPayment = PendingPayment{
        .product = *product,
        .originStationCode = originStationCode,
        .destinationStationCode = destinationStationCode,
        .quantity = quantity,
        .rechargeAmount = rechargeAmount,
    };
    if (product->type == QStringLiteral("SINGLE_TRIP")) {
        m_catalogState->setText(
            m_language == UiLanguage::Spanish ? QStringLiteral("Calculando el trayecto y el precio…")
                                              : QStringLiteral("Calculating journey and fare…"));
        m_catalogState->show();
        m_catalogPanel->setEnabled(false);
        m_journeyClient->load(originStationCode, destinationStationCode);
        return;
    }

    double amount = 0.0;
    if (product->type == QStringLiteral("MULTI_TRIP") && product->pricePerTrip) {
        amount = *product->pricePerTrip * quantity;
    } else if (product->type == QStringLiteral("TIME_PASS") && product->pricePerDay) {
        amount = *product->pricePerDay * quantity;
    } else if (product->type == QStringLiteral("SMART_BALANCE")) {
        amount = rechargeAmount;
    }
    showPaymentScreen(*product, amount);
}

void MainWindow::showPaymentScreen(const TicketProduct &product, double rawAmount)
{

    if (!m_pendingPayment) return;
    const bool spanish = m_language == UiLanguage::Spanish;
    const double amount = qRound64(rawAmount * 100.0) / 100.0;
    const QLocale locale = spanish ? QLocale(QLocale::Spanish, QLocale::Spain)
                                   : QLocale(QLocale::English, QLocale::UnitedKingdom);
    auto *panel = new QFrame(m_contentStack);
    panel->setObjectName(QStringLiteral("purchaseFlowPanel"));
    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(36, 30, 36, 30);
    layout->setSpacing(14);

    auto *title = new QLabel(spanish ? QStringLiteral("Revisa y paga")
                                    : QStringLiteral("Review and pay"), panel);
    title->setObjectName(QStringLiteral("dialogTitle"));
    auto *productLabel = new QLabel(productName(product), panel);
    productLabel->setObjectName(QStringLiteral("productName"));
    QString detail;
    if (product.type == QStringLiteral("SINGLE_TRIP")) {
        const auto stationName = [this](const QString &code) {
            const auto station = std::find_if(m_stations.cbegin(), m_stations.cend(),
                [&](const auto &item) { return item.code == code; });
            return station == m_stations.cend() ? code : station->name;
        };
        detail = QStringLiteral("%1 → %2").arg(
            stationName(m_pendingPayment->originStationCode),
            stationName(m_pendingPayment->destinationStationCode));
    } else if (product.type == QStringLiteral("MULTI_TRIP")) {
        detail = spanish ? QStringLiteral("%1 viajes").arg(m_pendingPayment->quantity)
                         : QStringLiteral("%1 trips").arg(m_pendingPayment->quantity);
    } else if (product.type == QStringLiteral("TIME_PASS")) {
        detail = spanish ? QStringLiteral("%1 días").arg(m_pendingPayment->quantity)
                         : QStringLiteral("%1 days").arg(m_pendingPayment->quantity);
    } else {
        detail = spanish ? QStringLiteral("Saldo recargado") : QStringLiteral("Balance top-up");
    }
    auto *detailLabel = new QLabel(detail, panel);
    detailLabel->setObjectName(QStringLiteral("screenHint"));
    auto *amountLabel = new QLabel(locale.toCurrencyString(amount, product.currency), panel);
    amountLabel->setObjectName(QStringLiteral("paymentAmount"));
    auto *method = new QLabel(
        spanish ? QStringLiteral("Pago simulado con tarjeta · No se realizará ningún cargo real")
                : QStringLiteral("Simulated card payment · No real charge will be made"), panel);
    method->setObjectName(QStringLiteral("screenHint"));
    method->setWordWrap(true);
    auto *state = new QLabel(panel);
    state->setObjectName(QStringLiteral("paymentApproved"));
    state->hide();

    layout->addWidget(title);
    layout->addWidget(productLabel);
    layout->addWidget(detailLabel);
    layout->addSpacing(8);
    layout->addWidget(amountLabel);
    layout->addWidget(method);
    layout->addWidget(state);
    layout->addStretch();
    auto *actions = new QHBoxLayout();
    auto *back = new QPushButton(spanish ? QStringLiteral("Volver")
                                        : QStringLiteral("Back"), panel);
    back->setObjectName(QStringLiteral("backAction"));
    auto *pay = new QPushButton(
        spanish ? QStringLiteral("Pagar %1").arg(locale.toCurrencyString(amount, product.currency))
                : QStringLiteral("Pay %1").arg(locale.toCurrencyString(amount, product.currency)),
        panel);
    pay->setObjectName(QStringLiteral("confirmAction"));
    actions->addStretch();
    actions->addWidget(back);
    actions->addWidget(pay);
    layout->addLayout(actions);

    connect(back, &QPushButton::clicked, panel, [this, product] {
        showProductConfiguration(product);
    });
    connect(pay, &QPushButton::clicked, panel,
            [this, panel, pay, back, state, spanish, amount] {
        pay->setEnabled(false);
        back->setEnabled(false);
        pay->setText(spanish ? QStringLiteral("Procesando…")
                             : QStringLiteral("Processing…"));
        QTimer::singleShot(900, panel, [this, pay, back, state, spanish, amount] {
            if (!m_pendingPayment) return;
            state->setText(spanish ? QStringLiteral("Pago aprobado. Solicitando la emisión…")
                                   : QStringLiteral("Payment approved. Requesting issuance…"));
            state->show();
            pay->hide();
            back->hide();
            emit paymentApproved(
                m_pendingPayment->product.code,
                m_pendingPayment->originStationCode,
                m_pendingPayment->destinationStationCode,
                m_pendingPayment->quantity,
                m_pendingPayment->rechargeAmount,
                amount);
        });
    });
    showPurchaseFlowPanel(panel);
}

void MainWindow::showIssuedTicketWindow(
    const QString &ticketCode,
    const QByteArray &qrPng,
    const QString &qrValue,
    const QString &linkingCode,
    const QString &purchaseReference)
{
    QPixmap qr;
    const bool spanish = m_language == UiLanguage::Spanish;
    if (!qr.loadFromData(qrPng, "PNG")) {
        m_issuanceClient->publishOperationEvent(
            QStringLiteral("TICKET_PURCHASE_FAILED"), purchaseReference,
            ticketCode, QStringLiteral("INVALID_QR_IMAGE"));
        QMessageBox::critical(
            this,
            spanish ? QStringLiteral("No se puede mostrar el billete")
                    : QStringLiteral("Ticket cannot be displayed"),
            spanish
                ? QStringLiteral("El billete se ha emitido, pero la imagen QR recibida no es válida. Solicita asistencia antes de abandonar la máquina.")
                : QStringLiteral("The ticket was issued, but the received QR image is invalid. Request assistance before leaving the machine."));
        showHome();
        return;
    }

    QDialog dialog(this);
    dialog.setObjectName(QStringLiteral("configurationDialog"));
    dialog.setModal(true);
    dialog.setWindowFlag(Qt::WindowCloseButtonHint, false);
    dialog.setMinimumSize(600, 680);
    dialog.setWindowTitle(spanish ? QStringLiteral("Billete emitido")
                                  : QStringLiteral("Ticket issued"));
    auto *layout = new QVBoxLayout(&dialog);
    layout->setContentsMargins(36, 28, 36, 28);
    layout->setSpacing(12);

    auto *title = new QLabel(spanish ? QStringLiteral("Tu billete está listo")
                                    : QStringLiteral("Your ticket is ready"), &dialog);
    title->setObjectName(QStringLiteral("dialogTitle"));
    auto *hint = new QLabel(
        spanish ? QStringLiteral("Escanea el QR o conserva el soporte físico que simula esta máquina.")
                : QStringLiteral("Scan the QR or keep the physical support simulated by this machine."),
        &dialog);
    hint->setObjectName(QStringLiteral("screenHint"));
    hint->setWordWrap(true);
    auto *qrLabel = new QLabel(&dialog);
    qrLabel->setAlignment(Qt::AlignCenter);
    qrLabel->setPixmap(qr.scaled(340, 340, Qt::KeepAspectRatio, Qt::SmoothTransformation));
    qrLabel->setAccessibleName(spanish ? QStringLiteral("Código QR del billete")
                                      : QStringLiteral("Ticket QR code"));
    auto *code = new QLabel(ticketCode, &dialog);
    code->setObjectName(QStringLiteral("productCode"));
    code->setAlignment(Qt::AlignCenter);
    code->setTextInteractionFlags(Qt::TextSelectableByMouse);
    auto *link = new QLabel(
        spanish ? QStringLiteral("Código de vinculación: %1").arg(linkingCode)
                : QStringLiteral("Linking code: %1").arg(linkingCode), &dialog);
    link->setObjectName(QStringLiteral("productName"));
    link->setAlignment(Qt::AlignCenter);
    constexpr int automaticReturnSeconds = 30;
    auto *automaticReturn = new QLabel(&dialog);
    automaticReturn->setObjectName(QStringLiteral("screenHint"));
    automaticReturn->setAlignment(Qt::AlignCenter);
    const auto updateAutomaticReturn = [automaticReturn, spanish](int seconds) {
        automaticReturn->setText(
            spanish ? QStringLiteral("Regreso automático al inicio en %1 s").arg(seconds)
                    : QStringLiteral("Returning automatically to the start in %1 s").arg(seconds));
    };
    updateAutomaticReturn(automaticReturnSeconds);
    auto *finish = new QPushButton(spanish ? QStringLiteral("Finalizar")
                                          : QStringLiteral("Finish"), &dialog);
    finish->setObjectName(QStringLiteral("confirmAction"));
    finish->setCursor(Qt::PointingHandCursor);
    finish->setProperty("qrValue", qrValue);
    connect(finish, &QPushButton::clicked, &dialog,
            [this, &dialog, purchaseReference, ticketCode] {
        m_issuanceClient->publishOperationEvent(
            QStringLiteral("TICKET_PURCHASE_COMPLETED"), purchaseReference,
            ticketCode, QStringLiteral("TICKET_PRESENTED"));
        dialog.accept();
    });

    layout->addWidget(title);
    layout->addWidget(hint);
    layout->addWidget(qrLabel, 0, Qt::AlignCenter);
    layout->addWidget(code);
    layout->addWidget(link);
    layout->addWidget(automaticReturn);
    layout->addStretch();
    layout->addWidget(finish, 0, Qt::AlignRight);

    int remainingSeconds = automaticReturnSeconds;
    QTimer automaticReturnTimer(&dialog);
    automaticReturnTimer.setInterval(1000);
    connect(&automaticReturnTimer, &QTimer::timeout, &dialog,
            [&remainingSeconds, updateAutomaticReturn, finish] {
        --remainingSeconds;
        if (remainingSeconds <= 0) {
            finish->click();
            return;
        }
        updateAutomaticReturn(remainingSeconds);
    });
    automaticReturnTimer.start();
    m_connectionState->setText(spanish ? QStringLiteral("Billete emitido")
                                      : QStringLiteral("Ticket issued"));
    dialog.exec();
    showHome();
}

QString MainWindow::productName(const TicketProduct &product) const
{
    const bool spanish = m_language == UiLanguage::Spanish;
    if (product.type == QStringLiteral("SINGLE_TRIP")) {
        return spanish ? QStringLiteral("Billete sencillo") : QStringLiteral("Single ticket");
    }
    if (product.type == QStringLiteral("MULTI_TRIP")) {
        return spanish ? QStringLiteral("Billete multiviaje") : QStringLiteral("Multi-trip ticket");
    }
    if (product.type == QStringLiteral("TIME_PASS")) {
        return spanish ? QStringLiteral("Abono temporal") : QStringLiteral("Time pass");
    }
    if (product.type == QStringLiteral("SMART_BALANCE")) {
        return spanish ? QStringLiteral("Saldo inteligente") : QStringLiteral("Smart balance");
    }
    return product.name;
}

QString MainWindow::productTariff(const TicketProduct &product) const
{
    const bool spanish = m_language == UiLanguage::Spanish;
    const QLocale locale = spanish ? QLocale(QLocale::Spanish, QLocale::Spain)
                                   : QLocale(QLocale::English, QLocale::UnitedKingdom);
    const auto money = [&locale, &product](double value) {
        return locale.toCurrencyString(value, product.currency);
    };
    if (product.type == QStringLiteral("SINGLE_TRIP") && product.basePrice && product.pricePerStation) {
        return spanish
            ? QStringLiteral("%1 de base + %2 por estación").arg(money(*product.basePrice), money(*product.pricePerStation))
            : QStringLiteral("%1 base fare + %2 per station").arg(money(*product.basePrice), money(*product.pricePerStation));
    }
    if (product.type == QStringLiteral("MULTI_TRIP") && product.pricePerTrip) {
        return spanish ? QStringLiteral("%1 por viaje").arg(money(*product.pricePerTrip))
                       : QStringLiteral("%1 per trip").arg(money(*product.pricePerTrip));
    }
    if (product.type == QStringLiteral("TIME_PASS") && product.pricePerDay) {
        return spanish ? QStringLiteral("%1 por día").arg(money(*product.pricePerDay))
                       : QStringLiteral("%1 per day").arg(money(*product.pricePerDay));
    }
    if (product.type == QStringLiteral("SMART_BALANCE") && product.basePrice && product.pricePerStation) {
        return spanish
            ? QStringLiteral("%1 de base + %2 por estación").arg(money(*product.basePrice), money(*product.pricePerStation))
            : QStringLiteral("%1 base fare + %2 per station").arg(money(*product.basePrice), money(*product.pricePerStation));
    }
    return spanish ? QStringLiteral("Tarifa disponible al configurar")
                   : QStringLiteral("Fare available during configuration");
}

QString MainWindow::productRules(const TicketProduct &product) const
{
    const bool spanish = m_language == UiLanguage::Spanish;
    if (product.type == QStringLiteral("SINGLE_TRIP")) {
        return spanish ? QStringLiteral("Selecciona una estación de origen y otra de destino.")
                       : QStringLiteral("Choose an origin and a destination station.");
    }
    if (product.type == QStringLiteral("MULTI_TRIP") && product.minTrips && product.maxTrips) {
        return spanish ? QStringLiteral("Entre %1 y %2 viajes.").arg(*product.minTrips).arg(*product.maxTrips)
                       : QStringLiteral("Between %1 and %2 trips.").arg(*product.minTrips).arg(*product.maxTrips);
    }
    if (product.type == QStringLiteral("TIME_PASS") && product.minDays && product.maxDays) {
        return spanish ? QStringLiteral("Validez de %1 a %2 días.").arg(*product.minDays).arg(*product.maxDays)
                       : QStringLiteral("Valid for %1 to %2 days.").arg(*product.minDays).arg(*product.maxDays);
    }
    if (product.type == QStringLiteral("SMART_BALANCE")
        && product.minRechargeAmount && product.maxRechargeAmount) {
        const QLocale locale = spanish ? QLocale(QLocale::Spanish, QLocale::Spain)
                                       : QLocale(QLocale::English, QLocale::UnitedKingdom);
        return spanish
            ? QStringLiteral("Recarga entre %1 y %2.")
                  .arg(locale.toCurrencyString(*product.minRechargeAmount, product.currency),
                       locale.toCurrencyString(*product.maxRechargeAmount, product.currency))
            : QStringLiteral("Recharge between %1 and %2.")
                  .arg(locale.toCurrencyString(*product.minRechargeAmount, product.currency),
                       locale.toCurrencyString(*product.maxRechargeAmount, product.currency));
    }
    return QString();
}

void MainWindow::retranslateUi()
{
    if (m_rechargeScanner) {
        m_rechargeScanner->setSpanish(m_language == UiLanguage::Spanish);
    }
    const bool spanish = m_language == UiLanguage::Spanish;
    setWindowTitle(spanish ? QStringLiteral("Máquina de venta · RMM")
                           : QStringLiteral("Ticket machine · RMM"));
    m_brandMark->setAccessibleName(
        spanish ? QStringLiteral("Red de Metro de Macegocia")
                : QStringLiteral("Macegocia Metro Network"));
    m_applicationName->setText(
        spanish ? QStringLiteral("RMM · Máquina de venta")
                : QStringLiteral("RMM · Ticket machine"));
    m_applicationContext->setText(
        spanish ? QStringLiteral("Red de Metro de Macegocia")
                : QStringLiteral("Macegocia Metro Network"));
    m_spanishLanguageButton->setToolTip(
        spanish ? QStringLiteral("Idioma actual: español")
                : QStringLiteral("Cambiar el idioma a español"));
    m_spanishLanguageButton->setAccessibleName(QStringLiteral("Español"));
    m_spanishLanguageButton->setChecked(spanish);
    m_englishLanguageButton->setToolTip(
        spanish ? QStringLiteral("Cambiar el idioma a inglés")
                : QStringLiteral("Current language: English"));
    m_englishLanguageButton->setAccessibleName(QStringLiteral("English"));
    m_englishLanguageButton->setChecked(!spanish);
    m_connectionState->setText(
        spanish ? QStringLiteral("Preparando conexión")
                : QStringLiteral("Preparing connection"));
    m_connectionState->setAccessibleName(
        spanish ? QStringLiteral("Estado de la máquina: preparando conexión")
                : QStringLiteral("Machine status: preparing connection"));
    m_eyebrow->setText(
        spanish ? QStringLiteral("BIENVENIDO A RMM") : QStringLiteral("WELCOME TO RMM"));
    m_screenTitle->setText(
        spanish ? QStringLiteral("¿Qué quieres hacer?") : QStringLiteral("What would you like to do?"));
    m_screenHint->setText(
        spanish
            ? QStringLiteral("Selecciona una opción para comenzar. Podrás revisar todos los datos antes de confirmar.")
            : QStringLiteral("Choose an option to begin. You can review all details before confirming."));
    m_purchaseButton->setText(
        spanish ? QStringLiteral("Comprar un billete\n\nElige el título y configura tu viaje")
                : QStringLiteral("Buy a ticket\n\nChoose a product and configure your journey"));
    m_purchaseButton->setAccessibleName(
        spanish ? QStringLiteral("Comprar un billete") : QStringLiteral("Buy a ticket"));
    m_rechargeButton->setText(
        spanish ? QStringLiteral("Recargar un billete\n\nAñade viajes, días o saldo a tu soporte")
                : QStringLiteral("Recharge a ticket\n\nAdd trips, days or credit to your ticket"));
    m_rechargeButton->setAccessibleName(
        spanish ? QStringLiteral("Recargar un billete") : QStringLiteral("Recharge a ticket"));
    m_footerContext->setText(
        spanish ? QStringLiteral("Máquina disponible para operaciones simuladas")
                : QStringLiteral("Machine available for simulated operations"));
    m_catalogTitle->setText(
        spanish ? QStringLiteral("Títulos y tarifas") : QStringLiteral("Tickets and fares"));
    m_catalogHint->setText(
        spanish ? QStringLiteral("Consulta los productos disponibles. El precio final dependerá de la configuración elegida.")
                : QStringLiteral("Browse the available products. The final price depends on your chosen configuration."));
    m_catalogBackButton->setText(spanish ? QStringLiteral("← Volver") : QStringLiteral("← Back"));
    m_catalogRetryButton->setText(spanish ? QStringLiteral("Reintentar") : QStringLiteral("Try again"));
    if (!m_products.isEmpty()) {
        renderCatalog();
    }
}
