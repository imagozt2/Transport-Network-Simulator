#include "mainwindow.h"

#include <QApplication>
#include <QCoreApplication>

int main(int argc, char *argv[])
{
    QApplication application(argc, argv);

    QCoreApplication::setOrganizationName(QStringLiteral("RMM"));
    QCoreApplication::setOrganizationDomain(QStringLiteral("rmm.local"));
    QCoreApplication::setApplicationName(QStringLiteral("RMM Ticket Validator"));
    QCoreApplication::setApplicationVersion(QStringLiteral("0.1.0"));

    MainWindow window;
    window.show();

    return application.exec();
}

