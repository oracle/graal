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

import java.util.random.RandomGenerator;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.Uninterruptible;

/**
 * Interface for a singleton that provides a runtime instance of a random number generator (and thus
 * ensures the generator is properly seeded). Implementing classes must use
 * {@link RuntimeRandomness} as the {@link ImageSingletons} registry key.
 */
public interface RuntimeRandomness {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static RuntimeRandomness instance() {
        return ImageSingletons.lookup(RuntimeRandomness.class);
    }

    /**
     * Initialize the underlying random number generator with a new seed. Note that this method must
     * be called before any call to {@link #getRandom()} because the random instance can only be
     * initialized once.
     */
    void initializeRandom(long seed);

    /**
     * Return a runtime-initialized random number generator (RNG). If the RNG is not initialized
     * already, it will be initialized.
     */
    RandomGenerator getRandom();

    /**
     * Return a runtime-initialized random number generator that does not block or acquire Java
     * monitors. This generator is suitable for VM operations. It is not cryptographically secure.
     */
    RandomGenerator getNonBlockingRandom();
}
