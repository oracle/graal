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

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

//JaCoCo Exclude

public final class HotSpotResolvedJavaFieldProxy extends CompilationProxyBase.CompilationProxyAnnotatedBase implements HotSpotResolvedJavaField {
    HotSpotResolvedJavaFieldProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotResolvedJavaField.class, name, params);
    }

    private static final SymbolicMethod isInObjectMethod = method("isInObject", JavaConstant.class);
    private static final InvokableMethod isInObjectInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).isInObject((JavaConstant) args[0]);

    @Override
    public boolean isInObject(JavaConstant object) {
        return (boolean) handle(isInObjectMethod, isInObjectInvokable, object);
    }

    public static final SymbolicMethod isStableMethod = method("isStable");
    public static final InvokableMethod isStableInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).isStable();

    @Override
    public boolean isStable() {
        return (boolean) handle(isStableMethod, isStableInvokable);
    }

    public static final SymbolicMethod getOffsetMethod = method("getOffset");
    public static final InvokableMethod getOffsetInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).getOffset();

    @Override
    public int getOffset() {
        return (int) handle(getOffsetMethod, getOffsetInvokable);
    }

    private static final SymbolicMethod isInternalMethod = method("isInternal");
    private static final InvokableMethod isInternalInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).isInternal();

    @Override
    public boolean isInternal() {
        return (boolean) handle(isInternalMethod, isInternalInvokable);
    }

    public static final SymbolicMethod isSyntheticMethod = method("isSynthetic");
    public static final InvokableMethod isSyntheticInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).isSynthetic();

    @Override
    public boolean isSynthetic() {
        return (boolean) handle(isSyntheticMethod, isSyntheticInvokable);
    }

    public static final SymbolicMethod getNameMethod = method("getName");
    public static final InvokableMethod getNameInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).getName();

    @Override
    public String getName() {
        return (String) handle(getNameMethod, getNameInvokable);
    }

    public static final SymbolicMethod getTypeMethod = method("getType");
    public static final InvokableMethod getTypeInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).getType();

    @Override
    public JavaType getType() {
        return (JavaType) handle(getTypeMethod, getTypeInvokable);
    }

    public static final SymbolicMethod getDeclaringClassMethod = method("getDeclaringClass");
    public static final InvokableMethod getDeclaringClassInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).getDeclaringClass();

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return (ResolvedJavaType) handle(getDeclaringClassMethod, getDeclaringClassInvokable);
    }

    private static final SymbolicMethod getConstantValueMethod = method("getConstantValue");
    private static final InvokableMethod getConstantValueInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).getConstantValue();

    @Override
    public JavaConstant getConstantValue() {
        return (JavaConstant) handle(getConstantValueMethod, getConstantValueInvokable);
    }

    public static final SymbolicMethod getModifiersMethod = method("getModifiers");
    public static final InvokableMethod getModifiersInvokable = (receiver, args) -> ((HotSpotResolvedJavaField) receiver).getModifiers();

    @Override
    public int getModifiers() {
        return (int) handle(getModifiersMethod, getModifiersInvokable);
    }
}
