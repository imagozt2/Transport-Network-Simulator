#include "qrreaderdialog.h"

#include <QDialogButtonBox>
#include <QLabel>
#include <QPlainTextEdit>
#include <QPushButton>
#include <QVBoxLayout>

namespace {
constexpr qsizetype maximumQrLength = 4096;
}

QrReaderDialog::QrReaderDialog(QWidget *parent)
    : QDialog(parent)
{
    setWindowTitle(tr("Leer código QR"));
    setModal(true);
    setMinimumWidth(560);
    setWindowFlag(Qt::WindowContextHelpButtonHint, false);

    auto *layout = new QVBoxLayout(this);
    layout->setContentsMargins(28, 26, 28, 24);
    layout->setSpacing(12);

    auto *title = new QLabel(tr("Lector QR del torniquete"), this);
    title->setStyleSheet(QStringLiteral("font-size: 22px; font-weight: 800; color: #0f172a;"));
    auto *instructions = new QLabel(
        tr("Presenta el código ante el lector conectado. En esta simulación también puedes pegar "
           "directamente su contenido codificado."), this);
    instructions->setWordWrap(true);
    instructions->setStyleSheet(QStringLiteral("color: #64748b; font-size: 14px;"));

    m_input = new QPlainTextEdit(this);
    m_input->setObjectName(QStringLiteral("qrInput"));
    m_input->setPlaceholderText(QStringLiteral("RMM:TICKET:1:<JWS>"));
    m_input->setAccessibleName(tr("Contenido del código QR"));
    m_input->setAccessibleDescription(
        tr("Campo que recibe el valor enviado por el lector físico o pegado para la simulación"));
    m_input->setMinimumHeight(150);
    m_input->setTabChangesFocus(true);
    m_input->setStyleSheet(QStringLiteral(
        "QPlainTextEdit { border: 2px solid #cbd5e1; border-radius: 12px; padding: 12px; "
        "background: #f8fafc; color: #0f172a; font-family: Consolas; font-size: 13px; } "
        "QPlainTextEdit:focus { border-color: #2294f2; background: white; }"));

    m_counter = new QLabel(this);
    m_counter->setAlignment(Qt::AlignRight);
    m_counter->setStyleSheet(QStringLiteral("color: #64748b; font-size: 12px;"));

    auto *buttons = new QDialogButtonBox(QDialogButtonBox::Cancel, this);
    m_readButton = buttons->addButton(tr("Leer código"), QDialogButtonBox::AcceptRole);
    m_readButton->setObjectName(QStringLiteral("readQrAction"));
    m_readButton->setDefault(true);
    m_readButton->setStyleSheet(QStringLiteral(
        "QPushButton { min-height: 40px; padding: 0 20px; border: 0; border-radius: 10px; "
        "background: #0f172a; color: white; font-weight: 800; } "
        "QPushButton:disabled { background: #cbd5e1; color: #64748b; }"));

    layout->addWidget(title);
    layout->addWidget(instructions);
    layout->addWidget(m_input);
    layout->addWidget(m_counter);
    layout->addWidget(buttons);

    connect(m_input, &QPlainTextEdit::textChanged, this, &QrReaderDialog::updateInputState);
    connect(buttons, &QDialogButtonBox::accepted, this, &QDialog::accept);
    connect(buttons, &QDialogButtonBox::rejected, this, &QDialog::reject);
    updateInputState();
    m_input->setFocus();
}

QString QrReaderDialog::qrValue() const
{
    return m_input->toPlainText().trimmed();
}

void QrReaderDialog::updateInputState()
{
    const qsizetype length = qrValue().size();
    const bool accepted = length > 0 && length <= maximumQrLength;
    m_readButton->setEnabled(accepted);
    m_counter->setText(tr("%1 de %2 caracteres").arg(length).arg(maximumQrLength));
    m_counter->setStyleSheet(length > maximumQrLength
        ? QStringLiteral("color: #b91c1c; font-size: 12px; font-weight: 700;")
        : QStringLiteral("color: #64748b; font-size: 12px;"));
}
