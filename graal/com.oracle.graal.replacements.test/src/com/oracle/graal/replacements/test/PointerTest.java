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
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.ReplacementsImpl.*;
import com.oracle.graal.replacements.Snippet.SnippetInliningPolicy;
import com.oracle.graal.word.*;
import com.oracle.graal.word.nodes.*;

/**
 * Tests for the {@link Pointer} read and write operations.
 */
public class PointerTest extends GraalCompilerTest implements Snippets {

    private static final LocationIdentity ID = new NamedLocationIdentity("ID");
    private static final Kind[] KINDS = new Kind[]{Kind.Byte, Kind.Char, Kind.Short, Kind.Int, Kind.Long, Kind.Float, Kind.Double, Kind.Object};
    private final TargetDescription target;
    private final ReplacementsImpl installer;

    public PointerTest() {
        target = getCodeCache().getTarget();
        installer = new ReplacementsImpl(getProviders(), new Assumptions(false), getTarget());
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

    private void assertRead(StructuredGraph graph, Kind kind, boolean indexConvert, LocationIdentity locationIdentity) {
        WordCastNode cast = (WordCastNode) graph.start().next();

        ReadNode read = (ReadNode) cast.next();
        Assert.assertEquals(kind.getStackKind(), read.kind());

        Assert.assertEquals(cast, read.object());
        Assert.assertEquals(graph.getParameter(0), cast.getInput());
        Assert.assertEquals(target.wordKind, cast.kind());

        IndexedLocationNode location = (IndexedLocationNode) read.location();
        Assert.assertEquals(kind, location.getValueKind());
        Assert.assertEquals(locationIdentity, location.getLocationIdentity());
        Assert.assertEquals(1, location.getIndexScaling());

        if (indexConvert) {
            ConvertNode convert = (ConvertNode) location.getIndex();
            Assert.assertEquals(Kind.Int, convert.getFromKind());
            Assert.assertEquals(Kind.Long, convert.getToKind());
            Assert.assertEquals(graph.getParameter(1), convert.value());
        } else {
            Assert.assertEquals(graph.getParameter(1), location.getIndex());
        }

        ReturnNode ret = (ReturnNode) read.next();
        Assert.assertEquals(read, ret.result());
    }

    private void assertWrite(StructuredGraph graph, Kind kind, boolean indexConvert, LocationIdentity locationIdentity) {
        WordCastNode cast = (WordCastNode) graph.start().next();

        WriteNode write = (WriteNode) cast.next();
        Assert.assertEquals(graph.getParameter(2), write.value());
        Assert.assertEquals(Kind.Void, write.kind());
        Assert.assertEquals(FrameState.AFTER_BCI, write.stateAfter().bci);

        Assert.assertEquals(cast, write.object());
        Assert.assertEquals(graph.getParameter(0), cast.getInput());
        Assert.assertEquals(target.wordKind, cast.kind());

        IndexedLocationNode location = (IndexedLocationNode) write.location();
        Assert.assertEquals(kind, location.getValueKind());
        Assert.assertEquals(locationIdentity, location.getLocationIdentity());
        Assert.assertEquals(1, location.getIndexScaling());

        if (indexConvert) {
            ConvertNode convert = (ConvertNode) location.getIndex();
            Assert.assertEquals(Kind.Int, convert.getFromKind());
            Assert.assertEquals(Kind.Long, convert.getToKind());
            Assert.assertEquals(graph.getParameter(1), convert.value());
        } else {
            Assert.assertEquals(graph.getParameter(1), location.getIndex());
        }

        ReturnNode ret = (ReturnNode) write.next();
        Assert.assertEquals(null, ret.result());
    }

    @Snippet
    public static byte readByte1(Object o, int offset) {
        return Word.fromObject(o).readByte(offset, ID);
    }

    @Snippet
    public static byte readByte2(Object o, int offset) {
        return Word.fromObject(o).readByte(Word.signed(offset), ID);
    }

    @Snippet
    public static byte readByte3(Object o, int offset) {
        return Word.fromObject(o).readByte(offset);
    }

    @Snippet
    public static void writeByte1(Object o, int offset, byte value) {
        Word.fromObject(o).writeByte(offset, value, ID);
    }

    @Snippet
    public static void writeByte2(Object o, int offset, byte value) {
        Word.fromObject(o).writeByte(Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeByte3(Object o, int offset, byte value) {
        Word.fromObject(o).writeByte(offset, value);
    }

    @Snippet
    public static char readChar1(Object o, int offset) {
        return Word.fromObject(o).readChar(offset, ID);
    }

    @Snippet
    public static char readChar2(Object o, int offset) {
        return Word.fromObject(o).readChar(Word.signed(offset), ID);
    }

    @Snippet
    public static char readChar3(Object o, int offset) {
        return Word.fromObject(o).readChar(offset);
    }

    @Snippet
    public static void writeChar1(Object o, int offset, char value) {
        Word.fromObject(o).writeChar(offset, value, ID);
    }

    @Snippet
    public static void writeChar2(Object o, int offset, char value) {
        Word.fromObject(o).writeChar(Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeChar3(Object o, int offset, char value) {
        Word.fromObject(o).writeChar(offset, value);
    }

    @Snippet
    public static short readShort1(Object o, int offset) {
        return Word.fromObject(o).readShort(offset, ID);
    }

    @Snippet
    public static short readShort2(Object o, int offset) {
        return Word.fromObject(o).readShort(Word.signed(offset), ID);
    }

    @Snippet
    public static short readShort3(Object o, int offset) {
        return Word.fromObject(o).readShort(offset);
    }

    @Snippet
    public static void writeShort1(Object o, int offset, short value) {
        Word.fromObject(o).writeShort(offset, value, ID);
    }

    @Snippet
    public static void writeShort2(Object o, int offset, short value) {
        Word.fromObject(o).writeShort(Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeShort3(Object o, int offset, short value) {
        Word.fromObject(o).writeShort(offset, value);
    }

    @Snippet
    public static int readInt1(Object o, int offset) {
        return Word.fromObject(o).readInt(offset, ID);
    }

    @Snippet
    public static int readInt2(Object o, int offset) {
        return Word.fromObject(o).readInt(Word.signed(offset), ID);
    }

    @Snippet
    public static int readInt3(Object o, int offset) {
        return Word.fromObject(o).readInt(offset);
    }

    @Snippet
    public static void writeInt1(Object o, int offset, int value) {
        Word.fromObject(o).writeInt(offset, value, ID);
    }

    @Snippet
    public static void writeInt2(Object o, int offset, int value) {
        Word.fromObject(o).writeInt(Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeInt3(Object o, int offset, int value) {
        Word.fromObject(o).writeInt(offset, value);
    }

    @Snippet
    public static long readLong1(Object o, int offset) {
        return Word.fromObject(o).readLong(offset, ID);
    }

    @Snippet
    public static long readLong2(Object o, int offset) {
        return Word.fromObject(o).readLong(Word.signed(offset), ID);
    }

    @Snippet
    public static long readLong3(Object o, int offset) {
        return Word.fromObject(o).readLong(offset);
    }

    @Snippet
    public static void writeLong1(Object o, int offset, long value) {
        Word.fromObject(o).writeLong(offset, value, ID);
    }

    @Snippet
    public static void writeLong2(Object o, int offset, long value) {
        Word.fromObject(o).writeLong(Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeLong3(Object o, int offset, long value) {
        Word.fromObject(o).writeLong(offset, value);
    }

    @Snippet
    public static float readFloat1(Object o, int offset) {
        return Word.fromObject(o).readFloat(offset, ID);
    }

    @Snippet
    public static float readFloat2(Object o, int offset) {
        return Word.fromObject(o).readFloat(Word.signed(offset), ID);
    }

    @Snippet
    public static float readFloat3(Object o, int offset) {
        return Word.fromObject(o).readFloat(offset);
    }

    @Snippet
    public static void writeFloat1(Object o, int offset, float value) {
        Word.fromObject(o).writeFloat(offset, value, ID);
    }

    @Snippet
    public static void writeFloat2(Object o, int offset, float value) {
        Word.fromObject(o).writeFloat(Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeFloat3(Object o, int offset, float value) {
        Word.fromObject(o).writeFloat(offset, value);
    }

    @Snippet
    public static double readDouble1(Object o, int offset) {
        return Word.fromObject(o).readDouble(offset, ID);
    }

    @Snippet
    public static double readDouble2(Object o, int offset) {
        return Word.fromObject(o).readDouble(Word.signed(offset), ID);
    }

    @Snippet
    public static double readDouble3(Object o, int offset) {
        return Word.fromObject(o).readDouble(offset);
    }

    @Snippet
    public static void writeDouble1(Object o, int offset, double value) {
        Word.fromObject(o).writeDouble(offset, value, ID);
    }

    @Snippet
    public static void writeDouble2(Object o, int offset, double value) {
        Word.fromObject(o).writeDouble(Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeDouble3(Object o, int offset, double value) {
        Word.fromObject(o).writeDouble(offset, value);
    }

    @Snippet
    public static Object readObject1(Object o, int offset) {
        return Word.fromObject(o).readObject(offset, ID);
    }

    @Snippet
    public static Object readObject2(Object o, int offset) {
        return Word.fromObject(o).readObject(Word.signed(offset), ID);
    }

    @Snippet
    public static Object readObject3(Object o, int offset) {
        return Word.fromObject(o).readObject(offset);
    }

    @Snippet
    public static void writeObject1(Object o, int offset, Object value) {
        Word.fromObject(o).writeObject(offset, value, ID);
    }

    @Snippet
    public static void writeObject2(Object o, int offset, Object value) {
        Word.fromObject(o).writeObject(Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeObject3(Object o, int offset, Object value) {
        Word.fromObject(o).writeObject(offset, value);
    }

    private void assertNumWordCasts(String snippetName, int expectedWordCasts) {
        Assumptions assumptions = new Assumptions(true);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, null, OptimisticOptimizations.ALL);

        StructuredGraph graph = parse(snippetName);
        new CanonicalizerPhase(false).apply(graph, context);
        Assert.assertEquals(expectedWordCasts, graph.getNodes().filter(WordCastNode.class).count());
    }

    @Test
    public void testUnusedFromObject() {
        assertNumWordCasts("unusedFromObject", 0);
    }

    @Snippet
    public static void unusedFromObject(Object o) {
        Word.fromObject(o);
    }

    @Test
    public void testUnusedRawValue() {
        assertNumWordCasts("unusedRawValue", 0);
    }

    @Snippet
    public static void unusedRawValue(Object o) {
        Word.fromObject(o).rawValue();
    }

    @Test
    public void testUsedRawValue() {
        assertNumWordCasts("usedRawValue", 1);
    }

    @Snippet
    public static long usedRawValue(Object o) {
        return Word.fromObject(o).rawValue();
    }

    @Test
    public void testUnusedToObject() {
        assertNumWordCasts("unusedToObject", 0);
    }

    @Snippet
    public static void unusedToObject(Word w) {
        w.toObject();
    }

    @Test
    public void testUsedToObject() {
        assertNumWordCasts("usedToObject", 1);
    }

    @Snippet
    public static Object usedToObject(Word w) {
        return w.toObject();
    }
}
