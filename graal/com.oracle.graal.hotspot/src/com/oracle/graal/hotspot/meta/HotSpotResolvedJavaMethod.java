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

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.counters.*;
import com.oracle.max.criutils.*;

/**
 * Implementation of RiMethod for resolved HotSpot methods.
 */
public final class HotSpotResolvedJavaMethod extends HotSpotMethod implements ResolvedJavaMethod {

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
    private Signature signature;
    private Boolean hasBalancedMonitors;
    private Map<Object, Object> compilerStorage;
    private ResolvedJavaType holder;
    private HotSpotMethodData methodData;
    private byte[] code;
    private boolean canBeInlined;
    private int compilationComplexity;

    private CompilationTask currentTask;

    private HotSpotResolvedJavaMethod() {
        throw new IllegalStateException("this constructor is never actually called, because the objects are allocated from within the VM");
    }

    @Override
    public ResolvedJavaType holder() {
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
            code = HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_code(this);
            assert code.length == codeSize : "expected: " + codeSize + ", actual: " + code.length;
        }
        return code;
    }

    @Override
    public int codeSize() {
        return codeSize;
    }

    @Override
    public ExceptionHandler[] exceptionHandlers() {
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_exceptionHandlers(this);
    }

    @Override
    public boolean hasBalancedMonitors() {
        if (hasBalancedMonitors == null) {
            hasBalancedMonitors = HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_hasBalancedMonitors(this);
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
    public String jniSymbol() {
        throw new UnsupportedOperationException("jniSymbol");
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
        if (bci < 0 || bci >= codeSize) {
            // HotSpot code can only construct stack trace elements for valid bcis
            StackTraceElement ste = HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_toStackTraceElement(this, 0);
            return new StackTraceElement(ste.getClassName(), ste.getMethodName(), ste.getFileName(), -1);
        }
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_toStackTraceElement(this, bci);
    }

    public ResolvedJavaMethod uniqueConcreteMethod() {
        return (ResolvedJavaMethod) HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_uniqueConcreteMethod(this);
    }

    @Override
    public Signature signature() {
        if (signature == null) {
            signature = new HotSpotSignature(HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_signature(this));
        }
        return signature;
    }

    @Override
    public String toString() {
        return "HotSpotMethod<" + CodeUtil.format("%h.%n", this) + ">";
    }

    public boolean hasCompiledCode() {
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_hasCompiledCode(this);
    }

    public int compiledCodeSize() {
        int result = HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_getCompiledCodeSize(this);
        if (result > 0) {
            assert result > MethodEntryCounters.getCodeSize();
            result =  result - MethodEntryCounters.getCodeSize();
        }
        return result;
    }

    @Override
    public int invocationCount() {
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_invocationCount(this);
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

    private static final MethodFilter profilingInfoFilter = GraalOptions.PIFilter == null ? null : new MethodFilter(GraalOptions.PIFilter);

    /**
     * Determines if the profiling info cache should be used for this method.
     */
    private boolean useProfilingInfoCache() {
        return GraalOptions.PICache != null && (profilingInfoFilter == null || profilingInfoFilter.matches(this));
    }

    private ProfilingInfo loadProfilingInfo() {
        if (!useProfilingInfoCache()) {
            return null;
        }
        synchronized (this) {
            File file = new File(GraalOptions.PICache, JniMangle.mangleMethod(holder, name, signature(), false));
            if (file.exists()) {
                try {
                    SnapshotProfilingInfo snapshot = SnapshotProfilingInfo.load(file, HotSpotGraalRuntime.getInstance().getRuntime());
                    if (snapshot.codeSize() != codeSize) {
                        // The class file was probably changed - ignore the saved profile
                        return null;
                    }
                    return snapshot;
                } catch (Exception e) {
                    // ignore
                }
            }
            return null;
        }
    }

    private void saveProfilingInfo(ProfilingInfo info) {
        if (useProfilingInfoCache()) {
            synchronized (this) {
                String base = JniMangle.mangleMethod(holder, name, signature(), false);
                File file = new File(GraalOptions.PICache, base);
                File txtFile = new File(GraalOptions.PICache, base + ".txt");
                SnapshotProfilingInfo snapshot = info instanceof SnapshotProfilingInfo ? (SnapshotProfilingInfo) info : new SnapshotProfilingInfo(info);
                try {
                    snapshot.save(file, txtFile);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public ProfilingInfo profilingInfo() {
        ProfilingInfo info = loadProfilingInfo();
        if (info != null) {
            return info;
        }

        if (GraalOptions.UseProfilingInformation && methodData == null) {
            methodData = HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_methodData(this);
        }

        if (methodData == null || (!methodData.hasNormalData() && !methodData.hasExtraData())) {
            // Be optimistic and return false for exceptionSeen. A methodDataOop is allocated in case of a deoptimization.
            info = BaseProfilingInfo.get(ExceptionSeen.FALSE);
        } else {
            info = new HotSpotProfilingInfo(methodData, codeSize);
            saveProfilingInfo(info);
        }
        return info;
    }

    @Override
    public Map<Object, Object> compilerStorage() {
        if (compilerStorage == null) {
            compilerStorage = new ConcurrentHashMap<>();
        }
        return compilerStorage;
    }

    @Override
    public ConstantPool getConstantPool() {
        return ((HotSpotResolvedJavaType) holder()).constantPool();
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

    private Method toJava() {
        try {
            return holder.toJava().getDeclaredMethod(name, CodeUtil.signatureToTypes(signature(), holder));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Constructor toJavaConstructor() {
        try {
            return holder.toJava().getDeclaredConstructor(CodeUtil.signatureToTypes(signature(), holder));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public boolean canBeInlined() {
        return canBeInlined;
    }

    public int vtableEntryOffset() {
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().JavaMethod_vtableEntryOffset(this);
    }

    public void setCurrentTask(CompilationTask task) {
        currentTask = task;
    }

    public CompilationTask currentTask() {
        return currentTask;
    }
}
