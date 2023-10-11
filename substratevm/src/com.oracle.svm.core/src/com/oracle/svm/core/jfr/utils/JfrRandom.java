/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.utils;

import org.graalvm.nativeimage.Platforms;
import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import com.oracle.svm.core.locks.VMMutex;

/**
 * This class is essentially the same as JfrPRNG in
 * jdk/src/hotspot/shar/jfr/utilities/jfrRandom.inline.hpp in the OpenJDK. Commit hash:
 * 1100dbc6b2a1f2d5c431c6f5c6eb0b9092aee817.
 */
public class JfrRandom {
    private static final long prngMult = 25214903917L;
    private static final long prngAdd = 11;
    private static final long prngModPower = 48;
    private static final long modMask = (1L << prngModPower) - 1;
    private volatile long random = 0;

    private VMMutex mutex;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrRandom() {
        mutex = new VMMutex("JfrRandom");
    }

    /**
     * This is the formula for RAND48 used in unix systems (linear congruential generator). This is
     * also what JFR in hotspot uses.
     */
    @Uninterruptible(reason = "Locking with no transition.")
    private long nextRandom() {
        // Should be atomic to avoid repeated values
        mutex.lockNoTransition();
        try {
            if (random == 0) {
                random = System.currentTimeMillis();
            }
            long next = (prngMult * random + prngAdd) & modMask;
            random = next;
            assert random > 0;
            return next;
        } finally {
            mutex.unlock();
        }
    }

    public void resetSeed() {
        mutex.lock();
        try {
            random = 0;
        } finally {
            mutex.unlock();
        }
    }

    /** This logic is essentially copied from JfrPRNG in Hotspot. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public double nextUniform() {
        long next = nextRandom();
        // Take the top 26 bits
        long masked = next >> (prngModPower - 26);
        // Normalize between 0 and 1
        return masked / (double) (1L << 26);
    }
}
