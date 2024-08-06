/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Singleton stamp representing the value of type {@code void}.
 */
public final class VoidStamp extends Stamp {

    private VoidStamp() {
    }

    @Override
    public void accept(Visitor v) {
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
    public JavaKind getStackKind() {
        return JavaKind.Void;
    }

    @Override
    public Stamp improveWith(Stamp other) {
        assert other instanceof VoidStamp : other;
        return this;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        throw GraalError.shouldNotReachHere("void stamp has no value"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(Void.TYPE);
    }

    @Override
    public String toString() {
        return "void";
    }

    @Override
    public boolean alwaysDistinct(Stamp other) {
        return this != other;
    }

    @Override
    public Stamp meet(Stamp other) {
        assert other instanceof VoidStamp : other;
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        assert other instanceof VoidStamp : other;
        return this;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        return stamp instanceof VoidStamp;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return false;
    }

    @Override
    public Stamp empty() {
        // the void stamp is always empty
        return this;
    }

    @Override
    public boolean hasValues() {
        return false;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        throw GraalError.shouldNotReachHere("can't read values of void stamp"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        throw GraalError.shouldNotReachHere("void stamp has no value"); // ExcludeFromJacocoGeneratedReport
    }

    private static final VoidStamp instance = new VoidStamp();

    static VoidStamp getInstance() {
        return instance;
    }
}
