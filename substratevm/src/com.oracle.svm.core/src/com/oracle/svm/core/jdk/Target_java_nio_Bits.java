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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.nio.Bits")
final class Target_java_nio_Bits {

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
