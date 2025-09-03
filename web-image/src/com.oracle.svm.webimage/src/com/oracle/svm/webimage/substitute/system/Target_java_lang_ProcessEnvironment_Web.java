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
package com.oracle.svm.webimage.substitute.system;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

/**
 * Mostly used to support {@link System#getenv()}.
 * <p>
 * Currently, we return values that are interpreted as an empty environment.
 */
@TargetClass(className = "java.lang.ProcessEnvironment")
public final class Target_java_lang_ProcessEnvironment_Web {

    @Substitute
    @TargetElement(onlyWith = IsUnix.class)
    private static byte[][] environ() {
        // TODO GR-35288. remove once JNI is fully supported.

        /*
         * The Unix implementation seems to expect an array with 2n entries. For each of the
         * entries, the first encodes the environment variable name and the second the value.
         */
        return new byte[0][0];
    }

    @Substitute
    @TargetElement(onlyWith = IsWindows.class)
    private static String environmentBlock() {
        // TODO GR-35288. remove once JNI is fully supported.

        /*
         * The Windows implementation seems to expect NUL-byte separated KEY=VALUE entries.
         */
        return "";
    }
}
