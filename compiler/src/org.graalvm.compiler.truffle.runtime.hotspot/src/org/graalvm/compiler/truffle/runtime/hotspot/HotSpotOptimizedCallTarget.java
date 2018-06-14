/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot;

import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleInstalledCode;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleCallBoundary;

import com.oracle.truffle.api.nodes.RootNode;

/**
 * A HotSpot specific {@link OptimizedCallTarget} that whose machine code (if any) is represented by
 * an associated {@link HotSpotTruffleInstalledCode}.
 */
public class HotSpotOptimizedCallTarget extends OptimizedCallTarget {
    /**
     * This field is read by the code injected by {@code TruffleCallBoundaryInstrumentationFactory}
     * into a method annotated by {@link TruffleCallBoundary}.
     */
    private HotSpotTruffleInstalledCode installedCode;

    public HotSpotOptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode, HotSpotTruffleInstalledCode installedCode) {
        super(sourceCallTarget, rootNode);
        this.installedCode = installedCode;
    }

    public void setInstalledCode(HotSpotTruffleInstalledCode code) {
        installedCode = code;
    }

    @Override
    public boolean isValid() {
        return installedCode.isValid();
    }

    @Override
    protected void invalidateCode() {
        if (installedCode.isValid()) {
            installedCode.invalidate();
        }
    }

    @Override
    public long getCodeAddress() {
        return installedCode.getAddress();
    }
}
