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
package com.oracle.max.graal.hotspot.ri;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.java.*;

/**
 * Implementation of RiMethod for resolved HotSpot methods.
 */
public final class HotSpotMethodResolvedImpl extends HotSpotMethod implements HotSpotMethodResolved {

    private static final long serialVersionUID = -5486975070147586588L;

    /** DO NOT USE IN JAVA CODE! */
    @SuppressWarnings("unused")
    @Deprecated
    private Object javaMirror;

    // cached values
    private final int codeSize;
    private final int accessFlags;
    private final int maxLocals;
    private final int maxStackSize;
    private RiSignature signature;
    private Boolean hasBalancedMonitors;
    private Map<Object, Object> compilerStorage;
    private RiResolvedType holder;
    private HotSpotMethodData methodData;
    private byte[] code;
    private boolean canBeInlined;
    private CiGenericCallback callback;
    private int compilationComplexity;

    private HotSpotMethodResolvedImpl() {
        super(null);
        throw new IllegalStateException("this constructor is never actually called, because the objects are allocated from within the VM");
    }

    @Override
    public RiResolvedType holder() {
        return holder;
    }

    @Override
    public int accessFlags() {
        return accessFlags;
    }

    @Override
    public boolean canBeStaticallyBound() {
        return isLeafMethod() || Modifier.isStatic(accessFlags());
    }

    @Override
    public byte[] code() {
        if (code == null) {
            code = compiler.getVMEntries().RiMethod_code(this);
            assert code.length == codeSize : "expected: " + codeSize + ", actual: " + code.length;
        }
        return code;
    }

    @Override
    public int codeSize() {
        return codeSize;
    }

    @Override
    public RiExceptionHandler[] exceptionHandlers() {
        return compiler.getVMEntries().RiMethod_exceptionHandlers(this);
    }

    @Override
    public boolean hasBalancedMonitors() {
        if (hasBalancedMonitors == null) {
            hasBalancedMonitors = compiler.getVMEntries().RiMethod_hasBalancedMonitors(this);
        }
        return hasBalancedMonitors;
    }

    @Override
    public boolean isClassInitializer() {
        return "<clinit>".equals(name) && Modifier.isStatic(accessFlags());
    }

    @Override
    public boolean isConstructor() {
        return "<init>".equals(name) && !Modifier.isStatic(accessFlags());
    }

    @Override
    public boolean isLeafMethod() {
        return Modifier.isFinal(accessFlags()) || Modifier.isPrivate(accessFlags());
    }

    @Override
    public boolean isOverridden() {
        throw new UnsupportedOperationException("isOverridden");
    }

    @Override
    public boolean noSafepointPolls() {
        return false;
    }

    @Override
    public String jniSymbol() {
        throw new UnsupportedOperationException("jniSymbol");
    }

    public CiBitMap[] livenessMap() {
        return null;
    }

    @Override
    public int maxLocals() {
        return maxLocals;
    }

    @Override
    public int maxStackSize() {
        return maxStackSize;
    }

    @Override
    public StackTraceElement toStackTraceElement(int bci) {
        return CiUtil.toStackTraceElement(this, bci);
    }

    @Override
    public RiResolvedMethod uniqueConcreteMethod() {
        return (RiResolvedMethod) compiler.getVMEntries().RiMethod_uniqueConcreteMethod(this);
    }

    @Override
    public RiSignature signature() {
        if (signature == null) {
            signature = new HotSpotSignature(compiler, compiler.getVMEntries().RiMethod_signature(this));
        }
        return signature;
    }

    @Override
    public String toString() {
        return "HotSpotMethod<" + CiUtil.format("%h.%n", this) + ">";
    }

    public boolean hasCompiledCode() {
        return compiler.getVMEntries().RiMethod_hasCompiledCode(this);
    }

    public int compiledCodeSize() {
        return compiler.getVMEntries().RiMethod_getCompiledCodeSize(this);
    }

    @Override
    public RiResolvedType accessor() {
        return null;
    }

    @Override
    public String intrinsic() {
        return null;
    }

    @Override
    public int invocationCount() {
        return compiler.getVMEntries().RiMethod_invocationCount(this);
    }

    @Override
    public int compilationComplexity() {
        if (compilationComplexity <= 0 && codeSize() > 0) {
            BytecodeStream s = new BytecodeStream(code());
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
    public RiProfilingInfo profilingInfo() {
        if (methodData == null) {
            methodData = compiler.getVMEntries().RiMethod_methodData(this);
        }

        if (methodData == null) {
            return new HotSpotNoProfilingInfo(compiler);
        } else {
            return new HotSpotProfilingInfo(compiler, methodData);
        }
    }

    @Override
    public Map<Object, Object> compilerStorage() {
        if (compilerStorage == null) {
            compilerStorage = new ConcurrentHashMap<>();
        }
        return compilerStorage;
    }

    @Override
    public RiConstantPool getConstantPool() {
        return ((HotSpotTypeResolvedImpl) holder()).constantPool();
    }

    public void dumpProfile() {
        TTY.println("profile info for %s", this);
        TTY.println("canBeStaticallyBound: " + canBeStaticallyBound());
        TTY.println("invocationCount: " + invocationCount());
        RiProfilingInfo profilingInfo = this.profilingInfo();
        for (int i = 0; i < codeSize(); i++) {
            if (profilingInfo.getExecutionCount(i) != -1) {
                TTY.println("  executionCount@%d: %d", i, profilingInfo.getExecutionCount(i));
            }

            if (profilingInfo.getBranchTakenProbability(i) != -1) {
                TTY.println("  branchProbability@%d: %f", i, profilingInfo.getBranchTakenProbability(i));
            }

            double[] switchProbabilities = profilingInfo.getSwitchProbabilities(i);
            if (switchProbabilities != null) {
                TTY.print("  switchProbabilities@%d:", i);
                for (int j = 0; j < switchProbabilities.length; j++) {
                    TTY.print(" %f", switchProbabilities[j]);
                }
                TTY.println();
            }

            if (profilingInfo.getExceptionSeen(i) != RiExceptionSeen.FALSE) {
                TTY.println("  exceptionSeen@%d: %s", i, profilingInfo.getExceptionSeen(i).name());
            }

            RiTypeProfile typeProfile = profilingInfo.getTypeProfile(i);
            if (typeProfile != null) {
                RiResolvedType[] types = typeProfile.getTypes();
                double[] probabilities = typeProfile.getProbabilities();
                if (types != null && probabilities != null) {
                    assert types.length == probabilities.length : "length must match";
                    TTY.print("  types@%d:", i);
                    for (int j = 0; j < types.length; j++) {
                        TTY.print(" %s (%f)", types[j], probabilities[j]);
                    }
                    TTY.println(" not recorded (%f)", typeProfile.getNotRecordedProbability());
                }
            }
        }
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
    public Type getGenericReturnType() {
        if (isConstructor()) {
            return void.class;
        }
        Method javaMethod = toJava();
        return javaMethod == null ? null : javaMethod.getGenericReturnType();
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

    private Method toJava() {
        try {
            return holder.toJava().getDeclaredMethod(name, CiUtil.signatureToTypes(signature(), holder));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Constructor toJavaConstructor() {
        try {
            return holder.toJava().getDeclaredConstructor(CiUtil.signatureToTypes(signature(), holder));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public boolean canBeInlined() {
        return canBeInlined && callback == null;
    }
    public void neverInline() {
        this.canBeInlined = false;
    }

    public CiGenericCallback callback() {
        return callback;
    }
    public void setCallback(CiGenericCallback callback) {
        this.callback = callback;
    }

    @Override
    public int vtableEntryOffset() {
        return compiler.getVMEntries().RiMethod_vtableEntryOffset(this);
    }
}
