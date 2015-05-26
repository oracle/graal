/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.type;

import com.oracle.jvmci.meta.PrimitiveConstant;
import com.oracle.jvmci.meta.ResolvedJavaType;
import com.oracle.jvmci.meta.LIRKind;
import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.MemoryAccessProvider;
import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.Constant;
import com.oracle.graal.compiler.common.spi.*;
import com.oracle.jvmci.common.*;

/**
 * This stamp represents the type of the {@link Kind#Illegal} value in the second slot of
 * {@link Kind#Long} and {@link Kind#Double} values. It can only appear in framestates or virtual
 * objects.
 */
public final class IllegalStamp extends Stamp {

    private IllegalStamp() {
    }

    @Override
    public Kind getStackKind() {
        return Kind.Illegal;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        throw JVMCIError.shouldNotReachHere("illegal stamp should not reach backend");
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public Stamp empty() {
        return this;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        assert ((PrimitiveConstant) c).getKind() == Kind.Illegal;
        return this;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw JVMCIError.shouldNotReachHere("illegal stamp has no Java type");
    }

    @Override
    public Stamp meet(Stamp other) {
        assert other instanceof IllegalStamp;
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        assert other instanceof IllegalStamp;
        return this;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        return stamp instanceof IllegalStamp;
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
        assert other instanceof IllegalStamp;
        return this;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        throw JVMCIError.shouldNotReachHere("can't read values of illegal stamp");
    }

    private static final IllegalStamp instance = new IllegalStamp();

    static IllegalStamp getInstance() {
        return instance;
    }
}
