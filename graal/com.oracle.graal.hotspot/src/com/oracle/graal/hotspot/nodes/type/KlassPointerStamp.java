/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import jdk.internal.jvmci.hotspot.*;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.compiler.common.type.*;

public final class KlassPointerStamp extends MetaspacePointerStamp {

    private final CompressEncoding encoding;

    private final Kind kind;

    public KlassPointerStamp(boolean nonNull, boolean alwaysNull, Kind kind) {
        this(nonNull, alwaysNull, null, kind);
    }

    private KlassPointerStamp(boolean nonNull, boolean alwaysNull, CompressEncoding encoding, Kind kind) {
        super(nonNull, alwaysNull);
        this.encoding = encoding;
        this.kind = kind;
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        return new KlassPointerStamp(newNonNull, newAlwaysNull, encoding, kind);
    }

    @Override
    public boolean isCompatible(Stamp otherStamp) {
        if (this == otherStamp) {
            return true;
        }
        if (otherStamp instanceof KlassPointerStamp) {
            KlassPointerStamp other = (KlassPointerStamp) otherStamp;
            return Objects.equals(this.encoding, other.encoding);
        }
        return false;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        if (isCompressed()) {
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(c)) {
                return new KlassPointerStamp(false, true, encoding, kind);
            }
        } else {
            if (JavaConstant.NULL_POINTER.equals(c)) {
                return new KlassPointerStamp(false, true, encoding, kind);
            }
        }

        assert c instanceof HotSpotMetaspaceConstant;
        assert ((HotSpotMetaspaceConstant) c).isCompressed() == isCompressed();
        if (nonNull()) {
            return this;
        }
        return new KlassPointerStamp(true, false, encoding, kind);
    }

    @Override
    public Constant asConstant() {
        if (alwaysNull() && isCompressed()) {
            return HotSpotCompressedNullConstant.COMPRESSED_NULL;
        } else {
            return super.asConstant();
        }
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        if (isCompressed()) {
            return LIRKind.value(Kind.Int);
        } else {
            return super.getLIRKind(tool);
        }
    }

    public boolean isCompressed() {
        return encoding != null;
    }

    public CompressEncoding getEncoding() {
        return encoding;
    }

    public KlassPointerStamp compressed(CompressEncoding newEncoding) {
        assert !isCompressed();
        return new KlassPointerStamp(nonNull(), alwaysNull(), newEncoding, Kind.Int);
    }

    public KlassPointerStamp uncompressed() {
        assert isCompressed();
        return new KlassPointerStamp(nonNull(), alwaysNull(), Kind.Long);
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        HotSpotMemoryAccessProvider hsProvider = (HotSpotMemoryAccessProvider) provider;
        if (isCompressed()) {
            return hsProvider.readNarrowKlassPointerConstant(base, displacement, encoding);
        } else {
            return hsProvider.readKlassPointerConstant(base, displacement);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((encoding == null) ? 0 : encoding.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof KlassPointerStamp)) {
            return false;
        }
        KlassPointerStamp other = (KlassPointerStamp) obj;
        return Objects.equals(this.encoding, other.encoding);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("Klass*");
        appendString(ret);
        if (isCompressed()) {
            ret.append("(compressed ").append(encoding).append(")");
        }
        return ret.toString();
    }

    @Override
    public Kind getStackKind() {
        return isCompressed() ? Kind.Int : Kind.Long;
    }
}
