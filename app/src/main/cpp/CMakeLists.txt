cmake_minimum_required(VERSION 3.4.1)

project(vulnkit)

# 添加当前目录和 include 子目录
include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

add_library(vulnkit SHARED
        native-lib.cpp
        root_shell.c
        exploits/cve_2019_2215.c
        exploits/cve_2024_43093_helper.c
        exploits/cve_2025_48572_helper.c
        exploits/cve_2026_0107.c
        exploits/cve_2026_0107_helper.c
        exploits/cve_2026_21385.c
        exploits/cve_2025_38352.c
        exploits/cve_2024_31317.c
)

find_library(log-lib log)
target_link_libraries(vulnkit ${log-lib})