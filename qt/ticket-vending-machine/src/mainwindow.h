#pragma once

#include <QMainWindow>

class QLabel;
class QPushButton;

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
    [[nodiscard]] QWidget *createFooter();
    void configureWindow();
    void showLanguageSelector();
    void setLanguage(UiLanguage language);
    void retranslateUi();

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
};
