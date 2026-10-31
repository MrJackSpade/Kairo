/*
 * kairo_chd_image: the host side of the shared CHD reader, for the catalog tools.
 *
 *   kairo_chd_image image <file.chd>   writes the CHD's disc image to standard output
 *   kairo_chd_image cue <file.chd>     writes its cue sheet, naming the image <file>.bin
 *   kairo_chd_image tracks <file.chd>  lists its tracks
 *
 * Exits non-zero with the reason on standard error when the CHD cannot be read.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#if defined(_WIN32)
#include <fcntl.h>
#include <io.h>
#endif

#include "kairo_chd.h"

static int image(kairo_chd *chd) {
    static unsigned char buffer[1 << 20];
    uint64_t offset = 0;
    uint64_t size = kairo_chd_size(chd);
#if defined(_WIN32)
    _setmode(_fileno(stdout), _O_BINARY);
#endif
    while (offset < size) {
        size_t read = kairo_chd_read(chd, offset, buffer, sizeof(buffer));
        if (read == (size_t)-1) {
            fprintf(stderr, "%s\n", kairo_chd_error(chd));
            return 1;
        }
        if (read == 0 || fwrite(buffer, 1, read, stdout) != read) {
            fprintf(stderr, "Cannot write the image\n");
            return 1;
        }
        offset += read;
    }
    return fflush(stdout) == 0 ? 0 : 1;
}

static int cue(kairo_chd *chd, const char *path) {
    const char *name = strrchr(path, '/');
    const char *back = strrchr(path, '\\');
    char bin[1024];
    size_t length;
    char *text;
    if (back && (!name || back > name)) name = back;
    name = name ? name + 1 : path;
    snprintf(bin, sizeof(bin), "%.*s.bin", (int)(strlen(name) - 4), name);
    length = kairo_chd_cue(chd, bin, NULL, 0);
    text = (char *)malloc(length + 1);
    if (!text) return 1;
    kairo_chd_cue(chd, bin, text, length + 1);
    fputs(text, stdout);
    free(text);
    return 0;
}

static int tracks(kairo_chd *chd) {
    int i;
    for (i = 0; i < kairo_chd_track_count(chd); i++) {
        const kairo_chd_track *t = kairo_chd_track_at(chd, i);
        printf("%02d %s sector_size=%u sectors=%u pregap=%u%s postgap=%u first=%u offset=%llu\n",
            t->number, t->audio ? "audio" : t->mode == 1 ? "mode1" : "mode2", t->sector_size, t->sectors,
            t->pregap, t->pregap_stored ? "(stored)" : "", t->postgap, t->first_sector,
            (unsigned long long)t->offset);
    }
    printf("size=%llu\n", (unsigned long long)kairo_chd_size(chd));
    return 0;
}

int main(int argc, char **argv) {
    char error[256];
    kairo_chd *chd;
    int result;
    if (argc != 3 || (strcmp(argv[1], "image") && strcmp(argv[1], "cue") && strcmp(argv[1], "tracks"))) {
        fprintf(stderr, "usage: kairo_chd_image image|cue|tracks <file.chd>\n");
        return 2;
    }
    chd = kairo_chd_open(argv[2], error, sizeof(error));
    if (!chd) {
        fprintf(stderr, "%s: %s\n", argv[2], error);
        return 1;
    }
    result = !strcmp(argv[1], "image") ? image(chd) : !strcmp(argv[1], "cue") ? cue(chd, argv[2]) : tracks(chd);
    kairo_chd_close(chd);
    return result;
}
