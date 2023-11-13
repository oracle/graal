/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.type;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Assertions;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Type describing pointers to raw memory. This stamp is used for example for direct pointers to
 * fields or array elements.
 */
public class RawPointerStamp extends AbstractPointerStamp {

    protected RawPointerStamp() {
        super(false, false);
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getWordKind();
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        // RawPointerStamp is a singleton
        assert newNonNull == nonNull() && newAlwaysNull == alwaysNull() : Assertions.errorMessageContext("nonNull", nonNull(), "alwaysNull", alwaysNull(), "newNonNull", newNonNull, "newAlwaysNull",
                        newAlwaysNull);
        return this;
    }

    @Override
    public Stamp meet(Stamp other) {
        assert isCompatible(other);
        return this;
    }

    @Override
    public Stamp improveWith(Stamp other) {
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        assert isCompatible(other);
        return this;
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public Stamp empty() {
        // there is no empty pointer stamp
        return this;
    }

    @Override
    public boolean hasValues() {
        return true;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("pointer has no Java type"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        return this;
    }

    @Override
    public boolean isCompatible(Stamp other) {
        return other instanceof RawPointerStamp;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof PrimitiveConstant) {
            return ((PrimitiveConstant) constant).getJavaKind().isNumericInteger();
        } else {
            return constant instanceof DataPointerConstant;
        }
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        throw GraalError.shouldNotReachHere("can't read raw pointer"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public String toString() {
        return "void*";
    }
}
