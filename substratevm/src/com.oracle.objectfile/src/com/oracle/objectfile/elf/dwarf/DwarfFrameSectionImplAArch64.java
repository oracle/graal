/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.elf.dwarf;

import com.oracle.objectfile.debuginfo.DebugInfoProvider;

import java.util.List;

/**
 * AArch64-specific section generator for debug_frame section that knows details of AArch64
 * registers and frame layout.
 */
public class DwarfFrameSectionImplAArch64 extends DwarfFrameSectionImpl {
    public static final int DW_CFA_FP_IDX = 29;
    private static final int DW_CFA_LR_IDX = 30;
    private static final int DW_CFA_SP_IDX = 31;
    // private static final int DW_CFA_PC_IDX = 32;

    public DwarfFrameSectionImplAArch64(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public int getReturnPCIdx() {
        return DW_CFA_LR_IDX;
    }

    @Override
    public int getSPIdx() {
        return DW_CFA_SP_IDX;
    }

    @Override
    public int writeInitialInstructions(byte[] buffer, int p) {
        int pos = p;
        /*
         * Invariant: CFA identifies last word of caller stack.
         *
         * So initial cfa is at rsp + 0:
         *
         * <ul>
         *
         * <li><code>def_cfa r31 (sp) offset 0</code>
         *
         * </ul>
         */
        pos = writeDefCFA(DW_CFA_SP_IDX, 0, buffer, pos);
        return pos;
    }

    @Override
    protected int writeFDEs(int frameSize, List<DebugInfoProvider.DebugFrameSizeChange> frameSizeInfos, byte[] buffer, int p) {
        int pos = p;
        int currentOffset = 0;
        for (DebugInfoProvider.DebugFrameSizeChange debugFrameSizeInfo : frameSizeInfos) {
            int advance = debugFrameSizeInfo.getOffset() - currentOffset;
            currentOffset += advance;
            pos = writeAdvanceLoc(advance, buffer, pos);
            if (debugFrameSizeInfo.getType() == DebugInfoProvider.DebugFrameSizeChange.Type.EXTEND) {
                /*
                 * SP has been extended so rebase CFA using full frame.
                 *
                 * Invariant: CFA identifies last word of caller stack.
                 */
                pos = writeDefCFAOffset(frameSize, buffer, pos);
                /*
                 * Notify push of lr and fp to stack slots 1 and 2.
                 *
                 * Scaling by -8 is automatic.
                 */
                pos = writeOffset(DW_CFA_LR_IDX, 1, buffer, pos);
                pos = writeOffset(DW_CFA_FP_IDX, 2, buffer, pos);
            } else {
                /*
                 * SP will have been contracted so rebase CFA using empty frame.
                 *
                 * Invariant: CFA identifies last word of caller stack.
                 */
                pos = writeDefCFAOffset(0, buffer, pos);
                /*
                 * notify restore of fp and lr
                 */
                pos = writeRestore(DW_CFA_FP_IDX, buffer, pos);
                pos = writeRestore(DW_CFA_LR_IDX, buffer, pos);
            }
        }
        return pos;
    }
}
