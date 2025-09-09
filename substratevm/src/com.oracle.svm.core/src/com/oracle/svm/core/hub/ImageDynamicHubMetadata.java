/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;
import org.graalvm.nativeimage.ImageSingletons;

import static com.oracle.svm.core.reflect.RuntimeMetadataDecoder.NO_DATA;

final class ImageDynamicHubMetadata implements DynamicHubMetadata {
    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class) //
    final int enclosingMethodInfoIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int annotationsIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int typeAnnotationsIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int classesEncodingIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int permittedSubclassesEncodingIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int nestMembersEncodingIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int signersEncodingIndex;

    ImageDynamicHubMetadata(int enclosingMethodInfoIndex, int annotationsIndex, int typeAnnotationsIndex, int classesEncodingIndex, int permittedSubclassesEncodingIndex,
                    int nestMembersEncodingIndex, int signersEncodingIndex) {
        this.enclosingMethodInfoIndex = enclosingMethodInfoIndex;
        this.annotationsIndex = annotationsIndex;
        this.typeAnnotationsIndex = typeAnnotationsIndex;
        this.classesEncodingIndex = classesEncodingIndex;
        this.permittedSubclassesEncodingIndex = permittedSubclassesEncodingIndex;
        this.nestMembersEncodingIndex = nestMembersEncodingIndex;
        this.signersEncodingIndex = signersEncodingIndex;
    }

    @Override
    public Object[] getEnclosingMethod(DynamicHub declaringClass) {
        if (enclosingMethodInfoIndex == NO_DATA) {
            return null;
        }
        Object[] enclosingMethod = ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseEnclosingMethod(enclosingMethodInfoIndex, declaringClass);
        if (enclosingMethod != null) {
            PredefinedClassesSupport.throwIfUnresolvable((Class<?>) enclosingMethod[0], declaringClass.getClassLoader0());
        }
        return enclosingMethod;
    }

    @Override
    public Object[] getSigners(DynamicHub declaringClass) {
        if (signersEncodingIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseObjects(signersEncodingIndex, declaringClass);
    }

    @Override
    public byte[] getRawAnnotations(DynamicHub declaringClass) {
        if (annotationsIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseByteArray(annotationsIndex, declaringClass);
    }

    @Override
    public byte[] getRawTypeAnnotations(DynamicHub declaringClass) {
        if (typeAnnotationsIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseByteArray(typeAnnotationsIndex, declaringClass);
    }

    @Override
    public Class<?>[] getDeclaredClasses(DynamicHub declaringClass) {
        if (classesEncodingIndex == NO_DATA) {
            return new Class<?>[0];
        }
        Class<?>[] declaredClasses = ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseClasses(classesEncodingIndex, declaringClass);
        for (Class<?> clazz : declaredClasses) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, declaringClass.getClassLoader0());
        }
        return declaredClasses;
    }

    @Override
    public Class<?>[] getNestMembers(DynamicHub declaringClass) {
        if (nestMembersEncodingIndex == NO_DATA) {
            return new Class<?>[]{DynamicHub.toClass(declaringClass)};
        }
        Class<?>[] nestMembers = ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseClasses(nestMembersEncodingIndex, declaringClass);
        for (Class<?> clazz : nestMembers) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, declaringClass.getClassLoader0());
        }
        return nestMembers;
    }

    @Override
    public Class<?>[] getPermittedSubClasses(DynamicHub declaringClass) {
        if (permittedSubclassesEncodingIndex == NO_DATA) {
            return new Class<?>[0];
        }
        Class<?>[] permittedSubclasses = ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseClasses(permittedSubclassesEncodingIndex, declaringClass);
        for (Class<?> clazz : permittedSubclasses) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, declaringClass.getClassLoader0());
        }
        return permittedSubclasses;
    }
}
