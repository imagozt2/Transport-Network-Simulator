#pragma once

#include <QDialog>
#include <QString>

class QLabel;
class QPlainTextEdit;
class QPushButton;

class QrReaderDialog final : public QDialog
{
    Q_OBJECT

public:
    explicit QrReaderDialog(QWidget *parent = nullptr);
    [[nodiscard]] QString qrValue() const;

private:
    void updateInputState();

    QPlainTextEdit *m_input = nullptr;
    QLabel *m_counter = nullptr;
    QPushButton *m_readButton = nullptr;
};
