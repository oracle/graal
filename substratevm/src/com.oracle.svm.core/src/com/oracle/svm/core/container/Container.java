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
package com.oracle.svm.core.container;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.PauseNode;

/** Provides container awareness to the rest of the VM. */
@AutomaticallyRegisteredImageSingleton
public class Container {
    /* The C++ library is shared between multiple isolates. */
    private static final CGlobalData<Pointer> STATE = CGlobalDataFactory.createWord();
    private static final int CACHE_MS = 20;

    private long activeProcessorCountTimeoutMs;
    private int cachedActiveProcessorCount;

    private long physicalMemoryTimeoutMs;
    private UnsignedWord cachedPhysicalMemorySize;
    private long memoryLimitInBytesTimeoutMs;
    private long cachedMemoryLimitInBytes;

    @Platforms(Platform.HOSTED_ONLY.class)
    Container() {
    }

    @Fold
    public static boolean isSupported() {
        return SubstrateOptions.UseContainerSupport.getValue() && Platform.includedIn(Platform.LINUX.class);
    }

    @Fold
    public static Container singleton() {
        return ImageSingletons.lookup(Container.class);
    }

    /**
     * Determines whether the image runs containerized.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isContainerized() {
        if (!isSupported()) {
            return false;
        }

        UnsignedWord value = STATE.get().readWord(0);
        assert value == State.CONTAINERIZED || value == State.NOT_CONTAINERIZED;
        return value == State.CONTAINERIZED;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void initialize() {
        if (!isSupported()) {
            return;
        }
        Pointer statePtr = STATE.get();
        UnsignedWord value = statePtr.compareAndSwapWord(0, State.UNINITIALIZED, State.INITIALIZING, LocationIdentity.ANY_LOCATION);
        if (value == State.UNINITIALIZED) {
            value = switch (ContainerLibrary.initialize(ContainerLibrary.VERSION)) {
                case ContainerLibrary.SUCCESS_IS_NOT_CONTAINERIZED:
                    yield State.NOT_CONTAINERIZED;
                case ContainerLibrary.SUCCESS_IS_CONTAINERIZED:
                    yield State.CONTAINERIZED;
                case ContainerLibrary.ERROR_LIBCONTAINER_TOO_OLD:
                    yield State.ERROR_LIBCONTAINER_TOO_OLD;
                case ContainerLibrary.ERROR_LIBCONTAINER_TOO_NEW:
                    yield State.ERROR_LIBCONTAINER_TOO_NEW;
                default:
                    yield State.ERROR_UNKNOWN;
            };
            // write
            statePtr.writeWordVolatile(0, value);
        } else {
            while (value == State.INITIALIZING) {
                PauseNode.pause();
                value = statePtr.readWordVolatile(0, LocationIdentity.ANY_LOCATION);
            }
        }
        VMError.guarantee(value != State.ERROR_LIBCONTAINER_TOO_OLD, "native-image tries to use a libsvm_container version that is too old");
        VMError.guarantee(value != State.ERROR_LIBCONTAINER_TOO_NEW, "native-image tries to use a libsvm_container version that is too new");
        VMError.guarantee(value == State.CONTAINERIZED || value == State.NOT_CONTAINERIZED, "unexpected libsvm_container initialize result");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getActiveProcessorCount() {
        VMError.guarantee(isContainerized());

        long currentMs = TimeUtils.currentTimeMillis();
        if (currentMs > activeProcessorCountTimeoutMs) {
            cachedActiveProcessorCount = ContainerLibrary.getActiveProcessorCount();
            activeProcessorCountTimeoutMs = currentMs + CACHE_MS;
        }
        return cachedActiveProcessorCount;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getCachedActiveProcessorCount() {
        VMError.guarantee(isContainerized());
        return cachedActiveProcessorCount;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getPhysicalMemory() {
        VMError.guarantee(isContainerized());

        long currentMs = TimeUtils.currentTimeMillis();
        if (currentMs > physicalMemoryTimeoutMs) {
            cachedPhysicalMemorySize = ContainerLibrary.physicalMemory();
            physicalMemoryTimeoutMs = currentMs + CACHE_MS;
        }
        return cachedPhysicalMemorySize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getCachedPhysicalMemory() {
        VMError.guarantee(isContainerized());
        return cachedPhysicalMemorySize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getMemoryLimitInBytes() {
        VMError.guarantee(isContainerized());

        long currentMs = TimeUtils.currentTimeMillis();
        if (currentMs > memoryLimitInBytesTimeoutMs) {
            cachedMemoryLimitInBytes = ContainerLibrary.getMemoryLimitInBytes();
            memoryLimitInBytesTimeoutMs = currentMs + CACHE_MS;
        }
        return cachedMemoryLimitInBytes;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getCachedMemoryLimitInBytes() {
        VMError.guarantee(isContainerized());
        return cachedMemoryLimitInBytes;
    }

    private static final class State {
        static final UnsignedWord UNINITIALIZED = Word.unsigned(0);
        static final UnsignedWord INITIALIZING = Word.unsigned(1);
        static final UnsignedWord NOT_CONTAINERIZED = Word.unsigned(2);
        static final UnsignedWord CONTAINERIZED = Word.unsigned(3);
        static final UnsignedWord ERROR_LIBCONTAINER_TOO_OLD = Word.unsigned(4);
        static final UnsignedWord ERROR_LIBCONTAINER_TOO_NEW = Word.unsigned(5);
        static final UnsignedWord ERROR_UNKNOWN = Word.unsigned(6);
    }
}
