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

#define UNINITIALIZED ((void*) ~0)
#define NOT_USED(x) ((void)(x))

extern void *__libc_dlsym(void *handle, const char *symbol);
extern void *__libc_dlopen_mode(const char *filename, int flags);
extern const unsigned short int ** __ctype_b_loc (void);

// Linking namespace where this library is loaded.
static Lmid_t namespace_id = 0;

// Debug flags, can be set with EDEN_DEBUG=true|1
static int eden_debug = 0;

#define LOG(fmt, ...) do { if (eden_debug) fprintf(stderr, "[eden #%ld] " fmt, namespace_id, ##__VA_ARGS__); } while (0)

static void *get_libc() {
    static void *libc = NULL;
    if (libc == NULL) {
        LOG("__libc_dlopen_mode(libc.so.6, RTLD_LAZY)\n");
        libc = __libc_dlopen_mode("libc.so.6", RTLD_LAZY);
    }
    LOG("get_libc(libc.so.6) => %p\n", libc);
    return libc;
}

static void *get_libdl() {
    static void *libdl = NULL;
    if (libdl == NULL) {
        LOG("__libc_dlopen_mode(libdl.so, RTLD_LAZY)\n");
        libdl = __libc_dlopen_mode("libdl.so", RTLD_LAZY);
    }
    LOG("get_libdl(libdl.so) => %p\n", libdl);
    return libdl;
}


static void *real_dlopen(const char *filename, int flags) {
    LOG("real_dlopen(%s, %d)\n", filename, flags);
    static void *(*the_real_dlopen)(const char *, int) = NULL;
    if (the_real_dlopen == NULL) {
        LOG("__libc_dlsym(get_libdl(), dlopen)\n");
        the_real_dlopen = __libc_dlsym(get_libdl(), "dlopen");
        LOG("__libc_dlsym(get_libdl(), dlopen) => %p\n", the_real_dlopen);
    }
    void *result = the_real_dlopen(filename, flags);
    LOG("real_dlopen(%s, %d) => %p\n", filename, flags, result);
    return result;
}

static void *get_libeden() {
    static void *libeden = NULL;
    if (libeden == NULL) {        
        LOG("__libc_dlopen_mode(libeden.so, RTLD_NOW)\n");
        libeden = __libc_dlopen_mode("libeden.so", RTLD_NOW);
    }
    LOG("get_libeden(libeden.so) => %p\n", libeden);
    return libeden;
}


static void *real_dlmopen(Lmid_t lmid, const char *filename, int flags) {
    LOG("real_dlmopen(%ld, %s, %d)\n", lmid, filename, flags);
    static void *(*the_real_dlmopen)(Lmid_t, const char *, int) = NULL;
    if (the_real_dlmopen == NULL) {
        LOG("__libc_dlsym(get_libdl(), dlmopen)\n");
        the_real_dlmopen = __libc_dlsym(get_libdl(), "dlmopen");
        LOG("__libc_dlsym(get_libdl(), dlmopen) => %p\n", the_real_dlmopen);
    }
    void *result = the_real_dlmopen(lmid, filename, flags);
    LOG("real_dlmopen(%ld, %s, %d) => %p\n", lmid, filename, flags, result);
    return result;
}

static int real_dlclose(void *handle) {
    LOG("real_dlclose(%p)\n", handle);
    static int (*the_real_dlclose)(void *) = NULL;
    if (the_real_dlclose == NULL) {
        LOG("__libc_dlsym(get_libdl(), dlclose)\n");
        the_real_dlclose = __libc_dlsym(get_libdl(), "dlclose");
        LOG("__libc_dlsym(get_libdl(), dlclose) => %p\n", the_real_dlclose);
    }
    int result = the_real_dlclose(handle);
    LOG("real_dlclose(%p) => %d\n", handle, result);
    return result;
}

static void *real_dlsym(void *handle, const char *symbol) {
    LOG("real_dlsym(%p, %s)\n", handle, symbol);
    static void *(*the_real_dlsym)(void *, const char *) = NULL;
    if (the_real_dlsym == NULL) {
        LOG("__libc_dlsym(get_libdl(), dlsym)\n");
        the_real_dlsym = __libc_dlsym(get_libdl(), "dlsym");
        LOG("__libc_dlsym(get_libdl(), dlsym) => %p\n", the_real_dlsym);
    }
    void *result = the_real_dlsym(handle, symbol);
    LOG("real_dlsym(%p, %s) => %p\n", handle, symbol, result);
    return result;
}

static int is_absolute_path(const char *path) {
    return path != NULL && path[0] == '/';
}

void *dlmopen(Lmid_t lmid, const char *filename, int flags) {
    LOG("dlmopen(%ld, %s, %d)\n", lmid, filename, flags);
    void *result = real_dlmopen(lmid, filename, flags);
    LOG("dlmopen(%ld, %s, %d) => %p\n", lmid, filename, flags, result);
    return result;
}

void *dlopen(const char *filename, int flags) {

    // real_dlopen is never used inside the namespace.
    NOT_USED(real_dlopen);

    LOG("dlopen(%s, %d)\n", filename, flags);
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
        LOG("dlopen(%s, %d) => error: %s\n", filename, flags, dlerror());
    } else {
        LOG("dlopen(%s, %d) => %p\n", filename, flags, result);
    }
    return result;
}

int dlclose(void *handle) {
    LOG("dlclose(%p)\n", handle);
    int result = real_dlclose(handle);
    LOG("dlclose(%p) => %d\n", handle, result);
    return result;
}

void *dlsym(void *handle, const char *symbol) {
    LOG("dlsym(%p, %s)\n", handle, symbol);
    void *result = real_dlsym(handle, symbol);
    LOG("dlsym(%p, %s) => %p\n", handle, symbol, result);
    return result;
}

void eden_ctypeInit(void) {    
    static void (*the_real__ctype_init)(void) = UNINITIALIZED;
    LOG("eden_ctypeInit() with __ctype_init = %p\n", the_real__ctype_init);
    if (the_real__ctype_init == UNINITIALIZED) {
        // __libc_dlsym is used here instead of the hooked dlsym to avoid crashes on glibc 2.17.
        LOG("__libc_dlsym(get_libc(), __ctype_init)\n");
        the_real__ctype_init = __libc_dlsym(get_libc(), "__ctype_init");
    }    
    // Older versions of glibc do not have __ctype_init since they do not use TLS.
    if (the_real__ctype_init != NULL) {
        LOG("calling __ctype_init()\n");
        the_real__ctype_init();
    }
}

static __attribute__((constructor)) void initialize(void) {
    LOG("initialize()\n");
    const char * mode = getenv("EDEN_DEBUG");
    if (mode != NULL) {
        eden_debug = (strcmp("true", mode) == 0) || (strcmp("1", mode) == 0);
    }

    // Eagerly load handles.
    get_libc();
    get_libdl();
    get_libeden();

    if (dlinfo(get_libeden(), RTLD_DI_LMID, &namespace_id) != 0) {
        LOG("init Error obtaining namespace (dlinfo): %s\n", dlerror());
    }
    if (!namespace_id) {
        LOG("init libeden.so should't be loaded in the default namespace\n");
    }
    LOG("initialize &__ctype_b_loc: %p\n", &__ctype_b_loc);
    LOG("initialize *__ctype_b_loc() = %p\n", *__ctype_b_loc());
    LOG("Current locale: %s\n", setlocale(LC_ALL, NULL));

    // Eargerly initialize ctype TLS.
    eden_ctypeInit();
}

void* eden_RTLD_DEFAULT(void) {
    LOG("eden_RTLD_DEFAULT()\n");
    return RTLD_DEFAULT;
}
