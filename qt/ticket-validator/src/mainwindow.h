#pragma once

#include <QMainWindow>

class MainWindow final : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);

private:
    [[nodiscard]] QWidget *createHeader();
    [[nodiscard]] QWidget *createWelcomePanel();
    void configureWindow();
};

