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
#ifndef POLYGLOT_ISOLATE_H
#define POLYGLOT_ISOLATE_H

#include <graal_isolate.h>
#include <polyglot_types.h>

#if defined(__cplusplus)
extern "C" {
#endif

/*
 * Create a new isolate, considering the passed parameters (which may be NULL).
 * Returns poly_ok on success, or a poly_generic_failure value on failure.
 * On success, the current thread is attached to the created isolate, and the
 * address of the isolate structure is written to the passed pointer.
 */
poly_status poly_create_isolate(const poly_isolate_params* params, poly_isolate* isolate);

/*
 * Attaches the current thread to the passed isolate.
 * On failure, returns poly_generic_failure. On success, writes the address of the
 * created isolate thread structure to the passed pointer and returns poly_ok.
 * If the thread has already been attached, the call succeeds and also provides
 * the thread's isolate thread structure.
 */
poly_status poly_attach_thread(poly_isolate isolate, poly_thread* thread);

/*
 * Given an isolate to which the current thread is attached, returns the address of
 * the thread's associated isolate thread structure.  If the current thread is not
 * attached to the passed isolate or if another error occurs, returns NULL.
 */
poly_thread poly_current_thread(poly_isolate isolate);

/*
 * Given an isolate thread structure for the current thread, determines to which
 * isolate it belongs and returns the address of its isolate structure.  If an
 * error occurs, returns NULL instead.
 */
poly_isolate poly_current_isolate(poly_thread thread);

/*
 * Detaches the passed isolate thread from its isolate and discards any state or
 * context that is associated with it. At the time of the call, no code may still
 * be executing in the isolate thread's context.
 * Returns poly_ok on success, or poly_generic_failure on failure.
 */
poly_status poly_detach_thread(poly_thread thread);

/*
 * Tears down the passed isolate, waiting for any attached threads to detach from
 * it, then discards the isolate's objects, threads, and any other state or context
 * that is associated with it.
 * Returns poly_ok on success, or poly_generic_failure on failure.
 */
poly_status poly_tear_down_isolate(poly_isolate isolate);


#if defined(__cplusplus)
}
#endif
#endif
