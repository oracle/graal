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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.BootstrapMethodIntrospection;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.serviceprovider.BootstrapMethodIntrospectionImpl;
import jdk.graal.compiler.serviceprovider.GraalServices;
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

    /**
     * The method jdk.vm.ci.meta.ConstantPool#lookupBootstrapMethodInvocation(int cpi, int opcode)
     * was introduced in JVMCI 22.1.
     */
    private static final Method cpLookupBootstrapMethodInvocation = ReflectionUtil.lookupMethod(true, ConstantPool.class, "lookupBootstrapMethodInvocation", int.class, int.class);

    /**
     * {@code jdk.vm.ci.meta.ConstantPool.lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller)}
     * was introduced in JVMCI 22.3.
     */
    private static final Method lookupMethodWithCaller = ReflectionUtil.lookupMethod(true, ConstantPool.class, "lookupMethod", int.class, int.class, ResolvedJavaMethod.class);

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
        ResolvedJavaMethod substMethod = universe.resolveSubstitution(((WrappedJavaMethod) method).getWrapped());
        return universe.lookupAllowUnresolved(wrapped.lookupField(cpi, substMethod, opcode));
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        return universe.lookupAllowUnresolved(wrapped.lookupMethod(cpi, opcode));
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        if (lookupMethodWithCaller == null) {
            /* Resort to version without caller. */
            return lookupMethod(cpi, opcode);
        }
        try {
            /* Unwrap the caller method. */
            ResolvedJavaMethod substCaller = universe.resolveSubstitution(((WrappedJavaMethod) caller).getWrapped());
            /*
             * Delegate to the lookup with caller method of the wrapped constant pool (via
             * reflection).
             */
            return universe.lookupAllowUnresolved((JavaMethod) lookupMethodWithCaller.invoke(wrapped, cpi, opcode, substCaller));
        } catch (Throwable ex) {
            Throwable cause = ex;
            if (ex instanceof InvocationTargetException && ex.getCause() != null) {
                cause = ex.getCause();
            } else if (ex instanceof ExceptionInInitializerError && ex.getCause() != null) {
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
        Object con = GraalServices.lookupConstant(wrapped, cpi, resolve);
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

    public BootstrapMethodIntrospection lookupBootstrapMethodIntrospection(int cpi, int opcode) {
        if (cpLookupBootstrapMethodInvocation != null) {
            try {
                Object bootstrapMethodInvocation = cpLookupBootstrapMethodInvocation.invoke(wrapped, cpi, opcode);
                if (bootstrapMethodInvocation != null) {
                    return new WrappedBootstrapMethodInvocation(bootstrapMethodInvocation);
                }
            } catch (InvocationTargetException ex) {
                throw rethrow(ex.getCause());
            } catch (IllegalAccessException e) {
                throw GraalError.shouldNotReachHere(e, "The method lookupBootstrapMethodInvocation should be accessible.");
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    public class WrappedBootstrapMethodInvocation extends BootstrapMethodIntrospectionImpl {

        public WrappedBootstrapMethodInvocation(Object wrapped) {
            super(wrapped);
        }

        @Override
        public ResolvedJavaMethod getMethod() {
            return universe.lookup(super.getMethod());
        }

        @Override
        public JavaConstant getType() {
            return lookupConstant(super.getType());
        }

        @Override
        public List<JavaConstant> getStaticArguments() {
            return super.getStaticArguments().stream().map(WrappedConstantPool.this::lookupConstant).collect(Collectors.toList());
        }
    }
}
