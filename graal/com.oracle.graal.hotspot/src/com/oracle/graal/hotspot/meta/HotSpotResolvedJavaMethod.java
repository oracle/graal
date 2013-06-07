/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.graph.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.debug.*;

/**
 * Implementation of {@link JavaMethod} for resolved HotSpot methods.
 */
public final class HotSpotResolvedJavaMethod extends HotSpotMethod implements ResolvedJavaMethod {

    private static final long serialVersionUID = -5486975070147586588L;

    /**
     * Reference to metaspace Method object.
     */
    final long metaspaceMethod;

    private final HotSpotResolvedObjectType holder;
    private/* final */int codeSize;
    private/* final */int exceptionHandlerCount;
    private boolean callerSensitive;
    private boolean forceInline;
    private boolean dontInline;
    private boolean ignoredBySecurityStackWalk;
    private HotSpotSignature signature;
    private Boolean hasBalancedMonitors;
    private Map<Object, Object> compilerStorage;
    private HotSpotMethodData methodData;
    private byte[] code;
    private CompilationTask currentTask;
    private SpeculationLog speculationLog;

    /**
     * Gets the holder of a HotSpot metaspace method native object.
     * 
     * @param metaspaceMethod a metaspace Method object
     * @return the {@link ResolvedJavaType} corresponding to the holder of the
     *         {@code metaspaceMethod}
     */
    public static HotSpotResolvedObjectType getHolder(long metaspaceMethod) {
        HotSpotVMConfig config = graalRuntime().getConfig();
        long constMethod = unsafe.getLong(metaspaceMethod + config.methodConstMethodOffset);
        assert constMethod != 0;
        long constantPool = unsafe.getLong(constMethod + config.constMethodConstantsOffset);
        assert constantPool != 0;
        long holder = unsafe.getLong(constantPool + config.constantPoolHolderOffset);
        assert holder != 0;
        return (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromMetaspaceKlass(holder);
    }

    /**
     * Gets the {@link ResolvedJavaMethod} for a HotSpot metaspace method native object.
     * 
     * @param metaspaceMethod a metaspace Method object
     * @return the {@link ResolvedJavaMethod} corresponding to {@code metaspaceMethod}
     */
    public static HotSpotResolvedJavaMethod fromMetaspace(long metaspaceMethod) {
        HotSpotResolvedObjectType holder = getHolder(metaspaceMethod);
        return holder.createMethod(metaspaceMethod);
    }

    HotSpotResolvedJavaMethod(HotSpotResolvedObjectType holder, long metaspaceMethod) {
        this.metaspaceMethod = metaspaceMethod;
        this.holder = holder;
        graalRuntime().getCompilerToVM().initializeMethod(metaspaceMethod, this);
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return holder;
    }

    public long getMetaspaceMethod() {
        return metaspaceMethod;
    }

    /**
     * Gets the address of the C++ Method object for this method.
     */
    public Constant getMetaspaceMethodConstant() {
        return Constant.forIntegerKind(graalRuntime().getTarget().wordKind, metaspaceMethod, this);
    }

    @Override
    public Constant getEncoding() {
        return getMetaspaceMethodConstant();
    }

    @Override
    public int getModifiers() {
        HotSpotVMConfig config = graalRuntime().getConfig();
        return unsafe.getInt(metaspaceMethod + config.methodAccessFlagsOffset) & Modifier.methodModifiers();
    }

    @Override
    public boolean canBeStaticallyBound() {
        int modifiers = getModifiers();
        return (Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)) && !Modifier.isAbstract(modifiers);
    }

    @Override
    public byte[] getCode() {
        if (codeSize == 0) {
            return null;
        }
        if (code == null) {
            code = graalRuntime().getCompilerToVM().initializeBytecode(metaspaceMethod, new byte[codeSize]);
            assert code.length == codeSize : "expected: " + codeSize + ", actual: " + code.length;
        }
        return code;
    }

    @Override
    public int getCodeSize() {
        return codeSize;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        if (exceptionHandlerCount == 0) {
            return new ExceptionHandler[0];
        }
        ExceptionHandler[] handlers = new ExceptionHandler[exceptionHandlerCount];
        for (int i = 0; i < exceptionHandlerCount; i++) {
            handlers[i] = new ExceptionHandler(-1, -1, -1, -1, null);
        }
        return graalRuntime().getCompilerToVM().initializeExceptionHandlers(metaspaceMethod, handlers);
    }

    /**
     * Returns true if this method has a CallerSensitive annotation.
     * 
     * @return true if CallerSensitive annotation present, false otherwise
     */
    public boolean isCallerSensitive() {
        return callerSensitive;
    }

    /**
     * Returns true if this method has a ForceInline annotation.
     * 
     * @return true if ForceInline annotation present, false otherwise
     */
    public boolean isForceInline() {
        return forceInline;
    }

    /**
     * Returns true if this method has a DontInline annotation.
     * 
     * @return true if DontInline annotation present, false otherwise
     */
    public boolean isDontInline() {
        return dontInline;
    }

    /**
     * Returns true if this method is one of the special methods that is ignored by security stack
     * walks.
     * 
     * @return true if special method ignored by security stack walks, false otherwise
     */
    public boolean ignoredBySecurityStackWalk() {
        return ignoredBySecurityStackWalk;
    }

    public boolean hasBalancedMonitors() {
        if (hasBalancedMonitors == null) {
            hasBalancedMonitors = graalRuntime().getCompilerToVM().hasBalancedMonitors(metaspaceMethod);
        }
        return hasBalancedMonitors;
    }

    @Override
    public boolean isClassInitializer() {
        return "<clinit>".equals(name) && Modifier.isStatic(getModifiers());
    }

    @Override
    public boolean isConstructor() {
        return "<init>".equals(name) && !Modifier.isStatic(getModifiers());
    }

    @Override
    public int getMaxLocals() {
        HotSpotVMConfig config = graalRuntime().getConfig();
        long metaspaceConstMethod = unsafe.getLong(metaspaceMethod + config.methodConstMethodOffset);
        return unsafe.getShort(metaspaceConstMethod + config.methodMaxLocalsOffset) & 0xFFFF;
    }

    @Override
    public int getMaxStackSize() {
        HotSpotVMConfig config = graalRuntime().getConfig();
        long metaspaceConstMethod = unsafe.getLong(metaspaceMethod + config.methodConstMethodOffset);
        return config.extraStackEntries + (unsafe.getShort(metaspaceConstMethod + config.constMethodMaxStackOffset) & 0xFFFF);
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        if (bci < 0 || bci >= codeSize) {
            // HotSpot code can only construct stack trace elements for valid bcis
            StackTraceElement ste = graalRuntime().getCompilerToVM().getStackTraceElement(metaspaceMethod, 0);
            return new StackTraceElement(ste.getClassName(), ste.getMethodName(), ste.getFileName(), -1);
        }
        return graalRuntime().getCompilerToVM().getStackTraceElement(metaspaceMethod, bci);
    }

    public ResolvedJavaMethod uniqueConcreteMethod() {
        HotSpotResolvedObjectType[] resultHolder = {null};
        long ucm = graalRuntime().getCompilerToVM().getUniqueConcreteMethod(metaspaceMethod, resultHolder);
        if (ucm != 0L) {
            assert resultHolder[0] != null;
            return resultHolder[0].createMethod(ucm);
        }
        return null;
    }

    @Override
    public HotSpotSignature getSignature() {
        if (signature == null) {
            signature = new HotSpotSignature(graalRuntime().getCompilerToVM().getSignature(metaspaceMethod));
        }
        return signature;
    }

    @Override
    public String toString() {
        return format("HotSpotMethod<%H.%n(%p)>", this);
    }

    public int getCompiledCodeSize() {
        return graalRuntime().getCompilerToVM().getCompiledCodeSize(metaspaceMethod);
    }

    public int invocationCount() {
        return graalRuntime().getCompilerToVM().getInvocationCount(metaspaceMethod);
    }

    @Override
    public ProfilingInfo getProfilingInfo() {
        ProfilingInfo info;

        if (UseProfilingInformation.getValue() && methodData == null) {
            long metaspaceMethodData = unsafeReadWord(metaspaceMethod + graalRuntime().getConfig().methodDataOffset);
            if (metaspaceMethodData != 0) {
                methodData = new HotSpotMethodData(metaspaceMethodData);
            }
        }

        if (methodData == null || (!methodData.hasNormalData() && !methodData.hasExtraData())) {
            // Be optimistic and return false for exceptionSeen. A methodDataOop is allocated in
            // case of a deoptimization.
            info = DefaultProfilingInfo.get(TriState.FALSE);
        } else {
            info = new HotSpotProfilingInfo(methodData, this);
        }
        return info;
    }

    @Override
    public void reprofile() {
        graalRuntime().getCompilerToVM().reprofile(metaspaceMethod);
    }

    @Override
    public Map<Object, Object> getCompilerStorage() {
        if (compilerStorage == null) {
            compilerStorage = new ConcurrentHashMap<>();
        }
        return compilerStorage;
    }

    @Override
    public ConstantPool getConstantPool() {
        return ((HotSpotResolvedObjectType) getDeclaringClass()).constantPool();
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        if (isConstructor()) {
            Constructor javaConstructor = toJavaConstructor();
            return javaConstructor == null ? null : javaConstructor.getParameterAnnotations();
        }
        Method javaMethod = toJava();
        return javaMethod == null ? null : javaMethod.getParameterAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (isConstructor()) {
            Constructor<?> javaConstructor = toJavaConstructor();
            return javaConstructor == null ? null : javaConstructor.getAnnotation(annotationClass);
        }
        Method javaMethod = toJava();
        return javaMethod == null ? null : javaMethod.getAnnotation(annotationClass);
    }

    @Override
    public Type[] getGenericParameterTypes() {
        if (isConstructor()) {
            Constructor javaConstructor = toJavaConstructor();
            return javaConstructor == null ? null : javaConstructor.getGenericParameterTypes();
        }
        Method javaMethod = toJava();
        return javaMethod == null ? null : javaMethod.getGenericParameterTypes();
    }

    public Class<?>[] signatureToTypes() {
        Signature sig = getSignature();
        int count = sig.getParameterCount(false);
        Class<?>[] result = new Class<?>[count];
        for (int i = 0; i < result.length; ++i) {
            result[i] = ((HotSpotResolvedJavaType) sig.getParameterType(i, holder).resolve(holder)).mirror();
        }
        return result;
    }

    private Method toJava() {
        try {
            return holder.mirror().getDeclaredMethod(name, signatureToTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Constructor toJavaConstructor() {
        try {
            return holder.mirror().getDeclaredConstructor(signatureToTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public boolean canBeInlined() {
        if (dontInline) {
            return false;
        }
        return graalRuntime().getCompilerToVM().isMethodCompilable(metaspaceMethod);
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        long[] values = graalRuntime().getCompilerToVM().getLineNumberTable(this);
        assert values.length % 2 == 0;
        int[] bci = new int[values.length / 2];
        int[] line = new int[values.length / 2];

        for (int i = 0; i < values.length / 2; i++) {
            bci[i] = (int) values[i * 2];
            line[i] = (int) values[i * 2 + 1];
        }

        return new LineNumberTableImpl(line, bci);
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        Local[] locals = graalRuntime().getCompilerToVM().getLocalVariableTable(this);
        return new LocalVariableTableImpl(locals);
    }

    /**
     * Returns the offset of this method into the v-table. The method must have a v-table entry has
     * indicated by {@link #isInVirtualMethodTable()}, otherwise an exception is thrown.
     * 
     * @return the offset of this method into the v-table
     */
    public int vtableEntryOffset() {
        if (!isInVirtualMethodTable() || !holder.isInitialized()) {
            throw new GraalInternalError("%s does not have a vtable entry", this);
        }
        return graalRuntime().getCompilerToVM().getVtableEntryOffset(metaspaceMethod);
    }

    @Override
    public boolean isInVirtualMethodTable() {
        return graalRuntime().getCompilerToVM().hasVtableEntry(metaspaceMethod);
    }

    public void setCurrentTask(CompilationTask task) {
        currentTask = task;
    }

    public CompilationTask currentTask() {
        return currentTask;
    }

    public SpeculationLog getSpeculationLog() {
        if (speculationLog == null) {
            speculationLog = new SpeculationLog();
        }
        return speculationLog;
    }

    public int intrinsicId() {
        HotSpotVMConfig config = graalRuntime().getConfig();
        return unsafe.getByte(metaspaceMethod + config.methodIntrinsicIdOffset) & 0xff;
    }

    @Override
    public Constant invoke(Constant receiver, Constant[] arguments) {
        assert !isConstructor();
        Method javaMethod = toJava();
        javaMethod.setAccessible(true);

        Object[] objArguments = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            objArguments[i] = arguments[i].asBoxedValue();
        }
        Object objReceiver = receiver != null ? receiver.asObject() : null;

        try {
            Object objResult = javaMethod.invoke(objReceiver, objArguments);
            return javaMethod.getReturnType() == void.class ? null : Constant.forBoxed(getSignature().getReturnKind(), objResult);

        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public Constant newInstance(Constant[] arguments) {
        assert isConstructor();
        Constructor javaConstructor = toJavaConstructor();
        javaConstructor.setAccessible(true);

        Object[] objArguments = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            objArguments[i] = arguments[i].asBoxedValue();
        }

        try {
            Object objResult = javaConstructor.newInstance(objArguments);
            assert objResult != null;
            return Constant.forObject(objResult);

        } catch (IllegalAccessException | InvocationTargetException | InstantiationException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
