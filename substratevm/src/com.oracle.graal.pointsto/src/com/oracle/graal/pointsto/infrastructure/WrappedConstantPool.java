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

import org.graalvm.compiler.core.common.BootstrapMethodIntrospection;
import org.graalvm.compiler.debug.GraalError;

import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import com.oracle.graal.pointsto.util.AnalysisError.TypeNotFoundError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.hotspot.HotSpotConstantPool;
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

    /**
     * The interface jdk.vm.ci.meta.ConstantPool.BootstrapMethodInvocation was introduced in JVMCI
     * 22.1.
     */
    private static final Class<?> bsmClass = ReflectionUtil.lookupClass(true, "jdk.vm.ci.meta.ConstantPool$BootstrapMethodInvocation");
    private static final Method bsmGetMethod = bsmClass == null ? null : ReflectionUtil.lookupMethod(bsmClass, "getMethod");
    private static final Method bsmIsInvokeDynamic = bsmClass == null ? null : ReflectionUtil.lookupMethod(bsmClass, "isInvokeDynamic");
    private static final Method bsmGetName = bsmClass == null ? null : ReflectionUtil.lookupMethod(bsmClass, "getName");
    private static final Method bsmGetType = bsmClass == null ? null : ReflectionUtil.lookupMethod(bsmClass, "getType");
    private static final Method bsmGetStaticArguments = bsmClass == null ? null : ReflectionUtil.lookupMethod(bsmClass, "getStaticArguments");

    public static void loadReferencedType(ConstantPool cp, int cpi, int opcode, boolean initialize) {
        ConstantPool root = cp;
        while (root instanceof WrappedConstantPool) {
            root = ((WrappedConstantPool) root).wrapped;
        }

        try {
            /*
             * GR-41975: loadReferencedType without triggering class initialization is available in
             * HotSpotConstantPool, but not yet in ConstantPool.
             */
            ((HotSpotConstantPool) root).loadReferencedType(cpi, opcode, initialize);
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
        loadReferencedType(wrapped, cpi, opcode, false);
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
            if (con instanceof ResolvedJavaType) {
                return universe.lookup((ResolvedJavaType) con);
            } else {
                /* The caller takes care of unresolved types. */
                return con;
            }
        } else if (con instanceof JavaConstant) {
            return universe.lookup((JavaConstant) con);
        } else {
            throw unimplemented();
        }
    }

    @Override
    public JavaType lookupReferencedType(int index, int opcode) {
        try {
            JavaType type = wrapped.lookupReferencedType(index, opcode);
            if (type != null) {
                return universe.lookupAllowUnresolved(type);
            }
        } catch (TypeNotFoundError e) {
        }
        return null;
    }

    public BootstrapMethodIntrospection lookupBootstrapMethodIntrospection(int cpi, int opcode) {
        if (cpLookupBootstrapMethodInvocation != null) {
            try {
                Object bootstrapMethodInvocation = cpLookupBootstrapMethodInvocation.invoke(wrapped, cpi, opcode);
                return new WrappedBootstrapMethodInvocation(bootstrapMethodInvocation);
            } catch (Throwable ignored) {
                // GR-38955 - understand why exception is thrown
            }
        }
        return null;
    }

    public class WrappedBootstrapMethodInvocation implements BootstrapMethodIntrospection {
        private final Object wrapped;

        public WrappedBootstrapMethodInvocation(Object wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public ResolvedJavaMethod getMethod() {
            if (bsmGetMethod != null) {
                try {
                    return universe.lookup((ResolvedJavaMethod) bsmGetMethod.invoke(wrapped));
                } catch (Throwable t) {
                    throw GraalError.shouldNotReachHere(t);
                }
            }
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public boolean isInvokeDynamic() {
            if (bsmIsInvokeDynamic != null) {
                try {
                    return (boolean) bsmIsInvokeDynamic.invoke(wrapped);
                } catch (Throwable t) {
                    throw GraalError.shouldNotReachHere(t);
                }
            }
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public String getName() {
            if (bsmGetName != null) {
                try {
                    return (String) bsmGetName.invoke(wrapped);
                } catch (Throwable t) {
                    throw GraalError.shouldNotReachHere(t);
                }
            }
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public JavaConstant getType() {
            if (bsmGetType != null) {
                try {
                    return universe.lookup((JavaConstant) bsmGetType.invoke(wrapped));
                } catch (Throwable t) {
                    throw GraalError.shouldNotReachHere(t);
                }
            }
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public List<JavaConstant> getStaticArguments() {
            if (bsmGetStaticArguments != null) {
                try {
                    List<?> original = (List<?>) bsmGetStaticArguments.invoke(wrapped);
                    return original.stream().map(e -> universe.lookup((JavaConstant) e)).collect(Collectors.toList());
                } catch (Throwable t) {
                    throw GraalError.shouldNotReachHere(t);
                }
            }
            throw GraalError.shouldNotReachHere();
        }
    }
}
