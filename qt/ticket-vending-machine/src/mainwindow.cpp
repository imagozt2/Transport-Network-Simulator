#include "mainwindow.h"

#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QSizePolicy>
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
}

void MainWindow::configureWindow()
{
    setObjectName(QStringLiteral("ticketVendingMainWindow"));
    setWindowTitle(tr("Máquina de venta · RMM"));
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

    auto *brandMark = new QLabel(QStringLiteral("M"), header);
    brandMark->setObjectName(QStringLiteral("brandMark"));
    brandMark->setAlignment(Qt::AlignCenter);
    brandMark->setAccessibleName(tr("Red de Metro de Macegocia"));

    auto *identity = new QWidget(header);
    auto *identityLayout = new QVBoxLayout(identity);
    identityLayout->setContentsMargins(0, 0, 0, 0);
    identityLayout->setSpacing(2);

    auto *name = new QLabel(tr("RMM · Máquina de venta"), identity);
    name->setObjectName(QStringLiteral("applicationName"));
    auto *context = new QLabel(tr("Red de Metro de Macegocia"), identity);
    context->setObjectName(QStringLiteral("applicationContext"));
    identityLayout->addWidget(name);
    identityLayout->addWidget(context);

    auto *connectionState = new QLabel(tr("Preparando conexión"), header);
    connectionState->setObjectName(QStringLiteral("connectionState"));
    connectionState->setAlignment(Qt::AlignCenter);
    connectionState->setAccessibleName(tr("Estado de la máquina: preparando conexión"));

    layout->addWidget(brandMark);
    layout->addWidget(identity);
    layout->addStretch();
    layout->addWidget(connectionState);

    return header;
}

QWidget *MainWindow::createMainPanel()
{
    auto *panel = new QFrame(this);
    panel->setObjectName(QStringLiteral("mainPanel"));

    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(42, 34, 42, 38);
    layout->setSpacing(14);

    auto *eyebrow = new QLabel(tr("BIENVENIDO A RMM"), panel);
    eyebrow->setObjectName(QStringLiteral("eyebrow"));
    auto *title = new QLabel(tr("¿Qué quieres hacer?"), panel);
    title->setObjectName(QStringLiteral("screenTitle"));
    auto *hint = new QLabel(
        tr("Selecciona una opción para comenzar. Podrás revisar todos los datos antes de confirmar."),
        panel);
    hint->setObjectName(QStringLiteral("screenHint"));
    hint->setWordWrap(true);

    auto *actions = new QWidget(panel);
    auto *actionsLayout = new QHBoxLayout(actions);
    actionsLayout->setContentsMargins(0, 16, 0, 0);
    actionsLayout->setSpacing(20);

    auto *purchaseButton = new QPushButton(
        tr("Comprar un billete\n\nElige el título y configura tu viaje"), actions);
    purchaseButton->setObjectName(QStringLiteral("primaryAction"));
    purchaseButton->setAccessibleName(tr("Comprar un billete"));
    purchaseButton->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    purchaseButton->setCursor(Qt::PointingHandCursor);

    auto *rechargeButton = new QPushButton(
        tr("Recargar un billete\n\nAñade viajes, días o saldo a tu soporte"), actions);
    rechargeButton->setObjectName(QStringLiteral("secondaryAction"));
    rechargeButton->setAccessibleName(tr("Recargar un billete"));
    rechargeButton->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    rechargeButton->setCursor(Qt::PointingHandCursor);

    connect(purchaseButton, &QPushButton::clicked, this, &MainWindow::purchaseRequested);
    connect(rechargeButton, &QPushButton::clicked, this, &MainWindow::rechargeRequested);

    actionsLayout->addWidget(purchaseButton, 1);
    actionsLayout->addWidget(rechargeButton, 1);

    layout->addWidget(eyebrow);
    layout->addWidget(title);
    layout->addWidget(hint);
    layout->addWidget(actions, 1);

    return panel;
}

QWidget *MainWindow::createFooter()
{
    auto *footer = new QWidget(this);
    auto *layout = new QHBoxLayout(footer);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(10);

    auto *context = new QLabel(tr("Máquina disponible para operaciones simuladas"), footer);
    context->setObjectName(QStringLiteral("footerContext"));

    auto *accessibilityButton = new QPushButton(tr("Accesibilidad"), footer);
    accessibilityButton->setObjectName(QStringLiteral("footerAction"));
    accessibilityButton->setCursor(Qt::PointingHandCursor);
    auto *languageButton = new QPushButton(tr("Idioma · ES"), footer);
    languageButton->setObjectName(QStringLiteral("footerAction"));
    languageButton->setCursor(Qt::PointingHandCursor);

    connect(accessibilityButton, &QPushButton::clicked, this, &MainWindow::accessibilityRequested);
    connect(languageButton, &QPushButton::clicked, this, &MainWindow::languageRequested);

    layout->addWidget(context);
    layout->addStretch();
    layout->addWidget(accessibilityButton);
    layout->addWidget(languageButton);

    return footer;
}
