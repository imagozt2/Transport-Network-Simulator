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

qt_add_executable(rmm-validator-protocol-tests
    validatorprotocoltest.cpp
    ../ticket-validator/src/validationresult.cpp
    ../ticket-validator/src/validationresult.h
    ../ticket-validator/src/validatorprotocol.cpp
    ../ticket-validator/src/validatorprotocol.h
)

target_include_directories(rmm-validator-protocol-tests
    PRIVATE ../ticket-validator/src
)

target_link_libraries(rmm-validator-protocol-tests
    PRIVATE
        Qt6::Core
        Qt6::Test
)

add_test(
    NAME rmm-validator-entries-exits-rejections-duplicates
    COMMAND rmm-validator-protocol-tests
)

set_tests_properties(rmm-validator-entries-exits-rejections-duplicates PROPERTIES
    ENVIRONMENT_MODIFICATION "PATH=path_list_prepend:$<TARGET_FILE_DIR:Qt6::Core>"
)

qt_add_executable(rmm-validator-feedback-tests
    validatorfeedbacktest.cpp
    ../ticket-validator/src/validatorfeedback.cpp
    ../ticket-validator/src/validatorfeedback.h
)

target_include_directories(rmm-validator-feedback-tests
    PRIVATE ../ticket-validator/src
)

target_link_libraries(rmm-validator-feedback-tests
    PRIVATE
        Qt6::Core
        Qt6::Test
)

add_test(
    NAME rmm-validator-camera-states-sounds-timers
    COMMAND rmm-validator-feedback-tests
)

set_tests_properties(rmm-validator-camera-states-sounds-timers PROPERTIES
    ENVIRONMENT_MODIFICATION "PATH=path_list_prepend:$<TARGET_FILE_DIR:Qt6::Core>"
)

qt_add_executable(rmm-qt-mqtt-identity-tests
    mqttidentityconfigurationtest.cpp
    ../ticket-vending-machine/src/ticketmachineconfiguration.cpp
    ../ticket-vending-machine/src/ticketmachineconfiguration.h
    ../ticket-validator/src/validatorconfiguration.cpp
    ../ticket-validator/src/validatorconfiguration.h
)

target_include_directories(rmm-qt-mqtt-identity-tests
    PRIVATE
        ../ticket-vending-machine/src
        ../ticket-validator/src
)

target_link_libraries(rmm-qt-mqtt-identity-tests
    PRIVATE
        Qt6::Core
        Qt6::Test
)

add_test(
    NAME rmm-qt-mqtt-identity-authentication
    COMMAND rmm-qt-mqtt-identity-tests
)

set_tests_properties(rmm-qt-mqtt-identity-authentication PROPERTIES
    ENVIRONMENT_MODIFICATION "PATH=path_list_prepend:$<TARGET_FILE_DIR:Qt6::Core>"
)
