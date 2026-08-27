qt_add_executable(rmm-ticket-vending-machine WIN32
    src/main.cpp
    src/mainwindow.cpp
    src/mainwindow.h
    src/ticketcatalogclient.cpp
    src/ticketcatalogclient.h
    src/stationcatalogclient.cpp
    src/stationcatalogclient.h
    src/journeyquoteclient.cpp
    src/journeyquoteclient.h
    src/ticketissuancerequestclient.cpp
    src/ticketissuancerequestclient.h
    src/ticketmachineconfiguration.cpp
    src/ticketmachineconfiguration.h
    src/ticketmachineprotocol.cpp
    src/ticketmachineprotocol.h
    src/qrcodescannerwidget.cpp
    src/qrcodescannerwidget.h
    src/ticketrechargelookupclient.cpp
    src/ticketrechargelookupclient.h
    src/ticketrechargequoteclient.cpp
    src/ticketrechargequoteclient.h
)

qt_add_resources(rmm-ticket-vending-machine "rmm-ticket-machine-assets"
    PREFIX "/rmm"
    FILES
        resources/chevron-down.svg
        resources/flag-spain.svg
        resources/flag-united-kingdom.svg
)

target_link_libraries(rmm-ticket-vending-machine
    PRIVATE
        Qt6::Core
        Qt6::Gui
        Qt6::Widgets
        Qt6::Network
        Qt6::Mqtt
        Qt6::Multimedia
        Qt6::MultimediaWidgets
        ZXing::ZXing
        rmm-local-services
)

set_target_properties(rmm-ticket-vending-machine PROPERTIES
    OUTPUT_NAME "RMMTicketVendingMachine"
    VERSION "${PROJECT_VERSION}"
)

install(TARGETS rmm-ticket-vending-machine
    RUNTIME DESTINATION bin
    BUNDLE DESTINATION .
)
