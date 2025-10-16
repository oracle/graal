/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.sdk.staging.hosted.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.sdk.staging.hosted.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.sdk.staging.hosted.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraits;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaKind;

/**
 * Note we now store this separately from {@link CIsolateData} so that it can be stored in a
 * multi-layered image singleton.
 */
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public class ImageCodeInfoStorage {

    private final byte[] data;

    /*
     * Always align structures to 8 bytes
     */
    static final int ALIGNMENT = Long.BYTES;

    protected ImageCodeInfoStorage() {
        long size = SizeOf.get(CodeInfoImpl.class);
        int arrayBaseOffset = ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);
        int alignedOffset = getAlignedOffsetInArray();
        int addend = alignedOffset - arrayBaseOffset;
        int dataSize = NumUtil.safeToInt(size + addend);
        data = new byte[dataSize];
    }

    @Fold
    static int getAlignedOffsetInArray() {
        return NumUtil.roundUp(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte), ALIGNMENT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public CodeInfoImpl getCodeInfo() {
        Pointer base = Word.objectToUntrackedPointer(data).add(Word.unsigned(getAlignedOffsetInArray()));
        return (CodeInfoImpl) base;
    }
}

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
class ImageCodeInfoStorageFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(ImageCodeInfoStorage.class, new ImageCodeInfoStorage());
    }
}
