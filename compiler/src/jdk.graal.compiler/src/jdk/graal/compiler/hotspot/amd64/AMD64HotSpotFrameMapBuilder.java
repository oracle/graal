/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import jdk.graal.compiler.lir.amd64.AMD64FrameMapBuilder;
import jdk.graal.compiler.lir.framemap.FrameMap;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;

public class AMD64HotSpotFrameMapBuilder extends AMD64FrameMapBuilder {
    public AMD64HotSpotFrameMapBuilder(FrameMap frameMap, CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        super(frameMap, codeCache, registerConfig);
    }

    @Override
    public AMD64HotSpotFrameMap getFrameMap() {
        return (AMD64HotSpotFrameMap) super.getFrameMap();
    }

    /**
     * For non-leaf methods, RBP is preserved in the special stack slot required by the HotSpot
     * runtime for walking/inspecting frames of such methods.
     */
    public StackSlot getRBPSpillSlot() {
        return getFrameMap().getRBPSpillSlot();
    }

    public StackSlot getDeoptimizationRescueSlot() {
        return getFrameMap().getDeoptimizationRescueSlot();
    }
}
