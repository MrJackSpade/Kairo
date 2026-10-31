/*
 * A CD kept as a CHD (MAME's compressed disc image), read as the single raw image it was made from:
 * every track's sectors in disc order at the size it was stored with (2352 for raw and audio
 * tracks, 2048 for cooked data), audio as little-endian samples, without the CHD's track padding.
 * A CHD made from a CloneCD .img or a one-file BIN/CUE reads back byte for byte as that file, so
 * its content ID is the image's, and a core reads its tracks as it reads that image.
 *
 * Built on libchdr (third_party/libchdr). One handle may be shared between threads; reads are
 * serialised.
 */
#ifndef KAIRO_CHD_H
#define KAIRO_CHD_H

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(__GNUC__)
#define KAIRO_CHD_EXPORT __attribute__((visibility("default")))
#else
#define KAIRO_CHD_EXPORT
#endif

typedef struct kairo_chd kairo_chd;

typedef struct kairo_chd_track {
    /** The track's number on the disc, from 1. */
    int number;
    /** 1 for an audio track, else 0. */
    int audio;
    /** The data track's CD mode (1 or 2); 0 for audio. */
    int mode;
    /** Bytes per sector in the image. */
    unsigned sector_size;
    /** The track's sectors in the image, its pregap's included when [pregap_stored]. */
    uint32_t sectors;
    /** Pregap sectors before the track's index 1. */
    uint32_t pregap;
    /** 1 when the pregap's sectors are in the image, else they exist only on the disc. */
    int pregap_stored;
    uint32_t postgap;
    /** The track's first sector in the image, counted from the image's first. */
    uint32_t first_sector;
    /** The byte offset of that sector in the image. */
    uint64_t offset;
} kairo_chd_track;

/** Opens a CD CHD, or returns NULL with the reason in [error]. */
KAIRO_CHD_EXPORT kairo_chd *kairo_chd_open(const char *path, char *error, size_t error_size);
/** Opens the CHD read through [fd], which stays the caller's: the reader reads a duplicate. */
KAIRO_CHD_EXPORT kairo_chd *kairo_chd_open_fd(int fd, char *error, size_t error_size);
KAIRO_CHD_EXPORT void kairo_chd_close(kairo_chd *chd);

KAIRO_CHD_EXPORT int kairo_chd_track_count(const kairo_chd *chd);
KAIRO_CHD_EXPORT const kairo_chd_track *kairo_chd_track_at(const kairo_chd *chd, int index);

/** The image's size in bytes. */
KAIRO_CHD_EXPORT uint64_t kairo_chd_size(const kairo_chd *chd);

/**
 * Reads [length] bytes of the image at [offset]. Returns the bytes read, fewer only past the
 * image's end, or (size_t)-1 when the CHD cannot be decoded; kairo_chd_error says why.
 */
KAIRO_CHD_EXPORT size_t kairo_chd_read(kairo_chd *chd, uint64_t offset, void *buffer, size_t length);
KAIRO_CHD_EXPORT const char *kairo_chd_error(const kairo_chd *chd);

/**
 * The image's cue sheet, naming [file_name] as its one BINARY file. Returns its length; the text
 * is written to [out] when it fits in [out_size] with its terminating zero.
 */
KAIRO_CHD_EXPORT size_t kairo_chd_cue(const kairo_chd *chd, const char *file_name, char *out, size_t out_size);

/** Whether [path] names a CHD, by its extension. */
KAIRO_CHD_EXPORT int kairo_chd_is_chd(const char *path);

#if defined(__ANDROID__)
/**
 * The image of the CHD at [path] as a read-only stdio stream, for a core that reads its discs
 * through FILE: it seeks and reads the image. NULL with errno set when it cannot be opened.
 */
KAIRO_CHD_EXPORT FILE *kairo_chd_fopen(const char *path);
#endif

#ifdef __cplusplus
}
#endif

#endif
