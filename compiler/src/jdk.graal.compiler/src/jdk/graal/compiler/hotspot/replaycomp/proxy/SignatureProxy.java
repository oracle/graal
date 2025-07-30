/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

//JaCoCo Exclude

public final class SignatureProxy extends CompilationProxyBase implements Signature {
    SignatureProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(Signature.class, name, params);
    }

    public static final SymbolicMethod getParameterCountMethod = method("getParameterCount", boolean.class);
    public static final InvokableMethod getParameterCountInvokable = (receiver, args) -> ((Signature) receiver).getParameterCount((boolean) args[0]);

    @Override
    public int getParameterCount(boolean rec) {
        return (int) handle(getParameterCountMethod, getParameterCountInvokable, rec);
    }

    private static final SymbolicMethod getParameterTypeMethod = method("getParameterType", int.class, ResolvedJavaType.class);
    private static final InvokableMethod getParameterTypeInvokable = (receiver, args) -> ((Signature) receiver).getParameterType((int) args[0], (ResolvedJavaType) args[1]);

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        return (JavaType) handle(getParameterTypeMethod, getParameterTypeInvokable, index, accessingClass);
    }

    public static final SymbolicMethod getReturnTypeMethod = method("getReturnType", ResolvedJavaType.class);
    public static final InvokableMethod getReturnTypeInvokable = (receiver, args) -> ((Signature) receiver).getReturnType((ResolvedJavaType) args[0]);

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        return (JavaType) handle(getReturnTypeMethod, getReturnTypeInvokable, accessingClass);
    }
}
