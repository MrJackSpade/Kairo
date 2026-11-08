# The MIDI synths more than one Kairo core links, built once from the pinned snapshots in shared's
# third_party/ (docs/source-import.md): Munt's libmt32emu (MT-32 and CM-32L, with the player's ROMs)
# as MT32Emu::mt32emu, and FluidSynth (General MIDI from a SoundFont) as FluidSynth::libfluidsynth,
# the names their own CMake packages give them. An app's core includes this file and links the
# targets, instead of carrying copies of its own.
#
# Both are static and LGPL-2.1-or-later. FluidSynth is built for its synthesis API only: no audio or
# MIDI drivers, no file rendering and no libsndfile, glib, OpenMP or network (the cores hand it MIDI
# and take its samples themselves); threads are C++11's.

include_guard(GLOBAL)

get_filename_component(KAIRO_MIDI_THIRD_PARTY "${CMAKE_CURRENT_LIST_DIR}/../third_party" ABSOLUTE)

function(_kairo_midi_build)
    # Options set here stay local to this function's scope, so the including project keeps its own.
    set(BUILD_SHARED_LIBS OFF)
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)

    # Munt libmt32emu 2.7.3
    set(libmt32emu_SHARED OFF)
    set(libmt32emu_C_INTERFACE ON)
    set(libmt32emu_CPP_INTERFACE ON)
    set(libmt32emu_PKGCONFIG_INSTALL_DIR "" CACHE INTERNAL "")
    add_subdirectory("${KAIRO_MIDI_THIRD_PARTY}/munt/mt32emu" kairo_mt32emu EXCLUDE_FROM_ALL)

    # FluidSynth 2.5.2, with GCEM 1.18.0 (header only)
    set(GCEM_INCLUDE_DIR "${KAIRO_MIDI_THIRD_PARTY}/gcem/include" CACHE PATH "" FORCE)
    set(osal cpp11 CACHE STRING "" FORCE)
    foreach(option
            enable-alsa enable-aufile enable-dbus enable-jack enable-ladspa enable-libinstpatch
            enable-libsndfile enable-midishare enable-opensles enable-oboe enable-network enable-oss
            enable-dsound enable-wasapi enable-waveout enable-winmidi enable-sdl3 enable-pulseaudio
            enable-pipewire enable-readline enable-openmp enable-systemd enable-coreaudio enable-coremidi
            enable-framework enable-dart enable-kai enable-ipv6 enable-coverage enable-floats
            enable-fpe-check enable-portaudio enable-profiling enable-trap-on-fpe enable-ubsan)
        set(${option} OFF CACHE BOOL "" FORCE)
    endforeach()
    set(CMAKE_DISABLE_FIND_PACKAGE_Doxygen ON)
    add_subdirectory("${KAIRO_MIDI_THIRD_PARTY}/fluidsynth" kairo_fluidsynth EXCLUDE_FROM_ALL)
endfunction()
_kairo_midi_build()

# Munt names its own alias; FluidSynth's is only in its installed package.
add_library(FluidSynth::libfluidsynth ALIAS libfluidsynth)
