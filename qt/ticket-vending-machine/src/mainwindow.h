#pragma once

#include <QMainWindow>
#include "ticketcatalogclient.h"

class QLabel;
class QPushButton;
class QStackedWidget;
class QVBoxLayout;

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
    void accessibilityRequested();
    void languageRequested();

private:
    [[nodiscard]] QWidget *createHeader();
    [[nodiscard]] QWidget *createMainPanel();
    [[nodiscard]] QWidget *createCatalogPanel();
    [[nodiscard]] QWidget *createFooter();
    void configureWindow();
    void showLanguageSelector();
    void setLanguage(UiLanguage language);
    void retranslateUi();
    void showCatalog();
    void showHome();
    void renderCatalog();
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
    QPushButton *m_accessibilityButton = nullptr;
    QPushButton *m_languageButton = nullptr;
    QStackedWidget *m_contentStack = nullptr;
    QWidget *m_homePanel = nullptr;
    QWidget *m_catalogPanel = nullptr;
    QLabel *m_catalogTitle = nullptr;
    QLabel *m_catalogHint = nullptr;
    QLabel *m_catalogState = nullptr;
    QPushButton *m_catalogBackButton = nullptr;
    QPushButton *m_catalogRetryButton = nullptr;
    QVBoxLayout *m_catalogList = nullptr;
    TicketCatalogClient *m_catalogClient = nullptr;
    QVector<TicketProduct> m_products;
};
