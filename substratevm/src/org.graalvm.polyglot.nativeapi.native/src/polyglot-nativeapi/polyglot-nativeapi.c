/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/* Parameters for the creation of a new isolate. */
struct __graal_create_isolate_params_t {
    int dummy;
};

typedef struct __graal_create_isolate_params_t graal_create_isolate_params_t;

/*
 * Create a new isolate, considering the passed parameters (which may be NULL).
 * Returns 0 on success, or a non-zero value on failure.
 * On success, the current thread is attached to the created isolate, and the
 * address of the isolate structure is written to the passed pointer.
 */
extern int graal_create_isolate(graal_create_isolate_params_t* params, graal_isolate_t** isolate);

/*
 * Attaches the current thread to the passed isolate.
 * On failure, returns a non-zero value. On success, writes the address of the
 * created isolate thread structure to the passed pointer and returns 0.
 * If the thread has already been attached, the call succeeds and also provides
 * the thread's isolate thread structure.
 */
extern int graal_attach_thread(graal_isolate_t* isolate, graal_isolatethread_t** thread);

/*
 * Given an isolate to which the current thread is attached, returns the address of
 * the thread's associated isolate thread structure.  If the current thread is not
 * attached to the passed isolate or if another error occurs, returns NULL.
 */
extern graal_isolatethread_t* graal_current_thread(graal_isolate_t* isolate);

/*
 * Given an isolate thread structure for the current thread, determines to which
 * isolate it belongs and returns the address of its isolate structure.  If an
 * error occurs, returns NULL instead.
 */
extern graal_isolate_t* graal_current_isolate(graal_isolatethread_t* thread);

/*
 * Detaches the passed isolate thread from its isolate and discards any state or
 * context that is associated with it. At the time of the call, no code may still
 * be executing in the isolate thread's context.
 * Returns 0 on success, or a non-zero value on failure.
 */
extern int graal_detach_thread(graal_isolatethread_t* thread);

/*
 * Tears down the passed isolate, waiting for any attached threads to detach from
 * it, then discards the isolate's objects, threads, and any other state or context
 * that is associated with it.
 * Returns 0 on success, or a non-zero value on failure.
 */
extern int graal_tear_down_isolate(graal_isolate_t* isolate);

#include <polyglot_types.h>

poly_status poly_create_isolate(graal_create_isolate_params_t* params, graal_isolate_t** isolate) {
  if (graal_create_isolate(params, isolate)) {
    return poly_generic_failure;
  } else {
    return poly_ok;
  }
};

poly_status poly_attach_thread(graal_isolate_t* isolate, graal_isolatethread_t** thread) {
  if (graal_attach_thread(isolate, thread)) {
    return poly_generic_failure;
  } else {
    return poly_ok;
  }
};

graal_isolatethread_t* poly_current_thread(graal_isolate_t* isolate) {
  return graal_current_thread(isolate);
};

graal_isolate_t* poly_current_isolate(graal_isolatethread_t* thread) {
  return graal_current_isolate(thread);
};

poly_status poly_detach_thread(graal_isolatethread_t* thread) {
   if (graal_detach_thread(thread)) {
    return poly_generic_failure;
  } else {
    return poly_ok;
  }
};

poly_status poly_tear_down_isolate(graal_isolate_t* isolate) {
  if (graal_tear_down_isolate(isolate)) {
    return poly_generic_failure;
  } else {
    return poly_ok;
  }
};
