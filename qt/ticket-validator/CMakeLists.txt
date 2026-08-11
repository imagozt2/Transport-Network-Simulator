qt_add_executable(rmm-ticket-validator WIN32
    src/main.cpp
    src/mainwindow.cpp
    src/mainwindow.h
    src/qrreaderdialog.cpp
    src/qrreaderdialog.h
    src/validatorconfiguration.cpp
    src/validatorconfiguration.h
    src/validationresult.cpp
    src/validationresult.h
)

target_link_libraries(rmm-ticket-validator
    PRIVATE
        Qt6::Core
        Qt6::Gui
        Qt6::Widgets
        Qt6::Mqtt
        rmm-local-services
)

set_target_properties(rmm-ticket-validator PROPERTIES
    OUTPUT_NAME "RMMTicketValidator"
    VERSION "${PROJECT_VERSION}"
)

install(TARGETS rmm-ticket-validator
    RUNTIME DESTINATION bin
    BUNDLE DESTINATION .
)
