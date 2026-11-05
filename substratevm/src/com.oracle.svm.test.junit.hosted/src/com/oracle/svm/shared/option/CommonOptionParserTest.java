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
package com.oracle.svm.shared.option;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CommonOptionParserTest {
    @Test
    public void wrapKeepsSameTextWidthForDifferentLineSeparators() {
        String description = "-march=compatibility for best compatibility, or -march=native for best performance if the native executable is deployed on the same machine or on a machine with the same CPU features.";

        String linuxWrapped = CommonOptionParser.wrap(description, 66, "\n");
        String windowsWrapped = CommonOptionParser.wrap(description, 66, "\r\n");

        assertEquals(linuxWrapped, windowsWrapped.replace("\r\n", "\n"));
    }
}
