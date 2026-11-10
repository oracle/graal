/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

import static com.oracle.truffle.espresso.jvmci.meta.EspressoConstantPool.INVOKEDYNAMIC;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;

/**
 * An implementation of BootstrapMethodInvocation copied from
 * jdk.vm.ci.hotspot.HotSpotConstantPool.BootstrapMethodInvocationImpl.
 */
public final class EspressoBootstrapMethodInvocation implements ConstantPool.BootstrapMethodInvocation {
    private final boolean indy;
    private final AbstractEspressoResolvedJavaMethod method;
    private final String name;
    private final JavaConstant type;
    private final List<JavaConstant> staticArguments;
    private final int[] staticArgumentIndices;
    private final int methodHandleIndex;
    private final int cpi;
    private final AbstractEspressoConstantPool constantPool;

    /**
     * Takes ownership of the freshly allocated argument arrays. Callers must not modify them after
     * construction.
     */
    public EspressoBootstrapMethodInvocation(boolean indy, AbstractEspressoResolvedJavaMethod method, String name, JavaConstant type, JavaConstant[] staticArguments, int[] staticArgumentIndices,
                    int cpi, int methodHandleIndex, AbstractEspressoConstantPool constantPool) {
        this.indy = indy;
        this.method = method;
        this.name = name;
        this.type = type;
        this.staticArguments = staticArguments == null ? null : Collections.unmodifiableList(Arrays.asList(staticArguments));
        this.staticArgumentIndices = staticArgumentIndices;
        this.methodHandleIndex = methodHandleIndex;
        this.cpi = cpi;
        this.constantPool = constantPool;
    }

    @Override
    public AbstractEspressoResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public int getMethodHandleIndex() {
        return methodHandleIndex;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isInvokeDynamic() {
        return indy;
    }

    @Override
    public JavaConstant getType() {
        return type;
    }

    @Override
    public List<JavaConstant> getStaticArguments() {
        if (staticArguments == null) {
            throw new IllegalStateException("Static arguments are unavailable for a non-resolving bootstrap method invocation");
        }
        return staticArguments;
    }

    @Override
    public int[] getStaticArgumentIndices() {
        return staticArgumentIndices.clone();
    }

    private void checkInvokeDynamicIndex() {
        // A rewritten operand packs the pool index in the high half and the site index in the low half.
        // A plain pool index has no high half and cannot identify an invokedynamic call site.
        if (cpi == -1 || (cpi >>> 16) == 0) {
            throw new IllegalStateException("No rewritten invokedynamic index is available");
        }
    }

    @Override
    public void resolve() {
        if (isInvokeDynamic()) {
            checkInvokeDynamicIndex();
            constantPool.loadReferencedType(cpi, INVOKEDYNAMIC);
        } else {
            constantPool.lookupConstant(cpi, true);
        }
    }

    @Override
    public JavaConstant lookup() {
        if (isInvokeDynamic()) {
            checkInvokeDynamicIndex();
            return constantPool.lookupAppendix(cpi, INVOKEDYNAMIC);
        } else {
            return (JavaConstant) constantPool.lookupConstant(cpi, false);
        }
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        if (staticArguments != null) {
            for (JavaConstant arg : staticArguments) {
                joiner.add(argumentAsString(arg));
            }
        }
        return "BootstrapMethod[" + (indy ? "indy" : "condy") +
                        ", method:" + (method == null ? null : method.format("%H.%n(%p)")) +
                        ", name: " + name +
                        ", type: " + (type == null ? null : type.toValueString()) +
                        ", method handle index: " + methodHandleIndex +
                        ", static arguments:" + (staticArguments == null ? "unavailable" : joiner) +
                        ", static argument indices:" + Arrays.toString(staticArgumentIndices) + "]";
    }

    private static String argumentAsString(JavaConstant arg) {
        String type = arg.getJavaKind().getJavaName();
        String value = arg.toValueString();
        return type + ":" + value;
    }
}
