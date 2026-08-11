#pragma once

#include <QObject>
#include <QString>
#include <QVector>

class QNetworkAccessManager;

struct NetworkStation
{
    QString code;
    QString name;
};

class StationCatalogClient final : public QObject
{
    Q_OBJECT

public:
    explicit StationCatalogClient(QObject *parent = nullptr);
    void load();

signals:
    void loaded(const QVector<NetworkStation> &stations);
    void failed();

private:
    QNetworkAccessManager *m_network;
    bool m_loading = false;
};
