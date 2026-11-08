# The MIDI synths more than one Kairo core links, built once from the pinned snapshots in shared's
# third_party/ (docs/source-import.md): Munt's libmt32emu (MT-32 and CM-32L, with the player's ROMs)
# as MT32Emu::mt32emu, and FluidSynth (General MIDI from a SoundFont) as FluidSynth::libfluidsynth,
# the names their own CMake packages give them. An app's core includes this file and links the
# targets, instead of carrying copies of its own.
#
# Both are built here from their source lists, as KairoLibraries.cmake builds zlib and libpng, not
# with their own CMake projects: those add install, uninstall, test, documentation and CPack targets
# and global options to whatever project adds them, and a core's other libraries have targets of
# the same names (SDL2's uninstall). The lists, headers and settings follow theirs (Munt's
# mt32emu/CMakeLists.txt; FluidSynth's CMakeLists.txt and src/CMakeLists.txt).
#
# Both are static and LGPL-2.1-or-later. FluidSynth is built for its synthesis API only: C++11
# threads, and no audio or MIDI drivers, file rendering, libsndfile, glib, OpenMP or network (the
# cores hand it MIDI and take its samples themselves).

include_guard(GLOBAL)
include(CheckIncludeFile)
include(CheckCXXSourceCompiles)

get_filename_component(KAIRO_MIDI_THIRD_PARTY "${CMAKE_CURRENT_LIST_DIR}/../third_party" ABSOLUTE)
find_package(Threads REQUIRED)

# ---- Munt libmt32emu 2.7.3 (its CMake data still says 2.7.2) ----
set(_kairo_munt "${KAIRO_MIDI_THIRD_PARTY}/munt/mt32emu")
set(_kairo_munt_out "${CMAKE_CURRENT_BINARY_DIR}/kairo_mt32emu/include")
include("${_kairo_munt}/cmake/project_data.cmake")
# Every API (C, C++ and plugin style), static, without version tagging: Munt's exports type 3.
set(libmt32emu_EXPORTS_TYPE 3)
set(libmt32emu_SHARED_DEFINITION "#undef MT32EMU_SHARED")
set(libmt32emu_RUNTIME_VERSION_CHECK 0)
foreach(header mt32emu.h globals.h Enumerations.h Types.h File.h FileStream.h MidiStreamParser.h ROMInfo.h
        SampleRateConverter.h Synth.h c_interface/c_interface.h c_interface/c_types.h c_interface/cpp_interface.h)
    configure_file("${_kairo_munt}/src/${header}" "${_kairo_munt_out}/mt32emu/${header}" COPYONLY)
endforeach()
file(WRITE "${_kairo_munt_out}/mt32emu.h" "#include <mt32emu/mt32emu.h>")
configure_file("${_kairo_munt}/src/config.h.in" "${_kairo_munt_out}/mt32emu/config.h")
add_library(kairo_mt32emu STATIC
    "${_kairo_munt}/src/Analog.cpp" "${_kairo_munt}/src/BReverbModel.cpp" "${_kairo_munt}/src/Display.cpp"
    "${_kairo_munt}/src/File.cpp" "${_kairo_munt}/src/FileStream.cpp" "${_kairo_munt}/src/LA32FloatWaveGenerator.cpp"
    "${_kairo_munt}/src/LA32Ramp.cpp" "${_kairo_munt}/src/LA32WaveGenerator.cpp" "${_kairo_munt}/src/MidiStreamParser.cpp"
    "${_kairo_munt}/src/Part.cpp" "${_kairo_munt}/src/Partial.cpp" "${_kairo_munt}/src/PartialManager.cpp"
    "${_kairo_munt}/src/Poly.cpp" "${_kairo_munt}/src/ROMInfo.cpp" "${_kairo_munt}/src/Synth.cpp"
    "${_kairo_munt}/src/Tables.cpp" "${_kairo_munt}/src/TVA.cpp" "${_kairo_munt}/src/TVF.cpp" "${_kairo_munt}/src/TVP.cpp"
    "${_kairo_munt}/src/sha1/sha1.cpp" "${_kairo_munt}/src/SampleRateConverter.cpp"
    "${_kairo_munt}/src/c_interface/c_interface.cpp"
    # Its own sample rate conversion
    "${_kairo_munt}/src/srchelper/srctools/src/FIRResampler.cpp" "${_kairo_munt}/src/srchelper/srctools/src/SincResampler.cpp"
    "${_kairo_munt}/src/srchelper/srctools/src/IIR2xResampler.cpp" "${_kairo_munt}/src/srchelper/srctools/src/LinearResampler.cpp"
    "${_kairo_munt}/src/srchelper/srctools/src/ResamplerModel.cpp" "${_kairo_munt}/src/srchelper/InternalResampler.cpp")
target_compile_definitions(kairo_mt32emu PRIVATE MT32EMU_WITH_INTERNAL_RESAMPLER)
target_include_directories(kairo_mt32emu PRIVATE "${_kairo_munt_out}/mt32emu" INTERFACE "${_kairo_munt_out}")
set_target_properties(kairo_mt32emu PROPERTIES POSITION_INDEPENDENT_CODE ON)
add_library(MT32Emu::mt32emu ALIAS kairo_mt32emu)

# ---- FluidSynth 2.5.2, with GCEM 1.18.0 (header only) ----
set(_kairo_fluid "${KAIRO_MIDI_THIRD_PARTY}/fluidsynth")
set(_kairo_fluid_out "${CMAKE_CURRENT_BINARY_DIR}/kairo_fluidsynth")
set(FLUIDSYNTH_VERSION_MAJOR 2)
set(FLUIDSYNTH_VERSION_MINOR 5)
set(FLUIDSYNTH_VERSION_MICRO 2)
set(FLUIDSYNTH_VERSION "${FLUIDSYNTH_VERSION_MAJOR}.${FLUIDSYNTH_VERSION_MINOR}.${FLUIDSYNTH_VERSION_MICRO}")
# config.h: the headers and maths this target has, as FluidSynth's configure looks for them; every
# driver and optional library off.
foreach(header arpa/inet.h errno.h fcntl.h inttypes.h io.h limits.h math.h netinet/in.h netinet/tcp.h pthread.h
        signal.h stdarg.h stdint.h stdio.h stdlib.h strings.h string.h sys/mman.h sys/socket.h sys/stat.h
        sys/time.h sys/types.h unistd.h windows.h getopt.h)
    string(TOUPPER "HAVE_${header}" have)
    string(REGEX REPLACE "[/.]" "_" have "${have}")
    check_include_file("${header}" ${have})
endforeach()
foreach(function SINF COSF FABSF POWF SQRTF LOGF)
    set(HAVE_${function} 1)
endforeach()
set(STDC_HEADERS 1)
set(SUPPORTS_VLA 1)
set(ENABLE_MIXER_THREADS 1)
set(ENABLE_NATIVE_DLS 1)
set(osal cpp11)
set(PACKAGE fluidsynth)
if(MINGW)
    set(MINGW32 1)
endif()
set(_kairo_cxx_standard ${CMAKE_CXX_STANDARD})
set(CMAKE_CXX_STANDARD 17)
check_cxx_source_compiles("#include <filesystem>
int main() { static_assert(__cpp_lib_filesystem == 201703L); std::filesystem::path p = std::filesystem::current_path(); }"
    HAVE_CXX_FILESYSTEM)
set(CMAKE_CXX_STANDARD ${_kairo_cxx_standard})
configure_file("${_kairo_fluid}/src/config.cmake" "${_kairo_fluid_out}/config.h")
configure_file("${_kairo_fluid}/include/fluidsynth/version.h.in" "${_kairo_fluid_out}/include/fluidsynth/version.h")
set(BUILD_SHARED_LIBS_SAVED ${BUILD_SHARED_LIBS})
set(BUILD_SHARED_LIBS 0)
configure_file("${_kairo_fluid}/include/fluidsynth.cmake" "${_kairo_fluid_out}/include/fluidsynth.h")
set(BUILD_SHARED_LIBS ${BUILD_SHARED_LIBS_SAVED})
set(_kairo_fluid_src "${_kairo_fluid}/src")
add_library(kairo_fluidsynth STATIC
    "${_kairo_fluid_src}/gentables/fluid_ct2hz.cpp" "${_kairo_fluid_src}/gentables/fluid_cb2amp.cpp"
    "${_kairo_fluid_src}/gentables/fluid_concave.cpp" "${_kairo_fluid_src}/gentables/fluid_convex.cpp"
    "${_kairo_fluid_src}/gentables/fluid_pan.cpp" "${_kairo_fluid_src}/gentables/fluid_interp_coeff.cpp"
    "${_kairo_fluid_src}/gentables/fluid_interp_coeff_linear.cpp" "${_kairo_fluid_src}/gentables/fluid_interp_coeff_sinc7.cpp"
    "${_kairo_fluid_src}/utils/fluid_conv.c" "${_kairo_fluid_src}/utils/fluid_hash.c" "${_kairo_fluid_src}/utils/fluid_list.c"
    "${_kairo_fluid_src}/utils/fluid_ringbuffer.c" "${_kairo_fluid_src}/utils/fluid_settings.c" "${_kairo_fluid_src}/utils/fluid_sys.c"
    "${_kairo_fluid_src}/utils/fluid_sys_cpp11.cpp" "${_kairo_fluid_src}/utils/fluid_file.cpp"
    "${_kairo_fluid_src}/sfloader/fluid_defsfont.c" "${_kairo_fluid_src}/sfloader/fluid_sfont.c"
    "${_kairo_fluid_src}/sfloader/fluid_sffile.c" "${_kairo_fluid_src}/sfloader/fluid_samplecache.c"
    "${_kairo_fluid_src}/sfloader/fluid_dls.cpp"
    "${_kairo_fluid_src}/rvoice/fluid_adsr_env.c" "${_kairo_fluid_src}/rvoice/fluid_chorus.c"
    "${_kairo_fluid_src}/rvoice/fluid_iir_filter_impl.cpp" "${_kairo_fluid_src}/rvoice/fluid_iir_filter.c"
    "${_kairo_fluid_src}/rvoice/fluid_lfo.c" "${_kairo_fluid_src}/rvoice/fluid_rvoice.c" "${_kairo_fluid_src}/rvoice/fluid_rvoice_dsp.cpp"
    "${_kairo_fluid_src}/rvoice/fluid_rvoice_event.c" "${_kairo_fluid_src}/rvoice/fluid_rvoice_mixer.c" "${_kairo_fluid_src}/rvoice/fluid_rev.c"
    "${_kairo_fluid_src}/synth/fluid_chan.c" "${_kairo_fluid_src}/synth/fluid_event.c" "${_kairo_fluid_src}/synth/fluid_gen.c"
    "${_kairo_fluid_src}/synth/fluid_mod.c" "${_kairo_fluid_src}/synth/fluid_synth.c" "${_kairo_fluid_src}/synth/fluid_synth_monopoly.c"
    "${_kairo_fluid_src}/synth/fluid_tuning.c" "${_kairo_fluid_src}/synth/fluid_voice.c"
    "${_kairo_fluid_src}/midi/fluid_midi.c" "${_kairo_fluid_src}/midi/fluid_midi_router.c" "${_kairo_fluid_src}/midi/fluid_seqbind.c"
    "${_kairo_fluid_src}/midi/fluid_seqbind_notes.cpp" "${_kairo_fluid_src}/midi/fluid_seq.c" "${_kairo_fluid_src}/midi/fluid_seq_queue.cpp"
    "${_kairo_fluid_src}/drivers/fluid_adriver.c" "${_kairo_fluid_src}/drivers/fluid_mdriver.c"
    "${_kairo_fluid_src}/bindings/fluid_cmd.c" "${_kairo_fluid_src}/bindings/fluid_filerenderer.c" "${_kairo_fluid_src}/bindings/fluid_ladspa.c")
set_source_files_properties("${_kairo_fluid_src}/rvoice/fluid_iir_filter_impl.cpp" PROPERTIES COMPILE_OPTIONS "-fno-math-errno;-ffast-math")
target_include_directories(kairo_fluidsynth
    PRIVATE "${_kairo_fluid_out}" "${_kairo_fluid_src}" "${_kairo_fluid_src}/drivers" "${_kairo_fluid_src}/synth"
        "${_kairo_fluid_src}/rvoice" "${_kairo_fluid_src}/midi" "${_kairo_fluid_src}/utils" "${_kairo_fluid_src}/sfloader"
        "${_kairo_fluid_src}/bindings" "${KAIRO_MIDI_THIRD_PARTY}/gcem/include"
    PUBLIC "${_kairo_fluid_out}/include" "${_kairo_fluid}/include")
set_target_properties(kairo_fluidsynth PROPERTIES POSITION_INDEPENDENT_CODE ON C_STANDARD 90 C_EXTENSIONS ON
    CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON CXX_EXTENSIONS OFF)
target_link_libraries(kairo_fluidsynth PRIVATE Threads::Threads)
if(WIN32)
    # FluidSynth's Windows settings: wide-character file names, Windows 7's API.
    target_compile_definitions(kairo_fluidsynth PRIVATE _UNICODE UNICODE _WIN32_WINNT=0x0601 WINVER=0x0601)
else()
    target_link_libraries(kairo_fluidsynth PRIVATE m)
endif()
if(ANDROID)
    target_link_libraries(kairo_fluidsynth PRIVATE log)
endif()
add_library(FluidSynth::libfluidsynth ALIAS kairo_fluidsynth)
