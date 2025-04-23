/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.test;

import jdk.graal.compiler.core.common.util.Util;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link jdk.graal.compiler.core.common.util.Util}.
 */
public class UtilTest {

    @Test
    public void truncateStringTest1() {
        for (int max = 1; max < 1024; max++) {

            String[] inputs = {
                            "X".repeat(max - 1),
                            "X".repeat(max),
                            "X".repeat(max + 1),
            };
            for (String s : inputs) {
                if (max <= Util.TRUNCATION_PLACEHOLDER.length()) {
                    try {
                        Util.truncateString(s, max);
                        throw new AssertionError("expected " + IllegalArgumentException.class.getName());
                    } catch (IllegalArgumentException e) {
                        // expected
                    }
                } else {
                    String cs = Util.truncateString(s, max);
                    if (s.length() <= max) {
                        Assert.assertEquals(s, cs);
                    } else {
                        Assert.assertNotEquals(s, cs);
                        Assert.assertTrue(cs, cs.contains("<truncated"));
                    }
                }
            }
        }
    }

    @Test
    public void truncateStringTest2() {
        int max = 40;

        // Tests example from javadoc
        String s = "123456789_123456789_123456789_123456789_123456789_";
        String cs = Util.truncateString(s, max);
        Assert.assertEquals(50, s.length());
        Assert.assertEquals(32, cs.length());
        Assert.assertEquals("123<truncated(43, c285d1fd)>789_", cs);
    }
}
