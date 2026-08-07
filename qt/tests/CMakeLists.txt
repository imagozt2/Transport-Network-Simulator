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
