/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk;

import java.util.concurrent.atomic.AtomicLong;

/**
 * RandomAccessors initializes a seeder at run time, on first access. The mechanism is used by both
 * SplittableRandomAccessors and ThreadLocalRandomAccessors since they share the same seeder
 * initialization logic, but use a different implementation of mix64().
 */
public abstract class RandomAccessors {

    /*
     * We read the value of java.util.secureRandomSeed deliberately during image generation, so that
     * the SecureRandom code is only reachable and included in the image when requested by the
     * application.
     */
    private static final boolean SECURE_SEED = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<Boolean>() {
                        @Override
                        public Boolean run() {
                            return Boolean.getBoolean("java.util.secureRandomSeed");
                        }
                    });

    protected volatile AtomicLong seeder;

    protected AtomicLong getOrInitializeSeeder() {
        AtomicLong result = seeder;
        if (result == null) {
            result = initialize();
        }
        return result;
    }

    // Checkstyle: allow synchronization
    /**
     * It is important that this synchronization is on an instance method and not on a static
     * method. A static synchronized method will lock the java.lang.Class object and SVM currently
     * uses a secondary storage map for locking on classes. Syncronizing on a class object can lead
     * to recursive locking problems when this particular code is called from the constructor of
     * JavaVMOperation.
     */
    private synchronized AtomicLong initialize() {
        AtomicLong result = seeder;
        if (result != null) {
            return result;
        }

        /*
         * The code below to compute the seed is taken from the original
         * SplittableRandom.initialSeed()/ThreadLocalRandom.initialSeed() implementation.
         */
        long seed;
        if (SECURE_SEED) {
            byte[] seedBytes = java.security.SecureRandom.getSeed(8);
            seed = seedBytes[0] & 0xffL;
            for (int i = 1; i < 8; ++i) {
                seed = (seed << 8) | (seedBytes[i] & 0xffL);
            }
        } else {
            seed = mix64(System.currentTimeMillis()) ^ mix64(System.nanoTime());
        }

        result = new AtomicLong(seed);
        seeder = result;
        return result;

    }
    // Checkstyle: disallow synchronization

    abstract long mix64(long l);
}
