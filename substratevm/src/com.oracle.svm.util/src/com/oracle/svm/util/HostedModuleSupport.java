/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HostedModuleSupport {
    public static final String MODULE_SET_ALL_DEFAULT = "ALL-DEFAULT";
    public static final String MODULE_SET_ALL_SYSTEM = "ALL-SYSTEM";
    public static final String MODULE_SET_ALL_MODULE_PATH = "ALL-MODULE-PATH";

    public static final List<String> nonExplicitModules = List.of(MODULE_SET_ALL_DEFAULT, MODULE_SET_ALL_SYSTEM, MODULE_SET_ALL_MODULE_PATH);

    public static final String PROPERTY_IMAGE_EXPLICITLY_ADDED_MODULES = "svm.modulesupport.addedModules";
    public static final String PROPERTY_IMAGE_EXPLICITLY_LIMITED_MODULES = "svm.modulesupport.limitedModules";

    public static final Set<String> SYSTEM_MODULES = Set.of(
                    "com.oracle.graal.graal_enterprise",
                    "com.oracle.svm.svm_enterprise",
                    "jdk.graal.compiler",
                    "org.graalvm.nativeimage.libgraal",
                    "jdk.internal.vm.ci",
                    "org.graalvm.nativeimage",
                    "org.graalvm.nativeimage.base",
                    "org.graalvm.nativeimage.builder",
                    "org.graalvm.nativeimage.guest.staging",
                    "org.graalvm.nativeimage.shared",
                    "org.graalvm.truffle.compiler",
                    "org.graalvm.word");

    public static Set<String> parseModuleSetModifierProperty(String prop) {
        Set<String> specifiedModules = new HashSet<>(); // noEconomicSet(streaming)
        String args = System.getProperty(prop, "");
        if (!args.isEmpty()) {
            specifiedModules.addAll(Arrays.asList(args.split(",")));
        }
        return specifiedModules;
    }

    private HostedModuleSupport() {
    }
}
