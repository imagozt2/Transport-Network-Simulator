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
)

target_link_libraries(rmm-ticket-vending-machine
    PRIVATE
        Qt6::Core
        Qt6::Gui
        Qt6::Widgets
        Qt6::Network
        Qt6::Mqtt
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
