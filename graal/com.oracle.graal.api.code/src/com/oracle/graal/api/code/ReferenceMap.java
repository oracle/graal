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

    private final BitSet registerRefMap;
    private final BitSet frameRefMap;

    public ReferenceMap(int registerCount, int frameSlotCount) {
        if (registerCount > 0) {
            this.registerRefMap = new BitSet(registerCount);
        } else {
            this.registerRefMap = null;
        }
        this.frameRefMap = new BitSet(frameSlotCount);
    }

    public void setRegister(int idx) {
        registerRefMap.set(idx);
    }

    public void setStackSlot(int idx) {
        frameRefMap.set(idx);
    }

    public boolean hasRegisterRefMap() {
        return registerRefMap != null && registerRefMap.size() > 0;
    }

    public boolean hasFrameRefMap() {
        return frameRefMap != null && frameRefMap.size() > 0;
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

        for (int reg = registerRefMap.nextSetBit(0); reg >= 0; reg = registerRefMap.nextSetBit(reg + 1)) {
            sb.append(' ').append(formatter.formatRegister(reg));
        }
    }

    public void appendFrameMap(StringBuilder sb, RefMapFormatter formatterArg) {
        RefMapFormatter formatter = formatterArg;
        if (formatter == null) {
            formatter = new NumberedRefMapFormatter();
        }

        for (int slot = frameRefMap.nextSetBit(0); slot >= 0; slot = frameRefMap.nextSetBit(slot + 1)) {
            sb.append(' ').append(formatter.formatStackSlot(slot));
        }
    }
}
