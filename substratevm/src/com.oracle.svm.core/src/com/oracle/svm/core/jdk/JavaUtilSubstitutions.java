/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.Currency;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(java.util.HashMap.class)
final class Target_java_util_HashMap {

    @Substitute
    private static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            /*
             * We cannot do all the generic interface checks that the original implementation is
             * doing, because we do not have the necessary metadata at run time.
             */
            return x.getClass();
        }
        return null;
    }
}

@TargetClass(java.util.SplittableRandom.class)
final class Target_java_util_SplittableRandom {

    @Alias @TargetElement(name = "GOLDEN_GAMMA") private static long GOLDENGAMMA;
    @Alias private long seed;
    @Alias private long gamma;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static volatile AtomicLong defaultGen;

    @Substitute
    protected Target_java_util_SplittableRandom() {
        if (defaultGen == null) {
            // This is the original expression used for the initialization
            // of defaultGen but it is invoked in a lazy way as initialSeed()
            // is derived from the current time (the seed cannot be fixed
            // in the image because the "random" values would be determined then).
            defaultGen = new AtomicLong(initialSeed());
        }

        // The original code of SplittableRandom() constructor
        long s = defaultGen.getAndAdd(2 * GOLDENGAMMA);
        this.seed = mix64(s);
        this.gamma = mixGamma(s + GOLDENGAMMA);
    }

    @Alias
    private static native long initialSeed();

    @Alias
    private static native long mix64(long z);

    @Alias
    private static native long mixGamma(long z);

}

@TargetClass(java.util.Currency.class)
final class Target_java_util_Currency {
    @Alias//
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    private static ConcurrentMap<String, Currency> instances = new ConcurrentHashMap<>();
}

/** Dummy class to have a class with the file's name. */
public final class JavaUtilSubstitutions {
}
