#include "mainwindow.h"

#include <QFrame>
#include <QDialog>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QSizePolicy>
#include <QSettings>
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
)";
}

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
{
    configureWindow();

    auto *centralWidget = new QWidget(this);
    auto *layout = new QVBoxLayout(centralWidget);
    layout->setContentsMargins(28, 24, 28, 22);
    layout->setSpacing(18);
    layout->addWidget(createHeader());
    layout->addWidget(createMainPanel(), 1);
    layout->addWidget(createFooter());

    setCentralWidget(centralWidget);
    const QSettings settings;
    m_language = settings.value(QStringLiteral("interface/language"), QStringLiteral("es"))
                         .toString() == QStringLiteral("en")
        ? UiLanguage::English
        : UiLanguage::Spanish;
    connect(this, &MainWindow::languageRequested, this, &MainWindow::showLanguageSelector);
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
}
