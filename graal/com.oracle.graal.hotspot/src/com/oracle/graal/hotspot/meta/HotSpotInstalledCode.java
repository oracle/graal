/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.graph.UnsafeAccess.*;
import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of {@link InstalledCode} for HotSpot.
 */
public abstract class HotSpotInstalledCode extends CompilerObject implements InstalledCode {

    private static final long serialVersionUID = 156632908220561612L;

    /**
     * Raw address of this code blob.
     */
    private long codeBlob;

    /**
     * Total size of the code blob.
     */
    private int size;

    /**
     * Start address of the code.
     */
    private long codeStart;

    /**
     * Size of the code.
     */
    private int codeSize;

    /**
     * @return the address of this code blob
     */
    public long getCodeBlob() {
        return codeBlob;
    }

    /**
     * @return the total size of this code blob
     */
    public int getSize() {
        return size;
    }

    /**
     * @return a copy of this code blob if it is {@linkplain #isValid() valid}, null otherwise.
     */
    public byte[] getBlob() {
        if (!isValid()) {
            return null;
        }
        byte[] blob = new byte[size];
        unsafe.copyMemory(null, codeBlob, blob, Unsafe.ARRAY_BYTE_BASE_OFFSET, size);
        return blob;
    }

    @Override
    public abstract String toString();

    public long getStart() {
        return codeStart;
    }

    public long getCodeSize() {
        return codeSize;
    }

    public byte[] getCode() {
        if (!isValid()) {
            return null;
        }
        byte[] code = new byte[codeSize];
        unsafe.copyMemory(null, codeStart, code, Unsafe.ARRAY_BYTE_BASE_OFFSET, codeSize);
        return code;
    }
}
