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

/**
 * AArch64-specific section generator for debug_frame section that knows details of AArch64
 * registers and frame layout.
 */
public class DwarfFrameSectionImplAArch64 extends DwarfFrameSectionImpl {
    // public static final int DW_CFA_FP_IDX = 29;
    private static final int DW_CFA_LR_IDX = 30;
    private static final int DW_CFA_SP_IDX = 31;
    private static final int DW_CFA_PC_IDX = 32;

    public DwarfFrameSectionImplAArch64(DwarfSections dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public int getPCIdx() {
        return DW_CFA_PC_IDX;
    }

    @Override
    public int getSPIdx() {
        return DW_CFA_SP_IDX;
    }

    @Override
    public int writeInitialInstructions(byte[] buffer, int p) {
        int pos = p;
        /*
         * rsp has not been updated and caller pc is in lr
         *
         * <ul>
         *
         * <li><code>register r32 (rpc), r30 (lr)</code>
         *
         * </ul>
         */
        pos = writeRegister(DW_CFA_PC_IDX, DW_CFA_LR_IDX, buffer, pos);
        return pos;
    }
}
