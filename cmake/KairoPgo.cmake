# Profile-guided optimization from a profile kept in the repository (KairoDos#70, Kairo68#6).
#
# kairo_use_profile(<text> <manifest> <source root>) builds every target in the calling directory
# with the LLVM text profile <text>. The manifest (shared/tools/pgo_freeze.py writes it) records
# the text's SHA-256 (portableTextSha256) and the Clang version that recorded it
# (compilerVersion). Function names in the text hold the checkout's root as @KAIRO_SOURCE@/; the
# actual root is put back before llvm-profdata indexes it, so LLVM rehashes named indirect-call
# targets for the relocated names. A changed text, another compiler or a function whose code no
# longer matches its profile is a build error: a stale profile never silently stops applying.
include_guard(GLOBAL)

function(kairo_use_profile text manifest source_root)
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${text}" "${manifest}")
    file(READ "${manifest}" _manifest)
    string(JSON _expected_hash GET "${_manifest}" portableTextSha256)
    string(JSON _compiler_version GET "${_manifest}" compilerVersion)
    if(NOT CMAKE_CXX_COMPILER_ID STREQUAL "Clang" OR
       NOT CMAKE_CXX_COMPILER_VERSION VERSION_EQUAL _compiler_version)
        message(FATAL_ERROR "PGO profile requires its recorded Clang ${_compiler_version}; use -DKAIRO_PGO=OFF for another compiler")
    endif()
    file(SHA256 "${text}" _actual_hash)
    if(NOT _actual_hash STREQUAL _expected_hash)
        message(FATAL_ERROR "PGO profile does not match its manifest (${manifest})")
    endif()
    get_filename_component(_llvm_bin "${CMAKE_CXX_COMPILER}" DIRECTORY)
    find_program(_profdata NAMES llvm-profdata llvm-profdata.exe
        PATHS "${_llvm_bin}" NO_DEFAULT_PATH NO_CACHE REQUIRED)
    get_filename_component(_root "${source_root}" ABSOLUTE)
    file(TO_CMAKE_PATH "${_root}" _root)
    string(REGEX REPLACE "/+$" "" _root "${_root}")
    string(SHA256 _root_hash "${_root}/")
    # Both hashes are in the flag path: a profile or checkout change rebuilds users even when a
    # compiler depfile does not list the profile as an input.
    set(_dir "${CMAKE_CURRENT_BINARY_DIR}/pgo/${_expected_hash}/${_root_hash}")
    file(MAKE_DIRECTORY "${_dir}")
    set(_profile "${_dir}/profile.profdata")
    if(NOT EXISTS "${_profile}")
        file(READ "${text}" _contents)
        string(REPLACE "@KAIRO_SOURCE@/" "${_root}/" _contents "${_contents}")
        file(WRITE "${_dir}/profile.proftext" "${_contents}")
        execute_process(COMMAND "${_profdata}" merge "${_dir}/profile.proftext" -o "${_profile}.pending"
            RESULT_VARIABLE _result ERROR_VARIABLE _error)
        if(NOT _result EQUAL 0)
            file(REMOVE "${_profile}.pending")
            message(FATAL_ERROR "Could not prepare PGO profile: ${_error}")
        endif()
        file(RENAME "${_profile}.pending" "${_profile}")
    endif()
    add_compile_options("-fprofile-use=${_profile}" -Werror=profile-instr-out-of-date)
    message(STATUS "Kairo PGO: profile ${_expected_hash}, root ${_root}/")
endfunction()
