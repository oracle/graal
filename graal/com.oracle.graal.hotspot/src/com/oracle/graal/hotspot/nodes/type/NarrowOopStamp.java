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
package com.oracle.graal.hotspot.nodes.type;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;

public class NarrowOopStamp extends AbstractObjectStamp {

    public static final PlatformKind NarrowOop = new PlatformKind() {

        public String name() {
            return "NarrowOop";
        }

        @Override
        public String toString() {
            return name();
        }

        public Constant getDefaultValue() {
            return HotSpotCompressedNullConstant.COMPRESSED_NULL;
        }
    };

    private final CompressEncoding encoding;

    public NarrowOopStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull, CompressEncoding encoding) {
        super(type, exactType, nonNull, alwaysNull);
        this.encoding = encoding;
    }

    @Override
    protected AbstractObjectStamp copyWith(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull) {
        return new NarrowOopStamp(type, exactType, nonNull, alwaysNull, encoding);
    }

    public static Stamp compressed(AbstractObjectStamp stamp, CompressEncoding encoding) {
        return new NarrowOopStamp(stamp.type(), stamp.isExactType(), stamp.nonNull(), stamp.alwaysNull(), encoding);
    }

    public Stamp uncompressed() {
        return new ObjectStamp(type(), isExactType(), nonNull(), alwaysNull());
    }

    public CompressEncoding getEncoding() {
        return encoding;
    }

    @Override
    public PlatformKind getPlatformKind(PlatformKindTool tool) {
        return NarrowOop;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append('n');
        appendString(str);
        return str.toString();
    }

    @Override
    public boolean isCompatible(Stamp other) {
        if (this == other) {
            return true;
        }
        if (other instanceof NarrowOopStamp) {
            NarrowOopStamp narrow = (NarrowOopStamp) other;
            return encoding.equals(narrow.encoding);
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + encoding.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        NarrowOopStamp other = (NarrowOopStamp) obj;
        if (!encoding.equals(other.encoding)) {
            return false;
        }
        return super.equals(other);
    }
}
