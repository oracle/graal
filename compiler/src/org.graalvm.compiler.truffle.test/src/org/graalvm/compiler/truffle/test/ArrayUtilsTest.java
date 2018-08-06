/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.ArrayUtils;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.truffle.compiler.substitutions.amd64.TruffleAMD64InvocationPlugins;
import org.junit.Test;

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
                    " ",
                    "\u0000",
                    "\uffff",
                    "X",
                    "ip",
                    "X0",
                    "LX",
                    "LXY",
                    "LXYZ",
                    "VXYZ",
                    "VXY0",
    };

    @Test
    public void testIndexOf() {
        for (String str : strings) {
            for (String sv : searchValues) {
                char[] needle = sv.toCharArray();
                for (int maxIndex : new int[]{-1, 0, str.length() - 1, str.length(), str.length() + 1}) {
                    for (int fromIndex : new int[]{-1, 0, 1, 15, 16, 17, 31, 32, 33, str.length() - 1, str.length(), str.length() + 1}) {
                        doTestIndexOf(str, fromIndex, maxIndex, needle);
                    }
                }
            }
        }
    }

    private void doTestIndexOf(String haystack, int fromIndex, int maxIndex, char... needle) {
        test("indexOfString", haystack, fromIndex, maxIndex, needle);
        test("indexOfCharArray", haystack.toCharArray(), fromIndex, maxIndex, needle);
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

    private static byte[] toByteArray(char[] arr) {
        byte[] ret = new byte[arr.length];
        for (int i = 0; i < arr.length; i++) {
            ret[i] = (byte) arr[i];
        }
        return ret;
    }

    private static byte[] toByteArray(String s) {
        byte[] ret = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            ret[i] = (byte) s.charAt(i);
        }
        return ret;
    }
}
