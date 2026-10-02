# First-party Android native targets use optimized Debug code without removing
# debug symbols, assertions, or diagnostics. Core-specific flags stay in hosts.
include_guard(GLOBAL)

function(kairo_native_debug_defaults target)
    if(NOT TARGET "${target}")
        message(FATAL_ERROR "kairo_native_debug_defaults: unknown target ${target}")
    endif()
    target_compile_options(${target} PRIVATE $<$<CONFIG:Debug>:-O2>)
endfunction()
