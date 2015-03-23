/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.gen;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.stackslotalloc.*;

public class LIRGenerationResultBase implements LIRGenerationResult {
    private final LIR lir;
    private final FrameMapBuilder frameMapBuilder;
    private FrameMap frameMap;
    /**
     * Records whether the code being generated makes at least one foreign call.
     */
    private boolean hasForeignCall;
    /**
     * Human readable name of this compilation unit.
     */
    private final String compilationUnitName;

    public LIRGenerationResultBase(String compilationUnitName, LIR lir, FrameMapBuilder frameMapBuilder) {
        this.lir = lir;
        this.frameMapBuilder = frameMapBuilder;
        this.compilationUnitName = compilationUnitName;
    }

    public LIR getLIR() {
        return lir;
    }

    /**
     * Determines whether the code being generated makes at least one foreign call.
     */
    public boolean hasForeignCall() {
        return hasForeignCall;
    }

    public final void setForeignCall(boolean hasForeignCall) {
        this.hasForeignCall = hasForeignCall;
    }

    public final FrameMapBuilder getFrameMapBuilder() {
        assert frameMap == null : "getFrameMapBuilder() can only be used before calling buildFrameMap()!";
        return frameMapBuilder;
    }

    public void buildFrameMap(StackSlotAllocator allocator) {
        assert frameMap == null : "buildFrameMap() can only be called once!";
        frameMap = frameMapBuilder.buildFrameMap(this, allocator);
    }

    public FrameMap getFrameMap() {
        assert frameMap != null : "getFrameMap() can only be used after calling buildFrameMap()!";
        return frameMap;
    }

    public String getCompilationUnitName() {
        return compilationUnitName;
    }
}
