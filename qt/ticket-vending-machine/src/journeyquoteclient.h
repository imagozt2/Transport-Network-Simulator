#pragma once

#include <QObject>
#include <QString>

class QNetworkAccessManager;

class JourneyQuoteClient final : public QObject
{
    Q_OBJECT

public:
    explicit JourneyQuoteClient(QObject *parent = nullptr);
    void load(const QString &originCode, const QString &destinationCode);

signals:
    void loaded(int stationCount);
    void failed();

private:
    QNetworkAccessManager *m_network;
    bool m_loading = false;
};
