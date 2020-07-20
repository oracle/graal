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
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CompilationInfo;

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

public class HostedMethod implements SharedMethod, WrappedJavaMethod, GraphProvider, JavaMethodContext, Comparable<HostedMethod>, OriginalMethodProvider {

    public static final String METHOD_NAME_DEOPT_SUFFIX = "**";

    public final AnalysisMethod wrapped;

    private final HostedType holder;
    private final Signature signature;
    private final ConstantPool constantPool;
    private final ExceptionHandler[] handlers;
    protected StaticAnalysisResults staticAnalysisResults;
    private final boolean hasNeverInlineDirective;
    protected int vtableIndex = -1;

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
    protected HostedMethod[] implementations;

    public final CompilationInfo compilationInfo;
    private final LocalVariableTable localVariableTable;

    public HostedMethod(HostedUniverse universe, AnalysisMethod wrapped, HostedType holder, Signature signature, ConstantPool constantPool, ExceptionHandler[] handlers) {
        this.wrapped = wrapped;
        this.holder = holder;
        this.signature = signature;
        this.constantPool = constantPool;
        this.handlers = handlers;
        this.compilationInfo = new CompilationInfo(this);

        LocalVariableTable newLocalVariableTable = null;
        if (wrapped.getLocalVariableTable() != null) {
            try {
                Local[] origLocals = wrapped.getLocalVariableTable().getLocals();
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
                newLocalVariableTable = new LocalVariableTable(newLocals);
            } catch (UnsupportedFeatureException e) {
                newLocalVariableTable = null;
            }
        }
        localVariableTable = newLocalVariableTable;
        hasNeverInlineDirective = SubstrateUtil.NativeImageLoadingShield.isNeverInline(wrapped);
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

    /*
     * Release compilation related information.
     */
    public void clear() {
        compilationInfo.clear();
        staticAnalysisResults = null;
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
        if (compilationInfo.isDeoptTarget()) {
            return wrapped.getName() + METHOD_NAME_DEOPT_SUFFIX;
        }
        return wrapped.getName();
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
    public Annotation[] getAnnotations() {
        return wrapped.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return wrapped.getDeclaredAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return wrapped.getAnnotation(annotationClass);
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
        return hasNeverInlineDirective;
    }

    @Override
    public boolean shouldBeInlined() {
        return getAnnotation(AlwaysInline.class) != null;
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
        /*
         * This method is used by Graal to decide if method-based inlining guards are possible. As
         * long as getEncoding() is unimplemented, this method needs to return false.
         */
        return false;
    }

    @Override
    public Constant getEncoding() {
        throw unimplemented();
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
    public int compareTo(HostedMethod other) {
        if (this.equals(other)) {
            return 0;
        }

        /*
         * Sort deoptimization targets towards the end of the code cache. They are rarely executed,
         * and we do not want a deoptimization target as the first method (because offset 0 means no
         * deoptimization target available).
         */
        int result = Boolean.compare(this.compilationInfo.isDeoptTarget(), other.compilationInfo.isDeoptTarget());

        if (result == 0) {
            result = this.getDeclaringClass().compareTo(other.getDeclaringClass());
        }
        if (result == 0) {
            result = this.getName().compareTo(other.getName());
        }
        if (result == 0) {
            result = this.getSignature().getParameterCount(false) - other.getSignature().getParameterCount(false);
        }
        if (result == 0) {
            for (int i = 0; i < this.getSignature().getParameterCount(false); i++) {
                result = ((HostedType) this.getSignature().getParameterType(i, null)).compareTo((HostedType) other.getSignature().getParameterType(i, null));
                if (result != 0) {
                    break;
                }
            }
        }
        if (result == 0) {
            result = ((HostedType) this.getSignature().getReturnType(null)).compareTo((HostedType) other.getSignature().getReturnType(null));
        }
        assert result != 0;
        return result;
    }

    @Override
    public Executable getJavaMethod() {
        return OriginalMethodProvider.getJavaMethod(getDeclaringClass().universe.getSnippetReflection(), wrapped);
    }
}
