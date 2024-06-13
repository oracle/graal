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

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ameta.ReadableJavaField;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

public class SubstrateField implements SharedField {

    protected static final SubstrateField[] EMPTY_ARRAY = new SubstrateField[0];

    @UnknownObjectField SubstrateType type;
    @UnknownObjectField SubstrateType declaringClass;
    private final String name;
    private final int modifiers;
    private int hashCode;

    @UnknownPrimitiveField(availability = AfterCompilation.class) int location;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private boolean isAccessed;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private boolean isWritten;
    @UnknownObjectField(types = {DirectSubstrateObjectConstant.class, PrimitiveConstant.class}, fullyQualifiedTypes = "jdk.vm.ci.meta.NullConstant", //
                    canBeNull = true, availability = AfterCompilation.class)//
    JavaConstant constantValue;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateField(AnalysisField aField, HostedStringDeduplication stringTable) {
        VMError.guarantee(!aField.isInternal(), "Internal fields are not supported for JIT compilation");

        this.modifiers = aField.getModifiers() |
                        (ReadableJavaField.injectFinalForRuntimeCompilation(aField.wrapped) ? Modifier.FINAL : 0);

        this.name = stringTable.deduplicate(aField.getName(), true);
        this.hashCode = aField.hashCode();
    }

    public void setLinks(SubstrateType type, SubstrateType declaringClass) {
        this.type = type;
        this.declaringClass = declaringClass;
    }

    public void setSubstrateData(int location, boolean isAccessed, boolean isWritten, JavaConstant constantValue) {
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

    @Override
    public Annotation[] getAnnotations() {
        throw VMError.unimplemented("Annotations are not available for JIT compilation at image run time");
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        throw VMError.unimplemented("Annotations are not available for JIT compilation at image run time");
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw VMError.unimplemented("Annotations are not available for JIT compilation at image run time");
    }

    @Override
    public boolean isSynthetic() {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean isValueAvailable() {
        return true;
    }

    @Override
    public boolean isInBaseLayer() {
        return false;
    }

    @Override
    public JavaConstant getStaticFieldBase() {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public String toString() {
        return "SubstrateField<" + format("%h.%n") + " location: " + location + ">";
    }
}
