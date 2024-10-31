/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK21OrEarlier;
import com.oracle.svm.core.jdk.JDKLatest;

@TargetClass(value = jdk.jfr.internal.StringPool.class, onlyWith = HasJfrSupport.class)
final class Target_jdk_jfr_internal_StringPool {
    @Substitute
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    public static long addString(@SuppressWarnings("unused") String s) {
        // This disables String caching and forces the EventWriter to write strings by value.
        return -1;
    }

    @SuppressWarnings("unused")
    @Substitute
    @TargetElement(onlyWith = JDKLatest.class)
    public static long addString(String s, boolean pinVirtualThread) {
        // This disables String caching and forces the EventWriter to write strings by value.
        return -1;
    }
}
