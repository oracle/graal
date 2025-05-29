/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.hosted.NativeImageOptions;

public interface CPUType {

    String getName();

    String getSpecificFeaturesString();

    CPUType getParent();

    static void printList() {
        String arch = SubstrateUtil.getArchitectureName();
        switch (arch) {
            case "amd64" -> print("AMD64", CPUTypeAMD64.values());
            case "aarch64" -> {
                print("AArch64", CPUTypeAArch64.values());
                CPUTypeAArch64.printFeatureModifiers();
            }
            case "riscv64" -> print("RISCV64", CPUTypeRISCV64.values());
            default -> throw new UnsupportedOperationException("Unsupported platform: " + arch);
        }
    }

    private static void print(String name, CPUType[] values) {
        Arrays.sort(values, Comparator.comparing(CPUType::getName));
        System.out.printf("On %s, the following machine types are available:%n%n", name);
        for (CPUType m : values) {
            String specificFeatures = m.getSpecificFeaturesString();
            String parentText;
            if (m.getParent() != null) {
                parentText = m.getParent() == null ? "" : "all of '" + m.getParent().getName() + "'";
                if (!specificFeatures.isEmpty()) {
                    parentText += " + ";
                }
            } else {
                parentText = "";
            }
            System.out.printf("'%s'%n  CPU features: %s%s%n", m.getName(), parentText, specificFeatures);
        }
        System.out.println();
    }

    static String getSelectedOrDefaultMArch() {
        String userValue = NativeImageOptions.MicroArchitecture.getValue();
        if (userValue != null) {
            return userValue;
        } else if (Platform.includedIn(Platform.AMD64.class)) {
            return CPUTypeAMD64.getDefaultName(false);
        } else if (Platform.includedIn(Platform.AARCH64.class)) {
            return CPUTypeAArch64.getDefaultName(false);
        } else if (Platform.includedIn(Platform.RISCV64.class)) {
            return CPUTypeRISCV64.getDefaultName();
        } else {
            return "unknown";
        }
    }

    static List<String> toNames(CPUType[] values) {
        return Arrays.stream(values).map(CPUType::getName).toList();
    }
}
