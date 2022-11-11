/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#ifndef __GRAAL_ISOLATE_H
#define __GRAAL_ISOLATE_H

/*
 * Structure representing an isolate. A pointer to such a structure can be
 * passed to an entry point as the execution context.
 */
struct __graal_isolate_t;
typedef struct __graal_isolate_t graal_isolate_t;

/*
 * Structure representing a thread that is attached to an isolate. A pointer to
 * such a structure can be passed to an entry point as the execution context,
 * requiring that the calling thread has been attached to that isolate.
 */
struct __graal_isolatethread_t;
typedef struct __graal_isolatethread_t graal_isolatethread_t;

#ifdef _WIN64
typedef unsigned long long __graal_uword;
#else
typedef unsigned long __graal_uword;
#endif

/*
 * These constants can be used for the pkey field in the
 * graal_create_isolate_params_t struct to either specify that the isolate is
 * not part of a protection domain or a new protection domain should be
 * created for it.
 */
#define NO_PROTECTION_DOMAIN 0
#define NEW_PROTECTION_DOMAIN -1

/* Parameters for the creation of a new isolate. */
enum { __graal_create_isolate_params_version = 4 };
struct __graal_create_isolate_params_t {
    int version;                                /* Version of this struct */

    /* Fields introduced in version 1 */
    __graal_uword  reserved_address_space_size; /* Size of address space to reserve */

    /* Fields introduced in version 2 */
    const char    *auxiliary_image_path;                /* Path to an auxiliary image to load. */
    __graal_uword  auxiliary_image_reserved_space_size; /* Reserved bytes for loading an auxiliary image. */

    /* Fields introduced in version 3 */
    int            _reserved_1;                 /* Internal usage, do not use. */
    char         **_reserved_2;                 /* Internal usage, do not use. */
    int            pkey;                        /* Isolate protection key or domain. */

    /* Fields introduced in version 4 */
    char           _reserved_3;                 /* Internal usage, do not use. */
    char           _reserved_4;                 /* Internal usage, do not use. */
};
typedef struct __graal_create_isolate_params_t graal_create_isolate_params_t;

#if defined(__cplusplus)
extern "C" {
#endif

/*
 * Create a new isolate, considering the passed parameters (which may be NULL).
 * Returns 0 on success, or a non-zero value on failure.
 * On success, the current thread is attached to the created isolate, and the
 * address of the isolate and the isolate thread are written to the passed pointers
 * if they are not NULL.
 */
typedef int (*graal_create_isolate_fn_t)(graal_create_isolate_params_t* params, graal_isolate_t** isolate, graal_isolatethread_t** thread);

/*
 * Attaches the current thread to the passed isolate.
 * On failure, returns a non-zero value. On success, writes the address of the
 * created isolate thread structure to the passed pointer and returns 0.
 * If the thread has already been attached, the call succeeds and also provides
 * the thread's isolate thread structure.
 */
typedef int (*graal_attach_thread_fn_t)(graal_isolate_t* isolate, graal_isolatethread_t** thread);

/*
 * Given an isolate to which the current thread is attached, returns the address of
 * the thread's associated isolate thread structure.  If the current thread is not
 * attached to the passed isolate or if another error occurs, returns NULL.
 */
typedef graal_isolatethread_t* (*graal_get_current_thread_fn_t)(graal_isolate_t* isolate);

/*
 * Given an isolate thread structure, determines to which isolate it belongs and returns
 * the address of its isolate structure. If an error occurs, returns NULL instead.
 */
typedef graal_isolate_t* (*graal_get_isolate_fn_t)(graal_isolatethread_t* thread);

/*
 * Detaches the passed isolate thread from its isolate and discards any state or
 * context that is associated with it. At the time of the call, no code may still
 * be executing in the isolate thread's context.
 * Returns 0 on success, or a non-zero value on failure.
 */
typedef int (*graal_detach_thread_fn_t)(graal_isolatethread_t* thread);

/*
 * Tears down the isolate of the passed (and still attached) isolate thread,
 * waiting for any attached threads to detach from it, then discards its objects,
 * threads, and any other state or context that is associated with it.
 * Returns 0 on success, or a non-zero value on failure.
 */
typedef int (*graal_tear_down_isolate_fn_t)(graal_isolatethread_t* isolateThread);

/*
 * In the isolate of the passed isolate thread, detach all those threads that were
 * externally started (not within Java, which includes the "main thread") and were
 * attached to the isolate afterwards. Afterwards, all threads that were started
 * within Java undergo a regular shutdown process, followed by the tear-down of the
 * entire isolate, which detaches the current thread and discards the objects,
 * threads, and any other state or context associated with the isolate.
 * None of the manually attached threads targeted by this function may be executing
 * Java code at the time when this function is called or at any point in the future
 * or this will cause entirely undefined (and likely fatal) behavior.
 * Returns 0 on success, or a non-zero value on (non-fatal) failure.
 */
typedef int (*graal_detach_all_threads_and_tear_down_isolate_fn_t)(graal_isolatethread_t* isolateThread);

#if defined(__cplusplus)
}
#endif
#endif
