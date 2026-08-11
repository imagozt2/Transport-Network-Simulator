#pragma once

#include <QMainWindow>

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
};
