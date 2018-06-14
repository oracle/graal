/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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
#include <ffi.h>
#include <trufflenfi.h>

void svm_libffi_initialize(void *thread, TruffleContext *ctx, void (*initializeNativeSimpleType)(void *thread, TruffleContext *ctx, const char *name, ffi_type *type)) {
    // it's important to initialize POINTER first, since the primitive array types depend on it
    initializeNativeSimpleType(thread, ctx, "POINTER", &ffi_type_pointer);

    initializeNativeSimpleType(thread, ctx, "VOID",    &ffi_type_void);
    initializeNativeSimpleType(thread, ctx, "UINT8",   &ffi_type_uint8);
    initializeNativeSimpleType(thread, ctx, "SINT8",   &ffi_type_sint8);
    initializeNativeSimpleType(thread, ctx, "UINT16",  &ffi_type_uint16);
    initializeNativeSimpleType(thread, ctx, "SINT16",  &ffi_type_sint16);
    initializeNativeSimpleType(thread, ctx, "UINT32",  &ffi_type_uint32);
    initializeNativeSimpleType(thread, ctx, "SINT32",  &ffi_type_sint32);
    initializeNativeSimpleType(thread, ctx, "UINT64",  &ffi_type_uint64);
    initializeNativeSimpleType(thread, ctx, "SINT64",  &ffi_type_sint64);
    initializeNativeSimpleType(thread, ctx, "FLOAT",   &ffi_type_float);
    initializeNativeSimpleType(thread, ctx, "DOUBLE",  &ffi_type_double);

    initializeNativeSimpleType(thread, ctx, "STRING",  &ffi_type_pointer);
    initializeNativeSimpleType(thread, ctx, "OBJECT",  &ffi_type_pointer);
}
