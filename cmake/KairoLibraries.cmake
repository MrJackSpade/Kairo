# Libraries more than one Kairo core links, built once from the pinned snapshots in shared's
# third_party/ (docs/source-import.md): zlib as kairo_zlib and libpng as kairo_png. An app's core
# includes this file and links the targets, instead of carrying copies of its own.
#
# libpng is built with its stock configuration and without NEON code: the cores use it for
# screenshots and images, never per frame.

include_guard(GLOBAL)

get_filename_component(KAIRO_LIBRARIES_THIRD_PARTY "${CMAKE_CURRENT_LIST_DIR}/../third_party" ABSOLUTE)

# zlib 1.3.2
set(_kairo_zlib "${KAIRO_LIBRARIES_THIRD_PARTY}/zlib")
add_library(kairo_zlib STATIC
    "${_kairo_zlib}/adler32.c" "${_kairo_zlib}/compress.c" "${_kairo_zlib}/crc32.c" "${_kairo_zlib}/deflate.c"
    "${_kairo_zlib}/gzclose.c" "${_kairo_zlib}/gzlib.c" "${_kairo_zlib}/gzread.c" "${_kairo_zlib}/gzwrite.c"
    "${_kairo_zlib}/infback.c" "${_kairo_zlib}/inffast.c" "${_kairo_zlib}/inflate.c" "${_kairo_zlib}/inftrees.c"
    "${_kairo_zlib}/trees.c" "${_kairo_zlib}/uncompr.c" "${_kairo_zlib}/zutil.c")
target_include_directories(kairo_zlib PUBLIC "${_kairo_zlib}")
set_target_properties(kairo_zlib PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(NOT WIN32)
    target_compile_definitions(kairo_zlib PRIVATE HAVE_UNISTD_H)
endif()

# libpng 1.6.59
set(_kairo_png "${KAIRO_LIBRARIES_THIRD_PARTY}/libpng")
set(_kairo_png_conf "${CMAKE_CURRENT_BINARY_DIR}/kairo_libpng")
configure_file("${_kairo_png}/scripts/pnglibconf.h.prebuilt" "${_kairo_png_conf}/pnglibconf.h" COPYONLY)
add_library(kairo_png STATIC
    "${_kairo_png}/png.c" "${_kairo_png}/pngerror.c" "${_kairo_png}/pngget.c" "${_kairo_png}/pngmem.c"
    "${_kairo_png}/pngpread.c" "${_kairo_png}/pngread.c" "${_kairo_png}/pngrio.c" "${_kairo_png}/pngrtran.c"
    "${_kairo_png}/pngrutil.c" "${_kairo_png}/pngset.c" "${_kairo_png}/pngtrans.c" "${_kairo_png}/pngwio.c"
    "${_kairo_png}/pngwrite.c" "${_kairo_png}/pngwtran.c" "${_kairo_png}/pngwutil.c")
target_include_directories(kairo_png PUBLIC "${_kairo_png}" "${_kairo_png_conf}")
target_compile_definitions(kairo_png PRIVATE PNG_ARM_NEON_OPT=0)
set_target_properties(kairo_png PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_link_libraries(kairo_png PUBLIC kairo_zlib)
