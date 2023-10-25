/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;

/**
 * Defines a section of memory where per-isolate singleton instances of native data structures can
 * be stored.
 */
@AutomaticallyRegisteredImageSingleton
public final class CIsolateDataStorage {

    @Platforms(Platform.HOSTED_ONLY.class)
    public CIsolateDataStorage() {
    }

    /*
     * Always align structures at 8 bytes, even on 32 bit platforms. Some data structures such as
     * pthread_mutex_t rely on this.
     */
    public static final int ALIGNMENT = Long.BYTES;

    @UnknownObjectField(availability = AfterHostedUniverse.class) //
    private byte[] managedIsolateSectionData;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSize(UnsignedWord size) {
        assert managedIsolateSectionData == null;
        UnsignedWord allocationSize = size.add(WordFactory.unsigned(ALIGNMENT - 1));
        managedIsolateSectionData = new byte[UnsignedUtils.safeToInt(allocationSize)];
    }

    @Fold
    public static CIsolateDataStorage singleton() {
        return ImageSingletons.lookup(CIsolateDataStorage.class);
    }

    @Fold
    protected static UnsignedWord arrayBaseOffset() {
        int offset = ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);
        return UnsignedUtils.roundUp(WordFactory.unsigned(offset), WordFactory.unsigned(ALIGNMENT));
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T get(CIsolateData<T> ptr) {
        VMError.guarantee(ptr != null, "null isolate data section entry");

        Pointer base = Word.objectToUntrackedPointer(managedIsolateSectionData).add(arrayBaseOffset());

        return (T) base.add(ptr.getOffset());
    }
}
