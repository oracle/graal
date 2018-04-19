/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.infrastructure;

import static jdk.vm.ci.common.JVMCIError.unimplemented;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.util.AnalysisError.TypeNotFoundError;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class WrappedConstantPool implements ConstantPool {

    private final Universe universe;
    protected final ConstantPool wrapped;
    private final WrappedJavaType defaultAccessingClass;

    public WrappedConstantPool(Universe universe, ConstantPool wrapped, WrappedJavaType defaultAccessingClass) {
        this.universe = universe;
        this.wrapped = wrapped;
        this.defaultAccessingClass = defaultAccessingClass;
    }

    @Override
    public int length() {
        return wrapped.length();
    }

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        try {
            wrapped.loadReferencedType(cpi, opcode);
        } catch (Throwable ex) {
            Throwable cause = ex;
            if (ex instanceof ExceptionInInitializerError && ex.getCause() != null) {
                cause = ex.getCause();
            }
            throw new UnsupportedFeatureException("Error loading a referenced type: " + cause.toString(), cause);
        }
    }

    @Override
    public JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        ResolvedJavaMethod substMethod = universe.resolveSubstitution(((WrappedJavaMethod) method).getWrapped());
        return universe.lookupAllowUnresolved(wrapped.lookupField(cpi, substMethod, opcode));
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        return universe.lookupAllowUnresolved(wrapped.lookupMethod(cpi, opcode));
    }

    /**
     * Trying to get straight to the VM constant pool without going through the layers of universe
     * lookups. The VM constant pool resolves a method without resolving its return/parameter/locals
     * types, whereas the universe is usually eagerly tries to resolve return/parameter/locals.
     * Thus, this method can be used when the JavaMethod is needed even when the universe resolution
     * fails. Since there might be multiple constant pools that wrap each other for various stages
     * in compilation, potentially each with a different universe, we go down until the constant
     * pool found is not a WrappedConstantPool.
     */
    public JavaMethod lookupMethodInWrapped(int cpi, int opcode) {
        if (wrapped instanceof WrappedConstantPool) {
            return ((WrappedConstantPool) wrapped).lookupMethodInWrapped(cpi, opcode);
        } else {
            return wrapped.lookupMethod(cpi, opcode);
        }
    }

    public JavaType lookupTypeInWrapped(int cpi, int opcode) {
        if (wrapped instanceof WrappedConstantPool) {
            return ((WrappedConstantPool) wrapped).lookupTypeInWrapped(cpi, opcode);
        } else {
            return wrapped.lookupType(cpi, opcode);
        }
    }

    public JavaField lookupFieldInWrapped(int cpi, ResolvedJavaMethod method, int opcode) {
        if (wrapped instanceof WrappedConstantPool) {
            return ((WrappedConstantPool) wrapped).lookupFieldInWrapped(cpi, method, opcode);
        } else {
            return wrapped.lookupField(cpi, method, opcode);
        }
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        try {
            return universe.lookupAllowUnresolved(wrapped.lookupType(cpi, opcode));
        } catch (TypeNotFoundError e) {
            /* If the universe was sealed there are no new types created. */
            return null;
        }
    }

    @Override
    public WrappedSignature lookupSignature(int cpi) {
        return universe.lookup(wrapped.lookupSignature(cpi), defaultAccessingClass);
    }

    @Override
    public JavaConstant lookupAppendix(int cpi, int opcode) {
        return universe.lookup(wrapped.lookupAppendix(cpi, opcode));
    }

    @Override
    public String lookupUtf8(int cpi) {
        return wrapped.lookupUtf8(cpi);
    }

    @Override
    public Object lookupConstant(int cpi) {
        Object con = wrapped.lookupConstant(cpi);
        if (con instanceof JavaType) {
            return universe.lookup((ResolvedJavaType) con);
        } else if (con instanceof JavaConstant) {
            return universe.lookup((JavaConstant) con);
        } else {
            throw unimplemented();
        }
    }
}
