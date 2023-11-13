/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This stamp represents the type of the {@link JavaKind#Illegal} value in the second slot of
 * {@link JavaKind#Long} and {@link JavaKind#Double} values. It can only appear in framestates or
 * virtual objects.
 */
public final class IllegalStamp extends Stamp {

    private IllegalStamp() {
    }

    @Override
    public void accept(Visitor v) {
    }

    @Override
    public JavaKind getStackKind() {
        return JavaKind.Illegal;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return LIRKind.Illegal;
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public boolean isUnrestricted() {
        return true;
    }

    @Override
    public Stamp empty() {
        return this;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        assert ((PrimitiveConstant) c).getJavaKind() == JavaKind.Illegal : c;
        return this;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("illegal stamp has no Java type"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Stamp meet(Stamp other) {
        assert other instanceof IllegalStamp : other;
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        assert other instanceof IllegalStamp : other;
        return this;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        return stamp instanceof IllegalStamp;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) constant;
            return prim.getJavaKind() == JavaKind.Illegal;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ILLEGAL";
    }

    @Override
    public boolean hasValues() {
        return true;
    }

    @Override
    public Stamp improveWith(Stamp other) {
        assert other instanceof IllegalStamp : other;
        return this;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        throw GraalError.shouldNotReachHere("can't read values of illegal stamp"); // ExcludeFromJacocoGeneratedReport
    }

    private static final IllegalStamp instance = new IllegalStamp();

    static IllegalStamp getInstance() {
        return instance;
    }
}
