/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.JavaVersion;

@FunctionalInterface
public interface VersionFilter extends LanguageFilter {

    default boolean isValidFor(EspressoLanguage language) {
        return isValidFor(language.getJavaVersion());
    }

    boolean isValidFor(JavaVersion version);

    final class NoFilter implements VersionFilter {
        public static final NoFilter INSTANCE = new NoFilter();

        private NoFilter() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return false;
        }
    }

    final class Java8OrEarlier implements VersionFilter {
        public static final Java8OrEarlier INSTANCE = new Java8OrEarlier();

        private Java8OrEarlier() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java8OrEarlier();
        }
    }

    final class Java9OrLater implements VersionFilter {
        public static final Java9OrLater INSTANCE = new Java9OrLater();

        private Java9OrLater() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java9OrLater();
        }
    }

    final class Java11OrEarlier implements VersionFilter {
        public static final Java11OrEarlier INSTANCE = new Java11OrEarlier();

        private Java11OrEarlier() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java11OrEarlier();
        }
    }

    final class Java11OrLater implements VersionFilter {
        public static final Java11OrLater INSTANCE = new Java11OrLater();

        private Java11OrLater() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java11OrLater();
        }
    }

    final class Java11To21 implements VersionFilter {
        public static final Java11To21 INSTANCE = new Java11To21();

        private Java11To21() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.inRange(11, 21);
        }
    }

    final class Java13OrEarlier implements VersionFilter {
        public static final Java13OrEarlier INSTANCE = new Java13OrEarlier();

        private Java13OrEarlier() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java13OrEarlier();
        }
    }

    final class Java17OrEarlier implements VersionFilter {
        public static final Java17OrEarlier INSTANCE = new Java17OrEarlier();

        private Java17OrEarlier() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java17OrEarlier();
        }
    }

    final class Java18OrEarlier implements VersionFilter {
        public static final Java18OrEarlier INSTANCE = new Java18OrEarlier();

        private Java18OrEarlier() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java18OrEarlier();
        }
    }

    final class Java19OrLater implements VersionFilter {
        public static final Java19OrLater INSTANCE = new Java19OrLater();

        private Java19OrLater() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java19OrLater();
        }
    }

    final class Java20OrLater implements VersionFilter {
        public static final Java20OrLater INSTANCE = new Java20OrLater();

        private Java20OrLater() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java20OrLater();
        }
    }

    final class Java21OrLater implements VersionFilter {
        public static final Java21OrLater INSTANCE = new Java21OrLater();

        private Java21OrLater() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java21OrLater();
        }
    }

    final class Java21 implements VersionFilter {
        public static final Java21 INSTANCE = new Java21();

        private Java21() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.inRange(21, 21);
        }
    }

    final class Java22OrLater implements VersionFilter {
        public static final Java22OrLater INSTANCE = new Java22OrLater();

        private Java22OrLater() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java22OrLater();
        }
    }

    final class Java24OrEarlier implements VersionFilter {
        public static final Java24OrEarlier INSTANCE = new Java24OrEarlier();

        private Java24OrEarlier() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java24OrEarlier();
        }
    }

    final class Java25OrLater implements VersionFilter {
        public static final Java25OrLater INSTANCE = new Java25OrLater();

        private Java25OrLater() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java25OrLater();
        }
    }

    final class Java25OrEarlier implements VersionFilter {
        public static final Java25OrEarlier INSTANCE = new Java25OrEarlier();

        private Java25OrEarlier() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java25OrEarlier();
        }
    }

    final class Java26OrLater implements VersionFilter {
        public static final Java26OrLater INSTANCE = new Java26OrLater();

        private Java26OrLater() {
        }

        @Override
        public boolean isValidFor(JavaVersion version) {
            return version.java26OrLater();
        }
    }
}
