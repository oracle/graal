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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(java.util.SplittableRandom.class)
final class Target_java_util_SplittableRandom {

    @Alias @InjectAccessors(SplittableRandomAccessors.class)//
    private static AtomicLong defaultGen;

    @Alias
    static native long mix64(long z);
}

public class SplittableRandomAccessors {

    /*
     * We run this code deliberately during image generation, so that the SecureRandom code is only
     * reachable and included in the image when requested by the application.
     */
    private static final boolean SECURE_SEED = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<Boolean>() {
                        @Override
                        public Boolean run() {
                            return Boolean.getBoolean("java.util.secureRandomSeed");
                        }
                    });

    private static volatile AtomicLong defaultGen;

    /** The get-accessor for SplittableRandom.defaultGen. */
    public static AtomicLong getDefaultGen() {
        AtomicLong result = defaultGen;
        if (result == null) {
            result = initialize();
        }
        return result;
    }

    private static class Lock {
    }

    private static final Lock lock = new Lock();

    // Checkstyle: allow synchronization
    private static AtomicLong initialize() {
        /**
         * Lock on an instance instead of a java.lang.Class object because SVM currently uses a
         * secondary storage map for locking on classes, which in this particular case can lead to
         * recursive locking problems when this code is called from the constructor of
         * JavaVMOperation.
         */
        synchronized (lock) {
            AtomicLong result = defaultGen;
            if (result != null) {
                return result;
            }

            /*
             * The code below to compute the seed is taken from the original
             * SplittableRandom.initialSeed() implementation.
             */
            long seed;
            if (SECURE_SEED) {
                byte[] seedBytes = java.security.SecureRandom.getSeed(8);
                seed = seedBytes[0] & 0xffL;
                for (int i = 1; i < 8; ++i) {
                    seed = (seed << 8) | (seedBytes[i] & 0xffL);
                }
            } else {
                seed = Target_java_util_SplittableRandom.mix64(System.currentTimeMillis()) ^ Target_java_util_SplittableRandom.mix64(System.nanoTime());
            }

            result = new AtomicLong(seed);
            defaultGen = result;
            return result;
        }
    }
    // Checkstyle: disallow synchronization
}
