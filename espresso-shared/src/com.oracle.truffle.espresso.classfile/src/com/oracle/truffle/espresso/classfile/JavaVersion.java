/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

/**
 * Utility class to provide version checking predicates for clarity in the code base.
 * <p>
 * Makes it harder to access the raw int version: please add new predicates instead.
 */
public final class JavaVersion implements Comparable<JavaVersion> {

    public static final class VersionRange {
        public static final VersionRange VERSION_8_OR_LOWER = lower(8);
        public static final VersionRange VERSION_9_OR_HIGHER = higher(9);
        public static final VersionRange VERSION_16_OR_HIGHER = higher(16);
        public static final VersionRange VERSION_17_OR_HIGHER = higher(17);
        public static final VersionRange VERSION_19_OR_HIGHER = higher(19);
        public static final VersionRange VERSION_20_OR_LOWER = lower(20);
        public static final VersionRange VERSION_21_OR_HIGHER = higher(21);
        public static final VersionRange VERSION_21_OR_LOWER = lower(21);
        public static final VersionRange VERSION_22_OR_HIGHER = higher(22);
        public static final VersionRange VERSION_24_OR_LOWER = lower(24);
        public static final VersionRange VERSION_25_OR_HIGHER = higher(25);
        public static final VersionRange VERSION_26_OR_HIGHER = higher(26);

        public static final VersionRange ALL = between(0, LATEST_SUPPORTED);
        public static final VersionRange VERSION_9_TO_21 = between(9, 21);
        public static final VersionRange VERSION_9_TO_23 = between(9, 23);
        public static final VersionRange VERSION_22_TO_23 = between(22, 23);

        private final int low;
        private final int high;

        public VersionRange(int low, int high) {
            this.low = low;
            this.high = high;
        }

        public static VersionRange lower(int version) {
            return new VersionRange(0, version);
        }

        public static VersionRange higher(int version) {
            return new VersionRange(version, LATEST_SUPPORTED);
        }

        public static VersionRange between(int low, int high) {
            return new VersionRange(low, high);
        }

        public boolean contains(JavaVersion version) {
            return version.inRange(low, high);
        }
    }

    public static final JavaVersion HOST_VERSION = forVersion(Runtime.version());

    public static final int LATEST_SUPPORTED = 26;
    public static final int LATEST_SUPPORTED_CLASSFILE = ClassfileParser.JAVA_26_VERSION;

    private final int version;

    JavaVersion(int version) {
        this.version = version;
    }

    public static JavaVersion forVersion(int version) {
        if (version < 1) {
            throw new IllegalArgumentException("Unsupported java version: " + version);
        }
        return new JavaVersion(version);
    }

    public static JavaVersion forVersion(String version) {
        int begin = 0;
        int end = version.length();
        if (version.startsWith("1.")) {
            begin = 2;
        }
        int firstDot = version.indexOf('.', begin);
        if (firstDot >= 0) {
            end = firstDot;
        }
        String normalizedVersion = version.substring(begin, end);
        try {
            int intVersion = Integer.parseInt(normalizedVersion);
            return forVersion(intVersion);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unsupported java version: " + version + " (" + normalizedVersion + ")");
        }
    }

    public Runtime.Version toRunTimeVersion() {
        return Runtime.Version.parse(toString());
    }

    private static JavaVersion forVersion(Runtime.Version version) {
        return forVersion(version.feature());
    }

    public static JavaVersion latestSupported() {
        return forVersion(LATEST_SUPPORTED);
    }

    public boolean java8OrEarlier() {
        return version <= 8;
    }

    public boolean java9OrLater() {
        return version >= 9;
    }

    public boolean java10OrLater() {
        return version >= 10;
    }

    public boolean java11OrLater() {
        return version >= 11;
    }

    public boolean java11OrEarlier() {
        return version <= 11;
    }

    public boolean java13OrEarlier() {
        return version <= 13;
    }

    public boolean java13OrLater() {
        return version >= 13;
    }

    public boolean java14OrLater() {
        return version >= 14;
    }

    public boolean java15OrLater() {
        return version >= 15;
    }

    public boolean java16OrLater() {
        return version >= 16;
    }

    public boolean java17OrEarlier() {
        return version <= 17;
    }

    public boolean java17OrLater() {
        return version >= 17;
    }

    public boolean java18OrEarlier() {
        return version <= 18;
    }

    public boolean java19OrLater() {
        return version >= 19;
    }

    public boolean java20OrLater() {
        return version >= 20;
    }

    public boolean java20OrEarlier() {
        return version <= 20;
    }

    public boolean java21Or25() {
        return version == 21 || version == 25;
    }

    public boolean java21OrLater() {
        return version >= 21;
    }

    public boolean java21OrEarlier() {
        return version <= 21;
    }

    public boolean java22OrLater() {
        return version >= 22;
    }

    public boolean java23OrEarlier() {
        return version <= 23;
    }

    public boolean java23OrLater() {
        return version >= 23;
    }

    public boolean java24OrEarlier() {
        return version <= 24;
    }

    public boolean java25OrLater() {
        return version >= 25;
    }

    public boolean java25OrEarlier() {
        return version <= 25;
    }

    public boolean java26OrLater() {
        return version >= 26;
    }

    public boolean inRange(int low, int high) {
        return version >= low && version <= high;
    }

    public boolean modulesEnabled() {
        return java9OrLater();
    }

    public boolean varHandlesEnabled() {
        return java9OrLater();
    }

    public boolean compactStringsEnabled() {
        return java9OrLater();
    }

    public int classFileVersion() {
        return version + 44;
    }

    public int featureVersion() {
        return version;
    }

    @Override
    public int compareTo(JavaVersion o) {
        return Integer.compare(this.version, o.version);
    }

    @Override
    public String toString() {
        return Integer.toString(version);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JavaVersion)) {
            return false;
        }
        return this.version == ((JavaVersion) obj).version;
    }

    @Override
    public int hashCode() {
        return version;
    }
}
