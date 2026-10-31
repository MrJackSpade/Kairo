# Host build of the shared CHD reader: kairo_chd_image, which the catalog tools run to read a CHD's
# disc image (shared/tools/chd_image.py builds and finds it).
cmake_minimum_required(VERSION 3.18)
project(kairo_chd_host C)

set(CHDR_WANT_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../../third_party/libchdr" "${CMAKE_BINARY_DIR}/libchdr" EXCLUDE_FROM_ALL)

add_executable(kairo_chd_image kairo_chd_image.c kairo_chd.c)
target_link_libraries(kairo_chd_image PRIVATE chdr-static pthread)
target_compile_options(kairo_chd_image PRIVATE -O2)
target_link_options(kairo_chd_image PRIVATE -static)
