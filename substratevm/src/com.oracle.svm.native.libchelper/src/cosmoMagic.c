#if defined(__COSMOPOLITAN__)
#define _COSMO_SOURCE
#include "libc/calls/calls.h"
#include "libc/cosmo.h"
#include "libc/dce.h"
#include "libc/intrin/kprintf.h"
#include "libc/mem/mem.h"
#include "libc/str/str.h"
#include "libc/sysv/consts/_posix.h"
#include "libc/sysv/consts/clock.h"
#include "libc/sysv/consts/limits.h"
#include "libc/sysv/consts/lock.h"
#include "libc/sysv/consts/map.h"
#include "libc/sysv/consts/mremap.h"
#include "libc/sysv/consts/prot.h"
#include "libc/sysv/consts/sa.h"
#include "libc/sysv/consts/sig.h"
#include "libc/runtime/runtime.h"
#include "libc/thread/thread.h"
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

int stubSA_RESTART() {
    return (int) SA_RESTART;
}

int stubSA_SIGINFO() {
    return (int) SA_SIGINFO;
}

int stubSA_NODEFER() {
    return (int) SA_NODEFER;
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

void *cosmo_vmem_reserve(void *addr, size_t size, int prot, int flags, int fd, int64_t off) {
    void *ptr = NULL;
    if (IsWindows()) {
        int prot2 = prot | PROT_READ | PROT_WRITE;
        ptr = mmap(addr, size, prot2, flags, fd, off);
    } else {
        ptr = mmap(addr, size, prot, flags, fd, off);
    }
    return ptr;
}

void *cosmo_vmem_mapfile(void *addr, size_t size, int prot, int flags, int fd, int64_t off) {
    void *ptr = NULL;
    if (IsWindows()) {
        int prot2 = prot | PROT_READ | PROT_WRITE;
        ptr = mmap(addr, size, prot2, flags, fd, off);
    } else {
        ptr = mmap(addr, size, prot, flags, fd, off);
    }
    return ptr;
}

void *cosmo_vmem_commit(void *addr, size_t size, int prot, int flags, int fd, int64_t off) {
    void *ptr = NULL;
    if (IsWindows()) {
        ptr = addr;
    } else {
        ptr = mmap(addr, size, prot, flags, fd, off);
    }
    return ptr;
}

int cosmo_vmem_protect(void *addr, size_t size, int prot) {
    if (IsWindows()) {
        return 0;
    }
    return mprotect(addr, size, prot);
}

int cosmo_vmem_free(void *mapBegin, size_t mapSize) {
    if (IsWindows()) {
        return 0;
    }
    return munmap(mapBegin, mapSize);
}

int cosmo_vmem_uncommit(void *addr, size_t size, int prot, int flags, int fd, int64_t off) {
    if (IsWindows()) {
        memset(addr, 0, size); /* vmem_commit expects zeroed out */
        return 0;
    }
    void *ptr = mmap(addr, size, prot, flags, fd, off);
    return ptr != MAP_FAILED ? 0 : -1;
}

#endif
