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

package com.oracle.svm.webimage.driver;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import com.oracle.svm.driver.NativeImage;

public class WebImage {
    public static void main(String[] args) {
        Deque<String> argList = new ArrayDeque<>(Arrays.asList(args));

        /*
         * --help* and --version flags cannot be used with other arguments. In that case, we just
         * forward the argument list as-is.
         */
        if (argList.stream().noneMatch(s -> s.startsWith("--help") || s.equals("--version"))) {
            argList.addFirst("--tool:web-image");
        }
        NativeImage.main(argList.toArray(new String[0]));
    }
}
