/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.time.ZoneOffset;
import java.time.zone.ZoneRules;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(ZoneOffset.class)
public final class Target_java_time_ZoneOffset {
    /**
     * This field is marked as @Stable, therefore we only constant fold it if it has a non-null
     * value at the time the constant folding decision is being made.
     * <p/>
     * The value of this field on the image heap constant {@link ZoneOffset#UTC} gets lazily
     * initialized at build time by calling the {@link ZoneOffset#getRules()} method. The same
     * method is also analyzed when simulating class initializers, which can lead to
     * non-deterministic analysis results: The success of the simulation depends on whether the
     * method was already executed at build time. To achieve determinism, we mark this field with
     * RecomputeFieldValue, thus preventing the constant folding during class initializer
     * simulation.
     */
    @SuppressWarnings("unused") //
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private ZoneRules rules;

}
