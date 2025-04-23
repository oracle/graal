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

import java.util.EnumSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.MetadataAccessorImpl;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;

/**
 * Stores the encoding of all runtime metadata.
 *
 * GR-52991: Currently we assume that all runtime metadata is in a single byte array, and that it
 * refers only to data in the first image CodeInfo (resolved via {@link MetadataAccessorImpl}). For
 * layered images, we will presumably want to build runtime metadata for each layer, and be able to
 * extend (over rewrite) metadata from a base layer in another layer, for example by including a new
 * member of an existing class, or registering an existing member for reflection. For that purpose,
 * we would need an index to locate all relevant runtime metadata of an entity from all layers.
 */
@AutomaticallyRegisteredImageSingleton
public class RuntimeMetadataEncoding implements MultiLayeredImageSingleton, UnsavedSingleton {
    @UnknownObjectField(availability = AfterCompilation.class) private byte[] encoding;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeMetadataEncoding currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(RuntimeMetadataEncoding.class, false, true);
    }

    public byte[] getEncoding() {
        return encoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setEncoding(byte[] encoding) {
        this.encoding = encoding;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }
}
