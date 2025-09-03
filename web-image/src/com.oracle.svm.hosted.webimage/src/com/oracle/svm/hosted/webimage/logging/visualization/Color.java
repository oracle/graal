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

@SuppressWarnings("unused")
public class Color {
    public static final Color RESET = new Color("\u001B[0m");
    public static final Color BLACK = new Color("\u001B[30m");
    public static final Color RED = new Color("\u001B[31m");
    public static final Color GREEN = new Color("\u001B[32m");
    public static final Color YELLOW = new Color("\u001B[33m");
    public static final Color BLUE = new Color("\u001B[34m");
    public static final Color PURPLE = new Color("\u001B[35m");
    public static final Color CYAN = new Color("\u001B[36m");
    public static final Color WHITE = new Color("\u001B[37m");
    public static final Color BLACK_BRIGHT = new Color("\033[0;90m");
    public static final Color RED_BRIGHT = new Color("\033[0;91m");
    public static final Color GREEN_BRIGHT = new Color("\033[0;92m");
    public static final Color YELLOW_BRIGHT = new Color("\033[0;93m");
    public static final Color BLUE_BRIGHT = new Color("\033[0;94m");
    public static final Color PURPLE_BRIGHT = new Color("\033[0;95m");
    public static final Color CYAN_BRIGHT = new Color("\033[0;96m");
    public static final Color WHITE_BRIGHT = new Color("\033[0;97m");
    public static final Color BOLD = new Color("\u001B[1m");

    private static final String RGB_PREFIX = "\033[38;2;";
    private static final String RGB_POSTFIX = "m";

    private final String ansiCode;

    private Color(String ansiCode) {
        this.ansiCode = ansiCode;
    }

    public Color(int r, int g, int b) {
        this(RGB_PREFIX + clamp(r) + ";" + clamp(g) + ";" + clamp(b) + RGB_POSTFIX);
    }

    public String ansiCode() {
        return ansiCode;
    }

    private static int clamp(int x) {
        if (x < 0) {
            return 0;
        }
        if (x > 255) {
            return 255;
        }
        return x;
    }

}
