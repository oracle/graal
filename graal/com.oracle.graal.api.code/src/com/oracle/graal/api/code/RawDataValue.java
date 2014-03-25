/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Represents some raw data.
 */
public final class RawDataValue extends Value {

    private static final long serialVersionUID = 7947228462156566360L;

    private final byte[] data;

    public RawDataValue(PlatformKind kind, byte[] data) {
        super(kind);
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        Formatter ret = new Formatter();
        ret.format("data[");
        boolean first = true;
        for (byte b : data) {
            ret.format(first ? "%02X" : " %02X", b);
            first = false;
        }
        return ret.format("]").toString();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof RawDataValue) {
            RawDataValue other = (RawDataValue) obj;
            return Arrays.equals(data, other.data);
        } else {
            return false;
        }
    }
}
