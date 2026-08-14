#pragma once

#include <QMainWindow>
#include "ticketcatalogclient.h"
#include "stationcatalogclient.h"
#include "journeyquoteclient.h"
#include "ticketissuancerequestclient.h"
#include "ticketrechargelookupclient.h"
#include <optional>

class QLabel;
class QGridLayout;
class QPushButton;
class QStackedWidget;
class QrCodeScannerWidget;

enum class UiLanguage
{
    Spanish,
    English
};

class MainWindow final : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);

signals:
    void purchaseRequested();
    void rechargeRequested();
    void rechargeQrScanned(const QString &qrValue);
    void rechargeConfigurationSelected(
        const QString &qrValue,
        const QString &originStationCode,
        const QString &destinationStationCode,
        int trips,
        int days,
        double balanceAmount);
    void configurationSelected(
        const QString &productCode,
        const QString &originStationCode,
        const QString &destinationStationCode,
        int quantity,
        double rechargeAmount);
    void paymentApproved(
        const QString &productCode,
        const QString &originStationCode,
        const QString &destinationStationCode,
        int quantity,
        double rechargeAmount,
        double paidAmount);

private:
    [[nodiscard]] QWidget *createHeader();
    [[nodiscard]] QWidget *createMainPanel();
    [[nodiscard]] QWidget *createCatalogPanel();
    [[nodiscard]] QWidget *createFooter();
    void configureWindow();
    void setLanguage(UiLanguage language);
    void retranslateUi();
    void showCatalog();
    void showHome();
    void showRechargeScanner();
    void showRechargeLookupProgress();
    void showRechargeOptions(const RechargeableTicket &ticket);
    void renderCatalog();
    void showPurchaseFlowPanel(QWidget *panel);
    void leavePurchaseFlow(QWidget *destination);
    void showProductConfiguration(const TicketProduct &product);
    void preparePayment(
        const QString &productCode,
        const QString &originStationCode,
        const QString &destinationStationCode,
        int quantity,
        double rechargeAmount);
    void showPaymentScreen(const TicketProduct &product, double amount);
    void showIssuedTicketWindow(
        const QString &ticketCode,
        const QByteArray &qrPng,
        const QString &qrValue,
        const QString &linkingCode,
        const QString &purchaseReference);
    [[nodiscard]] QString productName(const TicketProduct &product) const;
    [[nodiscard]] QString productTariff(const TicketProduct &product) const;
    [[nodiscard]] QString productRules(const TicketProduct &product) const;

    UiLanguage m_language = UiLanguage::Spanish;
    QLabel *m_brandMark = nullptr;
    QLabel *m_applicationName = nullptr;
    QLabel *m_applicationContext = nullptr;
    QLabel *m_connectionState = nullptr;
    QLabel *m_eyebrow = nullptr;
    QLabel *m_screenTitle = nullptr;
    QLabel *m_screenHint = nullptr;
    QLabel *m_footerContext = nullptr;
    QPushButton *m_purchaseButton = nullptr;
    QPushButton *m_rechargeButton = nullptr;
    QPushButton *m_spanishLanguageButton = nullptr;
    QPushButton *m_englishLanguageButton = nullptr;
    QStackedWidget *m_contentStack = nullptr;
    QWidget *m_homePanel = nullptr;
    QWidget *m_catalogPanel = nullptr;
    QWidget *m_purchaseFlowPanel = nullptr;
    QrCodeScannerWidget *m_rechargeScanner = nullptr;
    QLabel *m_catalogTitle = nullptr;
    QLabel *m_catalogHint = nullptr;
    QLabel *m_catalogState = nullptr;
    QPushButton *m_catalogBackButton = nullptr;
    QPushButton *m_catalogRetryButton = nullptr;
    QGridLayout *m_catalogGrid = nullptr;
    TicketCatalogClient *m_catalogClient = nullptr;
    StationCatalogClient *m_stationClient = nullptr;
    JourneyQuoteClient *m_journeyClient = nullptr;
    TicketIssuanceRequestClient *m_issuanceClient = nullptr;
    TicketRechargeLookupClient *m_rechargeLookupClient = nullptr;
    QVector<TicketProduct> m_products;
    QVector<NetworkStation> m_stations;
    QString m_machineStationCode;
    bool m_stationLoadFailed = false;
    std::optional<RechargeableTicket> m_pendingRecharge;
    struct PendingPayment
    {
        TicketProduct product;
        QString originStationCode;
        QString destinationStationCode;
        int quantity = 0;
        double rechargeAmount = 0.0;
    };
    std::optional<PendingPayment> m_pendingPayment;
};
