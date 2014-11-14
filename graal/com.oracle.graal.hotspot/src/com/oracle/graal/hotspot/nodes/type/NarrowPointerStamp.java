/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.hotspot.HotSpotVMConfig.*;
import com.oracle.graal.hotspot.meta.*;

public class NarrowPointerStamp extends AbstractPointerStamp {

    private final CompressEncoding encoding;

    public NarrowPointerStamp(PointerType type, CompressEncoding encoding) {
        super(type);
        assert type != PointerType.Object : "object pointers should use NarrowOopStamp";
        this.encoding = encoding;
    }

    @Override
    public boolean isCompatible(Stamp otherStamp) {
        if (this == otherStamp) {
            return true;
        }
        if (otherStamp instanceof NarrowPointerStamp) {
            NarrowPointerStamp other = (NarrowPointerStamp) otherStamp;
            return encoding.equals(other.encoding) && super.isCompatible(other);
        }
        return false;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return LIRKind.value(Kind.Int);
    }

    @Override
    public Stamp meet(Stamp other) {
        if (!isCompatible(other)) {
            return StampFactory.illegal();
        }
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        if (!isCompatible(other)) {
            return StampFactory.illegal();
        }
        return this;
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public Stamp illegal() {
        // there is no illegal pointer stamp
        return this;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        assert (c instanceof HotSpotMetaspaceConstantImpl) && ((HotSpotMetaspaceConstantImpl) c).isCompressed();
        return this;
    }

    @Override
    public Constant readConstant(ConstantReflectionProvider provider, Constant base, long displacement) {
        return ((HotSpotConstantReflectionProvider) provider).readNarrowPointerConstant(getType(), base, displacement);
    }

    @Override
    public boolean isLegal() {
        return true;
    }

    @Override
    public String toString() {
        return "narrow " + super.toString();
    }
}
