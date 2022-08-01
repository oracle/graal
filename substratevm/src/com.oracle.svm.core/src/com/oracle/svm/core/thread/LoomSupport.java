/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import static com.oracle.svm.core.SubstrateOptions.UseEpsilonGC;
import static com.oracle.svm.core.SubstrateOptions.UseSerialGC;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

public final class LoomSupport {
    private static final boolean isEnabled;
    static {
        boolean enabled = false;
        if (JavaVersionUtil.JAVA_SPEC == 19 && (UseSerialGC.getValue() || UseEpsilonGC.getValue())) {
            try {
                enabled = (Boolean) Class.forName("jdk.internal.misc.PreviewFeatures")
                                .getDeclaredMethod("isEnabled").invoke(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        isEnabled = enabled;
    }

    @Fold
    public static boolean isEnabled() {
        return isEnabled;
    }

    // See JDK native enum freeze_result
    static final int FREEZE_OK = 0;
    static final int FREEZE_PINNED_CS = 2; // critical section
    static final int FREEZE_PINNED_NATIVE = 3;

    public static Continuation getInternalContinuation(Target_jdk_internal_vm_Continuation cont) {
        return cont.internal;
    }

    private LoomSupport() {
    }
}
