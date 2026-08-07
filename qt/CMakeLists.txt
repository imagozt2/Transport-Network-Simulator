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

find_package(Qt6 6.11 REQUIRED COMPONENTS Core Gui Widgets Mqtt)
qt_standard_project_setup(REQUIRES 6.11)

add_subdirectory(ticket-vending-machine)

