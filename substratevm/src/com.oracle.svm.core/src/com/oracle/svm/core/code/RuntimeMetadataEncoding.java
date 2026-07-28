/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.MetadataAccessorImpl;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * Stores the compact encodings of runtime metadata.
 * <p>
 * The main {@link #encoding} array contains the generic runtime metadata stream consumed by
 * {@link RuntimeMetadataDecoderImpl}. In non-layered images, image-time reflection metadata for
 * {@code DynamicHub}s is stored separately in {@link #reflectionMetadataEncoding}. Each hub stores
 * an encoded reference into that side table (or an inline value) instead of a per-hub
 * {@code ImageReflectionMetadata} object.
 * <p>
 * Layered images intentionally keep using {@code LayeredReflectionMetadataSingleton} and
 * {@code ImageReflectionMetadata} objects because metadata can be contributed by multiple layers.
 * Classes loaded dynamically at image runtime use {@code RuntimeReflectionMetadata} instead because
 * their metadata is produced from the runtime class definition rather than from this image-time
 * encoding.
 *
 * GR-52991: Currently we assume that all runtime metadata is in a single byte array, and that it
 * refers only to data in the first image CodeInfo (resolved via {@link MetadataAccessorImpl}). For
 * layered images, we will presumably want to build runtime metadata for each layer, and be able to
 * extend (over rewrite) metadata from a base layer in another layer, for example by including a new
 * member of an existing class, or registering an existing member for reflection. For that purpose,
 * we would need an index to locate all relevant runtime metadata of an entity from all layers.
 */
@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public class RuntimeMetadataEncoding {
    private static final int INITIAL_REFLECTION_METADATA_ENCODING_CAPACITY = 1024;

    /** Generic runtime metadata encoding consumed by {@link RuntimeMetadataDecoderImpl}. */
    @UnknownObjectField(availability = AfterCompilation.class) private byte[] encoding;

    /**
     * Compact side-table encoding for non-layered image reflection metadata. During image building,
     * this array may have spare capacity and {@link #reflectionMetadataEncodingSize} tracks the used
     * prefix. {@link #trimReflectionMetadataEncoding()} shrinks it before installation in the image
     * heap.
     */
    @UnknownObjectField(availability = AfterCompilation.class) private byte[] reflectionMetadataEncoding = new byte[0];
    private int reflectionMetadataEncodingSize;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeMetadataEncoding currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(RuntimeMetadataEncoding.class, false, true);
    }

    public byte[] getEncoding() {
        return encoding;
    }

    public byte[] getReflectionMetadataEncoding() {
        return reflectionMetadataEncoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setEncoding(byte[] encoding) {
        this.encoding = encoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int addReflectionMetadata(int size) {
        int offset = reflectionMetadataEncodingSize;
        reflectionMetadataEncodingSize += size;
        if (reflectionMetadataEncodingSize > reflectionMetadataEncoding.length) {
            int newCapacity = Math.max(INITIAL_REFLECTION_METADATA_ENCODING_CAPACITY, reflectionMetadataEncoding.length);
            while (newCapacity < reflectionMetadataEncodingSize) {
                newCapacity *= 2;
            }
            reflectionMetadataEncoding = Arrays.copyOf(reflectionMetadataEncoding, newCapacity);
        }
        return offset;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void trimReflectionMetadataEncoding() {
        reflectionMetadataEncoding = Arrays.copyOf(reflectionMetadataEncoding, reflectionMetadataEncodingSize);
    }
}
