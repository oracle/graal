/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.meta;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SubstrateMethodRefStamp extends AbstractPointerStamp {

    private enum Kind {
        POINTER("SVMMethod*"),
        OFFSET("SVMMethodOffset");

        private final String description;
        private final SubstrateMethodRefStamp nullableStamp;
        private final SubstrateMethodRefStamp nonNullStamp;
        private final SubstrateMethodRefStamp alwaysNullStamp;

        Kind(String description) {
            this.description = description;
            this.nullableStamp = new SubstrateMethodRefStamp(this, false, false);
            this.nonNullStamp = new SubstrateMethodRefStamp(this, true, false);
            this.alwaysNullStamp = new SubstrateMethodRefStamp(this, false, true);
        }

        boolean isCompatibleConstant(Constant constant) {
            return switch (this) {
                case POINTER -> constant instanceof SubstrateMethodPointerConstant;
                case OFFSET -> constant instanceof SubstrateMethodOffsetConstant;
            };
        }

        SubstrateMethodRefStamp forFlags(boolean nonNull, boolean alwaysNull) {
            assert !(nonNull && alwaysNull);
            return nonNull ? nonNullStamp : (alwaysNull ? alwaysNullStamp : nullableStamp);
        }
    }

    private final Kind kind;

    private SubstrateMethodRefStamp(Kind kind, boolean nonNull, boolean alwaysNull) {
        super(nonNull, alwaysNull);
        this.kind = kind;
    }

    public static SubstrateMethodRefStamp pointerNonNull() {
        return Kind.POINTER.nonNullStamp;
    }

    public static SubstrateMethodRefStamp pointerAlwaysNull() {
        return Kind.POINTER.alwaysNullStamp;
    }

    public static SubstrateMethodRefStamp offsetNonNull() {
        return Kind.OFFSET.nonNullStamp;
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        return kind.forFlags(newNonNull, newAlwaysNull);
    }

    public boolean isOffset() {
        return kind == Kind.OFFSET;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("pointer has no Java type"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getWordKind();
    }

    @Override
    public Stamp join(Stamp other) {
        return defaultPointerJoin(other);
    }

    @Override
    public Stamp empty() {
        return this;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return kind.alwaysNullStamp;
        } else {
            assert kind.isCompatibleConstant(c);
            return kind.nonNullStamp;
        }
    }

    @Override
    public boolean isCompatible(Stamp other) {
        return other instanceof SubstrateMethodRefStamp that && kind == that.kind;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return JavaConstant.NULL_POINTER.equals(constant) || kind.isCompatibleConstant(constant);
    }

    @Override
    public boolean hasValues() {
        return true;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        return null;
    }

    @Override
    public String toString() {
        return kind.description;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof SubstrateMethodRefStamp other && kind == other.kind;
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 31 + kind.ordinal();
    }
}
