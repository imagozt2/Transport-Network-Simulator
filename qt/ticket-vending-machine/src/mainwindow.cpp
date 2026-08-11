#include "mainwindow.h"

#include <algorithm>
#include <QFrame>
#include <QComboBox>
#include <QDialog>
#include <QDoubleSpinBox>
#include <QHBoxLayout>
#include <QLocale>
#include <QLabel>
#include <QMessageBox>
#include <QPushButton>
#include <QSizePolicy>
#include <QSettings>
#include <QScrollArea>
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
    QFrame#mainPanel {
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
    QPushButton#footerAction {
        min-height: 48px;
        padding: 8px 18px;
        border: 1px solid #cbd5e1;
        border-radius: 14px;
        background-color: #ffffff;
        font-size: 14px;
        font-weight: 700;
    }
    QPushButton#footerAction:hover, QPushButton#footerAction:focus {
        border-color: #2294f2;
        background-color: #eaf5fe;
    }
    QDialog#languageDialog {
        background-color: #ffffff;
    }
    QLabel#dialogTitle {
        font-size: 25px;
        font-weight: 800;
    }
    QPushButton#languageOption {
        min-height: 72px;
        padding: 12px 20px;
        border: 2px solid #cbd5e1;
        border-radius: 16px;
        background-color: #f8fafc;
        font-size: 18px;
        font-weight: 700;
    }
    QPushButton#languageOption:hover, QPushButton#languageOption:focus,
    QPushButton#languageOption:checked {
        border-color: #2294f2;
        background-color: #eaf5fe;
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
    auto *layout = new QVBoxLayout(centralWidget);
    layout->setContentsMargins(28, 24, 28, 22);
    layout->setSpacing(18);
    layout->addWidget(createHeader());
    m_contentStack = new QStackedWidget(centralWidget);
    m_homePanel = createMainPanel();
    m_catalogPanel = createCatalogPanel();
    m_contentStack->addWidget(m_homePanel);
    m_contentStack->addWidget(m_catalogPanel);
    layout->addWidget(m_contentStack, 1);
    layout->addWidget(createFooter());

    setCentralWidget(centralWidget);
    const QSettings settings;
    m_language = settings.value(QStringLiteral("interface/language"), QStringLiteral("es"))
                         .toString() == QStringLiteral("en")
        ? UiLanguage::English
        : UiLanguage::Spanish;
    connect(this, &MainWindow::languageRequested, this, &MainWindow::showLanguageSelector);
    connect(this, &MainWindow::purchaseRequested, this, &MainWindow::showCatalog);
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
        QMessageBox::information(
            this,
            m_language == UiLanguage::Spanish ? QStringLiteral("Emisión solicitada")
                                              : QStringLiteral("Issuance requested"),
            m_language == UiLanguage::Spanish
                ? QStringLiteral("La solicitud se ha enviado de forma segura.\nReferencia: %1").arg(reference)
                : QStringLiteral("The request was sent securely.\nReference: %1").arg(reference));
        showHome();
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
    });
    connect(m_stationClient, &StationCatalogClient::failed, this, [this] {
        m_stations.clear();
        m_stationLoadFailed = true;
        if (!m_products.isEmpty()) {
            renderCatalog();
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
        showPaymentDialog(m_pendingPayment->product, amount);
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

    m_connectionState = new QLabel(header);
    m_connectionState->setObjectName(QStringLiteral("connectionState"));
    m_connectionState->setAlignment(Qt::AlignCenter);

    layout->addWidget(m_brandMark);
    layout->addWidget(identity);
    layout->addStretch();
    layout->addWidget(m_connectionState);

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

    m_accessibilityButton = new QPushButton(footer);
    m_accessibilityButton->setObjectName(QStringLiteral("footerAction"));
    m_accessibilityButton->setCursor(Qt::PointingHandCursor);
    m_languageButton = new QPushButton(footer);
    m_languageButton->setObjectName(QStringLiteral("footerAction"));
    m_languageButton->setCursor(Qt::PointingHandCursor);

    connect(m_accessibilityButton, &QPushButton::clicked, this, &MainWindow::accessibilityRequested);
    connect(m_languageButton, &QPushButton::clicked, this, &MainWindow::languageRequested);

    layout->addWidget(m_footerContext);
    layout->addStretch();
    layout->addWidget(m_accessibilityButton);
    layout->addWidget(m_languageButton);

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

    auto *scrollArea = new QScrollArea(panel);
    scrollArea->setWidgetResizable(true);
    scrollArea->setFrameShape(QFrame::NoFrame);
    auto *catalogContent = new QWidget(scrollArea);
    m_catalogList = new QVBoxLayout(catalogContent);
    m_catalogList->setContentsMargins(0, 0, 8, 0);
    m_catalogList->setSpacing(12);
    m_catalogList->addStretch();
    scrollArea->setWidget(catalogContent);

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
    layout->addWidget(scrollArea, 1);
    return panel;
}

void MainWindow::showLanguageSelector()
{
    QDialog dialog(this);
    dialog.setObjectName(QStringLiteral("languageDialog"));
    dialog.setModal(true);
    dialog.setMinimumWidth(460);
    dialog.setWindowTitle(
        m_language == UiLanguage::Spanish ? QStringLiteral("Seleccionar idioma")
                                          : QStringLiteral("Select language"));

    auto *layout = new QVBoxLayout(&dialog);
    layout->setContentsMargins(28, 26, 28, 24);
    layout->setSpacing(14);

    auto *title = new QLabel(
        m_language == UiLanguage::Spanish ? QStringLiteral("Selecciona el idioma")
                                          : QStringLiteral("Select your language"),
        &dialog);
    title->setObjectName(QStringLiteral("dialogTitle"));
    auto *description = new QLabel(
        m_language == UiLanguage::Spanish
            ? QStringLiteral("El cambio se aplicará ahora y se conservará para el próximo uso.")
            : QStringLiteral("The change will apply now and be remembered for your next visit."),
        &dialog);
    description->setObjectName(QStringLiteral("screenHint"));
    description->setWordWrap(true);

    auto *spanishButton = new QPushButton(QStringLiteral("Español"), &dialog);
    spanishButton->setObjectName(QStringLiteral("languageOption"));
    spanishButton->setCheckable(true);
    spanishButton->setChecked(m_language == UiLanguage::Spanish);
    spanishButton->setAccessibleName(QStringLiteral("Español"));
    spanishButton->setCursor(Qt::PointingHandCursor);

    auto *englishButton = new QPushButton(QStringLiteral("English"), &dialog);
    englishButton->setObjectName(QStringLiteral("languageOption"));
    englishButton->setCheckable(true);
    englishButton->setChecked(m_language == UiLanguage::English);
    englishButton->setAccessibleName(QStringLiteral("English"));
    englishButton->setCursor(Qt::PointingHandCursor);

    auto *cancelButton = new QPushButton(
        m_language == UiLanguage::Spanish ? QStringLiteral("Cancelar")
                                          : QStringLiteral("Cancel"),
        &dialog);
    cancelButton->setObjectName(QStringLiteral("footerAction"));
    cancelButton->setCursor(Qt::PointingHandCursor);

    connect(spanishButton, &QPushButton::clicked, &dialog, [this, &dialog] {
        setLanguage(UiLanguage::Spanish);
        dialog.accept();
    });
    connect(englishButton, &QPushButton::clicked, &dialog, [this, &dialog] {
        setLanguage(UiLanguage::English);
        dialog.accept();
    });
    connect(cancelButton, &QPushButton::clicked, &dialog, &QDialog::reject);

    layout->addWidget(title);
    layout->addWidget(description);
    layout->addSpacing(8);
    layout->addWidget(spanishButton);
    layout->addWidget(englishButton);
    layout->addSpacing(6);
    layout->addWidget(cancelButton, 0, Qt::AlignRight);

    dialog.exec();
}

void MainWindow::setLanguage(UiLanguage language)
{
    if (m_language == language) {
        return;
    }
    m_language = language;
    QSettings settings;
    settings.setValue(
        QStringLiteral("interface/language"),
        language == UiLanguage::Spanish ? QStringLiteral("es") : QStringLiteral("en"));
    retranslateUi();
}

void MainWindow::showCatalog()
{
    m_contentStack->setCurrentWidget(m_catalogPanel);
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
    m_contentStack->setCurrentWidget(m_homePanel);
}

void MainWindow::renderCatalog()
{
    while (auto *item = m_catalogList->takeAt(0)) {
        delete item->widget();
        delete item;
    }
    if (m_products.isEmpty()) {
        m_catalogState->setText(
            m_language == UiLanguage::Spanish ? QStringLiteral("No hay títulos disponibles.")
                                              : QStringLiteral("No ticket products are available."));
        m_catalogState->show();
        m_catalogList->addStretch();
        return;
    }

    m_catalogState->hide();
    m_catalogRetryButton->hide();
    for (const auto &product : m_products) {
        auto *card = new QFrame(m_catalogPanel);
        card->setObjectName(QStringLiteral("productCard"));
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
        m_catalogList->addWidget(card);
    }
    m_catalogList->addStretch();
}

void MainWindow::showProductConfiguration(const TicketProduct &product)
{
    const bool spanish = m_language == UiLanguage::Spanish;
    QDialog dialog(this);
    dialog.setObjectName(QStringLiteral("configurationDialog"));
    dialog.setModal(true);
    dialog.setMinimumWidth(620);
    dialog.setWindowTitle(productName(product));

    auto *layout = new QVBoxLayout(&dialog);
    layout->setContentsMargins(30, 28, 30, 26);
    layout->setSpacing(14);
    auto *title = new QLabel(productName(product), &dialog);
    title->setObjectName(QStringLiteral("dialogTitle"));
    auto *hint = new QLabel(productRules(product), &dialog);
    hint->setObjectName(QStringLiteral("screenHint"));
    hint->setWordWrap(true);
    layout->addWidget(title);
    layout->addWidget(hint);

    auto *origin = static_cast<QComboBox *>(nullptr);
    auto *destination = static_cast<QComboBox *>(nullptr);
    auto *quantity = static_cast<QSpinBox *>(nullptr);
    auto *amount = static_cast<QDoubleSpinBox *>(nullptr);

    if (product.type == QStringLiteral("SINGLE_TRIP")) {
        auto addStationField = [&](const QString &labelText) {
            auto *label = new QLabel(labelText, &dialog);
            label->setObjectName(QStringLiteral("fieldLabel"));
            auto *selector = new QComboBox(&dialog);
            selector->addItem(spanish ? QStringLiteral("Selecciona una estación")
                                      : QStringLiteral("Choose a station"));
            for (const auto &station : m_stations) {
                selector->addItem(
                    QStringLiteral("%1 · %2").arg(station.name, station.code), station.code);
            }
            layout->addWidget(label);
            layout->addWidget(selector);
            return selector;
        };
        origin = addStationField(spanish ? QStringLiteral("Estación de origen")
                                         : QStringLiteral("Origin station"));
        destination = addStationField(spanish ? QStringLiteral("Estación de destino")
                                              : QStringLiteral("Destination station"));
    } else if (product.type == QStringLiteral("MULTI_TRIP")) {
        auto *label = new QLabel(spanish ? QStringLiteral("Número de viajes")
                                        : QStringLiteral("Number of trips"), &dialog);
        label->setObjectName(QStringLiteral("fieldLabel"));
        quantity = new QSpinBox(&dialog);
        quantity->setRange(product.minTrips.value_or(2), product.maxTrips.value_or(30));
        layout->addWidget(label);
        layout->addWidget(quantity);
    } else if (product.type == QStringLiteral("TIME_PASS")) {
        auto *label = new QLabel(spanish ? QStringLiteral("Número de días")
                                        : QStringLiteral("Number of days"), &dialog);
        label->setObjectName(QStringLiteral("fieldLabel"));
        quantity = new QSpinBox(&dialog);
        quantity->setRange(product.minDays.value_or(2), product.maxDays.value_or(30));
        layout->addWidget(label);
        layout->addWidget(quantity);
    } else if (product.type == QStringLiteral("SMART_BALANCE")) {
        auto *label = new QLabel(spanish ? QStringLiteral("Importe de la recarga")
                                        : QStringLiteral("Recharge amount"), &dialog);
        label->setObjectName(QStringLiteral("fieldLabel"));
        amount = new QDoubleSpinBox(&dialog);
        amount->setDecimals(2);
        amount->setSingleStep(1.0);
        amount->setRange(product.minRechargeAmount.value_or(1.0),
                         product.maxRechargeAmount.value_or(100.0));
        amount->setSuffix(QStringLiteral(" €"));
        layout->addWidget(label);
        layout->addWidget(amount);
    }

    auto *validation = new QLabel(&dialog);
    validation->setObjectName(QStringLiteral("screenHint"));
    validation->hide();
    auto *actions = new QHBoxLayout();
    auto *cancel = new QPushButton(spanish ? QStringLiteral("Cancelar")
                                          : QStringLiteral("Cancel"), &dialog);
    cancel->setObjectName(QStringLiteral("backAction"));
    auto *confirm = new QPushButton(spanish ? QStringLiteral("Continuar")
                                           : QStringLiteral("Continue"), &dialog);
    confirm->setObjectName(QStringLiteral("confirmAction"));
    actions->addStretch();
    actions->addWidget(cancel);
    actions->addWidget(confirm);
    layout->addWidget(validation);
    layout->addSpacing(8);
    layout->addLayout(actions);

    connect(cancel, &QPushButton::clicked, &dialog, &QDialog::reject);
    connect(confirm, &QPushButton::clicked, &dialog, [&, this] {
        QString originCode;
        QString destinationCode;
        if (origin && destination) {
            originCode = origin->currentData().toString();
            destinationCode = destination->currentData().toString();
            if (originCode.isEmpty() || destinationCode.isEmpty() || originCode == destinationCode) {
                validation->setText(
                    spanish ? QStringLiteral("Selecciona dos estaciones diferentes.")
                            : QStringLiteral("Choose two different stations."));
                validation->show();
                return;
            }
        }
        emit configurationSelected(
            product.code,
            originCode,
            destinationCode,
            quantity ? quantity->value() : 0,
            amount ? amount->value() : 0.0);
        dialog.accept();
    });
    dialog.exec();
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
    showPaymentDialog(*product, amount);
}

void MainWindow::showPaymentDialog(const TicketProduct &product, double rawAmount)
{
    if (!m_pendingPayment) {
        return;
    }
    const bool spanish = m_language == UiLanguage::Spanish;
    const double amount = qRound64(rawAmount * 100.0) / 100.0;
    const QLocale locale = spanish ? QLocale(QLocale::Spanish, QLocale::Spain)
                                   : QLocale(QLocale::English, QLocale::UnitedKingdom);

    QDialog dialog(this);
    dialog.setObjectName(QStringLiteral("configurationDialog"));
    dialog.setModal(true);
    dialog.setMinimumWidth(620);
    dialog.setWindowTitle(spanish ? QStringLiteral("Pago simulado")
                                  : QStringLiteral("Simulated payment"));
    auto *layout = new QVBoxLayout(&dialog);
    layout->setContentsMargins(30, 28, 30, 26);
    layout->setSpacing(14);

    auto *title = new QLabel(spanish ? QStringLiteral("Revisa y paga")
                                    : QStringLiteral("Review and pay"), &dialog);
    title->setObjectName(QStringLiteral("dialogTitle"));
    auto *productLabel = new QLabel(productName(product), &dialog);
    productLabel->setObjectName(QStringLiteral("productName"));
    QString detail;
    if (product.type == QStringLiteral("SINGLE_TRIP")) {
        const auto stationName = [this](const QString &code) {
            const auto station = std::find_if(m_stations.cbegin(), m_stations.cend(), [&](const auto &item) {
                return item.code == code;
            });
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
    auto *detailLabel = new QLabel(detail, &dialog);
    detailLabel->setObjectName(QStringLiteral("screenHint"));
    auto *amountLabel = new QLabel(locale.toCurrencyString(amount, product.currency), &dialog);
    amountLabel->setObjectName(QStringLiteral("paymentAmount"));
    auto *method = new QLabel(
        spanish ? QStringLiteral("Pago simulado con tarjeta · No se realizará ningún cargo real")
                : QStringLiteral("Simulated card payment · No real charge will be made"),
        &dialog);
    method->setObjectName(QStringLiteral("screenHint"));
    method->setWordWrap(true);
    auto *state = new QLabel(&dialog);
    state->setObjectName(QStringLiteral("paymentApproved"));
    state->hide();

    auto *actions = new QHBoxLayout();
    auto *cancel = new QPushButton(spanish ? QStringLiteral("Volver")
                                          : QStringLiteral("Back"), &dialog);
    cancel->setObjectName(QStringLiteral("backAction"));
    auto *pay = new QPushButton(
        spanish ? QStringLiteral("Pagar %1").arg(locale.toCurrencyString(amount, product.currency))
                : QStringLiteral("Pay %1").arg(locale.toCurrencyString(amount, product.currency)),
        &dialog);
    pay->setObjectName(QStringLiteral("confirmAction"));
    actions->addStretch();
    actions->addWidget(cancel);
    actions->addWidget(pay);

    layout->addWidget(title);
    layout->addWidget(productLabel);
    layout->addWidget(detailLabel);
    layout->addSpacing(8);
    layout->addWidget(amountLabel);
    layout->addWidget(method);
    layout->addWidget(state);
    layout->addSpacing(8);
    layout->addLayout(actions);

    connect(cancel, &QPushButton::clicked, &dialog, &QDialog::reject);
    connect(pay, &QPushButton::clicked, &dialog, [&, this] {
        pay->setEnabled(false);
        cancel->setEnabled(false);
        pay->setText(spanish ? QStringLiteral("Procesando…") : QStringLiteral("Processing…"));
        QTimer::singleShot(900, &dialog, [&, this] {
            if (!m_pendingPayment) {
                return;
            }
            state->setText(spanish ? QStringLiteral("Pago aprobado")
                                   : QStringLiteral("Payment approved"));
            state->show();
            pay->setText(spanish ? QStringLiteral("Continuar") : QStringLiteral("Continue"));
            pay->setEnabled(true);
            disconnect(pay, nullptr, &dialog, nullptr);
            connect(pay, &QPushButton::clicked, &dialog, [&, this] {
                emit paymentApproved(
                    m_pendingPayment->product.code,
                    m_pendingPayment->originStationCode,
                    m_pendingPayment->destinationStationCode,
                    m_pendingPayment->quantity,
                    m_pendingPayment->rechargeAmount,
                    amount);
                dialog.accept();
            });
        });
    });
    dialog.exec();
    m_pendingPayment.reset();
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
    m_accessibilityButton->setText(
        spanish ? QStringLiteral("Accesibilidad") : QStringLiteral("Accessibility"));
    m_languageButton->setText(
        spanish ? QStringLiteral("Idioma · ES") : QStringLiteral("Language · EN"));
    m_languageButton->setAccessibleName(
        spanish ? QStringLiteral("Cambiar idioma. Idioma actual: español")
                : QStringLiteral("Change language. Current language: English"));
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
