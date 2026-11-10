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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Recomputes the JDK immutable collection iteration salt from the image build ID to remove a source
 * of nondeterminism in generated code and image data. These substituted fields are treated as final:
 * during ahead-of-time compilation, the compiler can embed {@code SALT32L} in generated code and fold
 * branches that depend on {@code REVERSE}, selecting one traversal direction and eliminating the
 * other. The image uses the same values at run time that the compiler assumed during compilation.
 * Retaining the builder JVM's randomly initialized salt could produce different machine code and
 * embedded constants in otherwise identical builds.
 *
 * This recomputation does not change the salt in the builder JVM. Class initializers executed there
 * can capture a collection's iteration order using the builder's salt, while later iteration in the
 * image uses the image-specific salt. Those captured results are not recomputed by this substitution
 * and can disagree with run-time iteration. They can also retain nondeterminism from the builder, so
 * this substitution alone does not make arbitrary build-time initialization reproducible.
 *
 * The JDK uses a similar approach for reproducible Class Data Sharing (CDS) archives: when running
 * with {@code -Xshare:dump}, {@code ImmutableCollections} obtains a seed derived from the JDK build
 * and version via {@code CDS.getRandomSeedForDumping()}. Outside static archive dumping, that method
 * returns zero and the JDK falls back to {@code System.nanoTime()}. Native Image instead derives its
 * salt from the image build ID in {@link ImmutableCollectionsSupport#getSaltValue()}.
 */
@TargetClass(className = "java.util.ImmutableCollections")
public final class Target_java_util_ImmutableCollections {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true) //
    static long SALT32L = ImmutableCollectionsSupport.getSaltValue();

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)  //
    static boolean REVERSE = (SALT32L & 1) == 0;
}

final class ImmutableCollectionsSupport {
    public static long getSaltValue() {
        /* To generate salt value we are using same approach as in java.util.ImmutableCollections */

        long color = 0x243F_6A88_85A3_08D3L; // slice of pi
        long seed = SubstrateOptions.getImageBuildID().getMostSignificantBits();
        return (int) ((color * seed) >> 16) & 0xFFFF_FFFFL;
    }
}
