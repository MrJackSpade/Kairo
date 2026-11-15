# The native library of a frame-driven Kairo app: the product's MachineCore sources
# (kairo/machine_core.h) with the shared JNI surface, frame host, audio output and
# Slang presenter. Link the core's own targets to it afterwards. KAIRO_SHADERS=OFF (the app's
# kairoShaders=false) builds it without the shader presenter.
include_guard(GLOBAL)
include("${CMAKE_CURRENT_LIST_DIR}/KairoNativeDefaults.cmake")

# Everything built into a machine app's library binds inside it: only the JNI entry points
# (JNIEXPORT) leave it. With default visibility every core function was exported and interposable,
# so calls between them went through the PLT (Kairo68#8: 24,858 jump slots in libkairo68.so, 382
# with these; 2.9% more frames on the RG DS). Set before the app adds its core, so the core's
# targets take them too.
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

get_filename_component(KAIRO_SHARED_ROOT "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
option(KAIRO_SHADERS "Present frames through the Slang shader runtime" ON)
# On Android: the shared boot test built into the app's library in place of the JNI surface, with
# <product>_test to run it on the device (shared/tools/device-perf.ps1, Kairo#192).
option(KAIRO_DEVICE_TEST "Build the shared boot test as the app's library, to run on the device" OFF)

# The Android system libraries the app's library links, and so the device test's: a core may draw
# its picture with OpenGL ES (Kairo98's GPU picture), and the frame host plays through AAudio.
set(KAIRO_ANDROID_LIBRARIES log android aaudio EGL GLESv3)

function(kairo_machine_library target)
    set(_test_sources "${KAIRO_SHARED_ROOT}/host-test/main.cpp" "${KAIRO_SHARED_ROOT}/host-test/harness.cpp"
        "${KAIRO_SHARED_ROOT}/host-test/checks.cpp"
        "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp/kairo/state_paths.cpp"
        "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp/kairo/midi_out.cpp")
    if(NOT ANDROID)
        # On the PC the same sources are the shared boot test (host-test/, Kairo#115), named after
        # the product, so the app's own CMakeLists says once what its core is.
        add_executable(${target} ${_test_sources} "${KAIRO_SHARED_ROOT}/host-test/entry.cpp" ${ARGN})
        target_compile_features(${target} PRIVATE cxx_std_17)
        target_include_directories(${target} PRIVATE "${KAIRO_SHARED_ROOT}/host-test/include"
            "${KAIRO_SHARED_ROOT}/host-test" "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp")
        # One file to copy and run: no MinGW runtime beside it.
        if(MINGW)
            target_link_options(${target} PRIVATE -static)
        endif()
        return()
    endif()
    if(KAIRO_DEVICE_TEST)
        # The test in the library the app's CMakeLists links its core into, built as the app's
        # library is (shared, PIC, hidden visibility, its profile), so the device measures the code
        # the app runs; the NDK's own <android/log.h> in place of the PC's stand-in.
        add_library(${target} SHARED ${_test_sources} ${ARGN})
        target_compile_features(${target} PRIVATE cxx_std_17)
        kairo_native_debug_defaults(${target})
        target_include_directories(${target} PRIVATE "${KAIRO_SHARED_ROOT}/host-test" "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp")
        target_link_libraries(${target} PRIVATE ${KAIRO_ANDROID_LIBRARIES})
        add_executable(${target}_test "${KAIRO_SHARED_ROOT}/host-test/entry.cpp")
        target_link_libraries(${target}_test PRIVATE ${target})
        return()
    endif()
    add_library(${target} SHARED "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp/kairo/machine_jni.cpp"
        "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp/kairo/state_paths.cpp"
            "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp/kairo/midi_out.cpp" ${ARGN})
    target_compile_features(${target} PRIVATE cxx_std_17)
    kairo_native_debug_defaults(${target})
    target_include_directories(${target} PRIVATE "${KAIRO_SHARED_ROOT}/frontend/src/main/cpp")
    if(KAIRO_SHADERS)
        target_include_directories(${target} PRIVATE "${KAIRO_SHARED_ROOT}/third_party/librashader/include")
        target_link_libraries(${target} PRIVATE dl)
    else()
        target_compile_definitions(${target} PRIVATE KAIRO_NO_SHADERS)
    endif()
    # Frames reach the window through OpenGL ES with or without shaders (gl_frame_presenter.h).
    target_link_libraries(${target} PRIVATE ${KAIRO_ANDROID_LIBRARIES})
endfunction()

# On the PC: a library the core loads by file name (KairoST's Hatari, KairoDos's Staging) beside the
# boot test, copied whenever the library changes. A POST_BUILD copy ran only when the test itself
# relinked, so a change in the library alone left the test loading the old copy.
function(kairo_library_beside_test target library)
    if(ANDROID)
        message(FATAL_ERROR "kairo_library_beside_test is for the PC boot test")
    endif()
    set(beside "$<TARGET_FILE_DIR:${target}>/$<TARGET_FILE_NAME:${library}>")
    # Named by both, as one library may go beside several executables.
    set(stamp "${CMAKE_CURRENT_BINARY_DIR}/${library}-beside-${target}.stamp")
    add_custom_command(OUTPUT "${stamp}"
        COMMAND ${CMAKE_COMMAND} -E copy_if_different $<TARGET_FILE:${library}> "${beside}"
        COMMAND ${CMAKE_COMMAND} -E touch "${stamp}"
        DEPENDS ${library} $<TARGET_FILE:${library}>)
    add_custom_target(${library}_beside_${target} DEPENDS "${stamp}")
    add_dependencies(${target} ${library}_beside_${target})
endfunction()
