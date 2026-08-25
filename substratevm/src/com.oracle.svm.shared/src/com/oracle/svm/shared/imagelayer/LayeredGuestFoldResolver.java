/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.shared.imagelayer;

/** Builder resolver names that can be used from guest-facing code. */
public final class LayeredGuestFoldResolver {
    /** Resolves the GuestFold in the initial layer. */
    public static final String INITIAL_LAYER = "com.oracle.svm.core.imagelayer.LayeredFoldResolver$InitialLayer";
    /** Resolves the GuestFold in the application layer. */
    public static final String APPLICATION_LAYER = "com.oracle.svm.core.imagelayer.LayeredFoldResolver$ApplicationLayer";

    private LayeredGuestFoldResolver() {
    }
}
