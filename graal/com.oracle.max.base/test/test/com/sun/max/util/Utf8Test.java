/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package test.com.sun.max.util;

import junit.framework.*;

import com.sun.max.util.*;

/**
 */
public class Utf8Test extends TestCase {

    public Utf8Test(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(Utf8Test.class);
    }

    private void convertStringToUtf8AndBack(String string) throws Utf8Exception {
        final byte[] utf8 = Utf8.stringToUtf8(string);
        final String result = Utf8.utf8ToString(false, utf8);
        assertEquals(result, string);
    }

    public void test_utf8() throws Utf8Exception {
        convertStringToUtf8AndBack("");
        convertStringToUtf8AndBack(" ");
        convertStringToUtf8AndBack("\n");
        convertStringToUtf8AndBack("abcABC!@#$%^&*()_=/.,;:?><|`~' xyzZXY");
        convertStringToUtf8AndBack("???????????????????????????????");
        convertStringToUtf8AndBack("????p??90=?a");
        for (char ch = Character.MIN_VALUE; ch < Character.MAX_VALUE; ch++) {
            convertStringToUtf8AndBack("abc" + ch + "mno" + ch + ch + "xyz");
        }
    }

}
