/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import static org.graalvm.word.WordFactory.nullPointer;
import static org.graalvm.word.WordFactory.zero;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

public class OSCommittedMemoryProvider extends ChunkBasedCommittedMemoryProvider {
    @Platforms(Platform.HOSTED_ONLY.class)
    public OSCommittedMemoryProvider() {
    }

    @Override
    @Uninterruptible(reason = "Still being initialized.")
    public int initialize(WordPointer heapBasePointer, CEntryPointCreateIsolateParameters parameters) {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            int result = protectSingleIsolateImageHeap();
            if (result == CEntryPointErrors.NO_ERROR) {
                heapBasePointer.write(Isolates.IMAGE_HEAP_BEGIN.get());
            }
            return result;
        }
        return ImageHeapProvider.get().initialize(nullPointer(), zero(), heapBasePointer, nullPointer());
    }

    @Override
    @Uninterruptible(reason = "Tear-down in progress.")
    public int tearDown() {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            return CEntryPointErrors.NO_ERROR;
        }

        PointerBase heapBase = Isolates.getHeapBase(CurrentIsolate.getIsolate());
        return ImageHeapProvider.get().freeImageHeap(heapBase);
    }
}

@AutomaticallyRegisteredFeature
class OSCommittedMemoryProviderFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(CommittedMemoryProvider.class)) {
            ImageSingletons.add(CommittedMemoryProvider.class, new OSCommittedMemoryProvider());
        }
    }
}
