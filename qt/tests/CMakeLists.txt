qt_add_executable(rmm-qt-foundation-tests
    localservicestest.cpp
)

target_link_libraries(rmm-qt-foundation-tests
    PRIVATE
        Qt6::Core
        Qt6::Test
        rmm-local-services
)

add_test(
    NAME rmm-local-services-configuration
    COMMAND rmm-qt-foundation-tests
)

set_tests_properties(rmm-local-services-configuration PROPERTIES
    ENVIRONMENT_MODIFICATION "PATH=path_list_prepend:$<TARGET_FILE_DIR:Qt6::Core>"
)

qt_add_executable(rmm-ticket-machine-protocol-tests
    ticketmachineprotocoltest.cpp
    ../ticket-vending-machine/src/ticketmachineprotocol.cpp
    ../ticket-vending-machine/src/ticketmachineprotocol.h
)

target_include_directories(rmm-ticket-machine-protocol-tests
    PRIVATE ../ticket-vending-machine/src
)

target_link_libraries(rmm-ticket-machine-protocol-tests
    PRIVATE
        Qt6::Core
        Qt6::Test
)

add_test(
    NAME rmm-ticket-machine-purchase-issuance-events
    COMMAND rmm-ticket-machine-protocol-tests
)

set_tests_properties(rmm-ticket-machine-purchase-issuance-events PROPERTIES
    ENVIRONMENT_MODIFICATION "PATH=path_list_prepend:$<TARGET_FILE_DIR:Qt6::Core>"
)
