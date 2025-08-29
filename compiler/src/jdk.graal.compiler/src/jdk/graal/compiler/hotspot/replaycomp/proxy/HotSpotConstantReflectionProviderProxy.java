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

import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.equalsInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.equalsMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.hashCodeInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.hashCodeMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.toStringInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.toStringMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.unproxifyInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.unproxifyMethod;

import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

//JaCoCo Exclude

public final class HotSpotConstantReflectionProviderProxy extends HotSpotConstantReflectionProvider implements CompilationProxy {
    private final InvocationHandler handler;

    HotSpotConstantReflectionProviderProxy(InvocationHandler handler) {
        super(null);
        this.handler = handler;
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotConstantReflectionProvider.class, name, params);
    }

    private Object handle(SymbolicMethod method, InvokableMethod invokable, Object... args) {
        return CompilationProxy.handle(handler, this, method, invokable, args);
    }

    private static final SymbolicMethod constantEqualsMethod = method("constantEquals", Constant.class, Constant.class);
    private static final InvokableMethod constantEqualsInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).constantEquals((Constant) args[0], (Constant) args[1]);

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return (Boolean) handle(constantEqualsMethod, constantEqualsInvokable, x, y);
    }

    private static final SymbolicMethod readArrayLengthMethod = method("readArrayLength", JavaConstant.class);
    private static final InvokableMethod readArrayLengthInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).readArrayLength((JavaConstant) args[0]);

    @Override
    public Integer readArrayLength(JavaConstant array) {
        return (Integer) handle(readArrayLengthMethod, readArrayLengthInvokable, array);
    }

    private static final SymbolicMethod readArrayElementMethod = method("readArrayElement", JavaConstant.class, int.class);
    private static final InvokableMethod readArrayElementInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).readArrayElement((JavaConstant) args[0], (int) args[1]);

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        return (JavaConstant) handle(readArrayElementMethod, readArrayElementInvokable, array, index);
    }

    private static final SymbolicMethod readFieldValueMethod = method("readFieldValue", ResolvedJavaField.class, JavaConstant.class);
    private static final InvokableMethod readFieldValueInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).readFieldValue((ResolvedJavaField) args[0],
                    (JavaConstant) args[1]);

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant rec) {
        return (JavaConstant) handle(readFieldValueMethod, readFieldValueInvokable, field, rec);
    }

    private static final SymbolicMethod boxPrimitiveMethod = method("boxPrimitive", JavaConstant.class);
    private static final InvokableMethod boxPrimitiveInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).boxPrimitive((JavaConstant) args[0]);

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        return (JavaConstant) handle(boxPrimitiveMethod, boxPrimitiveInvokable, source);
    }

    private static final SymbolicMethod unboxPrimitiveMethod = method("unboxPrimitive", JavaConstant.class);
    private static final InvokableMethod unboxPrimitiveInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).unboxPrimitive((JavaConstant) args[0]);

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        return (JavaConstant) handle(unboxPrimitiveMethod, unboxPrimitiveInvokable, source);
    }

    private static final SymbolicMethod forStringMethod = method("forString", String.class);
    private static final InvokableMethod forStringInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).forString((String) args[0]);

    @Override
    public JavaConstant forString(String value) {
        return (JavaConstant) handle(forStringMethod, forStringInvokable, value);
    }

    public static final SymbolicMethod forObjectMethod = method("forObject", Object.class);
    public static final InvokableMethod forObjectInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).forObject(args[0]);

    @Override
    public JavaConstant forObject(Object value) {
        return (JavaConstant) handle(forObjectMethod, forObjectInvokable, value);
    }

    public static final SymbolicMethod asJavaTypeMethod = method("asJavaType", Constant.class);
    private static final InvokableMethod asJavaTypeInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).asJavaType((Constant) args[0]);

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        return (ResolvedJavaType) handle(asJavaTypeMethod, asJavaTypeInvokable, constant);
    }

    private static final SymbolicMethod getMethodHandleAccessMethod = method("getMethodHandleAccess");
    private static final InvokableMethod getMethodHandleAccessInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).getMethodHandleAccess();

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return (MethodHandleAccessProvider) handle(getMethodHandleAccessMethod, getMethodHandleAccessInvokable);
    }

    private static final SymbolicMethod getMemoryAccessProviderMethod = method("getMemoryAccessProvider");
    private static final InvokableMethod getMemoryAccessProviderInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).getMemoryAccessProvider();

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return (MemoryAccessProvider) handle(getMemoryAccessProviderMethod, getMemoryAccessProviderInvokable);
    }

    public static final SymbolicMethod asJavaClassMethod = method("asJavaClass", ResolvedJavaType.class);
    private static final InvokableMethod asJavaClassInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).asJavaClass((ResolvedJavaType) args[0]);

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return (JavaConstant) handle(asJavaClassMethod, asJavaClassInvokable, type);
    }

    public static final SymbolicMethod asObjectHubMethod = method("asObjectHub", ResolvedJavaType.class);
    private static final InvokableMethod asObjectHubInvokable = (receiver, args) -> ((HotSpotConstantReflectionProvider) receiver).asObjectHub((ResolvedJavaType) args[0]);

    @Override
    public Constant asObjectHub(ResolvedJavaType type) {
        return (Constant) handle(asObjectHubMethod, asObjectHubInvokable, type);
    }

    @Override
    public Object unproxify() {
        return handle(unproxifyMethod, unproxifyInvokable);
    }

    @Override
    public int hashCode() {
        return (int) handle(hashCodeMethod, hashCodeInvokable);
    }

    @Override
    public boolean equals(Object obj) {
        return (boolean) handle(equalsMethod, equalsInvokable, obj);
    }

    @Override
    public String toString() {
        return (String) handle(toStringMethod, toStringInvokable);
    }
}
