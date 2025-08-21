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

package com.oracle.svm.hosted;

import java.net.URL;
import java.security.CodeSource;

final class ProgressReporterUtils {

    private static final double MILLIS_TO_SECONDS = 1000d;
    private static final double NANOS_TO_SECONDS = 1000d * 1000d * 1000d;
    public static final String TRUNCATION_PLACEHOLDER = "~";

    static double millisToSeconds(double millis) {
        return millis / MILLIS_TO_SECONDS;
    }

    static double nanosToSeconds(double nanos) {
        return nanos / NANOS_TO_SECONDS;
    }

    static String getUsedMemory() {
        return ByteFormattingUtil.bytesToHumanGB(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    static String stringFilledWith(int size, String fill) {
        return fill.repeat(size);
    }

    static double toPercentage(long part, long total) {
        return part / (double) total * 100;
    }

    static String truncateFQN(String fqn, double maxLineRatio) {
        return truncateFQN(fqn, maxLength(maxLineRatio));
    }

    static int maxLength(double maxLineRatio) {
        return (int) Math.floor(ProgressReporter.CHARACTERS_PER_LINE * maxLineRatio);
    }

    static String truncateFQN(String fqn, int maxLength) {
        int classNameLength = fqn.length();
        if (classNameLength <= maxLength) {
            return fqn;
        }
        StringBuilder sb = new StringBuilder();
        int currentDot = -1;
        while (true) {
            int nextDot = fqn.indexOf('.', currentDot + 1);
            if (nextDot < 0) { // Not more dots, handle the rest and return.
                String rest = fqn.substring(currentDot + 1);
                int sbLength = sb.length();
                int restLength = rest.length();
                if (sbLength + restLength <= maxLength) {
                    sb.append(rest);
                } else {
                    int remainingSpaceDivBy2 = (maxLength - sbLength) / 2;
                    sb.append(rest, 0, remainingSpaceDivBy2 - 1).append(TRUNCATION_PLACEHOLDER).append(rest, restLength - remainingSpaceDivBy2, restLength);
                }
                break;
            }
            sb.append(fqn.charAt(currentDot + 1)).append('.');
            if (sb.length() + (classNameLength - nextDot) <= maxLength) {
                // Rest fits maxLength, append and return.
                sb.append(fqn.substring(nextDot + 1));
                break;
            }
            currentDot = nextDot;
        }
        return sb.toString();
    }

    static String moduleNamePrefix(Module javaModule) {
        if (!javaModule.isNamed()) {
            return "";
        }
        String moduleName = javaModule.getName();
        return truncateFQN(mapToNativeImageRuntime(moduleName), 0.12) + "/";
    }

    private static String mapToNativeImageRuntime(String moduleName) {
        String modulePrefix = "org.graalvm.nativeimage.";
        if (moduleName.equals(modulePrefix + "builder")) {
            return modulePrefix + "runtime";
        }
        return moduleName;
    }

    record BreakDownClassifier(Package javaPackage, Module javaModule, String location) {
        static BreakDownClassifier of(Class<?> clazz) {
            return new BreakDownClassifier(clazz.getPackage(), clazz.getModule(), sourcePath(clazz));
        }

        private static String sourcePath(Class<?> clazz) {
            CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                URL sourceLocation = codeSource.getLocation();
                if (sourceLocation != null && !"jrt".equals(sourceLocation.getProtocol())) {
                    return sourceLocation.getPath();
                }
            }
            return null;
        }

        public String renderToString(int maxLength) {
            String packageName = javaPackage == null ? "null" : javaPackage.getName();
            String moduleNamePrefix = moduleNamePrefix(javaModule);
            // Give remainder of space to package-part
            int maxLengthPackage = maxLength - moduleNamePrefix.length();
            return moduleNamePrefix + truncateFQN(packageName, maxLengthPackage);
        }

        public String[] elements() {
            String moduleName = javaModule.isNamed() ? javaModule.getName() : "";
            String packageName = javaPackage == null ? "" : javaPackage.getName();
            String locationName = location == null ? "" : location;
            return new String[]{mapToNativeImageRuntime(moduleName), packageName, locationName};
        }
    }
}
