/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

public class SubstrateMethodPointerStamp extends AbstractPointerStamp {

    private static final SubstrateMethodPointerStamp METHOD_NON_NULL = new SubstrateMethodPointerStamp(true, false);
    private static final SubstrateMethodPointerStamp METHOD_ALWAYS_NULL = new SubstrateMethodPointerStamp(false, true);
    private static final SubstrateMethodPointerStamp METHOD = new SubstrateMethodPointerStamp(false, false);

    protected SubstrateMethodPointerStamp(boolean nonNull, boolean alwaysNull) {
        super(nonNull, alwaysNull);
    }

    public static SubstrateMethodPointerStamp methodNonNull() {
        return METHOD_NON_NULL;
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        if (newNonNull) {
            assert !newAlwaysNull;
            return METHOD_NON_NULL;
        } else if (newAlwaysNull) {
            return METHOD_ALWAYS_NULL;
        } else {
            return METHOD;
        }
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
            return METHOD_ALWAYS_NULL;
        } else {
            assert c instanceof SubstrateMethodPointerConstant;
            return METHOD_NON_NULL;
        }
    }

    @Override
    public boolean isCompatible(Stamp other) {
        return other instanceof SubstrateMethodPointerStamp;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return constant instanceof SubstrateMethodPointerConstant;
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
        return "SVMMethod*";
    }
}
