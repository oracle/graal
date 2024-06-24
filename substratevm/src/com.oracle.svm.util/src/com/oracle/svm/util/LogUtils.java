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
package com.oracle.svm.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.io.PrintStream;

// Checkstyle: Allow raw info or warning printing - begin
public class LogUtils {
    /**
     * Print an info message.
     */
    public static void info(String message) {
        info("Info", message);
    }

    /**
     * Print an info message with the given prefix.
     */
    public static void info(String prefix, String message) {
        System.out.println(prefix + ": " + message);
    }

    /**
     * Print an info using a formatted message.
     *
     * This method uses {@link String#format} which is currently not safe to be used at run time as
     * it pulls in high amounts of JDK code. This might change in the future, e.g., if parse-once is
     * fully supported (GR-39237). Until then, the format string variant of {@link LogUtils#info}
     * can only be used in hosted-only code.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void info(String format, Object... args) {
        info(format.formatted(args));
    }

    /**
     * Print a warning.
     */
    public static void warning(String message) {
        warning("Warning", message, false);
    }

    /**
     * Print a warning message with the given prefix, optionally to stderr.
     */
    public static void warning(String prefix, String message, boolean stderr) {
        PrintStream out = stderr ? System.err : System.out;
        out.println(prefix + ": " + message);
    }

    /**
     * Print a warning using a formatted message.
     *
     * This method uses {@link String#format} which is currently not safe to be used at run time as
     * it pulls in high amounts of JDK code. This might change in the future, e.g., if parse-once is
     * fully supported (GR-39237). Until then, the format string variant of {@link LogUtils#warning}
     * can only be used in hosted-only code.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void warning(String format, Object... args) {
        warning(format.formatted(args));
    }

    /**
     * Print a warning for a deprecated environment variable.
     */
    public static void warningDeprecatedEnvironmentVariable(String environmentVariableName) {
        warning("The " + environmentVariableName + " environment variable is deprecated and might be removed in a future release. Please refer to the GraalVM release notes.");
    }
}
