/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

import java.util.ArrayList;

/*
 * A CVSymbolSubsection is s special record in debug$S containing nested symbol records.
 * (the nested records inherit from CVSymbolSubrecord)
 */
final class CVSymbolSubsection extends CVSymbolRecord {

    private static final int SUBCMD_INITIAL_CAPACITY = 100;

    private final ArrayList<CVSymbolSubrecord> subcmds = new ArrayList<>(SUBCMD_INITIAL_CAPACITY);

    CVSymbolSubsection(CVDebugInfo cvDebugInfo) {
        super(cvDebugInfo, CVDebugConstants.DEBUG_S_SYMBOLS);
    }

    void addRecord(CVSymbolSubrecord subcmd) {
        subcmds.add(subcmd);
    }

    @Override
    protected int computeSize(int initialPos) {
        return computeContents(null, initialPos);
    }

    @Override
    protected int computeContents(byte[] buffer, int initialPos) {
        int pos = initialPos;
        for (CVSymbolSubrecord subcmd : subcmds) {
            pos = subcmd.computeFullContents(buffer, pos);
        }
        return pos;
    }

    @Override
    public void logContents() {
        CVSectionImpl section = cvDebugInfo.getCVSymbolSection();
        for (CVSymbolSubrecord subcmd : subcmds) {
            section.log("     [0x%08x]  %s", subcmd.getPos(), subcmd.toString());
        }
    }

    @Override
    public String toString() {
        return String.format("DEBUG_S_SYMBOLS type=0x%04x pos=0x%05x subrecordcount=%d", type, recordStartPosition, subcmds.size());
    }
}
