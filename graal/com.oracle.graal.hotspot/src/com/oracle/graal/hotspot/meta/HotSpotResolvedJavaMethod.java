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

import static com.oracle.graal.graph.FieldIntrospection.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.ExceptionSeen;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.debug.*;
import com.oracle.graal.phases.*;

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
    private HotSpotSignature signature;
    private Boolean hasBalancedMonitors;
    private Map<Object, Object> compilerStorage;
    private HotSpotMethodData methodData;
    private byte[] code;
    private int compilationComplexity;

    private CompilationTask currentTask;

    HotSpotResolvedJavaMethod(HotSpotResolvedObjectType holder, long metaspaceMethod) {
        this.metaspaceMethod = metaspaceMethod;
        this.holder = holder;
        HotSpotGraalRuntime.getInstance().getCompilerToVM().initializeMethod(metaspaceMethod, this);
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return holder;
    }

    @Override
    public int getModifiers() {
        HotSpotVMConfig config = HotSpotGraalRuntime.getInstance().getConfig();
        return unsafe.getInt(metaspaceMethod + config.methodAccessFlagsOffset) & Modifier.methodModifiers();
    }

    @Override
    public boolean canBeStaticallyBound() {
        int modifiers = getModifiers();
        return (Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)) && !Modifier.isAbstract(modifiers);
    }

    @Override
    public byte[] getCode() {
        if (code == null) {
            code = HotSpotGraalRuntime.getInstance().getCompilerToVM().initializeBytecode(metaspaceMethod, new byte[codeSize]);
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
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().initializeExceptionHandlers(metaspaceMethod, handlers);
    }

    public boolean hasBalancedMonitors() {
        if (hasBalancedMonitors == null) {
            hasBalancedMonitors = HotSpotGraalRuntime.getInstance().getCompilerToVM().hasBalancedMonitors(metaspaceMethod);
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
        HotSpotVMConfig config = HotSpotGraalRuntime.getInstance().getConfig();
        long metaspaceConstMethod = unsafe.getLong(metaspaceMethod + config.methodConstMethodOffset);
        return unsafe.getShort(metaspaceConstMethod + config.methodMaxLocalsOffset) & 0xFFFF;
    }

    @Override
    public int getMaxStackSize() {
        HotSpotVMConfig config = HotSpotGraalRuntime.getInstance().getConfig();
        long metaspaceConstMethod = unsafe.getLong(metaspaceMethod + config.methodConstMethodOffset);
        return config.extraStackEntries + (unsafe.getShort(metaspaceConstMethod + config.constMethodMaxStackOffset) & 0xFFFF);
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        if (bci < 0 || bci >= codeSize) {
            // HotSpot code can only construct stack trace elements for valid bcis
            StackTraceElement ste = HotSpotGraalRuntime.getInstance().getCompilerToVM().getStackTraceElement(metaspaceMethod, 0);
            return new StackTraceElement(ste.getClassName(), ste.getMethodName(), ste.getFileName(), -1);
        }
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().getStackTraceElement(metaspaceMethod, bci);
    }

    public ResolvedJavaMethod uniqueConcreteMethod() {
        HotSpotResolvedObjectType[] resultHolder = {null};
        long ucm = HotSpotGraalRuntime.getInstance().getCompilerToVM().getUniqueConcreteMethod(metaspaceMethod, resultHolder);
        if (ucm != 0L) {
            assert resultHolder[0] != null;
            return resultHolder[0].createMethod(ucm);
        }
        return null;
    }

    @Override
    public HotSpotSignature getSignature() {
        if (signature == null) {
            signature = new HotSpotSignature(HotSpotGraalRuntime.getInstance().getCompilerToVM().getSignature(metaspaceMethod));
        }
        return signature;
    }

    @Override
    public String toString() {
        return "HotSpotMethod<" + MetaUtil.format("%h.%n", this) + ">";
    }

    public int getCompiledCodeSize() {
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().getCompiledCodeSize(metaspaceMethod);
    }

    public int invocationCount() {
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().getInvocationCount(metaspaceMethod);
    }

    @Override
    public int getCompilationComplexity() {
        if (compilationComplexity <= 0 && getCodeSize() > 0) {
            BytecodeStream s = new BytecodeStream(getCode());
            int result = 0;
            int currentBC;
            while ((currentBC = s.currentBC()) != Bytecodes.END) {
                result += Bytecodes.compilationComplexity(currentBC);
                s.next();
            }
            assert result > 0;
            compilationComplexity = result;
        }
        return compilationComplexity;
    }

    @Override
    public ProfilingInfo getProfilingInfo() {
        ProfilingInfo info;

        if (GraalOptions.UseProfilingInformation && methodData == null) {
            long metaspaceMethodData = unsafeReadWord(metaspaceMethod + HotSpotGraalRuntime.getInstance().getConfig().methodDataOffset);
            if (metaspaceMethodData != 0) {
                methodData = new HotSpotMethodData(metaspaceMethodData);
            }
        }

        if (methodData == null || (!methodData.hasNormalData() && !methodData.hasExtraData())) {
            // Be optimistic and return false for exceptionSeen. A methodDataOop is allocated in
            // case of a deoptimization.
            info = DefaultProfilingInfo.get(ExceptionSeen.FALSE);
        } else {
            info = new HotSpotProfilingInfo(methodData, codeSize);
        }
        return info;
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
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().isMethodCompilable(metaspaceMethod);
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        long[] values = HotSpotGraalRuntime.getInstance().getCompilerToVM().getLineNumberTable(this);
        assert values.length % 2 == 0;
        int[] bci = new int[values.length / 2];
        int[] line = new int[values.length / 2];

        for (int i = 0; i < values.length / 2; i++) {
            bci[i] = (int) values[i * 2];
            line[i] = (int) values[i * 2 + 1];
        }

        return new LineNumberTableImpl(line, bci);
    }

    /**
     * Returns the offset of this method into the v-table. If the holder is not initialized, returns
     * -1
     * 
     * @return the offset of this method into the v-table
     */
    public int vtableEntryOffset() {
        if (!holder.isInitialized()) {
            return -1;
        }
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().getVtableEntryOffset(metaspaceMethod);
    }

    public void setCurrentTask(CompilationTask task) {
        currentTask = task;
    }

    public CompilationTask currentTask() {
        return currentTask;
    }

}
