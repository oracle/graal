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
import com.oracle.svm.guest.staging.reflect.target.Target_jdk_internal_reflect_ConstantPool;
import com.oracle.svm.guest.staging.reflect.RuntimeMetadataDecoder;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Provides metadata accessor lookup, constant-pool layer selection, and annotation-error decoding
 * for {@code RuntimeMetadataDecoderImpl} and the annotation-parser substitutions.
 * <p>
 * These helpers were extracted from {@link RuntimeMetadataDecoder}, including the singleton lookup
 * formerly in its nested {@code MetadataAccessor}, when the decoder contract moved to guest staging.
 * They remain separate to keep image-layer support and compiler folding dependencies out of that
 * contract.
 * <p>
 * Reconsider the split if guest-side facilities can provide the required layer-aware lookup and
 * folding behavior; the extraction does not establish that this support class is temporary.
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
