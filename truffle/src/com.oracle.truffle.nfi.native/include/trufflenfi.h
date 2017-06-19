/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#ifndef __TRUFFLE_NFI_H
#define __TRUFFLE_NFI_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Opaque handle to a {@link com.oracle.truffle.api.interop.TruffleObject}.
 */
typedef struct __TruffleObject *TruffleObject;

/**
 * Create a new handle to a TruffleObject.
 *
 * TruffleObjects that are passed to native code as argument are owned by the caller. If the native
 * code wants to keep the reference, it has to call {@link newObjectRef} to create a new reference.
 * TruffleObjects that are returned from a callback are owned by the caller. The native code has to
 * call {@link releaseObjectRef} to free the reference.
 */
TruffleObject newObjectRef(TruffleObject object);

/**
 * Release a handle to a TruffleObject.
 *
 * This should be called to free handles to a TruffleObject. The TruffleObject must not be used
 * afterwards.
 *
 * This (or {@link releaseAndReturn}) must be called on any TruffleObject owned by native code.
 * TruffleObjects that are returned from a callback function are owned by the native code and must
 * be released.
 */
void releaseObjectRef(TruffleObject object);

/**
 * Transfer ownership of a TruffleObject to the caller.
 *
 * Similar to {@link releaseObjectRef}, this function releases the ownership of a TruffleObject.
 * The TruffleObject must not be used afterwards. Additionally, it returns a new handle to the
 * TruffleObject. This new handle is not owned by the native code. The new handle can be returned
 * to the calling Truffle code. It must not be used for anything else.
 */
TruffleObject releaseAndReturn(TruffleObject object);

/**
 * Returns 1 iff object1 references the same underlying object as object2, 0 otherwise.
 */
int isSameObject(TruffleObject object1, TruffleObject object2);

/**
 * Increase the reference count of a callback closure.
 *
 * Closures that are passed from Truffle to native code as function pointer are owned by the caller
 * and are freed on return. If the native code wants to keep the function pointer, it needs to call
 * {@link newClosureRef} to increase the reference count.
 */
void newClosureRef(void *closure);

/**
 * Decrease the reference count of a callback closure.
 *
 * Closures that are returned by callback functions as function pointers are owned by the native
 * code and need to be freed manually.
 */
void releaseClosureRef(void *closure);

/**
 * Convenience macro that calls {@link newClosureRef} on a function pointer, and returns the
 * same function pointer without losing type information.
 */
#define dupClosureRef(closure) (newClosureRef(closure), (closure))

#ifdef __cplusplus
}
#endif

#endif
