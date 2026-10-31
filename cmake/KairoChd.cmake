# The shared CHD reader (native/chd/kairo_chd.h, kairo_chd_stream.h) on the pinned libchdr.
#
# kairo_chd_library() adds kairo_chd, the static reader a core whose discs may be CHDs links to.
# kairo_chd_jni_library() adds libkairo_chd.so, which the frontend's ChdImage loads to hash and
# verify CHDs; an app that lists .chd as a CD builds it beside its machine library.
include_guard(GLOBAL)

# The shared root by the function's own file: a variable set when this file is included would be
# missing in another directory's scope, which include_guard keeps from including it again.
function(kairo_chd_library)
    if(TARGET kairo_chd)
        return()
    endif()
    get_filename_component(KAIRO_CHD_SHARED_ROOT "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/.." ABSOLUTE)
    set(CHDR_WANT_TESTS OFF CACHE BOOL "" FORCE)
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
    add_subdirectory("${KAIRO_CHD_SHARED_ROOT}/third_party/libchdr" "${CMAKE_BINARY_DIR}/libchdr" EXCLUDE_FROM_ALL)
    add_library(kairo_chd STATIC "${KAIRO_CHD_SHARED_ROOT}/native/chd/kairo_chd.c")
    target_include_directories(kairo_chd PUBLIC "${KAIRO_CHD_SHARED_ROOT}/native/chd")
    target_link_libraries(kairo_chd PRIVATE chdr-static)
    if(ANDROID)
        target_link_libraries(kairo_chd PUBLIC log)
    else()
        target_link_libraries(kairo_chd PUBLIC pthread)
    endif()
    target_compile_options(kairo_chd PRIVATE -O3)
endfunction()

function(kairo_chd_jni_library)
    kairo_chd_library()
    get_filename_component(KAIRO_CHD_SHARED_ROOT "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/.." ABSOLUTE)
    add_library(kairo_chd_jni SHARED "${KAIRO_CHD_SHARED_ROOT}/native/chd/kairo_chd_jni.c")
    set_target_properties(kairo_chd_jni PROPERTIES OUTPUT_NAME kairo_chd)
    target_link_libraries(kairo_chd_jni PRIVATE kairo_chd)
endfunction()
