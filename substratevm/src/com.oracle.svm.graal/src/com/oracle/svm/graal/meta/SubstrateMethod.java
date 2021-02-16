/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.hub.AnnotationsEncoding;
import com.oracle.svm.core.util.HostedStringDeduplication;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;

public class SubstrateMethod implements SharedRuntimeMethod {

    private final byte[] encodedLineNumberTable;
    private final int modifiers;
    private final String name;
    private final int hashCode;
    private SubstrateType declaringClass;
    private int encodedGraphStartOffset;
    @UnknownPrimitiveField private int vTableIndex;
    private Object annotationsEncoding;

    /**
     * A pointer to the compiled code of the corresponding method in the native image. Used as
     * destination address if this method is called in a direct call.
     */
    @UnknownPrimitiveField private int codeOffsetInImage;

    /**
     * A pointer to the deoptimization target code in the native image. Used as destination address
     * for deoptimization. This is only != 0, if there _is_ a deoptimization target method in the
     * image for this method.
     */
    @UnknownPrimitiveField private int deoptOffsetInImage;

    @UnknownObjectField(types = {SubstrateMethod[].class, SubstrateMethod.class}, canBeNull = true)//
    protected Object implementations;

    private final boolean neverInline;
    private final boolean bridge;

    private SubstrateSignature signature;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateMethod(ResolvedJavaMethod original, HostedStringDeduplication stringTable) {
        encodedLineNumberTable = EncodedLineNumberTable.encode(original.getLineNumberTable());

        assert original.getAnnotation(CEntryPoint.class) == null : "Can't compile entry point method";

        modifiers = original.getModifiers();
        name = stringTable.deduplicate(original.getName(), true);
        neverInline = SubstrateUtil.NativeImageLoadingShield.isNeverInline(original);

        /*
         * AnalysisMethods of snippets are stored in a hash map of SubstrateReplacements. The
         * GraalObjectReplacer replaces them with SubstrateMethods. Therefore we have to preserve
         * the hashCode of the original AnalysisMethod. Note that this is only required because it
         * is a replaced object. For not replaced objects the hash code is preserved automatically
         * in a synthetic hash-code field (see BootImageHeap.ObjectInfo.identityHashCode).
         */
        hashCode = original.hashCode();
        implementations = new SubstrateMethod[0];
        encodedGraphStartOffset = -1;
        bridge = original.isBridge();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean setAnnotationsEncoding(Object annotationsEncoding) {
        boolean result = this.annotationsEncoding != annotationsEncoding;
        this.annotationsEncoding = annotationsEncoding;
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object getAnnotationsEncoding() {
        return annotationsEncoding;
    }

    public byte[] getEncodedLineNumberTable() {
        return encodedLineNumberTable;
    }

    /**
     * Returns the hashCode of the original AnalysisMethod.
     */
    @Override
    public int hashCode() {
        return hashCode;
    }

    public void setLinks(SubstrateSignature signature, SubstrateType declaringClass) {
        this.signature = signature;
        this.declaringClass = declaringClass;
    }

    public boolean setImplementations(SubstrateMethod[] rawImplementations) {
        Object newImplementations;
        if (rawImplementations.length == 0) {
            newImplementations = null;
        } else if (rawImplementations.length == 1) {
            newImplementations = rawImplementations[0];
        } else if (!(this.implementations instanceof SubstrateMethod[]) || !Arrays.equals((SubstrateMethod[]) this.implementations, rawImplementations)) {
            newImplementations = rawImplementations;
        } else {
            newImplementations = this.implementations;
        }

        boolean result = this.implementations != newImplementations;
        this.implementations = newImplementations;
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object getRawImplementations() {
        return implementations;
    }

    public void setSubstrateData(int vTableIndex, int codeOffsetInImage, int deoptOffsetInImage) {
        this.vTableIndex = vTableIndex;
        this.codeOffsetInImage = codeOffsetInImage;
        this.deoptOffsetInImage = deoptOffsetInImage;
    }

    @Override
    public int getCodeOffsetInImage() {
        assert codeOffsetInImage != 0;
        return codeOffsetInImage;
    }

    @Override
    public int getDeoptOffsetInImage() {
        return deoptOffsetInImage;
    }

    @Override
    public int getEncodedGraphStartOffset() {
        return encodedGraphStartOffset;
    }

    public void setEncodedGraphStartOffset(long encodedGraphStartOffset) {
        this.encodedGraphStartOffset = TypeConversion.asS4(encodedGraphStartOffset);
    }

    @Override
    public boolean isEntryPoint() {
        return false;
    }

    @Override
    public boolean hasCalleeSavedRegisters() {
        return false;
    }

    @Override
    public SubstrateMethod[] getImplementations() {
        if (implementations == null) {
            return new SubstrateMethod[0];
        } else if (implementations instanceof SubstrateMethod) {
            return new SubstrateMethod[]{(SubstrateMethod) implementations};
        } else {
            return (SubstrateMethod[]) implementations;
        }
    }

    @Override
    public boolean isDeoptTarget() {
        return false;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public int getVTableIndex() {
        if (vTableIndex < 0) {
            throw shouldNotReachHere("no vtable index");
        }
        return vTableIndex;
    }

    @Override
    public Deoptimizer.StubType getDeoptStubType() {
        return Deoptimizer.StubType.NoDeoptStub;
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
    public byte[] getCode() {
        return null;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    public SubstrateType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public int getMaxLocals() {
        return getSignature().getParameterCount(!Modifier.isStatic(getModifiers())) * 2;
    }

    @Override
    public int getMaxStackSize() {
        // A dummy number for now.
        return 2;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public boolean isClassInitializer() {
        assert !("<clinit>".equals(name) && isStatic()) : "class initializers are executed during native image generation and are never in the native image";
        return false;
    }

    @Override
    public boolean isConstructor() {
        return "<init>".equals(name) && !isStatic();
    }

    @Override
    public boolean canBeStaticallyBound() {
        /*
         * If the method has only a single implementation we have to return true. This let's a
         * virtual call be canonicalized to a special call. This is not just an optimization but a
         * requirement, because such methods don't get a vtable index assigned in the
         * UniverseBuilder.
         */
        return this.equals(implementations);
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        throw shouldNotReachHere();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        int lineNumber = EncodedLineNumberTable.getLineNumber(bci, encodedLineNumberTable);
        return new StackTraceElement(getDeclaringClass().toClassName(), getName(), getDeclaringClass().getSourceFileName(), lineNumber);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return DefaultProfilingInfo.get(TriState.UNKNOWN);
    }

    @Override
    public void reprofile() {
        throw unimplemented();
    }

    @Override
    public ConstantPool getConstantPool() {
        throw shouldNotReachHere();
    }

    @Override
    public Annotation[] getAnnotations() {
        return AnnotationsEncoding.decodeAnnotations(annotationsEncoding).getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return AnnotationsEncoding.decodeAnnotations(annotationsEncoding).getAnnotation(annotationClass);
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw unimplemented();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw unimplemented();
    }

    @Override
    public boolean canBeInlined() {
        return !hasNeverInlineDirective();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        // If there is no graph in the image, then the method must never be considered
        // for inlining (because any attempt to inline it would fail).
        return neverInline || encodedGraphStartOffset < 0;
    }

    @Override
    public boolean shouldBeInlined() {
        return false;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return EncodedLineNumberTable.decode(encodedLineNumberTable);
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return null;
    }

    @Override
    public Constant getEncoding() {
        throw unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw unimplemented();
    }

    @Override
    public boolean isSynthetic() {
        return false;
    }

    @Override
    public boolean isVarArgs() {
        throw unimplemented();
    }

    @Override
    public boolean isBridge() {
        return bridge;
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
    public String toString() {
        return "SubstrateMethod<" + format("%h.%n") + ">";
    }
}
