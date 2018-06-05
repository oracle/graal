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
package com.oracle.svm.core.c.function;

/**
 * Errors returned by {@link CEntryPointActions} and {@link CEntryPointNativeFunctions} and their
 * implementation, including snippets and foreign function calls. These are non-API, with the
 * exception of 0 = success.
 */
public interface CEntryPointErrors {
    int NO_ERROR = 0;
    int UNSPECIFIED = 1;
    int NULL_ARGUMENT = 2;
    int UNATTACHED_THREAD = 4;
    int UNINITIALIZED_ISOLATE = 5;
    int LOCATE_IMAGE_FAILED = 6;
    int OPEN_IMAGE_FAILED = 7;
    int MAP_HEAP_FAILED = 8;
    int PROTECT_HEAP_FAILED = 9;
}
