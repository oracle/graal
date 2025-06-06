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
package com.oracle.svm.core.jfr;

import java.util.Random;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.jfr.internal.settings.Throttler")
public final class Target_jdk_jfr_internal_settings_Throttler {
    @Alias //
    @InjectAccessors(ThrottlerRandomAccessor.class) //
    private Random randomGenerator;
}

final class ThrottlerRandomAccessor {
    private static Random cachedRandom;

    @SuppressWarnings("unused")
    static Random get(Target_jdk_jfr_internal_settings_Throttler receiver) {
        if (cachedRandom != null) {
            return cachedRandom;
        }
        return initializeRandom();
    }

    private static synchronized Random initializeRandom() {
        if (cachedRandom != null) {
            return cachedRandom;
        }

        cachedRandom = new Random();
        return cachedRandom;
    }

    @SuppressWarnings("unused")
    static synchronized void set(Target_jdk_jfr_internal_settings_Throttler receiver, Random value) {
        throw new RuntimeException("The field jdk.jfr.internal.settings.Throttler.randomGenerator cannot be set");
    }
}
