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
package com.oracle.svm.hosted.imagelayer;

import java.lang.reflect.Method;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.imagelayer.LayeredImageMarker;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;

/**
 * A collection of special routines used within layered image builds.
 */
public class LayeredImageUtils {

    /*
     * Marker method used for identifying objects which were registered as roots but are only
     * accessed by code only present in other layers.
     */
    private static final Method registeredEmbeddedRootMarker = ReflectionUtil.lookupMethod(LayeredImageMarker.class, "forcedEmbeddedRootMarker");

    /* Registers an object as a root so that it will be fully processed by analysis. */
    public static int registerObjectAsEmbeddedRoot(AnalysisUniverse universe, Object obj) {
        var method = universe.getBigbang().getMetaAccess().lookupJavaMethod(registeredEmbeddedRootMarker);
        var ihc = (ImageHeapConstant) universe.getSnippetReflection().forObject(obj);
        universe.registerEmbeddedRoot(ihc, new BytecodePosition(null, method, BytecodeFrame.UNKNOWN_BCI));
        return ImageHeapConstant.getConstantID(ihc);
    }
}
