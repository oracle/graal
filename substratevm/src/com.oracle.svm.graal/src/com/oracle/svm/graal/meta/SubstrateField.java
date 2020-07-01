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

import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.annotation.Annotation;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.hub.AnnotationsEncoding;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeCloneable;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateField implements SharedField {

    protected static final SubstrateField[] EMPTY_ARRAY = new SubstrateField[0];

    SubstrateType type;
    SubstrateType declaringClass;
    private final String name;
    private final int modifiers;
    private int hashCode;
    private Object annotationsEncoding;

    @UnknownPrimitiveField int location;
    @UnknownPrimitiveField private boolean isAccessed;
    @UnknownPrimitiveField private boolean isWritten;
    @UnknownObjectField(types = {DirectSubstrateObjectConstant.class, PrimitiveConstant.class}, fullyQualifiedTypes = "jdk.vm.ci.meta.NullConstant")//
    JavaConstant constantValue;

    /* Truffle access this information frequently, so it is worth caching it in a field. */
    final boolean truffleChildField;
    final boolean truffleChildrenField;
    final boolean truffleCloneableField;

    public SubstrateField(MetaAccessProvider originalMetaAccess, ResolvedJavaField original, int modifiers, HostedStringDeduplication stringTable) {
        this.modifiers = modifiers;
        this.name = stringTable.deduplicate(original.getName(), true);
        this.hashCode = original.hashCode();

        truffleChildField = original.getAnnotation(Child.class) != null;
        truffleChildrenField = original.getAnnotation(Children.class) != null;
        truffleCloneableField = originalMetaAccess.lookupJavaType(NodeCloneable.class).isAssignableFrom((ResolvedJavaType) original.getType());
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
        throw unimplemented();
    }

    @Override
    public boolean isInternal() {
        throw unimplemented();
    }

    @Override
    public SubstrateType getDeclaringClass() {
        return declaringClass;
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
    public boolean isSynthetic() {
        throw unimplemented();
    }

    @Override
    public String toString() {
        return "SubstrateField<" + format("%h.%n") + " location: " + location + ">";
    }
}
