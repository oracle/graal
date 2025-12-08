/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import java.util.List;
import java.util.function.Function;

import com.oracle.svm.interpreter.Interpreter;
import com.oracle.svm.interpreter.metadata.CremaResolvedJavaFieldImpl;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaMethod;

/**
 * JVMCI representation of a {@link ConstantPool} used by Ristretto for compilation. Exists once per
 * {@link InterpreterConstantPool}. Allocated during runtime compilation by a
 * {@link RistrettoMethod} if the parser accesses the constant pool over JVMCI.
 * <p>
 * Life cycle: lives until the referencing {@link InterpreterConstantPool} is gc-ed.
 */
public final class RistrettoConstantPool implements ConstantPool {
    private final InterpreterConstantPool interpreterConstantPool;

    private RistrettoConstantPool(InterpreterConstantPool interpreterConstantPool) {
        this.interpreterConstantPool = interpreterConstantPool;
    }

    private static final Function<InterpreterConstantPool, ConstantPool> RISTRETTO_CONSTANTPOOL_FUNCTION = RistrettoConstantPool::new;

    public static RistrettoConstantPool create(InterpreterConstantPool interpreterConstantPool) {
        return (RistrettoConstantPool) interpreterConstantPool.getRistrettoConstantPool(RISTRETTO_CONSTANTPOOL_FUNCTION);
    }

    @Override
    public int length() {
        return interpreterConstantPool.length();
    }

    @Override
    public void loadReferencedType(int rawIndex, int opcode) {
        interpreterConstantPool.loadReferencedType(rawIndex, opcode);
    }

    @Override
    public JavaType lookupReferencedType(int rawIndex, int opcode) {
        JavaType lookupType = interpreterConstantPool.lookupReferencedType(rawIndex, opcode);
        if (lookupType instanceof InterpreterResolvedJavaType iType) {
            return RistrettoType.create(iType);
        }
        return lookupType;
    }

    @Override
    public JavaField lookupField(int rawIndex, ResolvedJavaMethod method, int opcode) {
        JavaField javaField = interpreterConstantPool.lookupField(rawIndex, method, opcode);
        if (javaField instanceof CremaResolvedJavaFieldImpl iField) {
            return RistrettoField.create(iField);
        }
        return javaField;
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        assert caller instanceof RistrettoMethod;
        InterpreterResolvedJavaMethod iMethod = ((RistrettoMethod) caller).getInterpreterMethod();
        // unresolved still
        if (iMethod.getConstantPool().peekCachedEntry(cpi) instanceof UnresolvedJavaMethod unresolvedJavaMethod) {
            return unresolvedJavaMethod;
        }
        return RistrettoMethod.create(Interpreter.resolveMethod(iMethod, opcode, (char) cpi));
    }

    @Override
    public List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic) {
        return interpreterConstantPool.lookupBootstrapMethodInvocations(invokeDynamic);
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        JavaType lookupType = interpreterConstantPool.lookupType(cpi, opcode);
        if (lookupType instanceof InterpreterResolvedJavaType iType) {
            return RistrettoType.create(iType);
        }
        return lookupType;
    }

    @Override
    public String lookupUtf8(int cpi) {
        return interpreterConstantPool.lookupUtf8(cpi);
    }

    @Override
    public Signature lookupSignature(int cpi) {
        return interpreterConstantPool.lookupSignature(cpi);
    }

    @Override
    public Object lookupConstant(int cpi) {
        return interpreterConstantPool.lookupConstant(cpi);
    }

    @Override
    public Object lookupConstant(int cpi, boolean resolve) {
        return interpreterConstantPool.lookupConstant(cpi, resolve);
    }

    @Override
    public JavaConstant lookupAppendix(int rawIndex, int opcode) {
        return interpreterConstantPool.lookupAppendix(rawIndex, opcode);
    }

    @Override
    public String toString() {
        return "RistrettoConstantPool{interpreterConstantPool=" + interpreterConstantPool + '}';
    }
}
