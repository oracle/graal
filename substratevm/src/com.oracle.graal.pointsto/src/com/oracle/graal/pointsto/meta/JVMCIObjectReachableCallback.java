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
package com.oracle.graal.pointsto.meta;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.ObjectScanner.ScanReason;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Builder-side analysis callback notified with the original backing {@link JavaConstant} when an
 * object becomes reachable in the image heap. The analysis access and scan reason remain
 * builder-owned; guest callbacks adapt this notification to the single reachable-object argument.
 */
public interface JVMCIObjectReachableCallback {
    /**
     * Notifies this callback that {@code object} is reachable.
     *
     * @param access the builder-side concurrent analysis access shared with standalone points-to
     *            analysis. GR-78969: use {@code JVMCIFeatureAccess} once it is visible to the points-to module.
     * @param object the original backing {@link JavaConstant}
     * @param reason why {@code object} was reached
     */
    void doCallback(Feature.DuringAnalysisAccess access, JavaConstant object, ScanReason reason);
}
