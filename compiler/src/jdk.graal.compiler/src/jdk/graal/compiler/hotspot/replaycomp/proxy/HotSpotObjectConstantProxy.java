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

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

//JaCoCo Exclude

public final class HotSpotObjectConstantProxy extends HotSpotConstantProxy implements HotSpotObjectConstant {
    HotSpotObjectConstantProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotObjectConstant.class, name, params);
    }

    public static final SymbolicMethod getTypeMethod = method("getType");
    public static final InvokableMethod getTypeInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).getType();

    @Override
    public HotSpotResolvedObjectType getType() {
        return (HotSpotResolvedObjectType) handle(getTypeMethod, getTypeInvokable);
    }

    private static final SymbolicMethod getIdentityHashCodeMethod = method("getIdentityHashCode");
    private static final InvokableMethod getIdentityHashCodeInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).getIdentityHashCode();

    @Override
    public int getIdentityHashCode() {
        return (int) handle(getIdentityHashCodeMethod, getIdentityHashCodeInvokable);
    }

    private static final SymbolicMethod getCallSiteTargetMethod = method("getCallSiteTarget");
    private static final InvokableMethod getCallSiteTargetInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).getCallSiteTarget();

    @Override
    @SuppressWarnings("unchecked")
    public Assumptions.AssumptionResult<JavaConstant> getCallSiteTarget() {
        return (Assumptions.AssumptionResult<JavaConstant>) handle(getCallSiteTargetMethod, getCallSiteTargetInvokable);
    }

    private static final SymbolicMethod isInternedStringMethod = method("isInternedString");
    private static final InvokableMethod isInternedStringInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).isInternedString();

    @Override
    public boolean isInternedString() {
        return (boolean) handle(isInternedStringMethod, isInternedStringInvokable);
    }

    private static final SymbolicMethod asObjectClassMethod = method("asObject", Class.class);
    private static final InvokableMethod asObjectClassInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).asObject((Class<?>) args[0]);

    @Override
    @SuppressWarnings("unchecked")
    public <T> T asObject(Class<T> type) {
        return (T) handle(asObjectClassMethod, asObjectClassInvokable, type);
    }

    private static final SymbolicMethod asObjectResolvedJavaTypeMethod = method("asObject", ResolvedJavaType.class);
    private static final InvokableMethod asObjectResolvedJavaTypeInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).asObject((ResolvedJavaType) args[0]);

    @Override
    public Object asObject(ResolvedJavaType type) {
        return handle(asObjectResolvedJavaTypeMethod, asObjectResolvedJavaTypeInvokable, type);
    }

    public static final SymbolicMethod getJavaKindMethod = method("getJavaKind");
    private static final InvokableMethod getJavaKindInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).getJavaKind();

    @Override
    public JavaKind getJavaKind() {
        return (JavaKind) handle(getJavaKindMethod, getJavaKindInvokable);
    }

    public static final SymbolicMethod isNullMethod = method("isNull");
    private static final InvokableMethod isNullInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).isNull();

    @Override
    public boolean isNull() {
        return (boolean) handle(isNullMethod, isNullInvokable);
    }

    private static final SymbolicMethod asBoxedPrimitiveMethod = method("asBoxedPrimitive");
    private static final InvokableMethod asBoxedPrimitiveInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).asBoxedPrimitive();

    @Override
    public Object asBoxedPrimitive() {
        return handle(asBoxedPrimitiveMethod, asBoxedPrimitiveInvokable);
    }

    private static final SymbolicMethod asIntMethod = method("asInt");
    private static final InvokableMethod asIntInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).asInt();

    @Override
    public int asInt() {
        return (int) handle(asIntMethod, asIntInvokable);
    }

    private static final SymbolicMethod asBooleanMethod = method("asBoolean");
    private static final InvokableMethod asBooleanInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).asBoolean();

    @Override
    public boolean asBoolean() {
        return (boolean) handle(asBooleanMethod, asBooleanInvokable);
    }

    private static final SymbolicMethod asLongMethod = method("asLong");
    private static final InvokableMethod asLongInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).asLong();

    @Override
    public long asLong() {
        return (long) handle(asLongMethod, asLongInvokable);
    }

    private static final SymbolicMethod asFloatMethod = method("asFloat");
    private static final InvokableMethod asFloatInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).asFloat();

    @Override
    public float asFloat() {
        return (float) handle(asFloatMethod, asFloatInvokable);
    }

    private static final SymbolicMethod asDoubleMethod = method("asDouble");
    private static final InvokableMethod asDoubleInvokable = (receiver, args) -> ((HotSpotObjectConstant) receiver).asDouble();

    @Override
    public double asDouble() {
        return (double) handle(asDoubleMethod, asDoubleInvokable);
    }

    @Override
    public JavaConstant compress() {
        return (JavaConstant) super.compress();
    }

    @Override
    public JavaConstant uncompress() {
        return (JavaConstant) super.uncompress();
    }
}
