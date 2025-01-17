/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.graal;

import java.util.Objects;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import com.oracle.truffle.espresso.jvmci.meta.KlassConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class KlassStamp extends AbstractPointerStamp {
    KlassStamp(boolean nonNull, boolean alwaysNull) {
        super(nonNull, alwaysNull);
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        return new KlassStamp(newNonNull, newAlwaysNull);
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("KlassStamp has no Java type");
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public Stamp join(Stamp other) {
        return defaultPointerJoin(other);
    }

    @Override
    public Stamp empty() {
        return copyWith(true, true);
    }

    @Override
    public boolean hasValues() {
        return !(alwaysNull() && nonNull());
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return new KlassStamp(false, true);
        }
        assert c instanceof KlassConstant;
        return new KlassStamp(true, false);
    }

    @Override
    public boolean isCompatible(Stamp other) {
        return other instanceof KlassStamp;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof KlassConstant) {
            return !alwaysNull();
        } else {
            return !nonNull() && JavaConstant.NULL_POINTER.equals(constant);
        }
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public String toString() {
        if (!hasValues()) {
            return "Klass empty";
        }
        if (alwaysNull()) {
            return "Klass NULL";
        }
        if (nonNull()) {
            return "!Klass";
        }
        return "Klass";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        KlassStamp other = (KlassStamp) obj;
        return this.alwaysNull() == other.alwaysNull() && this.nonNull() == other.nonNull();
    }

    @Override
    public int hashCode() {
        return Objects.hash(alwaysNull(), nonNull());
    }
}
