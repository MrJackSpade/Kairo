# The native library of a frame-driven Kairo app: the product's MachineCore sources
# (kairo/machine_core.h) with the shared JNI surface, frame host, audio output and
# Slang presenter. Link the core's own targets to it afterwards. KAIRO_SHADERS=OFF (the app's
# kairoShaders=false) builds it without the shader presenter.
include_guard(GLOBAL)
include("${CMAKE_CURRENT_LIST_DIR}/KairoNativeDefaults.cmake")

get_filename_component(KAIRO_SHARED_ROOT "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
option(KAIRO_SHADERS "Present frames through the Slang shader runtime" ON)

function(kairo_machine_library target)
    add_library(${target} SHARED "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp/kairo/machine_jni.cpp" ${ARGN})
    target_compile_features(${target} PRIVATE cxx_std_17)
    kairo_native_debug_defaults(${target})
    target_include_directories(${target} PRIVATE "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp")
    if(KAIRO_SHADERS)
        target_include_directories(${target} PRIVATE "${KAIRO_SHARED_ROOT}/third_party/librashader/include")
        target_link_libraries(${target} PRIVATE EGL GLESv3 dl)
    else()
        target_compile_definitions(${target} PRIVATE KAIRO_NO_SHADERS)
    endif()
    target_link_libraries(${target} PRIVATE log android aaudio)
endfunction()
