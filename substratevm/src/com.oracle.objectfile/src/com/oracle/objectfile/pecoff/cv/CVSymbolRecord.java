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

import org.graalvm.compiler.debug.DebugContext;

/*
 * A Symbol record is a top-level record in the CodeView .debug$S section.
 */
abstract class CVSymbolRecord {

    /* Symbol record header: record type (4 bytes), length (4 bytes). */
    private static final int SYMBOL_RECORD_HEADER_SIZE = Integer.BYTES * 2;

    protected final CVDebugInfo cvDebugInfo;
    protected final int type;
    protected int recordStartPosition;

    CVSymbolRecord(CVDebugInfo cvDebugInfo, int type) {
        this.cvDebugInfo = cvDebugInfo;
        this.type = type;
    }

    final int computeFullSize(int initialPos) {
        this.recordStartPosition = initialPos;
        int pos = initialPos + SYMBOL_RECORD_HEADER_SIZE;
        return computeSize(pos);
    }

    final int computeFullContents(byte[] buffer, int initialPos) {
        int pos = CVUtil.putInt(type, buffer, initialPos);
        int lenPos = pos;
        pos = computeContents(buffer, initialPos + SYMBOL_RECORD_HEADER_SIZE);
        /* Length does not include debug record header (4 bytes record id + 4 bytes length). */
        CVUtil.putInt(pos - initialPos - SYMBOL_RECORD_HEADER_SIZE, buffer, lenPos);
        return pos;
    }

    public int getPos() {
        return recordStartPosition;
    }

    public int getCommand() {
        return type;
    }

    protected abstract int computeSize(int pos);

    protected abstract int computeContents(byte[] buffer, int pos);

    @Override
    public String toString() {
        return "CVSymbolRecord(type=" + type + ",pos=" + recordStartPosition + ")";
    }

    public void logContents(@SuppressWarnings("unused") DebugContext debugContext) {
    }
}
