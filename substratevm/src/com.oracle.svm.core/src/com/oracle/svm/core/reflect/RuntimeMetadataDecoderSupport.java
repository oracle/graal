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
package com.oracle.svm.core.reflect;

import java.lang.annotation.AnnotationFormatError;
import java.nio.ByteBuffer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.LayeredFoldResolver;
import com.oracle.svm.core.reflect.target.Target_jdk_internal_reflect_ConstantPool;
import com.oracle.svm.guest.staging.reflect.RuntimeMetadataDecoder;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Core-side support for {@link RuntimeMetadataDecoder}, including metadata accessor lookup,
 * constant-pool layer resolution, and annotation-error decoding. Image-layer dependencies stay here
 * so the decoder contract can reside in guest staging.
 */
public final class RuntimeMetadataDecoderSupport {
    private RuntimeMetadataDecoderSupport() {
    }

    @Fold(resolver = LayeredFoldResolver.InitialLayer.class)
    public static RuntimeMetadataDecoder.MetadataAccessor metadataAccessor() {
        return ImageSingletons.lookup(RuntimeMetadataDecoder.MetadataAccessor.class);
    }

    public static int getConstantPoolLayerId(Target_jdk_internal_reflect_ConstantPool constPool) {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            return constPool.getLayerId();
        } else {
            return 0;
        }
    }

    /**
     * Reads an int from {@code buf} as a {@linkplain RuntimeMetadataDecoder.MetadataAccessor#getOtherString string pool}
     * index and uses it to create an {@link AnnotationFormatError}. The string originates from
     * {@code AnnotationMetadataEncoder#getMessageWithStackTrace()}.
     *
     * @return the created {@link AnnotationFormatError}
     */
    public static AnnotationFormatError decodeAnnotationFormatError(ByteBuffer buf, Target_jdk_internal_reflect_ConstantPool constPool) {
        String errorMessageWithStackTrace = metadataAccessor().getOtherString(buf.getInt(), getConstantPoolLayerId(constPool));
        return new AnnotationFormatError(errorMessageWithStackTrace);
    }
}
