/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.imagelayer;

import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Marker class used to indicate an object was explicitly registered as an analysis root while
 * building a layered image.
 *
 * When building image layers, we sometimes need to install objects into the image heap which are
 * not automatically reachable from analysis. Hence, we must mark these objects as roots to ensure
 * they are properly processed by analysis. This registration is performed by
 * {@code LayeredImageUtils#registerObjectAsEmbeddedRoot}.
 *
 * When registering an object as a root, one must also provide a {@link BytecodePosition} denoting
 * the object's origins. However, when building an image layer, sometimes we need to install objects
 * into the heap which are only referred to by code installed in a separate layer. Hence, we use the
 * method {@link #forcedEmbeddedRootMarker()} as an artificial marker method to indicate we
 * explicitly registered this object.
 */
public class LayeredImageMarker {

    /**
     * Marker method used to denote an object was registered as a root via
     * {@code LayeredImageUtils#registerObjectAsEmbeddedRoot}.
     */
    @SuppressWarnings("unused")
    public static void forcedEmbeddedRootMarker() {
        throw VMError.shouldNotReachHere("This is a marker method used to indicate an object was registered as a root. This method should never be called");
    }
}
