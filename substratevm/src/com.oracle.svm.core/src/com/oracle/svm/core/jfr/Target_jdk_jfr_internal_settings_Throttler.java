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
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.graal.compiler.nodes.extended.MembarNode;

@TargetClass(className = "jdk.jfr.internal.settings.Throttler")
public final class Target_jdk_jfr_internal_settings_Throttler {
    @Alias //
    @InjectAccessors(ThrottlerRandomAccessor.class) //
    private Random randomGenerator;

    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Random injectedRandomGenerator;
}

final class ThrottlerRandomAccessor {
    @SuppressWarnings("unused")
    static Random get(Target_jdk_jfr_internal_settings_Throttler receiver) {
        Random value = receiver.injectedRandomGenerator;
        if (value != null) {
            return value;
        }
        return initializeRandom(receiver);
    }

    private static synchronized Random initializeRandom(Target_jdk_jfr_internal_settings_Throttler receiver) {
        Random value = receiver.injectedRandomGenerator;
        if (value != null) {
            return value;
        }

        value = new Random();
        /* Ensure that other threads see a fully initialized Random object once published below. */
        MembarNode.memoryBarrier(MembarNode.FenceKind.STORE_STORE);

        receiver.injectedRandomGenerator = value;
        return value;
    }

    @SuppressWarnings("unused")
    static synchronized void set(Target_jdk_jfr_internal_settings_Throttler receiver, Random value) {
        receiver.injectedRandomGenerator = value;
    }
}
