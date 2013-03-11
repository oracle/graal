/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import java.io.*;
import java.util.*;

/**
 * Represents the debugging information for a particular place in the code, which includes the code
 * position, a reference map, and deoptimization information.
 */
public class DebugInfo implements Serializable {

    private static final long serialVersionUID = -6047206624915812516L;

    private final BytecodePosition bytecodePosition;
    private final BitSet registerRefMap;
    private final BitSet frameRefMap;
    private final short deoptimizationReason;

    /**
     * Creates a new {@link DebugInfo} from the given values.
     * 
     * @param codePos the {@linkplain BytecodePosition code position} or {@linkplain BytecodeFrame
     *            frame} info
     * @param registerRefMap the register map
     * @param frameRefMap the reference map for {@code frame}, which may be {@code null}
     */
    public DebugInfo(BytecodePosition codePos, BitSet registerRefMap, BitSet frameRefMap, short deoptimizationReason) {
        this.bytecodePosition = codePos;
        this.registerRefMap = registerRefMap;
        this.frameRefMap = frameRefMap;
        this.deoptimizationReason = deoptimizationReason;
    }

    /**
     * @return {@code true} if this debug information has a frame
     */
    public boolean hasFrame() {
        return getBytecodePosition() instanceof BytecodeFrame;
    }

    /**
     * @return {@code true} if this debug info has a reference map for the registers
     */
    public boolean hasRegisterRefMap() {
        return getRegisterRefMap() != null && getRegisterRefMap().size() > 0;
    }

    /**
     * @return {@code true} if this debug info has a reference map for the stack
     */
    public boolean hasStackRefMap() {
        return getFrameRefMap() != null && getFrameRefMap().size() > 0;
    }

    /**
     * Gets the deoptimization information for each inlined frame (if available).
     * 
     * @return {@code null} if no frame de-opt info is {@linkplain #hasFrame() available}
     */
    public BytecodeFrame frame() {
        if (hasFrame()) {
            return (BytecodeFrame) getBytecodePosition();
        }
        return null;
    }

    @Override
    public String toString() {
        return CodeUtil.append(new StringBuilder(100), this, null).toString();
    }

    /**
     * @return The code position (including all inlined methods) of this debug info. If this is a
     *         {@link BytecodeFrame} instance, then it is also the deoptimization information for
     *         each inlined frame.
     */
    public BytecodePosition getBytecodePosition() {
        return bytecodePosition;
    }

    /**
     * @return The reference map for the registers at this point. The reference map is <i>packed</i>
     *         in that for bit {@code k} in byte {@code n}, it refers to the register whose
     *         {@linkplain Register#number number} is {@code (k + n * 8)}.
     */
    public BitSet getRegisterRefMap() {
        return registerRefMap;
    }

    /**
     * @return The reference map for the stack frame at this point. A set bit at {@code k} in the
     *         map represents stack slot number {@code k}.
     */
    public BitSet getFrameRefMap() {
        return frameRefMap;
    }

    /**
     * Identifies the reason in case a deoptimization happens at this program counter.
     * 
     * @return the reason of the deoptimization
     */
    public short getDeoptimizationReason() {
        return deoptimizationReason;
    }
}
