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
package com.oracle.svm.hosted.util;

import java.util.Arrays;
import java.util.Comparator;

public interface CPUType {

    String getName();

    String getSpecificFeaturesString();

    CPUType getParent();

    static void print(String name, CPUType[] values) {
        Arrays.sort(values, Comparator.comparing(v -> v.getName()));
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
}
