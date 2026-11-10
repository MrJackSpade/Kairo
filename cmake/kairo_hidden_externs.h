/*
 * Hidden extern declarations for a core's C (KairoX86#376), force-included by kairo_hide_c_externs()
 * (KairoHiddenExterns.cmake).
 *
 * -fvisibility=hidden hides what a file defines, not what it declares: a global another file of the
 * same library defines is declared default, so the compiler must assume another module may provide
 * it and reads it through the GOT (an extra dependent load on every access). Declaring everything
 * hidden lets each access be a direct PC-relative one.
 *
 * libc is declared first, before the pragma, so its functions and data (stdout, stderr) keep default
 * visibility and bind to libc.so. A libc declaration first reached after the pragma would be hidden
 * and fail the link loudly ("undefined hidden symbol: <name>"): add its header to this list.
 * Declarations with an explicit visibility attribute (a library's exported API) keep their own.
 */
#ifndef KAIRO_HIDDEN_EXTERNS_H
#define KAIRO_HIDDEN_EXTERNS_H

#include <arpa/inet.h>
#include <assert.h>
#include <ctype.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <fenv.h>
#include <float.h>
#include <ifaddrs.h>
#include <inttypes.h>
#include <limits.h>
#include <locale.h>
#include <math.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <pthread.h>
#include <pwd.h>
#include <sched.h>
#include <semaphore.h>
#include <setjmp.h>
#include <signal.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>
#include <wchar.h>
#include <wctype.h>
#include <android/hardware_buffer.h>
#include <android/log.h>

/* libc data a core declares again itself (86Box's vfio.c and sdl_plat_unix.c: extern char **environ): an explicit
   visibility outranks the pragma, so its later declarations keep binding to libc. */
extern char **environ __attribute__((visibility("default")));

#pragma GCC visibility push(hidden)

#endif
