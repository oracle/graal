/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This substitution is necessary in order to make native images deterministic. We substitute Java
 * safety feature, which based on SALT32L value randomize iteration through a map and set data
 * structures to make sure no implementation relies on iteration order.
 *
 * Depending on salt value constant folding will pick a different branch and eliminate the other so
 * the code can look slightly different. Since anyway the SALT32L value is the same for one native
 * image this will just help to make native images fully deterministic.
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
