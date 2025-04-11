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

package com.oracle.svm.hosted.webimage.logging.visualization;

public class HeaderWidget implements Widget {
    private static final int MIN_WIDTH = 8;

    private final String title;

    public HeaderWidget(String title) {
        this.title = title;
    }

    @Override
    public void visualize(DrawKit kit) {
        if (kit.width() < MIN_WIDTH) {
            return;
        }

        int titleWidth = Math.min(kit.width() - 4, title.length());

        kit.println();

        // Top border.
        kit.print("\u256d\u2500");
        for (int i = 0; i < titleWidth; i++) {
            kit.print("\u2500");
        }
        kit.print("\u2500\u256e");
        kit.println();

        // Side borders and title.
        kit.println("\u2502 " + title.substring(0, titleWidth) + " \u2502");

        // Bottom border.
        kit.print("\u2570\u2500");
        for (int i = 0; i < titleWidth; i++) {
            kit.print("\u2500");
        }
        kit.print("\u2500\u256f");
        kit.println();

        kit.println();
    }
}
