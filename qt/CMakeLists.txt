cmake_minimum_required(VERSION 3.21)

project(
    RMMClientApplications
    VERSION 0.1.0
    DESCRIPTION "Aplicaciones Qt de las máquinas simuladas de RMM"
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

set(
    RMM_LOCAL_SERVICES_FILE
    "${CMAKE_CURRENT_SOURCE_DIR}/../config/local-services.properties"
    CACHE FILEPATH
    "Archivo local de direcciones de los servicios RMM"
)

if(NOT EXISTS "${RMM_LOCAL_SERVICES_FILE}")
    set(
        RMM_LOCAL_SERVICES_FILE
        "${CMAKE_CURRENT_SOURCE_DIR}/../config/local-services.properties.example"
    )
endif()

if(NOT EXISTS "${RMM_LOCAL_SERVICES_FILE}")
    message(FATAL_ERROR "No se ha encontrado la configuración local de servicios en /config")
endif()

function(rmm_read_local_service output_variable property_name)
    file(
        STRINGS "${RMM_LOCAL_SERVICES_FILE}" property_lines
        REGEX "^${property_name}="
        LIMIT_COUNT 1
        ENCODING UTF-8
    )

    if(NOT property_lines)
        message(FATAL_ERROR "Falta ${property_name} en ${RMM_LOCAL_SERVICES_FILE}")
    endif()

    string(REGEX REPLACE "^[^=]+=" "" property_value "${property_lines}")
    string(STRIP "${property_value}" property_value)

    if(property_value STREQUAL "")
        message(FATAL_ERROR "${property_name} no puede estar vacía")
    endif()

    set(${output_variable} "${property_value}" PARENT_SCOPE)
endfunction()

rmm_read_local_service(RMM_API_DESKTOP_BASE_URL RMM_API_DESKTOP_BASE_URL)
rmm_read_local_service(RMM_MQTT_HOST RMM_MQTT_HOST)
rmm_read_local_service(RMM_MQTT_PORT RMM_MQTT_PORT)
rmm_read_local_service(RMM_MQTT_TLS RMM_MQTT_TLS)

if(NOT RMM_MQTT_PORT MATCHES "^[0-9]+$")
    message(FATAL_ERROR "RMM_MQTT_PORT debe ser un número")
endif()

if(RMM_MQTT_PORT LESS 1 OR RMM_MQTT_PORT GREATER 65535)
    message(FATAL_ERROR "RMM_MQTT_PORT debe encontrarse entre 1 y 65535")
endif()

if(RMM_MQTT_TLS STREQUAL "true")
    set(RMM_MQTT_TLS_CPP true)
elseif(RMM_MQTT_TLS STREQUAL "false")
    set(RMM_MQTT_TLS_CPP false)
else()
    message(FATAL_ERROR "RMM_MQTT_TLS debe ser true o false")
endif()

configure_file(
    config/localservices.h.in
    generated/rmm/localservices.h
    @ONLY
)

add_library(rmm-local-services INTERFACE)
target_include_directories(
    rmm-local-services
    INTERFACE "${CMAKE_CURRENT_BINARY_DIR}/generated"
)

find_package(Qt6 6.11 REQUIRED COMPONENTS Core Gui Widgets Mqtt)
qt_standard_project_setup(REQUIRES 6.11)

add_subdirectory(ticket-vending-machine)
add_subdirectory(ticket-validator)
