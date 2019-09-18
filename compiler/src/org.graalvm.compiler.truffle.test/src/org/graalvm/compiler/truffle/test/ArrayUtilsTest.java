/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.util.ArrayList;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.truffle.compiler.amd64.substitutions.TruffleAMD64InvocationPlugins;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.ArrayUtils;

@RunWith(Parameterized.class)
public class ArrayUtilsTest extends GraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        new TruffleAMD64InvocationPlugins().registerInvocationPlugins(getProviders(), getBackend().getTarget().arch, invocationPlugins, true);
        super.registerInvocationPlugins(invocationPlugins);
    }

    private static final String[] strings = {
                    "L",
                    "Lorem ipsum dolor sit amet, cons0",
                    "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed dia0",
                    "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy " +
                                    "eirmod tempor invidunt ut labore et dolore magna aliquyam" +
                                    " erat, \u0000 sed diam voluptua. At vero \uffff eos et ac" +
                                    "cusam et justo duo dolores 0",
    };
    private static final String[] searchValues = {
                    "L",
                    "0",
                    "t",
                    "X0",
                    "LX",
                    "XYL",
                    "XLYZ",
                    "VXY0",
    };

    @Parameters(name = "{index}: haystack {0} fromIndex {1} maxIndex {2} needle {3}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> parameters = new ArrayList<>();
        for (String str : strings) {
            for (String sv : searchValues) {
                addTests(parameters, str, sv);
            }
        }
        addTests(parameters, searchValues[searchValues.length - 1], "\u0000");
        addTests(parameters, searchValues[searchValues.length - 1], "\uffff");
        String str = strings[1];
        String sv = searchValues[0];
        for (int maxIndex : new int[]{-1, 0, str.length() + 1}) {
            for (int fromIndex : new int[]{-1, 0, str.length(), str.length() + 1}) {
                parameters.add(new Object[]{str, fromIndex, maxIndex, sv});
            }
        }
        return parameters;
    }

    private static void addTests(ArrayList<Object[]> parameters, String str, String sv) {
        for (int maxIndex : new int[]{str.length() - 1, str.length()}) {
            for (int fromIndex : new int[]{0, 15, 16, 17, 31, 32, 33, str.length() - 1, str.length()}) {
                if (fromIndex < maxIndex) {
                    parameters.add(new Object[]{str, fromIndex, maxIndex, sv});
                }
            }
        }
    }

    private final String haystack;
    private final int fromIndex;
    private final int maxIndex;
    private final String needle;

    public ArrayUtilsTest(String haystack, int fromIndex, int maxIndex, String needle) {
        this.haystack = haystack;
        this.fromIndex = fromIndex;
        this.maxIndex = maxIndex;
        this.needle = needle;
    }

    @Test
    public void testString() {
        test("indexOfString", haystack, fromIndex, maxIndex, needle.toCharArray());
    }

    @Test
    public void testCharArray() {
        test("indexOfCharArray", haystack.toCharArray(), fromIndex, maxIndex, needle.toCharArray());
    }

    @Test
    public void testByteArray() {
        test("indexOfByteArray", toByteArray(haystack), fromIndex, maxIndex, toByteArray(needle));
    }

    public static int indexOfString(String haystack, int fromIndex, int maxIndex, char... needle) {
        return ArrayUtils.indexOf(haystack, fromIndex, maxIndex, needle);
    }

    public static int indexOfCharArray(char[] haystack, int fromIndex, int maxIndex, char... needle) {
        return ArrayUtils.indexOf(haystack, fromIndex, maxIndex, needle);
    }

    public static int indexOfByteArray(byte[] haystack, int fromIndex, int maxIndex, byte... needle) {
        return ArrayUtils.indexOf(haystack, fromIndex, maxIndex, needle);
    }

    private static byte[] toByteArray(String s) {
        byte[] ret = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            ret[i] = (byte) s.charAt(i);
        }
        return ret;
    }
}
