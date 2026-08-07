#include "mainwindow.h"

#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QVBoxLayout>
#include <QWidget>

namespace {
constexpr auto windowStyle = R"(
    QMainWindow {
        background-color: #f4f7fa;
    }
    QLabel {
        color: #0f172a;
        font-family: "Segoe UI";
    }
    QLabel#brandMark {
        min-width: 52px;
        min-height: 52px;
        max-width: 52px;
        max-height: 52px;
        border-radius: 14px;
        background-color: #2294f2;
        color: white;
        font-size: 28px;
        font-weight: 900;
    }
    QLabel#applicationName {
        font-size: 22px;
        font-weight: 700;
    }
    QLabel#applicationContext {
        color: #64748b;
        font-size: 13px;
    }
    QLabel#connectionState {
        padding: 7px 12px;
        border-radius: 14px;
        background-color: #e2e8f0;
        color: #334155;
        font-size: 12px;
        font-weight: 700;
    }
    QFrame#welcomePanel {
        border: 1px solid #dbe3ec;
        border-radius: 18px;
        background-color: white;
    }
    QLabel#welcomeTitle {
        font-size: 30px;
        font-weight: 800;
    }
    QLabel#welcomeDescription {
        color: #64748b;
        font-size: 15px;
    }
    QLabel#machineType {
        color: #0060a8;
        font-size: 13px;
        font-weight: 700;
    }
)";
}

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
{
    configureWindow();

    auto *centralWidget = new QWidget(this);
    auto *layout = new QVBoxLayout(centralWidget);
    layout->setContentsMargins(32, 28, 32, 32);
    layout->setSpacing(24);
    layout->addWidget(createHeader());
    layout->addWidget(createWelcomePanel(), 1);

    setCentralWidget(centralWidget);
}

void MainWindow::configureWindow()
{
    setWindowTitle(tr("Máquina validadora · RMM"));
    setMinimumSize(700, 560);
    resize(860, 680);
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

    auto *connectionState = new QLabel(tr("Sin configurar"), header);
    connectionState->setObjectName(QStringLiteral("connectionState"));
    connectionState->setAlignment(Qt::AlignCenter);

    layout->addWidget(brandMark);
    layout->addWidget(identity);
    layout->addStretch();
    layout->addWidget(connectionState);

    return header;
}

QWidget *MainWindow::createWelcomePanel()
{
    auto *panel = new QFrame(this);
    panel->setObjectName(QStringLiteral("welcomePanel"));

    auto *layout = new QVBoxLayout(panel);
    layout->setContentsMargins(48, 48, 48, 48);
    layout->setSpacing(12);
    layout->setAlignment(Qt::AlignCenter);

    auto *machineType = new QLabel(tr("APLICACIÓN QT"), panel);
    machineType->setObjectName(QStringLiteral("machineType"));
    machineType->setAlignment(Qt::AlignCenter);

    auto *title = new QLabel(tr("Máquina validadora RMM"), panel);
    title->setObjectName(QStringLiteral("welcomeTitle"));
    title->setAlignment(Qt::AlignCenter);

    auto *description = new QLabel(
        tr("La estructura inicial está preparada para incorporar la lectura y validación de billetes."),
        panel);
    description->setObjectName(QStringLiteral("welcomeDescription"));
    description->setAlignment(Qt::AlignCenter);
    description->setWordWrap(true);

    layout->addStretch();
    layout->addWidget(machineType);
    layout->addWidget(title);
    layout->addWidget(description);
    layout->addStretch();

    return panel;
}

