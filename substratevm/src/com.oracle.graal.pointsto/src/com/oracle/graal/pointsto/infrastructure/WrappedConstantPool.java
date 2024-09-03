/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.infrastructure;

import static jdk.vm.ci.common.JVMCIError.unimplemented;

import java.util.List;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import com.oracle.graal.pointsto.util.GraalAccess;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class WrappedConstantPool implements ConstantPool, ConstantPoolPatch {

    protected final Universe universe;
    protected final ConstantPool wrapped;
    private final ResolvedJavaType defaultAccessingClass;

    public WrappedConstantPool(Universe universe, ConstantPool wrapped, ResolvedJavaType defaultAccessingClass) {
        this.universe = universe;
        this.wrapped = wrapped;
        this.defaultAccessingClass = defaultAccessingClass;
    }

    @Override
    public int length() {
        return wrapped.length();
    }

    private JavaConstant lookupConstant(JavaConstant constant) {
        return universe.lookup(extractResolvedType(constant));
    }

    public JavaConstant extractResolvedType(JavaConstant constant) {
        if (constant != null && constant.getJavaKind().isObject() && !constant.isNull()) {
            SnippetReflectionProvider snippetReflection = GraalAccess.getOriginalSnippetReflection();
            if (snippetReflection.asObject(Object.class, constant) instanceof ResolvedJavaType resolvedJavaType) {
                /*
                 * BootstrapMethodInvocation.getStaticArguments can output a constant containing a
                 * HotspotJavaType when a static argument of type Class if loaded lazily in pull
                 * mode. In this case, the type has to be converted back to a Class, as it would
                 * cause a hotspot value to be reachable otherwise.
                 *
                 * If the constant contains an UnresolvedJavaType, it cannot be converted as a
                 * Class. It is not a problem for this type to be reachable, so those constants can
                 * be handled later.
                 */
                return snippetReflection.forObject(OriginalClassProvider.getJavaClass(resolvedJavaType));
            }
        }
        return constant;
    }

    @Override
    public void loadReferencedType(int cpi, int opcode, boolean initialize) {
        GraalError.guarantee(!initialize, "Must not initialize classes");
        try {
            wrapped.loadReferencedType(cpi, opcode, initialize);
        } catch (Throwable ex) {
            Throwable cause = ex;
            if (cause instanceof BootstrapMethodError && cause.getCause() != null) {
                cause = cause.getCause();
            } else if (cause instanceof ExceptionInInitializerError && cause.getCause() != null) {
                cause = cause.getCause();
            }
            throw new UnresolvedElementException("Error loading a referenced type: " + cause.toString(), cause);
        }
    }

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        loadReferencedType(cpi, opcode, false);
    }

    @Override
    public JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        return universe.lookupAllowUnresolved(wrapped.lookupField(cpi, OriginalMethodProvider.getOriginalMethod(method), opcode));
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        return universe.lookupAllowUnresolved(wrapped.lookupMethod(cpi, opcode));
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        try {
            return universe.lookupAllowUnresolved(wrapped.lookupMethod(cpi, opcode, OriginalMethodProvider.getOriginalMethod(caller)));
        } catch (Throwable ex) {
            Throwable cause = ex;
            if (ex instanceof ExceptionInInitializerError && ex.getCause() != null) {
                cause = ex.getCause();
            }
            throw new UnresolvedElementException("Error loading a referenced type: " + cause.toString(), cause);
        }
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        return universe.lookupAllowUnresolved(wrapped.lookupType(cpi, opcode));
    }

    @Override
    public ResolvedSignature<?> lookupSignature(int cpi) {
        return universe.lookup(wrapped.lookupSignature(cpi), defaultAccessingClass);
    }

    @Override
    public JavaConstant lookupAppendix(int cpi, int opcode) {
        return lookupConstant(wrapped.lookupAppendix(cpi, opcode));
    }

    @Override
    public String lookupUtf8(int cpi) {
        return wrapped.lookupUtf8(cpi);
    }

    @Override
    public Object lookupConstant(int cpi) {
        return lookupConstant(cpi, true);
    }

    @Override
    public Object lookupConstant(int cpi, boolean resolve) {
        Object con = wrapped.lookupConstant(cpi, resolve);
        if (con instanceof JavaType) {
            if (con instanceof ResolvedJavaType) {
                return universe.lookup((ResolvedJavaType) con);
            } else {
                /* The caller takes care of unresolved types. */
                return con;
            }
        } else if (con instanceof JavaConstant) {
            return lookupConstant((JavaConstant) con);
        } else if (con == null && resolve == false) {
            return null;
        } else {
            throw unimplemented();
        }
    }

    @Override
    public JavaType lookupReferencedType(int index, int opcode) {
        return universe.lookupAllowUnresolved(wrapped.lookupReferencedType(index, opcode));
    }

    @Override
    public BootstrapMethodInvocation lookupBootstrapMethodInvocation(int cpi, int opcode) {
        BootstrapMethodInvocation bootstrapMethodInvocation = wrapped.lookupBootstrapMethodInvocation(cpi, opcode);
        if (bootstrapMethodInvocation != null) {
            return new WrappedBootstrapMethodInvocation(bootstrapMethodInvocation);
        }
        return null;
    }

    public class WrappedBootstrapMethodInvocation implements BootstrapMethodInvocation {

        private final BootstrapMethodInvocation wrapped;

        public WrappedBootstrapMethodInvocation(BootstrapMethodInvocation wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public ResolvedJavaMethod getMethod() {
            return universe.lookup(wrapped.getMethod());
        }

        @Override
        public boolean isInvokeDynamic() {
            return wrapped.isInvokeDynamic();
        }

        @Override
        public String getName() {
            return wrapped.getName();
        }

        @Override
        public JavaConstant getType() {
            return lookupConstant(wrapped.getType());
        }

        @Override
        public List<JavaConstant> getStaticArguments() {
            return wrapped.getStaticArguments().stream().map(WrappedConstantPool.this::lookupConstant).collect(Collectors.toList());
        }
    }
}
