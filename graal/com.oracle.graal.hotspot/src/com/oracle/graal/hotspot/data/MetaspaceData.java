/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.data;

import java.nio.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;

/**
 * A data item that represents a metaspace pointer.
 */
public class MetaspaceData extends PatchedData {

    public final long value;
    public final Object annotation;
    public final boolean compressed;

    public MetaspaceData(int alignment, long value, Object annotation, boolean compressed) {
        super(alignment);
        assert annotation != null;
        this.value = value;
        this.annotation = annotation;
        this.compressed = compressed;
    }

    @Override
    public int getSize(TargetDescription target) {
        if (compressed) {
            return target.getSizeInBytes(Kind.Int);
        } else {
            return target.getSizeInBytes(target.wordKind);
        }
    }

    @Override
    public Kind getKind() {
        return Kind.Long;
    }

    @Override
    public void emit(TargetDescription target, ByteBuffer buffer) {
        switch (getSize(target)) {
            case 4:
                buffer.putInt((int) value);
                break;
            case 8:
                buffer.putLong(value);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("unexpected metaspace pointer size");
        }
    }

    @Override
    public String toString() {
        return (compressed ? "NarrowPointer[0x" + Integer.toHexString((int) value) : "Pointer[0x" + Long.toHexString(value)) + "]{" + annotation + "}";
    }
}
