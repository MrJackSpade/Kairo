# Hidden extern declarations for a core's C (KairoX86#376).
#
# kairo_hide_c_externs() force-includes kairo_hidden_externs.h into every C file of the targets the
# calling directory (and its subdirectories) creates after the call. A global defined in the same
# shared library is then read with one PC-relative load instead of through the GOT. Android (ELF)
# only: Windows' PE/COFF builds have no GOT to remove.
include_guard(GLOBAL)

function(kairo_hide_c_externs)
    if(NOT ANDROID)
        return()
    endif()
    set(_header "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/kairo_hidden_externs.h")
    add_compile_options("$<$<COMPILE_LANGUAGE:C>:SHELL:-include \"${_header}\">")
endfunction()
