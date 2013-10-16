/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of {@link ConstantPool} for HotSpot.
 */
public class HotSpotConstantPool extends CompilerObject implements ConstantPool {

    private static final long serialVersionUID = -5443206401485234850L;

    private final HotSpotResolvedObjectType type;

    public HotSpotConstantPool(HotSpotResolvedObjectType type) {
        this.type = type;
    }

    @Override
    public int length() {
        return runtime().getCompilerToVM().constantPoolLength(type);
    }

    @Override
    public Object lookupConstant(int cpi) {
        assert cpi != 0;
        Object constant = runtime().getCompilerToVM().lookupConstantInPool(type, cpi);
        return constant;
    }

    @Override
    public Signature lookupSignature(int cpi) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object lookupAppendix(int cpi, int opcode) {
        assert Bytecodes.isInvoke(opcode);
        return runtime().getCompilerToVM().lookupAppendixInPool(type, cpi, (byte) opcode);
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        return runtime().getCompilerToVM().lookupMethodInPool(type, cpi, (byte) opcode);
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        return runtime().getCompilerToVM().lookupTypeInPool(type, cpi);
    }

    @Override
    public JavaField lookupField(int cpi, int opcode) {
        return runtime().getCompilerToVM().lookupFieldInPool(type, cpi, (byte) opcode);
    }

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        runtime().getCompilerToVM().lookupReferencedTypeInPool(type, cpi, (byte) opcode);
    }
}
