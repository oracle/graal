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

import static jdk.graal.compiler.word.Word.nullPointer;
import static jdk.graal.compiler.word.Word.zero;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.IsolateArguments;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.word.Word;

public class OSCommittedMemoryProvider extends ChunkBasedCommittedMemoryProvider {
    @Platforms(Platform.HOSTED_ONLY.class)
    public OSCommittedMemoryProvider() {
    }

    @Override
    @Uninterruptible(reason = "Still being initialized.")
    public int initialize(WordPointer heapBaseOut, IsolateArguments arguments) {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            int result = protectSingleIsolateImageHeap();
            if (result == CEntryPointErrors.NO_ERROR) {
                heapBaseOut.write(Isolates.IMAGE_HEAP_BEGIN.get());
            }
            return result;
        }
        WordPointer imageHeapEndOut = StackValue.get(WordPointer.class);
        return ImageHeapProvider.get().initialize(nullPointer(), zero(), heapBaseOut, imageHeapEndOut);
    }

    @Override
    @Uninterruptible(reason = "Tear-down in progress.")
    public int tearDown() {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            return CEntryPointErrors.NO_ERROR;
        }
        return ImageHeapProvider.get().freeImageHeap(KnownIntrinsics.heapBase());
    }

    @Override
    public UnsignedWord getCollectedHeapAddressSpaceSize() {
        assert getReservedAddressSpaceSize().aboveOrEqual(ImageHeapProvider.get().getImageHeapEndOffsetInAddressSpace());
        return getReservedAddressSpaceSize().subtract(ImageHeapProvider.get().getImageHeapEndOffsetInAddressSpace());
    }

    @Override
    public UnsignedWord getReservedAddressSpaceSize() {
        UnsignedWord maxAddressSpaceSize = ReferenceAccess.singleton().getMaxAddressSpaceSize();
        UnsignedWord optionValue = Word.unsigned(SubstrateGCOptions.ReservedAddressSpaceSize.getValue());
        if (optionValue.notEqual(0)) {
            return UnsignedUtils.min(optionValue, maxAddressSpaceSize);
        }
        return maxAddressSpaceSize;
    }
}
