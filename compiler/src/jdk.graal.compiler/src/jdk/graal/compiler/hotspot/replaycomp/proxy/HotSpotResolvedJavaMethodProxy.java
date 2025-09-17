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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.BitSet;
import java.util.Formattable;
import java.util.Formatter;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

//JaCoCo Exclude

public final class HotSpotResolvedJavaMethodProxy extends CompilationProxyBase.CompilationProxyAnnotatedBase implements HotSpotResolvedJavaMethod, Formattable {
    HotSpotResolvedJavaMethodProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotResolvedJavaMethod.class, name, params);
    }

    private static final SymbolicMethod isCallerSensitiveMethod = method("isCallerSensitive");
    private static final InvokableMethod isCallerSensitiveInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isCallerSensitive();

    @Override
    public boolean isCallerSensitive() {
        return (boolean) handle(isCallerSensitiveMethod, isCallerSensitiveInvokable);
    }

    public static final SymbolicMethod getCodeMethod = method("getCode");
    public static final InvokableMethod getCodeInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getCode();

    @Override
    public byte[] getCode() {
        return (byte[]) handle(getCodeMethod, getCodeInvokable);
    }

    public static final SymbolicMethod getCodeSizeMethod = method("getCodeSize");
    private static final InvokableMethod getCodeSizeInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getCodeSize();

    @Override
    public int getCodeSize() {
        return (int) handle(getCodeSizeMethod, getCodeSizeInvokable);
    }

    public static final SymbolicMethod getNameMethod = method("getName");
    public static final InvokableMethod getNameInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getName();

    @Override
    public String getName() {
        return (String) handle(getNameMethod, getNameInvokable);
    }

    public static final SymbolicMethod getDeclaringClassMethod = method("getDeclaringClass");
    public static final InvokableMethod getDeclaringClassInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getDeclaringClass();

    @Override
    public HotSpotResolvedObjectType getDeclaringClass() {
        return (HotSpotResolvedObjectType) handle(getDeclaringClassMethod, getDeclaringClassInvokable);
    }

    public static final SymbolicMethod getSignatureMethod = method("getSignature");
    public static final InvokableMethod getSignatureInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getSignature();

    @Override
    public Signature getSignature() {
        return (Signature) handle(getSignatureMethod, getSignatureInvokable);
    }

    private static final SymbolicMethod getMaxLocalsMethod = method("getMaxLocals");
    private static final InvokableMethod getMaxLocalsInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getMaxLocals();

    @Override
    public int getMaxLocals() {
        return (int) handle(getMaxLocalsMethod, getMaxLocalsInvokable);
    }

    private static final SymbolicMethod getMaxStackSizeMethod = method("getMaxStackSize");
    private static final InvokableMethod getMaxStackSizeInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getMaxStackSize();

    @Override
    public int getMaxStackSize() {
        return (int) handle(getMaxStackSizeMethod, getMaxStackSizeInvokable);
    }

    private static final SymbolicMethod isSyntheticMethod = method("isSynthetic");
    private static final InvokableMethod isSyntheticInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isSynthetic();

    @Override
    public boolean isSynthetic() {
        return (boolean) handle(isSyntheticMethod, isSyntheticInvokable);
    }

    private static final SymbolicMethod isVarArgsMethod = method("isVarArgs");
    private static final InvokableMethod isVarArgsInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isVarArgs();

    @Override
    public boolean isVarArgs() {
        return (boolean) handle(isVarArgsMethod, isVarArgsInvokable);
    }

    private static final SymbolicMethod isBridgeMethod = method("isBridge");
    private static final InvokableMethod isBridgeInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isBridge();

    @Override
    public boolean isBridge() {
        return (boolean) handle(isBridgeMethod, isBridgeInvokable);
    }

    private static final SymbolicMethod isDeclaredMethod = method("isDeclared");
    private static final InvokableMethod isDeclaredInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isDeclared();

    @Override
    public boolean isDeclared() {
        return (boolean) handle(isDeclaredMethod, isDeclaredInvokable);
    }

    private static final SymbolicMethod isClassInitializerMethod = method("isClassInitializer");
    private static final InvokableMethod isClassInitializerInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isClassInitializer();

    @Override
    public boolean isClassInitializer() {
        return (boolean) handle(isClassInitializerMethod, isClassInitializerInvokable);
    }

    public static final SymbolicMethod isConstructorMethod = method("isConstructor");
    public static final InvokableMethod isConstructorInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isConstructor();

    @Override
    public boolean isConstructor() {
        return (boolean) handle(isConstructorMethod, isConstructorInvokable);
    }

    public static final SymbolicMethod canBeStaticallyBoundMethod = method("canBeStaticallyBound");
    public static final InvokableMethod canBeStaticallyBoundInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).canBeStaticallyBound();

    @Override
    public boolean canBeStaticallyBound() {
        return (boolean) handle(canBeStaticallyBoundMethod, canBeStaticallyBoundInvokable);
    }

    private static final SymbolicMethod getExceptionHandlersMethod = method("getExceptionHandlers");
    private static final InvokableMethod getExceptionHandlersInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getExceptionHandlers();

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return (ExceptionHandler[]) handle(getExceptionHandlersMethod, getExceptionHandlersInvokable);
    }

    public static final SymbolicMethod asStackTraceElementMethod = method("asStackTraceElement", int.class);
    public static final InvokableMethod asStackTraceElementInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).asStackTraceElement((int) args[0]);

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return (StackTraceElement) handle(asStackTraceElementMethod, asStackTraceElementInvokable, bci);
    }

    private static final SymbolicMethod getProfilingInfoMethod = method("getProfilingInfo", boolean.class, boolean.class);
    private static final InvokableMethod getProfilingInfoInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getProfilingInfo((boolean) args[0], (boolean) args[1]);

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return (ProfilingInfo) handle(getProfilingInfoMethod, getProfilingInfoInvokable, includeNormal, includeOSR);
    }

    private static final SymbolicMethod reprofileMethod = method("reprofile");
    private static final InvokableMethod reprofileInvokable = (receiver, args) -> {
        ((HotSpotResolvedJavaMethod) receiver).reprofile();
        return null;
    };

    @Override
    public void reprofile() {
        handle(reprofileMethod, reprofileInvokable);
    }

    public static final SymbolicMethod getConstantPoolMethod = method("getConstantPool");
    public static final InvokableMethod getConstantPoolInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getConstantPool();

    @Override
    public ConstantPool getConstantPool() {
        return (ConstantPool) handle(getConstantPoolMethod, getConstantPoolInvokable);
    }

    public static final SymbolicMethod getParametersMethod = method("getParameters");
    private static final InvokableMethod getParametersInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getParameters();

    @Override
    public Parameter[] getParameters() {
        return (Parameter[]) handle(getParametersMethod, getParametersInvokable);
    }

    public static final SymbolicMethod getParameterAnnotationsMethod = method("getParameterAnnotations");
    private static final InvokableMethod getParameterAnnotationsInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getParameterAnnotations();

    @Override
    public Annotation[][] getParameterAnnotations() {
        return (Annotation[][]) handle(getParameterAnnotationsMethod, getParameterAnnotationsInvokable);
    }

    public static final SymbolicMethod getGenericParameterTypesMethod = method("getGenericParameterTypes");
    private static final InvokableMethod getGenericParameterTypesInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getGenericParameterTypes();

    @Override
    public Type[] getGenericParameterTypes() {
        return (Type[]) handle(getGenericParameterTypesMethod, getGenericParameterTypesInvokable);
    }

    public static final SymbolicMethod canBeInlinedMethod = method("canBeInlined");
    private static final InvokableMethod canBeInlinedInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).canBeInlined();

    @Override
    public boolean canBeInlined() {
        return (boolean) handle(canBeInlinedMethod, canBeInlinedInvokable);
    }

    private static final SymbolicMethod hasNeverInlineDirectiveMethod = method("hasNeverInlineDirective");
    private static final InvokableMethod hasNeverInlineDirectiveInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).hasNeverInlineDirective();

    @Override
    public boolean hasNeverInlineDirective() {
        return (boolean) handle(hasNeverInlineDirectiveMethod, hasNeverInlineDirectiveInvokable);
    }

    private static final SymbolicMethod shouldBeInlinedMethod = method("shouldBeInlined");
    private static final InvokableMethod shouldBeInlinedInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).shouldBeInlined();

    @Override
    public boolean shouldBeInlined() {
        return (boolean) handle(shouldBeInlinedMethod, shouldBeInlinedInvokable);
    }

    private static final SymbolicMethod getLineNumberTableMethod = method("getLineNumberTable");
    private static final InvokableMethod getLineNumberTableInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getLineNumberTable();

    @Override
    public LineNumberTable getLineNumberTable() {
        return (LineNumberTable) handle(getLineNumberTableMethod, getLineNumberTableInvokable);
    }

    private static final SymbolicMethod getLocalVariableTableMethod = method("getLocalVariableTable");
    private static final InvokableMethod getLocalVariableTableInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getLocalVariableTable();

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return (LocalVariableTable) handle(getLocalVariableTableMethod, getLocalVariableTableInvokable);
    }

    private static final SymbolicMethod getEncodingMethod = method("getEncoding");
    private static final InvokableMethod getEncodingInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getEncoding();

    @Override
    public Constant getEncoding() {
        return (Constant) handle(getEncodingMethod, getEncodingInvokable);
    }

    public static final SymbolicMethod isInVirtualMethodTableMethod = method("isInVirtualMethodTable", ResolvedJavaType.class);
    private static final InvokableMethod isInVirtualMethodTableInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isInVirtualMethodTable((ResolvedJavaType) args[0]);

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        return (boolean) handle(isInVirtualMethodTableMethod, isInVirtualMethodTableInvokable, resolved);
    }

    private static final SymbolicMethod isScopedMethod = method("isScoped");
    private static final InvokableMethod isScopedInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isScoped();

    @Override
    public boolean isScoped() {
        return (boolean) handle(isScopedMethod, isScopedInvokable);
    }

    private static final SymbolicMethod getSpeculationLogMethod = method("getSpeculationLog");
    private static final InvokableMethod getSpeculationLogInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getSpeculationLog();

    @Override
    public SpeculationLog getSpeculationLog() {
        return (SpeculationLog) handle(getSpeculationLogMethod, getSpeculationLogInvokable);
    }

    private static final SymbolicMethod isForceInlineMethod = method("isForceInline");
    private static final InvokableMethod isForceInlineInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isForceInline();

    @Override
    public boolean isForceInline() {
        return (boolean) handle(isForceInlineMethod, isForceInlineInvokable);
    }

    private static final SymbolicMethod hasReservedStackAccessMethod = method("hasReservedStackAccess");
    private static final InvokableMethod hasReservedStackAccessInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).hasReservedStackAccess();

    @Override
    public boolean hasReservedStackAccess() {
        return (boolean) handle(hasReservedStackAccessMethod, hasReservedStackAccessInvokable);
    }

    private static final SymbolicMethod setNotInlinableOrCompilableMethod = method("setNotInlinableOrCompilable");
    private static final InvokableMethod setNotInlinableOrCompilableInvokable = (receiver, args) -> {
        ((HotSpotResolvedJavaMethod) receiver).setNotInlinableOrCompilable();
        return null;
    };

    @Override
    public void setNotInlinableOrCompilable() {
        handle(setNotInlinableOrCompilableMethod, setNotInlinableOrCompilableInvokable);
    }

    private static final SymbolicMethod ignoredBySecurityStackWalkMethod = method("ignoredBySecurityStackWalk");
    private static final InvokableMethod ignoredBySecurityStackWalkInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).ignoredBySecurityStackWalk();

    @Override
    public boolean ignoredBySecurityStackWalk() {
        return (boolean) handle(ignoredBySecurityStackWalkMethod, ignoredBySecurityStackWalkInvokable);
    }

    private static final SymbolicMethod uniqueConcreteMethodMethod = method("uniqueConcreteMethod", HotSpotResolvedObjectType.class);
    private static final InvokableMethod uniqueConcreteMethodInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).uniqueConcreteMethod((HotSpotResolvedObjectType) args[0]);

    @Override
    public ResolvedJavaMethod uniqueConcreteMethod(HotSpotResolvedObjectType rec) {
        return (ResolvedJavaMethod) handle(uniqueConcreteMethodMethod, uniqueConcreteMethodInvokable, rec);
    }

    private static final SymbolicMethod hasCompiledCodeMethod = method("hasCompiledCode");
    private static final InvokableMethod hasCompiledCodeInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).hasCompiledCode();

    @Override
    public boolean hasCompiledCode() {
        return (boolean) handle(hasCompiledCodeMethod, hasCompiledCodeInvokable);
    }

    private static final SymbolicMethod hasCompiledCodeAtLevelMethod = method("hasCompiledCodeAtLevel", int.class);
    private static final InvokableMethod hasCompiledCodeAtLevelInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).hasCompiledCodeAtLevel((int) args[0]);

    @Override
    public boolean hasCompiledCodeAtLevel(int level) {
        return (boolean) handle(hasCompiledCodeAtLevelMethod, hasCompiledCodeAtLevelInvokable, level);
    }

    public static final SymbolicMethod vtableEntryOffsetMethod = method("vtableEntryOffset", ResolvedJavaType.class);
    private static final InvokableMethod vtableEntryOffsetInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).vtableEntryOffset((ResolvedJavaType) args[0]);

    @Override
    public int vtableEntryOffset(ResolvedJavaType resolved) {
        return (int) handle(vtableEntryOffsetMethod, vtableEntryOffsetInvokable, resolved);
    }

    public static final SymbolicMethod intrinsicIdMethod = method("intrinsicId");
    private static final InvokableMethod intrinsicIdInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).intrinsicId();

    @Override
    public int intrinsicId() {
        return (int) handle(intrinsicIdMethod, intrinsicIdInvokable);
    }

    private static final SymbolicMethod isIntrinsicCandidateMethod = method("isIntrinsicCandidate");
    private static final InvokableMethod isIntrinsicCandidateInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).isIntrinsicCandidate();

    @Override
    public boolean isIntrinsicCandidate() {
        return (boolean) handle(isIntrinsicCandidateMethod, isIntrinsicCandidateInvokable);
    }

    private static final SymbolicMethod allocateCompileIdMethod = method("allocateCompileId", int.class);
    private static final InvokableMethod allocateCompileIdInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).allocateCompileId((int) args[0]);

    @Override
    public int allocateCompileId(int entryBCI) {
        return (int) handle(allocateCompileIdMethod, allocateCompileIdInvokable, entryBCI);
    }

    public static final SymbolicMethod hasCodeAtLevelMethod = method("hasCodeAtLevel", int.class, int.class);
    private static final InvokableMethod hasCodeAtLevelInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).hasCodeAtLevel((int) args[0], (int) args[1]);

    @Override
    public boolean hasCodeAtLevel(int entryBCI, int level) {
        return (boolean) handle(hasCodeAtLevelMethod, hasCodeAtLevelInvokable, entryBCI, level);
    }

    private static final SymbolicMethod methodIdnumMethod = method("methodIdnum");
    private static final InvokableMethod methodIdnumInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).methodIdnum();

    @Override
    public int methodIdnum() {
        return (int) handle(methodIdnumMethod, methodIdnumInvokable);
    }

    private static final SymbolicMethod getOopMapAtMethod = method("getOopMapAt", int.class);
    private static final InvokableMethod getOopMapAtInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getOopMapAt((int) args[0]);

    @Override
    public BitSet getOopMapAt(int bci) {
        return (BitSet) handle(getOopMapAtMethod, getOopMapAtInvokable, bci);
    }

    public static final SymbolicMethod getModifiersMethod = method("getModifiers");
    public static final InvokableMethod getModifiersInvokable = (receiver, args) -> ((HotSpotResolvedJavaMethod) receiver).getModifiers();

    @Override
    public int getModifiers() {
        return (int) handle(getModifiersMethod, getModifiersInvokable);
    }

    public static final SymbolicMethod formatToMethod = new SymbolicMethod(Formattable.class, "formatTo", Formatter.class, int.class, int.class, int.class);
    private static final InvokableMethod formatToInvokable = (receiver, args) -> {
        ((Formattable) receiver).formatTo((Formatter) args[0], (int) args[1], (int) args[2], (int) args[3]);
        return null;
    };

    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        handle(formatToMethod, formatToInvokable, formatter, flags, width, precision);
    }
}
