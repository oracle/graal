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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

//JaCoCo Exclude

public final class MetaAccessProviderProxy extends CompilationProxyBase implements MetaAccessProvider {
    MetaAccessProviderProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(MetaAccessProvider.class, name, params);
    }

    public static final SymbolicMethod lookupJavaTypeClassMethod = method("lookupJavaType", Class.class);
    public static final InvokableMethod lookupJavaTypeClassInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).lookupJavaType((Class<?>) args[0]);

    @Override
    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        return (ResolvedJavaType) handle(lookupJavaTypeClassMethod, lookupJavaTypeClassInvokable, clazz);
    }

    private static final SymbolicMethod lookupJavaMethodMethod = method("lookupJavaMethod", Executable.class);
    private static final InvokableMethod lookupJavaMethodInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).lookupJavaMethod((Executable) args[0]);

    @Override
    public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        return (ResolvedJavaMethod) handle(lookupJavaMethodMethod, lookupJavaMethodInvokable, reflectionMethod);
    }

    private static final SymbolicMethod lookupJavaFieldMethod = method("lookupJavaField", Field.class);
    private static final InvokableMethod lookupJavaFieldInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).lookupJavaField((Field) args[0]);

    @Override
    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        return (ResolvedJavaField) handle(lookupJavaFieldMethod, lookupJavaFieldInvokable, reflectionField);
    }

    private static final SymbolicMethod lookupJavaTypeConstantMethod = method("lookupJavaType", JavaConstant.class);
    private static final InvokableMethod lookupJavaTypeConstantInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).lookupJavaType((JavaConstant) args[0]);

    @Override
    public ResolvedJavaType lookupJavaType(JavaConstant constant) {
        return (ResolvedJavaType) handle(lookupJavaTypeConstantMethod, lookupJavaTypeConstantInvokable, constant);
    }

    private static final SymbolicMethod getMemorySizeMethod = method("getMemorySize", JavaConstant.class);
    private static final InvokableMethod getMemorySizeInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).getMemorySize((JavaConstant) args[0]);

    @Override
    public long getMemorySize(JavaConstant constant) {
        return (long) handle(getMemorySizeMethod, getMemorySizeInvokable, constant);
    }

    private static final SymbolicMethod parseMethodDescriptorMethod = method("parseMethodDescriptor", String.class);
    private static final InvokableMethod parseMethodDescriptorInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).parseMethodDescriptor((String) args[0]);

    @Override
    public Signature parseMethodDescriptor(String methodDescriptor) {
        return (Signature) handle(parseMethodDescriptorMethod, parseMethodDescriptorInvokable, methodDescriptor);
    }

    public static final SymbolicMethod encodeDeoptActionAndReasonMethod = method("encodeDeoptActionAndReason", DeoptimizationAction.class, DeoptimizationReason.class, int.class);
    private static final InvokableMethod encodeDeoptActionAndReasonInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).encodeDeoptActionAndReason((DeoptimizationAction) args[0],
                    (DeoptimizationReason) args[1], (int) args[2]);

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId) {
        return (JavaConstant) handle(encodeDeoptActionAndReasonMethod, encodeDeoptActionAndReasonInvokable, action, reason, debugId);
    }

    public static final SymbolicMethod encodeSpeculationMethod = method("encodeSpeculation", SpeculationLog.Speculation.class);
    private static final InvokableMethod encodeSpeculationInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).encodeSpeculation((SpeculationLog.Speculation) args[0]);

    @Override
    public JavaConstant encodeSpeculation(SpeculationLog.Speculation speculation) {
        return (JavaConstant) handle(encodeSpeculationMethod, encodeSpeculationInvokable, speculation);
    }

    public static final SymbolicMethod decodeSpeculationMethod = method("decodeSpeculation", JavaConstant.class, SpeculationLog.class);
    private static final InvokableMethod decodeSpeculationInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).decodeSpeculation((JavaConstant) args[0], (SpeculationLog) args[1]);

    @Override
    public SpeculationLog.Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
        return (SpeculationLog.Speculation) handle(decodeSpeculationMethod, decodeSpeculationInvokable, constant, speculationLog);
    }

    public static final SymbolicMethod decodeDeoptReasonMethod = method("decodeDeoptReason", JavaConstant.class);
    private static final InvokableMethod decodeDeoptReasonInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).decodeDeoptReason((JavaConstant) args[0]);

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        return (DeoptimizationReason) handle(decodeDeoptReasonMethod, decodeDeoptReasonInvokable, constant);
    }

    public static final SymbolicMethod decodeDeoptActionMethod = method("decodeDeoptAction", JavaConstant.class);
    private static final InvokableMethod decodeDeoptActionInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).decodeDeoptAction((JavaConstant) args[0]);

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        return (DeoptimizationAction) handle(decodeDeoptActionMethod, decodeDeoptActionInvokable, constant);
    }

    public static final SymbolicMethod decodeDebugIdMethod = method("decodeDebugId", JavaConstant.class);
    private static final InvokableMethod decodeDebugIdInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).decodeDebugId((JavaConstant) args[0]);

    @Override
    public int decodeDebugId(JavaConstant constant) {
        return (int) handle(decodeDebugIdMethod, decodeDebugIdInvokable, constant);
    }

    public static final SymbolicMethod getArrayBaseOffsetMethod = method("getArrayBaseOffset", JavaKind.class);
    private static final InvokableMethod getArrayBaseOffsetInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).getArrayBaseOffset((JavaKind) args[0]);

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        return (int) handle(getArrayBaseOffsetMethod, getArrayBaseOffsetInvokable, elementKind);
    }

    public static final SymbolicMethod getArrayIndexScaleMethod = method("getArrayIndexScale", JavaKind.class);
    private static final InvokableMethod getArrayIndexScaleInvokable = (receiver, args) -> ((MetaAccessProvider) receiver).getArrayIndexScale((JavaKind) args[0]);

    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        return (int) handle(getArrayIndexScaleMethod, getArrayIndexScaleInvokable, elementKind);
    }
}
