/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.JavaMethodContext;
import org.graalvm.compiler.nodes.StructuredGraph;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.results.StaticAnalysisResults;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.StubCallingConvention;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CompilationInfo;

import jdk.internal.vm.annotation.ForceInline;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

public final class HostedMethod implements SharedMethod, WrappedJavaMethod, GraphProvider, JavaMethodContext, OriginalMethodProvider {

    public static final String METHOD_NAME_COLLISION_SUFFIX = "*";
    public static final String METHOD_NAME_DEOPT_SUFFIX = "**";

    public final AnalysisMethod wrapped;

    private final HostedType holder;
    private final Signature signature;
    private final ConstantPool constantPool;
    private final ExceptionHandler[] handlers;
    StaticAnalysisResults staticAnalysisResults;
    int vtableIndex = -1;

    /**
     * The address offset of the compiled code relative to the code of the first method in the
     * buffer.
     */
    private int codeAddressOffset;
    private boolean codeAddressOffsetValid;
    private boolean compiled;

    /**
     * All concrete methods that can actually be called when calling this method. This includes all
     * overridden methods in subclasses, as well as this method if it is non-abstract.
     */
    HostedMethod[] implementations;

    public final CompilationInfo compilationInfo;
    private final LocalVariableTable localVariableTable;

    private final String name;
    private final String uniqueShortName;

    public static HostedMethod create(HostedUniverse universe, AnalysisMethod wrapped, HostedType holder, Signature signature,
                    ConstantPool constantPool, ExceptionHandler[] handlers, HostedMethod deoptOrigin) {
        LocalVariableTable localVariableTable = createLocalVariableTable(universe, wrapped);
        String name = deoptOrigin != null ? wrapped.getName() + METHOD_NAME_DEOPT_SUFFIX : wrapped.getName();
        String uniqueShortName = SubstrateUtil.uniqueShortName(SubstrateUtil.classLoaderNameAndId(holder.getJavaClass().getClassLoader()), holder, name, signature, wrapped.isConstructor());
        int collisionCount = universe.uniqueHostedMethodNames.merge(uniqueShortName, 0, (oldValue, value) -> oldValue + 1);
        if (collisionCount > 0) {
            name = name + METHOD_NAME_COLLISION_SUFFIX + collisionCount;
            uniqueShortName = SubstrateUtil.uniqueShortName(SubstrateUtil.classLoaderNameAndId(holder.getJavaClass().getClassLoader()), holder, name, signature, wrapped.isConstructor());
        }
        return new HostedMethod(wrapped, holder, signature, constantPool, handlers, deoptOrigin, name, uniqueShortName, localVariableTable);
    }

    private static LocalVariableTable createLocalVariableTable(HostedUniverse universe, AnalysisMethod wrapped) {
        LocalVariableTable lvt = wrapped.getLocalVariableTable();
        if (lvt == null) {
            return null;
        }
        try {
            Local[] origLocals = lvt.getLocals();
            Local[] newLocals = new Local[origLocals.length];
            for (int i = 0; i < newLocals.length; ++i) {
                Local origLocal = origLocals[i];
                JavaType origType = origLocal.getType();
                if (!universe.contains(origType)) {
                    throw new UnsupportedFeatureException("No HostedType for given AnalysisType");
                }
                HostedType newType = universe.lookup(origType);
                newLocals[i] = new Local(origLocal.getName(), newType, origLocal.getStartBCI(), origLocal.getEndBCI(), origLocal.getSlot());
            }
            return new LocalVariableTable(newLocals);
        } catch (UnsupportedFeatureException e) {
            return null;
        }
    }

    private HostedMethod(AnalysisMethod wrapped, HostedType holder, Signature signature, ConstantPool constantPool,
                    ExceptionHandler[] handlers, HostedMethod deoptOrigin, String name, String uniqueShortName, LocalVariableTable localVariableTable) {
        this.wrapped = wrapped;
        this.holder = holder;
        this.signature = signature;
        this.constantPool = constantPool;
        this.handlers = handlers;
        this.compilationInfo = new CompilationInfo(this, deoptOrigin);
        this.localVariableTable = localVariableTable;
        this.name = name;
        this.uniqueShortName = uniqueShortName;
    }

    @Override
    public HostedMethod[] getImplementations() {
        return implementations;
    }

    public String getQualifiedName() {
        return wrapped.getQualifiedName();
    }

    public void setCodeAddressOffset(int address) {
        assert isCompiled();
        assert !codeAddressOffsetValid;

        codeAddressOffset = address;
        codeAddressOffsetValid = true;
    }

    /**
     * Returns the address offset of the compiled code relative to the code of the first method in
     * the buffer.
     */
    public int getCodeAddressOffset() {
        if (!codeAddressOffsetValid) {
            throw VMError.shouldNotReachHere(format("%H.%n(%p)") + ": has no code address offset set.");
        }
        return codeAddressOffset;
    }

    public boolean isCodeAddressOffsetValid() {
        return codeAddressOffsetValid;
    }

    public void setCompiled() {
        this.compiled = true;
    }

    public boolean isCompiled() {
        return compiled;
    }

    public String getUniqueShortName() {
        return uniqueShortName;
    }

    /*
     * Release compilation related information.
     */
    public void clear() {
        compilationInfo.clear();
        staticAnalysisResults = null;
    }

    @Override
    public boolean hasCodeOffsetInImage() {
        throw unimplemented();
    }

    @Override
    public int getCodeOffsetInImage() {
        throw unimplemented();
    }

    @Override
    public int getDeoptOffsetInImage() {
        HostedMethod deoptTarget = compilationInfo.getDeoptTargetMethod();
        int result = 0;
        if (deoptTarget != null && deoptTarget.isCodeAddressOffsetValid()) {
            result = deoptTarget.getCodeAddressOffset();
            assert result != 0;
        } else if (compilationInfo.isDeoptTarget()) {
            result = getCodeAddressOffset();
            assert result != 0;
        }
        return result;
    }

    @Override
    public AnalysisMethod getWrapped() {
        return wrapped;
    }

    @Override
    public Parameter[] getParameters() {
        return wrapped.getParameters();
    }

    @Override
    public boolean isDeoptTarget() {
        return compilationInfo.isDeoptTarget();
    }

    @Override
    public boolean canDeoptimize() {
        return compilationInfo.canDeoptForTesting();
    }

    public boolean hasVTableIndex() {
        return vtableIndex != -1;
    }

    @Override
    public int getVTableIndex() {
        assert vtableIndex != -1;
        return vtableIndex;
    }

    @Override
    public Deoptimizer.StubType getDeoptStubType() {
        Deoptimizer.DeoptStub stubAnnotation = getAnnotation(Deoptimizer.DeoptStub.class);
        if (stubAnnotation != null) {
            return stubAnnotation.stubType();
        }
        return Deoptimizer.StubType.NoDeoptStub;
    }

    /**
     * Returns true if this method is a native entry point, i.e., called from C code. The method
     * must not be called from Java code then.
     */
    @Override
    public boolean isEntryPoint() {
        return wrapped.isEntryPoint();
    }

    @Override
    public boolean hasCalleeSavedRegisters() {
        return StubCallingConvention.Utils.hasStubCallingConvention(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        return wrapped.buildGraph(debug, method, providers, purpose);
    }

    @Override
    public boolean allowRuntimeCompilation() {
        return wrapped.allowRuntimeCompilation();
    }

    @Override
    public byte[] getCode() {
        return wrapped.getCode();
    }

    @Override
    public int getCodeSize() {
        return wrapped.getCodeSize();
    }

    @Override
    public HostedType getDeclaringClass() {
        return holder;
    }

    @Override
    public int getMaxLocals() {
        return wrapped.getMaxLocals();
    }

    @Override
    public int getMaxStackSize() {
        return wrapped.getMaxStackSize();
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public boolean isSynthetic() {
        return wrapped.isSynthetic();
    }

    @Override
    public boolean isVarArgs() {
        return wrapped.isVarArgs();
    }

    @Override
    public boolean isBridge() {
        return wrapped.isBridge();
    }

    @Override
    public boolean isClassInitializer() {
        return wrapped.isClassInitializer();
    }

    @Override
    public boolean isConstructor() {
        return wrapped.isConstructor();
    }

    @Override
    public boolean canBeStaticallyBound() {
        return implementations.length == 1 && implementations[0].equals(this);
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return handlers;
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return wrapped.asStackTraceElement(bci);
    }

    @Override
    public StaticAnalysisResults getProfilingInfo() {
        return staticAnalysisResults;
    }

    @Override
    public StaticAnalysisResults getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return staticAnalysisResults;
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        return wrapped.getParameterAnnotations();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        return wrapped.getGenericParameterTypes();
    }

    @Override
    public boolean canBeInlined() {
        return !hasNeverInlineDirective();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return wrapped.hasNeverInlineDirective();
    }

    @Override
    public boolean shouldBeInlined() {
        return getAnnotation(AlwaysInline.class) != null || getAnnotation(ForceInline.class) != null;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return wrapped.getLineNumberTable();
    }

    @Override
    public String toString() {
        return "HostedMethod<" + format("%h.%n") + " -> " + wrapped.toString() + ">";
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return localVariableTable;
    }

    @Override
    public void reprofile() {
        throw unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        return hasVTableIndex();
    }

    @Override
    public Constant getEncoding() {
        return new SubstrateMethodPointerConstant(new MethodPointer(this));
    }

    @Override
    public boolean isDefault() {
        throw unimplemented();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw shouldNotReachHere();
    }

    @Override
    public JavaMethod asJavaMethod() {
        return this;
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public Executable getJavaMethod() {
        return OriginalMethodProvider.getJavaMethod(getDeclaringClass().universe.getSnippetReflection(), wrapped);
    }
}
