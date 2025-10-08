/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.intentionallyUnimplemented;

import java.lang.reflect.Modifier;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.BuildPhaseProvider.AfterAnalysis;
import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.OriginalFieldProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

public class SubstrateField implements SharedField {

    protected static final SubstrateField[] EMPTY_ARRAY = new SubstrateField[0];

    @UnknownObjectField(availability = AfterAnalysis.class) SubstrateType type;
    @UnknownObjectField(availability = AfterAnalysis.class) SubstrateType declaringClass;
    private final String name;
    private final int modifiers;
    private final int hashCode;

    @UnknownPrimitiveField(availability = AfterCompilation.class) int location;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private boolean isAccessed;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private boolean isWritten;
    @UnknownObjectField(types = {DirectSubstrateObjectConstant.class, PrimitiveConstant.class}, fullyQualifiedTypes = "jdk.vm.ci.meta.NullConstant", //
                    canBeNull = true, availability = AfterCompilation.class)//
    JavaConstant constantValue;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateField(AnalysisField aField, HostedStringDeduplication stringTable) {
        VMError.guarantee(!aField.isInternal(), "Internal fields are not supported for JIT compilation");

        /*
         * AliasField removes the "final" modifier for AOT compilation because the recomputed value
         * is not guaranteed to be known yet. But for runtime compilation, we know that we can treat
         * the field as "final".
         */
        ResolvedJavaField oField = OriginalFieldProvider.getOriginalField(aField);
        boolean injectFinalForRuntimeCompilation = oField != null && oField.isFinal();

        this.modifiers = aField.getModifiers() |
                        (injectFinalForRuntimeCompilation ? Modifier.FINAL : 0);

        this.name = stringTable.deduplicate(aField.getName(), true);
        this.hashCode = aField.hashCode();
    }

    /**
     * Must only be called at run-time from Ristretto.
     */
    protected SubstrateField() {
        assert SubstrateOptions.useRistretto() : "Must only be initialized at runtime by ristretto";
        name = null;
        modifiers = -1;
        hashCode = -1;
    }

    public void setLinks(SubstrateType type, SubstrateType declaringClass) {
        this.type = type;
        this.declaringClass = declaringClass;
    }

    public void setSubstrateDataAfterCompilation(int location, boolean isAccessed, boolean isWritten, JavaConstant constantValue) {
        this.location = location;
        this.isAccessed = isAccessed;
        this.isWritten = isWritten;
        this.constantValue = constantValue;
    }

    @Override
    public int getLocation() {
        return location;
    }

    @Override
    public boolean isAccessed() {
        return isAccessed;
    }

    @Override
    public boolean isReachable() {
        return isAccessed || isWritten;
    }

    @Override
    public boolean isWritten() {
        return isWritten;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public JavaKind getStorageKind() {
        return getType().getStorageKind();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SubstrateType getType() {
        return type;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public int getOffset() {
        return getLocation();
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public SubstrateType getDeclaringClass() {
        return declaringClass;
    }

    private RuntimeException annotationsUnimplemented() {
        return VMError.unimplemented("Annotations are not available for JIT compilation at image run time: " + format("%H.%n"));
    }

    @Override
    public <T> T getDeclaredAnnotationInfo(Function<AnnotationsInfo, T> parser) {
        throw annotationsUnimplemented();
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        throw annotationsUnimplemented();
    }

    @Override
    public boolean isSynthetic() {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int getInstalledLayerNum() {
        /*
         * GR-62500: Layered images are not yet supported for runtime compilation.
         */
        return MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER;
    }

    @Override
    public String toString() {
        return "SubstrateField<" + format("%h.%n") + " location: " + location + ">";
    }

    @Override
    public Object getStaticFieldBaseForRuntimeLoadedClass() {
        // only AOT known static fields available, those are in regular static arrays
        return null;
    }
}
