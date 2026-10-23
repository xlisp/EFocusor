cmake_minimum_required(VERSION 3.16)
project(indi_ikun_focuser VERSION 1.1 LANGUAGES CXX)

find_package(INDI REQUIRED)

add_executable(indi_ikun_focuser
    ikunfocuser.cpp
    ikunfocuser_protocol.cpp
)

target_compile_features(indi_ikun_focuser PRIVATE cxx_std_17)
target_include_directories(indi_ikun_focuser PRIVATE ${INDI_INCLUDE_DIR})
target_link_libraries(indi_ikun_focuser PRIVATE ${INDI_LIBRARIES})

install(TARGETS indi_ikun_focuser RUNTIME DESTINATION bin)

set(IKUNFOCUSER_VERSION_MAJOR ${PROJECT_VERSION_MAJOR})
set(IKUNFOCUSER_VERSION_MINOR ${PROJECT_VERSION_MINOR})
configure_file(ikunfocuser.xml.cmake ikunfocuser.xml @ONLY)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/ikunfocuser.xml DESTINATION ${INDI_DATA_DIR})

include(CTest)
if(BUILD_TESTING)
    add_executable(ikunfocuser_protocol_tests
        tests/ikunfocuser_protocol_tests.cpp
        ikunfocuser_protocol.cpp
    )
    target_compile_features(ikunfocuser_protocol_tests PRIVATE cxx_std_17)
    target_include_directories(ikunfocuser_protocol_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    add_test(NAME ikunfocuser_protocol_tests COMMAND ikunfocuser_protocol_tests)
endif()
