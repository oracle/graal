/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

/**
 * An image singleton that provides:
 * A secure random number generator that is initialized at runtime with a
 * {@link SecureRandom} instance. (see {@link RuntimeRandomness#getRandom()}). This is the default
 * implementation if no other {@link RuntimeRandomness} is registered (see
 * {@link RuntimeRandomnessFeature}).
 * Also provides a non-blocking, non-cryptographically secure PRNG that can be used from @Uninterruptible methods such as VM operations.
 */
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class RuntimeRandomnessSupport implements RuntimeRandomness {

    private final AtomicReference<SecureRandom> random = new AtomicReference<>();
    private final NonBlockingRandom nonBlockingRandom = new NonBlockingRandom();

    @Override
    public void initializeRandom(long seed) {
        VMError.guarantee(ImageInfo.inImageRuntimeCode(), "Cannot access runtime random instance during build");

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        byte[] seedBytes = buffer.putLong(seed).array();
        if (!random.compareAndSet(null, new SecureRandom(seedBytes))) {
            throw new IllegalStateException("The runtime random instance is already initialized");
        }
        nonBlockingRandom.initialize(seed);
    }

    @Override
    public RandomGenerator getRandom() {
        VMError.guarantee(ImageInfo.inImageRuntimeCode(), "Cannot access runtime random instance during build");

        SecureRandom r = random.get();
        if (r == null) {
            r = new SecureRandom();
            if (!random.compareAndSet(null, r)) {
                return random.get();
            }
        }
        return r;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public RandomGenerator getNonBlockingRandom() {
        return nonBlockingRandom;
    }

    /**
     * A lock-free SplitMix64 generator. The initial state is set at runtime so it is not persisted
     * in the image heap.
     */
    private static final class NonBlockingRandom implements RandomGenerator {
        private static final long GAMMA = 0x9e3779b97f4a7c15L;

        private final UninterruptibleUtils.AtomicLong state = new UninterruptibleUtils.AtomicLong(0);

        void initialize(long seed) {
            state.set(nonZero(mix64(seed)));
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long nextLong() {
            long current = state.get();
            if (current == 0) {
                state.compareAndSet(0, nonZero(mix64(System.nanoTime())));
            }
            return mix64(state.getAndAdd(GAMMA));
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static long nonZero(long value) {
            return value == 0 ? 1 : value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static long mix64(long value) {
            long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
            mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
            return mixed ^ (mixed >>> 31);
        }
    }
}
