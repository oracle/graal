/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include "eden.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <locale.h>
#include <unistd.h>
#include <dlfcn.h>

extern void *__libc_dlsym(void *handle, const char *symbol);
extern void *__libc_dlopen_mode(const char *filename, int flags);
extern const unsigned short int ** __ctype_b_loc (void);

static Lmid_t namespace_id = 0;

static int eden_debug = 0;

#define LOG(fmt, ...) do { if (eden_debug) fprintf(stderr, "[eden #%ld] " fmt, namespace_id, ##__VA_ARGS__); } while (0)

static void *get_libc() {
    static void *libc = NULL;
    if (libc == NULL) {
        libc = __libc_dlopen_mode("libc.so.6", RTLD_LAZY);
        LOG("get_libc(libc.so.6) => %p\n", libc);
    }
    return libc;
}

static void *get_libdl() {
    static void *libdl = NULL;
    if (libdl == NULL) {
        libdl = __libc_dlopen_mode("libdl.so", RTLD_LAZY);
    }
    return libdl;
}

static void *real_dlmopen(Lmid_t lmid, const char *filename, int flags) {
    static void *(*the_real_dlmopen)(Lmid_t, const char *, int) = NULL;
    if (the_real_dlmopen == NULL) {
        the_real_dlmopen = __libc_dlsym(get_libdl(), "dlmopen");
    }
    return the_real_dlmopen(lmid, filename, flags);
}

void *dlmopen(Lmid_t lmid, const char *filename, int flags) {
    void * result = real_dlmopen(lmid, filename, flags);
    LOG("dlmopen(%s, %d) => %p\n", filename, flags, result);
    return result;
}

static void *real_dlopen(const char *filename, int flags) {
    static void *(*the_real_dlopen)(const char *, int) = NULL;
    if (the_real_dlopen == NULL) {
        the_real_dlopen = __libc_dlsym(get_libdl(), "dlopen");
    }
    return the_real_dlopen(filename, flags);
}

static void *get_libeden() {
    static void *libeden = NULL;
    if (libeden == NULL) {
        libeden = real_dlopen("libeden.so", RTLD_LAZY);
    }
    return libeden;
}

static int is_absolute_path(const char *path) {
    return path != NULL && path[0] == '/';
}

void *dlopen(const char *filename, int flags) {
    if (flags & RTLD_GLOBAL) {
        // dlmopen does not support RTLD_GLOBAL.
        // See https://patchwork.ozlabs.org/project/glibc/patch/55A73673.3060104@redhat.com/
        LOG("dlopen Ignoring RTLD_GLOBAL for %s\n", filename);
        flags &= ~RTLD_GLOBAL;
    }

    // glibc yields a loading error if the file doesn't exists.
    if (is_absolute_path(filename) && access(filename, R_OK) != 0) {
        LOG("dlopen(%s, %d): File not accesible\n", filename, flags);
        return NULL;
    }

    LOG("dlopen => dlmopen: %s\n", filename);
    void * result = real_dlmopen(namespace_id, filename, flags);
    if (result == NULL) {
        LOG("dlopen(%s, %d): %s\n", filename, flags, dlerror());
    } else {
        LOG("dlopen(%s, %d) => %p\n", filename, flags, result);
    }
    return result;
}

static int real_dlclose(void *handle) {
    static int (*the_real_dlclose)(void *) = NULL;
    if (the_real_dlclose == NULL) {
        the_real_dlclose = __libc_dlsym(get_libdl(), "dlclose");
    }
    return the_real_dlclose(handle);
}

int dlclose(void *handle) {
    LOG("dlclose(%p)\n", handle);
    return real_dlclose(handle);
}

static void *real_dlsym(void *handle, const char *symbol) {
    static void *(*the_real_dlsym)(void *, const char *) = NULL;
    if (the_real_dlsym == NULL) {
        the_real_dlsym = __libc_dlsym(get_libdl(), "dlsym");
    }
    return the_real_dlsym(handle, symbol);
}

void *dlsym(void *handle, const char *symbol) {
    void * result = real_dlsym(handle, symbol);
    LOG("dlsym(%p, %s) => %p\n", handle, symbol, result);
    return result;
}

static __attribute__((constructor)) void initialize(void) {
    const char * mode = getenv("EDEN_DEBUG");
    if (mode != NULL) {
        eden_debug = (strcmp("true", mode) == 0) || (strcmp("1", mode) == 0);
    }

    if (dlinfo(get_libeden(), RTLD_DI_LMID, &namespace_id) != 0) {
        LOG("init Error obtaining namespace (dlinfo): %s\n", dlerror());
    }
    if (!namespace_id) {
        LOG("init libeden.so should't be loaded in the default namespace\n");
    }
    LOG("init &__ctype_b_loc: %p\n", &__ctype_b_loc);
    LOG("init *__ctype_b_loc() = %p\n", *__ctype_b_loc());
    LOG("Current locale: %s\n", setlocale(LC_ALL, NULL));
}

#define UNINITIALIZED ((void*) ~0)

void ctypeInit(void) {
    static void (*__ctype_init)(void) = UNINITIALIZED;
    if (__ctype_init == UNINITIALIZED) {
        __ctype_init = dlsym(get_libc(), "__ctype_init");
    }
    if (__ctype_init != NULL) {
        __ctype_init();
    }
}
