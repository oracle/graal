/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public class VarArgsTest extends ProxyLanguageEnvTest {

    @Test
    public void testStringJoin1() throws InteropException {
        TruffleObject strClass = asTruffleHostSymbol(String.class);

        TruffleObject join = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), strClass, "join");
        TruffleObject delimiter = asTruffleObject(" ");
        TruffleObject elements = asTruffleObject(new String[]{"Hello", "World"});
        Object result = ForeignAccess.sendExecute(Message.EXECUTE.createNode(), join, new Object[]{delimiter, elements});
        Assert.assertEquals("Hello World", result);
    }

    @Test
    public void testStringJoin2() throws InteropException {
        TruffleObject strClass = asTruffleHostSymbol(String.class);

        TruffleObject join = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), strClass, "join");
        TruffleObject delimiter = asTruffleObject(" ");
        TruffleObject element1 = asTruffleObject("Hello");
        TruffleObject element2 = asTruffleObject("World");
        Object result = ForeignAccess.sendExecute(Message.EXECUTE.createNode(), join, new Object[]{delimiter, element1, element2});
        Assert.assertEquals("Hello World", result);
    }

    @Test
    public void testStringEllipsis() throws InteropException {
        TruffleObject mainClass = asTruffleHostSymbol(Join.class);

        TruffleObject ellipsis = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), mainClass, "stringEllipsis");
        TruffleObject element1 = asTruffleObject("Hello");
        TruffleObject element2 = asTruffleObject("World");
        Object result = ForeignAccess.sendExecute(Message.EXECUTE.createNode(), ellipsis, new Object[]{element1, element2});
        Assert.assertEquals("Hello World", result);

        TruffleObject elements = asTruffleObject(new String[]{"Hello", "World"});
        result = ForeignAccess.sendExecute(Message.EXECUTE.createNode(), ellipsis, elements);
        Assert.assertEquals("Hello World", result);
    }

    @Test
    public void testCharSequenceEllipsis() throws InteropException {
        TruffleObject mainClass = asTruffleHostSymbol(Join.class);

        TruffleObject ellipsis = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), mainClass, "charSequenceEllipsis");
        TruffleObject element1 = asTruffleObject("Hello");
        TruffleObject element2 = asTruffleObject("World");
        Object result = ForeignAccess.sendExecute(Message.EXECUTE.createNode(), ellipsis, new Object[]{element1, element2});
        Assert.assertEquals("Hello World", result);

        TruffleObject elements = asTruffleObject(new String[]{"Hello", "World"});
        result = ForeignAccess.sendExecute(Message.EXECUTE.createNode(), ellipsis, elements);
        Assert.assertEquals("Hello World", result);
    }

    @Test
    public void testPathsGet() throws InteropException {
        Node n = Message.INVOKE.createNode();
        TruffleObject paths = asTruffleHostSymbol(Paths.class);
        TruffleObject result;
        result = (TruffleObject) ForeignAccess.sendInvoke(n, paths, "get", "dir");
        assertEquals("dir", asJavaObject(Path.class, result).toString());
        result = (TruffleObject) ForeignAccess.sendInvoke(n, paths, "get", "dir1", "dir2");
        assertEquals("dir1/dir2", asJavaObject(Path.class, result).toString());
        result = (TruffleObject) ForeignAccess.sendInvoke(n, paths, "get", "dir1", "dir2", "dir3");
        assertEquals("dir1/dir2/dir3", asJavaObject(Path.class, result).toString());
        result = (TruffleObject) ForeignAccess.sendInvoke(n, paths, "get", "dir1", asTruffleObject(new String[]{"dir2", "dir3"}));
        assertEquals("dir1/dir2/dir3", asJavaObject(Path.class, result).toString());
    }

    @Test
    public void testOverloadedVarArgsPrimitive() throws InteropException {
        Node n = Message.INVOKE.createNode();
        TruffleObject paths = asTruffleHostSymbol(Sum.class);
        Object result;
        result = ForeignAccess.sendInvoke(n, paths, "sum", 10);
        assertEquals("I", result);
        result = ForeignAccess.sendInvoke(n, paths, "sum", 10, 20);
        assertEquals("DD", result);
        result = ForeignAccess.sendInvoke(n, paths, "sum", 10, 20, 30);
        assertEquals("I[I", result);
    }

    public static class Join {
        public static String stringEllipsis(String... args) {
            return String.join(" ", args);
        }

        public static String charSequenceEllipsis(CharSequence... args) {
            return String.join(" ", args);
        }
    }

    @SuppressWarnings("unused")
    public static class Sum {
        public static String sum(int first) {
            return "I";
        }

        public static String sum(int first, int... more) {
            return "I[I";
        }

        public static String sum(double first) {
            return "D";
        }

        public static String sum(double first, double second) {
            return "DD";
        }

        public static String sum(double first, double... more) {
            return "D[D";
        }
    }
}
