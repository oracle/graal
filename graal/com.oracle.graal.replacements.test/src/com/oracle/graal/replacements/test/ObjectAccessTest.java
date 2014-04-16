/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.ReplacementsImpl.FrameStateProcessing;
import com.oracle.graal.replacements.Snippet.SnippetInliningPolicy;
import com.oracle.graal.word.*;

/**
 * Tests for the {@link Pointer} read and write operations.
 */
public class ObjectAccessTest extends GraalCompilerTest implements Snippets {

    private static final LocationIdentity ID = new NamedLocationIdentity("ID");
    private static final Kind[] KINDS = new Kind[]{Kind.Byte, Kind.Char, Kind.Short, Kind.Int, Kind.Long, Kind.Float, Kind.Double, Kind.Object};
    private final ReplacementsImpl installer;

    public ObjectAccessTest() {
        installer = new ReplacementsImpl(getProviders(), getSnippetReflection(), new Assumptions(false), getTarget());
    }

    private static final ThreadLocal<SnippetInliningPolicy> inliningPolicy = new ThreadLocal<>();

    @Override
    protected StructuredGraph parse(Method m) {
        ResolvedJavaMethod resolvedMethod = getMetaAccess().lookupJavaMethod(m);
        return installer.makeGraph(resolvedMethod, null, resolvedMethod, inliningPolicy.get(), FrameStateProcessing.CollapseFrameForSingleSideEffect);
    }

    @Test
    public void testRead1() {
        for (Kind kind : KINDS) {
            assertRead(parse("read" + kind.name() + "1"), kind, true, ID);
        }
    }

    @Test
    public void testRead2() {
        for (Kind kind : KINDS) {
            assertRead(parse("read" + kind.name() + "2"), kind, true, ID);
        }
    }

    @Test
    public void testRead3() {
        for (Kind kind : KINDS) {
            assertRead(parse("read" + kind.name() + "3"), kind, true, LocationIdentity.ANY_LOCATION);
        }
    }

    @Test
    public void testWrite1() {
        for (Kind kind : KINDS) {
            assertWrite(parse("write" + kind.name() + "1"), kind, true, ID);
        }
    }

    @Test
    public void testWrite2() {
        for (Kind kind : KINDS) {
            assertWrite(parse("write" + kind.name() + "2"), kind, true, ID);
        }
    }

    @Test
    public void testWrite3() {
        for (Kind kind : KINDS) {
            assertWrite(parse("write" + kind.name() + "3"), kind, true, LocationIdentity.ANY_LOCATION);
        }
    }

    private static void assertRead(StructuredGraph graph, Kind kind, boolean indexConvert, LocationIdentity locationIdentity) {
        JavaReadNode read = (JavaReadNode) graph.start().next();
        Assert.assertEquals(kind.getStackKind(), read.stamp().getStackKind());
        Assert.assertEquals(graph.getParameter(0), read.object());

        IndexedLocationNode location = (IndexedLocationNode) read.location();
        Assert.assertEquals(kind, location.getValueKind());
        Assert.assertEquals(locationIdentity, location.getLocationIdentity());
        Assert.assertEquals(1, location.getIndexScaling());

        if (indexConvert) {
            SignExtendNode convert = (SignExtendNode) location.getIndex();
            Assert.assertEquals(convert.getInputBits(), 32);
            Assert.assertEquals(convert.getResultBits(), 64);
            Assert.assertEquals(graph.getParameter(1), convert.getInput());
        } else {
            Assert.assertEquals(graph.getParameter(1), location.getIndex());
        }

        ReturnNode ret = (ReturnNode) read.next();
        Assert.assertEquals(read, ret.result());
    }

    private static void assertWrite(StructuredGraph graph, Kind kind, boolean indexConvert, LocationIdentity locationIdentity) {
        JavaWriteNode write = (JavaWriteNode) graph.start().next();
        Assert.assertEquals(graph.getParameter(2), write.value());
        Assert.assertEquals(graph.getParameter(0), write.object());
        Assert.assertEquals(FrameState.AFTER_BCI, write.stateAfter().bci);

        IndexedLocationNode location = (IndexedLocationNode) write.location();
        Assert.assertEquals(kind, location.getValueKind());
        Assert.assertEquals(locationIdentity, location.getLocationIdentity());
        Assert.assertEquals(1, location.getIndexScaling());

        if (indexConvert) {
            SignExtendNode convert = (SignExtendNode) location.getIndex();
            Assert.assertEquals(convert.getInputBits(), 32);
            Assert.assertEquals(convert.getResultBits(), 64);
            Assert.assertEquals(graph.getParameter(1), convert.getInput());
        } else {
            Assert.assertEquals(graph.getParameter(1), location.getIndex());
        }

        ReturnNode ret = (ReturnNode) write.next();
        Assert.assertEquals(null, ret.result());
    }

    @Snippet
    public static byte readByte1(Object o, int offset) {
        return ObjectAccess.readByte(o, offset, ID);
    }

    @Snippet
    public static byte readByte2(Object o, int offset) {
        return ObjectAccess.readByte(o, Word.signed(offset), ID);
    }

    @Snippet
    public static byte readByte3(Object o, int offset) {
        return ObjectAccess.readByte(o, offset);
    }

    @Snippet
    public static void writeByte1(Object o, int offset, byte value) {
        ObjectAccess.writeByte(o, offset, value, ID);
    }

    @Snippet
    public static void writeByte2(Object o, int offset, byte value) {
        ObjectAccess.writeByte(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeByte3(Object o, int offset, byte value) {
        ObjectAccess.writeByte(o, offset, value);
    }

    @Snippet
    public static char readChar1(Object o, int offset) {
        return ObjectAccess.readChar(o, offset, ID);
    }

    @Snippet
    public static char readChar2(Object o, int offset) {
        return ObjectAccess.readChar(o, Word.signed(offset), ID);
    }

    @Snippet
    public static char readChar3(Object o, int offset) {
        return ObjectAccess.readChar(o, offset);
    }

    @Snippet
    public static void writeChar1(Object o, int offset, char value) {
        ObjectAccess.writeChar(o, offset, value, ID);
    }

    @Snippet
    public static void writeChar2(Object o, int offset, char value) {
        ObjectAccess.writeChar(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeChar3(Object o, int offset, char value) {
        ObjectAccess.writeChar(o, offset, value);
    }

    @Snippet
    public static short readShort1(Object o, int offset) {
        return ObjectAccess.readShort(o, offset, ID);
    }

    @Snippet
    public static short readShort2(Object o, int offset) {
        return ObjectAccess.readShort(o, Word.signed(offset), ID);
    }

    @Snippet
    public static short readShort3(Object o, int offset) {
        return ObjectAccess.readShort(o, offset);
    }

    @Snippet
    public static void writeShort1(Object o, int offset, short value) {
        ObjectAccess.writeShort(o, offset, value, ID);
    }

    @Snippet
    public static void writeShort2(Object o, int offset, short value) {
        ObjectAccess.writeShort(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeShort3(Object o, int offset, short value) {
        ObjectAccess.writeShort(o, offset, value);
    }

    @Snippet
    public static int readInt1(Object o, int offset) {
        return ObjectAccess.readInt(o, offset, ID);
    }

    @Snippet
    public static int readInt2(Object o, int offset) {
        return ObjectAccess.readInt(o, Word.signed(offset), ID);
    }

    @Snippet
    public static int readInt3(Object o, int offset) {
        return ObjectAccess.readInt(o, offset);
    }

    @Snippet
    public static void writeInt1(Object o, int offset, int value) {
        ObjectAccess.writeInt(o, offset, value, ID);
    }

    @Snippet
    public static void writeInt2(Object o, int offset, int value) {
        ObjectAccess.writeInt(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeInt3(Object o, int offset, int value) {
        ObjectAccess.writeInt(o, offset, value);
    }

    @Snippet
    public static long readLong1(Object o, int offset) {
        return ObjectAccess.readLong(o, offset, ID);
    }

    @Snippet
    public static long readLong2(Object o, int offset) {
        return ObjectAccess.readLong(o, Word.signed(offset), ID);
    }

    @Snippet
    public static long readLong3(Object o, int offset) {
        return ObjectAccess.readLong(o, offset);
    }

    @Snippet
    public static void writeLong1(Object o, int offset, long value) {
        ObjectAccess.writeLong(o, offset, value, ID);
    }

    @Snippet
    public static void writeLong2(Object o, int offset, long value) {
        ObjectAccess.writeLong(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeLong3(Object o, int offset, long value) {
        ObjectAccess.writeLong(o, offset, value);
    }

    @Snippet
    public static float readFloat1(Object o, int offset) {
        return ObjectAccess.readFloat(o, offset, ID);
    }

    @Snippet
    public static float readFloat2(Object o, int offset) {
        return ObjectAccess.readFloat(o, Word.signed(offset), ID);
    }

    @Snippet
    public static float readFloat3(Object o, int offset) {
        return ObjectAccess.readFloat(o, offset);
    }

    @Snippet
    public static void writeFloat1(Object o, int offset, float value) {
        ObjectAccess.writeFloat(o, offset, value, ID);
    }

    @Snippet
    public static void writeFloat2(Object o, int offset, float value) {
        ObjectAccess.writeFloat(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeFloat3(Object o, int offset, float value) {
        ObjectAccess.writeFloat(o, offset, value);
    }

    @Snippet
    public static double readDouble1(Object o, int offset) {
        return ObjectAccess.readDouble(o, offset, ID);
    }

    @Snippet
    public static double readDouble2(Object o, int offset) {
        return ObjectAccess.readDouble(o, Word.signed(offset), ID);
    }

    @Snippet
    public static double readDouble3(Object o, int offset) {
        return ObjectAccess.readDouble(o, offset);
    }

    @Snippet
    public static void writeDouble1(Object o, int offset, double value) {
        ObjectAccess.writeDouble(o, offset, value, ID);
    }

    @Snippet
    public static void writeDouble2(Object o, int offset, double value) {
        ObjectAccess.writeDouble(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeDouble3(Object o, int offset, double value) {
        ObjectAccess.writeDouble(o, offset, value);
    }

    @Snippet
    public static Object readObject1(Object o, int offset) {
        return ObjectAccess.readObject(o, offset, ID);
    }

    @Snippet
    public static Object readObject2(Object o, int offset) {
        return ObjectAccess.readObject(o, Word.signed(offset), ID);
    }

    @Snippet
    public static Object readObject3(Object o, int offset) {
        return ObjectAccess.readObject(o, offset);
    }

    @Snippet
    public static void writeObject1(Object o, int offset, Object value) {
        ObjectAccess.writeObject(o, offset, value, ID);
    }

    @Snippet
    public static void writeObject2(Object o, int offset, Object value) {
        ObjectAccess.writeObject(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeObject3(Object o, int offset, Object value) {
        ObjectAccess.writeObject(o, offset, value);
    }
}
