/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graalos;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.lang.ProcessImpl", onlyWith = GraalOSEnabled.class)
final class Target_java_lang_ProcessImpl_GraalOS {
    @Substitute
    private static Target_java_lang_ProcessImpl_LaunchMechanism launchMechanism() {
        String s = System.getProperty("jdk.lang.Process.launchMechanism");
        if (s == null || "VFORK".equals(s)) {
            /*
             * The default is POSIX_SPAWN in the JDK. However, this requires a helper binary
             * (jspawnhelper), which doesn't fit well into the self-contained nature of native
             * images. Thus, Native Image defaults to FORK instead.
             *
             * GraalOS does not implement fork+exec due to its complexity (yet). Technically
             * POSIX_SPAWN would be fine, but this requires said helper binary "jspawnhelper".
             * Instead, use VFORK on GraalOS, which is supported by it.
             *
             * Note that VFORK is deprecated. The purpose of this substitution is primarily to
             * suppress the warnings that the JDK will print when selecting VFORK.
             */
            return Target_java_lang_ProcessImpl_LaunchMechanism.VFORK;
        }

        if ("POSIX_SPAWN".equals(s)) {
            return Target_java_lang_ProcessImpl_LaunchMechanism.POSIX_SPAWN;
        }

        throw new Error(s + " is not a supported process launch mechanism on this platform: GraalOS");
    }

    @TargetClass(className = "java.lang.ProcessImpl$LaunchMechanism", onlyWith = GraalOSEnabled.class)
    static final class Target_java_lang_ProcessImpl_LaunchMechanism {
        @Alias//
        public static Target_java_lang_ProcessImpl_LaunchMechanism VFORK;
        @Alias//
        public static Target_java_lang_ProcessImpl_LaunchMechanism POSIX_SPAWN;
    }
}
