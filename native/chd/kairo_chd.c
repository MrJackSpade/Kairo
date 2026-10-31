#if defined(__ANDROID__)
/* Bionic declares funopen64 (kairo_chd_fopen) for BSD sources. */
#define _BSD_SOURCE
#endif

#include "kairo_chd.h"

#include <ctype.h>
#include <errno.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#if defined(__ANDROID__)
#include <android/log.h>
#endif

#include <libchdr/cdrom.h>
#include <libchdr/chd.h>

#define CHD_TAG_TEXT_MAX 256

/** A run of sectors in one track stored alike: the pregap when stored, then the track. */
typedef struct region {
    uint64_t offset;      /* the run's first byte in the image */
    uint64_t bytes;
    uint32_t chd_frame;   /* its first frame in the CHD */
    unsigned sector_size; /* bytes per sector in the image */
    int audio;            /* samples are big-endian in the CHD */
} region;

struct kairo_chd {
    chd_file *file;
    /* The stream opened from a file descriptor, closed after the CHD. */
    FILE *owned;
    uint32_t frames_per_hunk;
    uint32_t hunk_bytes;
    int tracks;
    kairo_chd_track track[CD_MAX_TRACKS];
    int regions;
    region region[CD_MAX_TRACKS * 2];
    uint64_t size;
    uint8_t *hunk;
    uint32_t cached_hunk;
    int has_cached_hunk;
    pthread_mutex_t lock;
    char error[256];
};

static void fail(char *error, size_t size, const char *format, ...) {
    va_list args;
    if (!error || !size) return;
    va_start(args, format);
    vsnprintf(error, size, format, args);
    va_end(args);
}

/** The bytes each sector of a CHD track type keeps; 0 for a type this reader does not know. */
static unsigned sector_size_of(const char *type, int *audio, int *mode) {
    static const struct { const char *name; unsigned size; int audio; int mode; } types[] = {
        {"MODE1", 2048, 0, 1}, {"MODE1/2048", 2048, 0, 1},
        {"MODE1_RAW", 2352, 0, 1}, {"MODE1/2352", 2352, 0, 1},
        {"MODE2", 2336, 0, 2}, {"MODE2/2336", 2336, 0, 2},
        {"MODE2_FORM1", 2048, 0, 2}, {"MODE2/2048", 2048, 0, 2},
        {"MODE2_FORM2", 2324, 0, 2}, {"MODE2/2324", 2324, 0, 2},
        {"MODE2_FORM_MIX", 2336, 0, 2},
        {"MODE2_RAW", 2352, 0, 2}, {"MODE2/2352", 2352, 0, 2},
        {"AUDIO", 2352, 1, 0},
    };
    size_t i;
    for (i = 0; i < sizeof(types) / sizeof(types[0]); i++) {
        if (!strcmp(types[i].name, type)) {
            *audio = types[i].audio;
            *mode = types[i].mode;
            return types[i].size;
        }
    }
    return 0;
}

static int read_tracks(kairo_chd *chd, char *error, size_t error_size) {
    char text[CHD_TAG_TEXT_MAX];
    uint32_t chd_frame = 0;
    uint32_t sector = 0;
    uint64_t offset = 0;
    int v2 = 1;
    int index;
    for (index = 0; index < CD_MAX_TRACKS; index++) {
        int number = 0, frames = 0, pregap = 0, postgap = 0;
        char type[32] = "", subtype[32] = "", pgtype[32] = "", pgsub[32] = "";
        uint32_t length = 0;
        chd_error result = chd_get_metadata(chd->file, v2 ? CDROM_TRACK_METADATA2_TAG : CDROM_TRACK_METADATA_TAG,
            (uint32_t)index, text, sizeof(text) - 1, &length, NULL, NULL);
        if (result == CHDERR_METADATA_NOT_FOUND && index == 0 && v2) {
            v2 = 0;
            index = -1;
            continue;
        }
        if (result == CHDERR_METADATA_NOT_FOUND) break;
        if (result != CHDERR_NONE) {
            fail(error, error_size, "Cannot read track %d's metadata: %s", index + 1, chd_error_string(result));
            return 0;
        }
        text[length < sizeof(text) ? length : sizeof(text) - 1] = '\0';
        if (v2) {
            if (sscanf(text, "TRACK:%d TYPE:%31s SUBTYPE:%31s FRAMES:%d PREGAP:%d PGTYPE:%31s PGSUB:%31s POSTGAP:%d",
                    &number, type, subtype, &frames, &pregap, pgtype, pgsub, &postgap) != 8) {
                fail(error, error_size, "Unreadable track metadata: %s", text);
                return 0;
            }
        } else if (sscanf(text, "TRACK:%d TYPE:%31s SUBTYPE:%31s FRAMES:%d", &number, type, subtype, &frames) != 4) {
            fail(error, error_size, "Unreadable track metadata: %s", text);
            return 0;
        }
        if (frames <= 0 || pregap < 0 || postgap < 0 || (v2 && pregap > frames && pgtype[0] == 'V')) {
            fail(error, error_size, "Track %d has impossible lengths: %s", number, text);
            return 0;
        }
        {
            kairo_chd_track *track = &chd->track[chd->tracks];
            int audio = 0, mode = 0, pg_audio = 0, pg_mode = 0;
            unsigned size = sector_size_of(type, &audio, &mode);
            int stored = v2 && pgtype[0] == 'V';
            unsigned pg_size = stored ? sector_size_of(pgtype + 1, &pg_audio, &pg_mode) : 0;
            uint32_t stored_pregap = stored ? (uint32_t)pregap : 0;
            if (!size || (stored && !pg_size)) {
                fail(error, error_size, "Track %d has a type this reader does not know: %s", number, text);
                return 0;
            }
            if (stored && pg_size != size) {
                fail(error, error_size, "Track %d's stored pregap (%s) differs in sector size from the track (%s)",
                    number, pgtype + 1, type);
                return 0;
            }
            track->number = number;
            track->audio = audio;
            track->mode = mode;
            track->sector_size = size;
            track->sectors = (uint32_t)frames;
            track->pregap = (uint32_t)pregap;
            track->pregap_stored = stored;
            track->postgap = (uint32_t)postgap;
            track->first_sector = sector;
            track->offset = offset;
            if (stored_pregap) {
                region *r = &chd->region[chd->regions++];
                r->offset = offset;
                r->bytes = (uint64_t)stored_pregap * pg_size;
                r->chd_frame = chd_frame;
                r->sector_size = pg_size;
                r->audio = pg_audio;
            }
            {
                region *r = &chd->region[chd->regions++];
                r->offset = offset + (uint64_t)stored_pregap * pg_size;
                r->bytes = (uint64_t)(frames - stored_pregap) * size;
                r->chd_frame = chd_frame + stored_pregap;
                r->sector_size = size;
                r->audio = audio;
            }
            offset += (uint64_t)stored_pregap * pg_size + (uint64_t)(frames - stored_pregap) * size;
            sector += (uint32_t)frames;
            /* Each track's frames start on a multiple of CD_TRACK_PADDING in the CHD. */
            chd_frame += ((uint32_t)frames + CD_TRACK_PADDING - 1) / CD_TRACK_PADDING * CD_TRACK_PADDING;
            chd->tracks++;
        }
    }
    if (!chd->tracks) {
        fail(error, error_size, "The CHD holds no CD tracks");
        return 0;
    }
    chd->size = offset;
    return 1;
}

/** Reads the opened CHD's tracks; on failure frees [chd] and its file and returns NULL. */
static kairo_chd *finish_open(kairo_chd *chd, chd_error opened, char *error, size_t error_size) {
    const chd_header *header;
    if (opened != CHDERR_NONE) {
        fail(error, error_size, "%s", chd_error_string(opened));
        if (chd->owned) fclose(chd->owned);
        free(chd);
        return NULL;
    }
    header = chd_get_header(chd->file);
    if (header->unitbytes != CD_FRAME_SIZE || header->hunkbytes % CD_FRAME_SIZE != 0) {
        fail(error, error_size, "The CHD is not a CD (%u bytes per unit)", header->unitbytes);
    } else {
        chd->hunk_bytes = header->hunkbytes;
        chd->frames_per_hunk = header->hunkbytes / CD_FRAME_SIZE;
        chd->hunk = (uint8_t *)malloc(chd->hunk_bytes);
        if (!chd->hunk) fail(error, error_size, "Out of memory");
        else if (read_tracks(chd, error, error_size)) {
            pthread_mutex_init(&chd->lock, NULL);
            return chd;
        }
    }
    free(chd->hunk);
    chd_close(chd->file);
    if (chd->owned) fclose(chd->owned);
    free(chd);
    return NULL;
}

kairo_chd *kairo_chd_open(const char *path, char *error, size_t error_size) {
    kairo_chd *chd = (kairo_chd *)calloc(1, sizeof(kairo_chd));
    if (!chd) {
        fail(error, error_size, "Out of memory");
        return NULL;
    }
    return finish_open(chd, chd_open(path, CHD_OPEN_READ, NULL, &chd->file), error, error_size);
}

kairo_chd *kairo_chd_open_fd(int fd, char *error, size_t error_size) {
    kairo_chd *chd = (kairo_chd *)calloc(1, sizeof(kairo_chd));
    int copy;
    if (!chd) {
        fail(error, error_size, "Out of memory");
        return NULL;
    }
    copy = dup(fd);
    chd->owned = copy < 0 ? NULL : fdopen(copy, "rb");
    if (!chd->owned) {
        fail(error, error_size, "Cannot open the CHD: %s", strerror(errno));
        if (copy >= 0) close(copy);
        free(chd);
        return NULL;
    }
    return finish_open(chd, chd_open_file(chd->owned, CHD_OPEN_READ, NULL, &chd->file), error, error_size);
}

void kairo_chd_close(kairo_chd *chd) {
    if (!chd) return;
    chd_close(chd->file);
    if (chd->owned) fclose(chd->owned);
    pthread_mutex_destroy(&chd->lock);
    free(chd->hunk);
    free(chd);
}

int kairo_chd_track_count(const kairo_chd *chd) { return chd->tracks; }

const kairo_chd_track *kairo_chd_track_at(const kairo_chd *chd, int index) {
    return index >= 0 && index < chd->tracks ? &chd->track[index] : NULL;
}

uint64_t kairo_chd_size(const kairo_chd *chd) { return chd->size; }

const char *kairo_chd_error(const kairo_chd *chd) { return chd->error; }

/** The frame's sector data, decoding its hunk unless it is the one decoded last. */
static const uint8_t *frame_data(kairo_chd *chd, uint32_t frame) {
    uint32_t hunk = frame / chd->frames_per_hunk;
    if (!chd->has_cached_hunk || chd->cached_hunk != hunk) {
        chd_error result = chd_read(chd->file, hunk, chd->hunk);
        if (result != CHDERR_NONE) {
            chd->has_cached_hunk = 0;
            fail(chd->error, sizeof(chd->error), "Cannot decode hunk %u: %s", hunk, chd_error_string(result));
            return NULL;
        }
        chd->cached_hunk = hunk;
        chd->has_cached_hunk = 1;
    }
    return chd->hunk + (size_t)(frame % chd->frames_per_hunk) * CD_FRAME_SIZE;
}

static size_t read_locked(kairo_chd *chd, uint64_t offset, uint8_t *out, size_t length) {
    size_t done = 0;
    int r = 0;
    while (done < length && offset < chd->size) {
        const region *run;
        uint64_t within;
        uint32_t sector;
        unsigned in_sector;
        size_t take;
        const uint8_t *data;
        while (r + 1 < chd->regions && chd->region[r + 1].offset <= offset) r++;
        while (r > 0 && chd->region[r].offset > offset) r--;
        run = &chd->region[r];
        within = offset - run->offset;
        if (within >= run->bytes) {
            /* A region of no bytes (a track that is all stored pregap); move on. */
            r++;
            continue;
        }
        sector = (uint32_t)(within / run->sector_size);
        in_sector = (unsigned)(within % run->sector_size);
        take = run->sector_size - in_sector;
        if (take > length - done) take = length - done;
        data = frame_data(chd, run->chd_frame + sector);
        if (!data) return (size_t)-1;
        if (run->audio) {
            unsigned i;
            for (i = 0; i < take; i++) out[done + i] = data[(in_sector + i) ^ 1];
        } else {
            memcpy(out + done, data + in_sector, take);
        }
        done += take;
        offset += take;
    }
    return done;
}

size_t kairo_chd_read(kairo_chd *chd, uint64_t offset, void *buffer, size_t length) {
    size_t read;
    pthread_mutex_lock(&chd->lock);
    read = read_locked(chd, offset, (uint8_t *)buffer, length);
    pthread_mutex_unlock(&chd->lock);
    return read;
}

static const char *cue_type(const kairo_chd_track *track) {
    if (track->audio) return "AUDIO";
    switch (track->sector_size) {
    case 2048: return track->mode == 1 ? "MODE1/2048" : "MODE2/2048";
    case 2324: return "MODE2/2324";
    case 2336: return "MODE2/2336";
    default: return track->mode == 1 ? "MODE1/2352" : "MODE2/2352";
    }
}

static size_t append(char *out, size_t out_size, size_t at, const char *format, ...) {
    char line[160];
    int written;
    va_list args;
    va_start(args, format);
    written = vsnprintf(line, sizeof(line), format, args);
    va_end(args);
    if (written > 0 && at + (size_t)written < out_size) memcpy(out + at, line, (size_t)written + 1);
    return at + (written > 0 ? (size_t)written : 0);
}

static size_t append_msf(char *out, size_t out_size, size_t at, const char *label, uint32_t sectors) {
    return append(out, out_size, at, "%s %02u:%02u:%02u\r\n", label, sectors / 75 / 60, sectors / 75 % 60, sectors % 75);
}

size_t kairo_chd_cue(const kairo_chd *chd, const char *file_name, char *out, size_t out_size) {
    size_t at = 0;
    int i;
    if (out && out_size) out[0] = '\0';
    at = append(out, out_size, at, "FILE \"%s\" BINARY\r\n", file_name);
    for (i = 0; i < chd->tracks; i++) {
        const kairo_chd_track *track = &chd->track[i];
        at = append(out, out_size, at, "  TRACK %02d %s\r\n", track->number, cue_type(track));
        if (track->pregap_stored && track->pregap) {
            at = append_msf(out, out_size, at, "    INDEX 00", track->first_sector);
            at = append_msf(out, out_size, at, "    INDEX 01", track->first_sector + track->pregap);
        } else {
            if (track->pregap) at = append_msf(out, out_size, at, "    PREGAP", track->pregap);
            at = append_msf(out, out_size, at, "    INDEX 01", track->first_sector);
        }
        if (track->postgap) at = append_msf(out, out_size, at, "    POSTGAP", track->postgap);
    }
    return at;
}

int kairo_chd_is_chd(const char *path) {
    size_t length = strlen(path);
    return length >= 4 && path[length - 4] == '.' && tolower((unsigned char)path[length - 3]) == 'c' &&
        tolower((unsigned char)path[length - 2]) == 'h' && tolower((unsigned char)path[length - 1]) == 'd';
}

#if defined(__ANDROID__)
typedef struct stream {
    kairo_chd *chd;
    uint64_t position;
} stream;

static int stream_read(void *cookie, char *buffer, int size) {
    stream *s = (stream *)cookie;
    size_t read = kairo_chd_read(s->chd, s->position, buffer, (size_t)size);
    if (read == (size_t)-1) {
        errno = EIO;
        return -1;
    }
    s->position += read;
    return (int)read;
}

static fpos64_t stream_seek(void *cookie, fpos64_t offset, int whence) {
    stream *s = (stream *)cookie;
    int64_t base;
    if (whence != SEEK_SET && whence != SEEK_CUR && whence != SEEK_END) {
        errno = EINVAL;
        return -1;
    }
    base = whence == SEEK_SET ? 0 : whence == SEEK_CUR ? (int64_t)s->position : (int64_t)kairo_chd_size(s->chd);
    if (base + offset < 0) {
        errno = EINVAL;
        return -1;
    }
    s->position = (uint64_t)(base + offset);
    return (fpos64_t)s->position;
}

static int stream_close(void *cookie) {
    stream *s = (stream *)cookie;
    kairo_chd_close(s->chd);
    free(s);
    return 0;
}

FILE *kairo_chd_fopen(const char *path) {
    char error[256];
    stream *s = (stream *)calloc(1, sizeof(stream));
    FILE *file;
    if (!s) {
        errno = ENOMEM;
        return NULL;
    }
    s->chd = kairo_chd_open(path, error, sizeof(error));
    if (!s->chd) {
        __android_log_print(ANDROID_LOG_ERROR, "KairoChd", "%s: %s", path, error);
        free(s);
        errno = EIO;
        return NULL;
    }
    file = funopen64(s, stream_read, NULL, stream_seek, stream_close);
    if (!file) stream_close(s);
    return file;
}
#endif
