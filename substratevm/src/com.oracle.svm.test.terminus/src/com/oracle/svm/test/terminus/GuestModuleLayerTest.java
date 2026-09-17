/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.terminus;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature;

/**
 * Validates the SVM core guest-module family in the fully isolated guest boot layer and intentionally
 * fails with a sentinel after successful equality.
 */
public class GuestModuleLayerTest {

    private static final String SENTINEL_MESSAGE = "guest-module-layer-sentinel";
    private static final SortedSet<String> EXPECTED_MODULES = Collections.unmodifiableSortedSet(new TreeSet<>(Set.of(
                    "org.graalvm.nativeimage",
                    "org.graalvm.nativeimage.guest",
                    "org.graalvm.nativeimage.guest.staging",
                    "org.graalvm.nativeimage.libgraal",
                    "org.graalvm.nativeimage.librarysupport",
                    "org.graalvm.nativeimage.shared")));

    public static void main(String[] args) {
    }

    public static final class ModuleLayerTestException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ModuleLayerTestException(String message) {
            super(message);
        }
    }

    /** A public user feature with the same shape as an application-provided feature. */
    public static final class TestFeature implements Feature {

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            SortedSet<String> actualModules = Collections.unmodifiableSortedSet(ModuleLayer.boot().modules().stream()
                            .map(Module::getName)
                            .filter(name -> name.startsWith("org.graalvm.nativeimage"))
                            .collect(Collectors.toCollection(TreeSet::new)));
            if (!EXPECTED_MODULES.equals(actualModules)) {
                throw new AssertionError("Unexpected guest boot module set. Expected:\n" + String.join("\n", EXPECTED_MODULES) + "\nactual:\n" + String.join("\n", actualModules));
            }
            throw new ModuleLayerTestException(SENTINEL_MESSAGE);
        }
    }
}
