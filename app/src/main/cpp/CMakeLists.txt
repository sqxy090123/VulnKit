cmake_minimum_required(VERSION 3.4.1)

project(leak_phys)
add_executable(leak_phys leak_phys.c)
target_link_libraries(leak_phys log)
set_target_properties(leak_phys PROPERTIES PIE TRUE)

project(vulnkit)

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
        exploits/cve_2026_0107_helper.c
        exploits/cve_2026_0107.c
        exploits/cve_2026_21385.c
        exploits/cve_2025_54642.c
        exploits/cve_2023_20938.c
        exploits/cve_2022_22057.c
        exploits/cve_2025_20801.c
        exploits/cve_2024_43066.c
        exploits/cve_2025_48543.c
        exploits/cve_2025_21479.c
        exploits/cve_2025_36920.c
        exploits/cve_2025_38352.c
        exploits/cve_2026_0038.c
        exploits/cve_2026_0032.c
        exploits/cve_2025_64312.c
        exploits/cve_2024_53104.c
        exploits/cve_2025_0088.c
        exploits/cve_2025_36929.c           # 新增
        exploits/cve_2025_48567.c
        exploits/cve_2026_0027.c
        include/gpu_physical_mem.c
)

find_library(log-lib log)
target_link_libraries(vulnkit ${log-lib})