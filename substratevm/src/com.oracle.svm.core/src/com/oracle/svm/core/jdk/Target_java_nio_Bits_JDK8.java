/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.nio.Bits", onlyWith = JDK8OrEarlier.class)
final class Target_java_nio_Bits_JDK8 {

    /*
     * The original native method implementation calls back into the Java HotSpot VM, via the
     * function JVM_CopySwapMemory. So this substitution is necessary even when we use the JDK
     * native code. Our implementation is also OS and architecture independent - so we can have this
     * substitution without a @Platforms annotation.
     */
    @Substitute
    private static void copySwapMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize) {
        MemoryUtil.copyConjointSwap(
                        Word.objectToUntrackedPointer(srcBase).add(WordFactory.unsigned(srcOffset)),
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes), WordFactory.unsigned(elemSize));
    }

    /* Ensure lazy initialization of page size happens at image run time. */
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static int pageSize = -1;

    /* Ensure lazy initialization of maximum direct memory size happens at image run time. */
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static boolean memoryLimitSet = false;
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static long maxMemory = -1;

    /* Do not inherit any memory statistics from the image generator. */
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static AtomicLong reservedMemory = new AtomicLong();
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static AtomicLong totalCapacity = new AtomicLong();
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static AtomicLong count = new AtomicLong();
}

/* Fields changed names between JDK 8 and 11. */
@TargetClass(className = "java.nio.Bits", onlyWith = JDK11OrLater.class)
final class Target_java_nio_Bits_JDK11 {

    // Checkstyle: stop

    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static int PAGE_SIZE = -1;

    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static boolean MEMORY_LIMIT_SET = false;
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static long MAX_MEMORY = -1;

    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static AtomicLong RESERVED_MEMORY = new AtomicLong();
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static AtomicLong TOTAL_CAPACITY = new AtomicLong();
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static AtomicLong COUNT = new AtomicLong();

    // Checkstyle: resume
}
