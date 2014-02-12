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

import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;

public class ReferenceMap implements Serializable {

    private static final long serialVersionUID = -1052183095979496819L;

    /**
     * Contains 2 bits per register.
     * <ul>
     * <li>bit0 = 0: contains no references</li>
     * <li>bit0 = 1, bit1 = 0: contains a wide oop</li>
     * <li>bit0 = 1, bit1 = 1: contains a narrow oop</li>
     * </ul>
     */
    private final BitSet registerRefMap;

    /**
     * Contains 3 bits per stack slot.
     * <ul>
     * <li>bit0 = 0: contains no references</li>
     * <li>bit0 = 1, bit1+2 = 0: contains a wide oop</li>
     * <li>bit0 = 1, bit1 = 1: contains a narrow oop in the lower half</li>
     * <li>bit0 = 1, bit2 = 1: contains a narrow oop in the upper half</li>
     * </ul>
     */
    private final BitSet frameRefMap;

    public ReferenceMap(int registerCount, int frameSlotCount) {
        if (registerCount > 0) {
            this.registerRefMap = new BitSet(registerCount * 2);
        } else {
            this.registerRefMap = null;
        }
        this.frameRefMap = new BitSet(frameSlotCount * 3);
    }

    public void setRegister(int idx, boolean narrow) {
        registerRefMap.set(2 * idx);
        if (narrow) {
            registerRefMap.set(2 * idx + 1);
        }
    }

    public void setStackSlot(int idx, boolean narrow1, boolean narrow2) {
        frameRefMap.set(3 * idx);
        if (narrow1) {
            frameRefMap.set(3 * idx + 1);
        }
        if (narrow2) {
            frameRefMap.set(3 * idx + 2);
        }
    }

    public boolean hasRegisterRefMap() {
        return registerRefMap != null && registerRefMap.size() > 0;
    }

    public boolean hasFrameRefMap() {
        return frameRefMap != null && frameRefMap.size() > 0;
    }

    public interface Iterator {
        void register(int idx, boolean narrow);

        void stackSlot(int idx, boolean narrow1, boolean narrow2);
    }

    public void iterate(Iterator iterator) {
        if (hasRegisterRefMap()) {
            for (int i = 0; i < registerRefMap.size() / 2; i++) {
                if (registerRefMap.get(2 * i)) {
                    iterator.register(i, registerRefMap.get(2 * i + 1));
                }
            }
        }
        if (hasFrameRefMap()) {
            for (int i = 0; i < frameRefMap.size() / 3; i++) {
                if (frameRefMap.get(3 * i)) {
                    iterator.stackSlot(i, frameRefMap.get(3 * i + 1), frameRefMap.get(3 * i + 2));
                }
            }
        }
    }

    private static class NumberedRefMapFormatter implements RefMapFormatter {

        public String formatStackSlot(int frameRefMapIndex) {
            return "s" + frameRefMapIndex;
        }

        public String formatRegister(int regRefMapIndex) {
            return "r" + regRefMapIndex;
        }
    }

    public void appendRegisterMap(StringBuilder sb, RefMapFormatter formatterArg) {
        RefMapFormatter formatter = formatterArg;
        if (formatter == null) {
            formatter = new NumberedRefMapFormatter();
        }

        for (int reg = registerRefMap.nextSetBit(0); reg >= 0; reg = registerRefMap.nextSetBit(reg + 2)) {
            sb.append(' ').append(formatter.formatRegister(reg / 2));
        }
    }

    public void appendFrameMap(StringBuilder sb, RefMapFormatter formatterArg) {
        RefMapFormatter formatter = formatterArg;
        if (formatter == null) {
            formatter = new NumberedRefMapFormatter();
        }

        for (int slot = frameRefMap.nextSetBit(0); slot >= 0; slot = frameRefMap.nextSetBit(slot + 3)) {
            sb.append(' ').append(formatter.formatStackSlot(slot / 3));
        }
    }
}
