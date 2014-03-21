/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.graph.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.debug.*;
import com.oracle.graal.nodes.*;

/**
 * Implementation of {@link JavaMethod} for resolved HotSpot methods.
 */
public final class HotSpotResolvedJavaMethod extends HotSpotMethod implements ResolvedJavaMethod {

    private static final long serialVersionUID = -5486975070147586588L;

    /**
     * Reference to metaspace Method object.
     */
    private final long metaspaceMethod;

    private final HotSpotResolvedObjectType holder;
    private final HotSpotConstantPool constantPool;
    private final HotSpotSignature signature;
    private HotSpotMethodData methodData;
    private byte[] code;
    private SpeculationLog speculationLog;

    /**
     * Gets the holder of a HotSpot metaspace method native object.
     * 
     * @param metaspaceMethod a metaspace Method object
     * @return the {@link ResolvedJavaType} corresponding to the holder of the
     *         {@code metaspaceMethod}
     */
    public static HotSpotResolvedObjectType getHolder(long metaspaceMethod) {
        HotSpotVMConfig config = runtime().getConfig();
        final long metaspaceConstMethod = unsafe.getAddress(metaspaceMethod + config.methodConstMethodOffset);
        final long metaspaceConstantPool = unsafe.getAddress(metaspaceConstMethod + config.constMethodConstantsOffset);
        final long metaspaceKlass = unsafe.getAddress(metaspaceConstantPool + config.constantPoolHolderOffset);
        return (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromMetaspaceKlass(metaspaceKlass);
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
        // It would be too much work to get the method name here so we fill it in later.
        super(null);
        this.metaspaceMethod = metaspaceMethod;
        this.holder = holder;

        HotSpotVMConfig config = runtime().getConfig();
        final long constMethod = getConstMethod();

        /*
         * Get the constant pool from the metaspace method. Some methods (e.g. intrinsics for
         * signature-polymorphic method handle methods) have their own constant pool instead of the
         * one from their holder.
         */
        final long metaspaceConstantPool = unsafe.getAddress(constMethod + config.constMethodConstantsOffset);
        this.constantPool = new HotSpotConstantPool(metaspaceConstantPool);

        final int nameIndex = unsafe.getChar(constMethod + config.constMethodNameIndexOffset);
        this.name = constantPool.lookupUtf8(nameIndex);

        final int signatureIndex = unsafe.getChar(constMethod + config.constMethodSignatureIndexOffset);
        this.signature = (HotSpotSignature) constantPool.lookupSignature(signatureIndex);
    }

    /**
     * Returns a pointer to this method's constant method data structure (
     * {@code Method::_constMethod}).
     * 
     * @return pointer to this method's ConstMethod
     */
    private long getConstMethod() {
        assert metaspaceMethod != 0;
        return unsafe.getAddress(metaspaceMethod + runtime().getConfig().methodConstMethodOffset);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HotSpotResolvedJavaMethod) {
            HotSpotResolvedJavaMethod that = (HotSpotResolvedJavaMethod) obj;
            return that.metaspaceMethod == metaspaceMethod;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) metaspaceMethod;
    }

    /**
     * Returns this method's flags ({@code Method::_flags}).
     * 
     * @return flags of this method
     */
    private int getFlags() {
        return unsafe.getByte(metaspaceMethod + runtime().getConfig().methodFlagsOffset);
    }

    /**
     * Returns this method's constant method flags ({@code ConstMethod::_flags}).
     * 
     * @return flags of this method's ConstMethod
     */
    private int getConstMethodFlags() {
        return unsafe.getChar(getConstMethod() + runtime().getConfig().constMethodFlagsOffset);
    }

    @Override
    public HotSpotResolvedObjectType getDeclaringClass() {
        return holder;
    }

    /**
     * Gets the address of the C++ Method object for this method.
     */
    public Constant getMetaspaceMethodConstant() {
        return Constant.forIntegerKind(getHostWordKind(), metaspaceMethod, this);
    }

    @Override
    public Constant getEncoding() {
        return getMetaspaceMethodConstant();
    }

    /**
     * Gets the complete set of modifiers for this method which includes the JVM specification
     * modifiers as well as the HotSpot internal modifiers.
     */
    public int getAllModifiers() {
        return unsafe.getInt(metaspaceMethod + runtime().getConfig().methodAccessFlagsOffset);
    }

    @Override
    public int getModifiers() {
        return getAllModifiers() & Modifier.methodModifiers();
    }

    @Override
    public boolean canBeStaticallyBound() {
        int modifiers = getModifiers();
        return (Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)) && !Modifier.isAbstract(modifiers);
    }

    @Override
    public byte[] getCode() {
        if (getCodeSize() == 0) {
            return null;
        }
        if (code == null && holder.isLinked()) {
            code = runtime().getCompilerToVM().initializeBytecode(metaspaceMethod, new byte[getCodeSize()]);
            assert code.length == getCodeSize() : "expected: " + getCodeSize() + ", actual: " + code.length;
        }
        return code;
    }

    @Override
    public int getCodeSize() {
        return unsafe.getChar(getConstMethod() + runtime().getConfig().constMethodCodeSizeOffset);
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        final boolean hasExceptionTable = (getConstMethodFlags() & runtime().getConfig().constMethodHasExceptionTable) != 0;
        if (!hasExceptionTable) {
            return new ExceptionHandler[0];
        }

        HotSpotVMConfig config = runtime().getConfig();
        final int exceptionTableLength = runtime().getCompilerToVM().exceptionTableLength(metaspaceMethod);
        ExceptionHandler[] handlers = new ExceptionHandler[exceptionTableLength];
        long exceptionTableElement = runtime().getCompilerToVM().exceptionTableStart(metaspaceMethod);

        for (int i = 0; i < exceptionTableLength; i++) {
            final int startPc = unsafe.getChar(exceptionTableElement + config.exceptionTableElementStartPcOffset);
            final int endPc = unsafe.getChar(exceptionTableElement + config.exceptionTableElementEndPcOffset);
            final int handlerPc = unsafe.getChar(exceptionTableElement + config.exceptionTableElementHandlerPcOffset);
            int catchTypeIndex = unsafe.getChar(exceptionTableElement + config.exceptionTableElementCatchTypeIndexOffset);

            JavaType catchType;
            if (catchTypeIndex == 0) {
                catchType = null;
            } else {
                final int opcode = -1;  // opcode is not used
                catchType = constantPool.lookupType(catchTypeIndex, opcode);

                // Check for Throwable which catches everything.
                if (catchType instanceof HotSpotResolvedObjectType) {
                    HotSpotResolvedObjectType resolvedType = (HotSpotResolvedObjectType) catchType;
                    if (resolvedType.mirror() == Throwable.class) {
                        catchTypeIndex = 0;
                        catchType = null;
                    }
                }
            }
            handlers[i] = new ExceptionHandler(startPc, endPc, handlerPc, catchTypeIndex, catchType);

            // Go to the next ExceptionTableElement
            exceptionTableElement += config.exceptionTableElementSize;
        }

        return handlers;
    }

    /**
     * Returns true if this method has a {@code CallerSensitive} annotation.
     * 
     * @return true if CallerSensitive annotation present, false otherwise
     */
    public boolean isCallerSensitive() {
        return (getFlags() & runtime().getConfig().methodFlagsCallerSensitive) != 0;
    }

    /**
     * Returns true if this method has a {@code ForceInline} annotation.
     * 
     * @return true if ForceInline annotation present, false otherwise
     */
    public boolean isForceInline() {
        return (getFlags() & runtime().getConfig().methodFlagsForceInline) != 0;
    }

    /**
     * Returns true if this method has a {@code DontInline} annotation.
     * 
     * @return true if DontInline annotation present, false otherwise
     */
    public boolean isDontInline() {
        return (getFlags() & runtime().getConfig().methodFlagsDontInline) != 0;
    }

    /**
     * Manually adds a DontInline annotation to this method.
     */
    public void setNotInlineable() {
        runtime().getCompilerToVM().doNotInlineOrCompile(metaspaceMethod);
    }

    /**
     * Returns true if this method is one of the special methods that is ignored by security stack
     * walks.
     * 
     * @return true if special method ignored by security stack walks, false otherwise
     */
    public boolean ignoredBySecurityStackWalk() {
        return runtime().getCompilerToVM().methodIsIgnoredBySecurityStackWalk(metaspaceMethod);
    }

    public boolean hasBalancedMonitors() {
        HotSpotVMConfig config = runtime().getConfig();
        final int modifiers = getAllModifiers();

        // Method has no monitorenter/exit bytecodes.
        if ((modifiers & config.jvmAccHasMonitorBytecodes) == 0) {
            return false;
        }

        // Check to see if a previous compilation computed the monitor-matching analysis.
        if ((modifiers & config.jvmAccMonitorMatch) != 0) {
            return true;
        }

        // This either happens only once if monitors are balanced or very rarely multiple-times.
        return runtime().getCompilerToVM().hasBalancedMonitors(metaspaceMethod);
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
        int modifiers = getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
            return 0;
        }
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getChar(getConstMethod() + config.methodMaxLocalsOffset);
    }

    @Override
    public int getMaxStackSize() {
        int modifiers = getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
            return 0;
        }
        HotSpotVMConfig config = runtime().getConfig();
        return config.extraStackEntries + unsafe.getChar(getConstMethod() + config.constMethodMaxStackOffset);
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        if (bci < 0 || bci >= getCodeSize()) {
            // HotSpot code can only construct stack trace elements for valid bcis
            StackTraceElement ste = runtime().getCompilerToVM().getStackTraceElement(metaspaceMethod, 0);
            return new StackTraceElement(ste.getClassName(), ste.getMethodName(), ste.getFileName(), -1);
        }
        return runtime().getCompilerToVM().getStackTraceElement(metaspaceMethod, bci);
    }

    public ResolvedJavaMethod uniqueConcreteMethod() {
        if (holder.isInterface()) {
            // Cannot trust interfaces. Because of:
            // interface I { void foo(); }
            // class A { public void foo() {} }
            // class B extends A implements I { }
            // class C extends B { public void foo() { } }
            // class D extends B { }
            // Would lead to identify C.foo() as the unique concrete method for I.foo() without
            // seeing A.foo().
            return null;
        }
        final long uniqueConcreteMethod = runtime().getCompilerToVM().findUniqueConcreteMethod(metaspaceMethod);
        if (uniqueConcreteMethod == 0) {
            return null;
        }
        return fromMetaspace(uniqueConcreteMethod);
    }

    @Override
    public HotSpotSignature getSignature() {
        return signature;
    }

    /**
     * Gets the value of {@code Method::_code}.
     * 
     * @return the value of {@code Method::_code}
     */
    private long getCompiledCode() {
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getAddress(metaspaceMethod + config.methodCodeOffset);
    }

    /**
     * Returns whether this method has compiled code.
     * 
     * @return true if this method has compiled code, false otherwise
     */
    public boolean hasCompiledCode() {
        return getCompiledCode() != 0L;
    }

    /**
     * @param level
     * @return true if the currently installed code was generated at {@code level}.
     */
    public boolean hasCompiledCodeAtLevel(int level) {
        long compiledCode = getCompiledCode();
        if (compiledCode != 0) {
            return unsafe.getInt(compiledCode + runtime().getConfig().nmethodCompLevelOffset) == level;
        }
        return false;
    }

    private static final String TraceMethodDataFilter = System.getProperty("graal.traceMethodDataFilter");

    @Override
    public ProfilingInfo getProfilingInfo() {
        return getProfilingInfo(true, true);
    }

    public ProfilingInfo getCompilationProfilingInfo(boolean isOSR) {
        return getProfilingInfo(!isOSR, isOSR);
    }

    private ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        ProfilingInfo info;

        if (UseProfilingInformation.getValue() && methodData == null) {
            long metaspaceMethodData = unsafeReadWord(metaspaceMethod + runtime().getConfig().methodDataOffset);
            if (metaspaceMethodData != 0) {
                methodData = new HotSpotMethodData(metaspaceMethodData);
                if (TraceMethodDataFilter != null && MetaUtil.format("%H.%n", this).contains(TraceMethodDataFilter)) {
                    TTY.println("Raw method data for " + MetaUtil.format("%H.%n(%p)", this) + ":");
                    TTY.println(methodData.toString());
                }
            }
        }

        if (methodData == null || (!methodData.hasNormalData() && !methodData.hasExtraData())) {
            // Be optimistic and return false for exceptionSeen. A methodDataOop is allocated in
            // case of a deoptimization.
            info = DefaultProfilingInfo.get(TriState.FALSE);
        } else {
            info = new HotSpotProfilingInfo(methodData, this, includeNormal, includeOSR);
        }
        return info;
    }

    @Override
    public void reprofile() {
        runtime().getCompilerToVM().reprofile(metaspaceMethod);
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
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
    public boolean isSynthetic() {
        int modifiers = getAllModifiers();
        return (runtime().getConfig().syntheticFlag & modifiers) != 0;
    }

    public boolean isDefault() {
        if (isConstructor()) {
            return false;
        }
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringClass().isInterface();
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
        if (isDontInline()) {
            return false;
        }
        return runtime().getCompilerToVM().canInlineMethod(metaspaceMethod);
    }

    @Override
    public boolean shouldBeInlined() {
        if (isForceInline()) {
            return true;
        }
        return runtime().getCompilerToVM().shouldInlineMethod(metaspaceMethod);
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        final boolean hasLineNumberTable = (getConstMethodFlags() & runtime().getConfig().constMethodHasLineNumberTable) != 0;
        if (!hasLineNumberTable) {
            return null;
        }

        long[] values = runtime().getCompilerToVM().getLineNumberTable(metaspaceMethod);
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
        final boolean hasLocalVariableTable = (getConstMethodFlags() & runtime().getConfig().constMethodHasLocalVariableTable) != 0;
        if (!hasLocalVariableTable) {
            return null;
        }

        HotSpotVMConfig config = runtime().getConfig();
        long localVariableTableElement = runtime().getCompilerToVM().getLocalVariableTableStart(metaspaceMethod);
        final int localVariableTableLength = runtime().getCompilerToVM().getLocalVariableTableLength(metaspaceMethod);
        Local[] locals = new Local[localVariableTableLength];

        for (int i = 0; i < localVariableTableLength; i++) {
            final int startBci = unsafe.getChar(localVariableTableElement + config.localVariableTableElementStartBciOffset);
            final int endBci = startBci + unsafe.getChar(localVariableTableElement + config.localVariableTableElementLengthOffset);
            final int nameCpIndex = unsafe.getChar(localVariableTableElement + config.localVariableTableElementNameCpIndexOffset);
            final int typeCpIndex = unsafe.getChar(localVariableTableElement + config.localVariableTableElementDescriptorCpIndexOffset);
            final int slot = unsafe.getChar(localVariableTableElement + config.localVariableTableElementSlotOffset);

            String localName = getConstantPool().lookupUtf8(nameCpIndex);
            String localType = getConstantPool().lookupUtf8(typeCpIndex);

            locals[i] = new LocalImpl(localName, localType, holder, startBci, endBci, slot);

            // Go to the next LocalVariableTableElement
            localVariableTableElement += config.localVariableTableElementSize;
        }

        return new LocalVariableTableImpl(locals);
    }

    /**
     * Returns the offset of this method into the v-table. The method must have a v-table entry as
     * indicated by {@link #isInVirtualMethodTable()}, otherwise an exception is thrown.
     * 
     * @return the offset of this method into the v-table
     */
    public int vtableEntryOffset() {
        if (!isInVirtualMethodTable() || !holder.isInitialized()) {
            throw new GraalInternalError("%s does not have a vtable entry", this);
        }
        HotSpotVMConfig config = runtime().getConfig();
        final int vtableIndex = getVtableIndex();
        return config.instanceKlassVtableStartOffset + vtableIndex * config.vtableEntrySize + config.vtableEntryMethodOffset;
    }

    @Override
    public boolean isInVirtualMethodTable() {
        return getVtableIndex() >= 0;
    }

    /**
     * Returns this method's virtual table index.
     * 
     * @return virtual table index
     */
    private int getVtableIndex() {
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getInt(metaspaceMethod + config.methodVtableIndexOffset);
    }

    public SpeculationLog getSpeculationLog() {
        if (speculationLog == null) {
            speculationLog = new SpeculationLog();
        }
        return speculationLog;
    }

    public int intrinsicId() {
        HotSpotVMConfig config = runtime().getConfig();
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

    /**
     * Allocates a compile id for this method by asking the VM for one.
     * 
     * @param entryBCI entry bci
     * @return compile id
     */
    public int allocateCompileId(int entryBCI) {
        return runtime().getCompilerToVM().allocateCompileId(metaspaceMethod, entryBCI);
    }

    public boolean tryToQueueForCompilation() {
        // other threads may update certain bits of the access flags field concurrently. So, the
        // loop ensures that this method only returns false when another thread has set the
        // queuedForCompilation bit.
        do {
            long address = getAccessFlagsAddress();
            int actualValue = unsafe.getInt(address);
            int expectedValue = actualValue & ~runtime().getConfig().methodQueuedForCompilationBit;
            if (actualValue != expectedValue) {
                return false;
            } else {
                int newValue = expectedValue | runtime().getConfig().methodQueuedForCompilationBit;
                boolean success = unsafe.compareAndSwapInt(null, address, expectedValue, newValue);
                if (success) {
                    return true;
                }
            }
        } while (true);
    }

    public void clearQueuedForCompilation() {
        long address = getAccessFlagsAddress();
        boolean success;
        do {
            int actualValue = unsafe.getInt(address);
            int newValue = actualValue & ~runtime().getConfig().methodQueuedForCompilationBit;
            assert isQueuedForCompilation() : "queued for compilation must be set";
            success = unsafe.compareAndSwapInt(null, address, actualValue, newValue);
        } while (!success);
    }

    public boolean isQueuedForCompilation() {
        return (unsafe.getInt(getAccessFlagsAddress()) & runtime().getConfig().methodQueuedForCompilationBit) != 0;
    }

    private long getAccessFlagsAddress() {
        return metaspaceMethod + runtime().getConfig().methodAccessFlagsOffset;
    }

    public boolean hasCodeAtLevel(int entryBCI, int level) {
        if (entryBCI == StructuredGraph.INVOCATION_ENTRY_BCI) {
            return hasCompiledCodeAtLevel(level);
        }
        return runtime().getCompilerToVM().hasCompiledCodeForOSR(metaspaceMethod, entryBCI, level);
    }
}
