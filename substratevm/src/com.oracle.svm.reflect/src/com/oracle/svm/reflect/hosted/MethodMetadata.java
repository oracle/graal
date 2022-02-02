/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

import java.lang.annotation.Annotation;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.reflect.MethodMetadataDecoder;
import com.oracle.svm.hosted.image.NativeImageCodeCache.MethodMetadataEncoderFactory;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.reflect.target.MethodMetadataDecoderImpl;
import com.oracle.svm.reflect.target.MethodMetadataEncoding;

import sun.reflect.annotation.TypeAnnotation;

@AutomaticFeature
class MethodMetadataFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(MethodMetadataEncoderFactory.class, new MethodMetadataEncoderImpl.Factory());
        ImageSingletons.add(MethodMetadataDecoder.class, new MethodMetadataDecoderImpl());
        ImageSingletons.add(MethodMetadataEncoding.class, new MethodMetadataEncoding());
    }
}

public class MethodMetadata {
    final HostedType declaringType;
    final String name;
    final HostedType[] parameterTypes;

    MethodMetadata(HostedType declaringType, String name, HostedType[] parameterTypes) {
        this.declaringType = declaringType;
        this.name = name;
        this.parameterTypes = parameterTypes;
    }

    static class ReflectionMethodMetadata extends MethodMetadata {
        final int modifiers;
        final HostedType returnType;
        final HostedType[] exceptionTypes;
        final String signature;
        final Annotation[] annotations;
        final Annotation[][] parameterAnnotations;
        final TypeAnnotation[] typeAnnotations;
        final boolean hasRealParameterData;
        final MethodMetadataDecoderImpl.ReflectParameterDescriptor[] reflectParameters;

        ReflectionMethodMetadata(HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType, HostedType[] exceptionTypes, String signature,
                        Annotation[] annotations, Annotation[][] parameterAnnotations, TypeAnnotation[] typeAnnotations, boolean hasRealParameterData,
                        MethodMetadataDecoderImpl.ReflectParameterDescriptor[] reflectParameters) {
            super(declaringClass, name, parameterTypes);
            this.modifiers = modifiers;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
            this.signature = signature;
            this.annotations = annotations;
            this.parameterAnnotations = parameterAnnotations;
            this.typeAnnotations = typeAnnotations;
            this.hasRealParameterData = hasRealParameterData;
            this.reflectParameters = reflectParameters;
        }
    }

}
