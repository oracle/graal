#if defined(__COSMOPOLITAN__)
#define _COSMO_SOURCE
#include "libc/sysv/consts/_posix.h"
#include "libc/sysv/consts/clock.h"
#include "libc/sysv/consts/limits.h"
#include "libc/sysv/consts/lock.h"
#include "libc/sysv/consts/map.h"
#include "libc/sysv/consts/mremap.h"
#include "libc/sysv/consts/sig.h"
#include "libc/dce.h"
#include <stdbool.h>

bool stubIsWindows() {
    return IsWindows();
}

bool stubIsXnu() {
    return IsXnu();
}

int stubMREMAP_MAYMOVE() {
    return MREMAP_MAYMOVE;
}

int stubMREMAP_FIXED() {
    return MREMAP_FIXED;
}

int stubMAP_ANON() {
    return MAP_ANONYMOUS;
}

int stubMAP_NORESERVE() {
    return MAP_NORESERVE;
}

int stubMAP_JIT() {
    return MAP_JIT;
}

int stubLOCK_NB() {
    return LOCK_NB;
}

int stubNAME_MAX() {
    return _NAME_MAX;
}

int stubPATH_MAX() {
    return _PATH_MAX;
}

int stubSIG_BLOCK() {
    return SIG_BLOCK;
}

int stubSIG_UNBLOCK() {
    return SIG_UNBLOCK;
}

int stubSIG_SETMASK() {
    return SIG_SETMASK;
}

int stubCLOCK_MONOTONIC() {
    return CLOCK_MONOTONIC;
}

int stubCLOCK_THREAD_CPUTIME_ID() {
    return CLOCK_THREAD_CPUTIME_ID;
}
#endif
