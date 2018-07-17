/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.util;

import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.graalvm.compiler.truffle.pelang.array.PELangArrayType;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;

public class PELangSample {

    public static PELangRootNode simpleAdd() {
        PELangBuilder b = new PELangBuilder();
        return b.root("main", b.return$(b.add(b.long$(5L), b.long$(5L))));
    }

    public static PELangRootNode simpleBlock() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(0L)),
                b.incrementLocal("i", b.long$(2L)),
                b.incrementLocal("i", b.long$(2L)),
                b.incrementLocal("i", b.long$(2L)),
                b.incrementLocal("i", b.long$(2L)),
                b.incrementLocal("i", b.long$(2L)),
                b.return$(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleLocalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(10L)),
                b.return$(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeGlobal("i", b.long$(10L)),
                b.return$(b.readGlobal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleBranch() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(0L)),
                b.if$(
                    b.lt(b.readLocal("i"), b.long$(10L)),
                    b.writeLocal("i", b.long$(10L)),
                    b.writeLocal("i", b.long$(5L))),
                b.return$(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleLoop() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("counter", b.long$(0L)),
                b.while$(
                    b.lt(b.readLocal("counter"), b.long$(10L)),
                    b.incrementLocal("counter", b.long$(1L))),
                b.return$(b.readLocal("counter"))));
        // @formatter:on
    }

    public static PELangRootNode simpleSwitch() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("counter", b.long$(0L)),
                b.switch$(
                    b.readLocal("counter"),
                    b.case$(b.long$(0L), b.incrementLocal("counter", b.long$(10L))),
                    b.case$(b.long$(5L), b.incrementLocal("counter", b.long$(5L)))),
                b.return$(b.readLocal("counter"))));
        // @formatter:on
    }

    public static PELangRootNode simpleInvoke() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.return$(b.invoke(
                    b.function(
                        f -> f.header("add", "a", "b"),
                        f -> f.return$(f.add(f.readLocal("a"), f.readLocal("b")))),
                    b.long$(5L),
                    b.long$(5L))));
        // @formatter:on
    }

    public static PELangRootNode simpleObject() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("obj", b.newObject()),
                b.writeProperty(b.readLocal("obj"), "p1", b.long$(10L)),
                b.return$(b.readProperty(b.readLocal("obj"), "p1"))));
        // @formatter:on
    }

    public static PELangRootNode simpleArrayRead() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.return$(
                b.readArray(
                    b.array(new long[] {10L, 5L, 0L}),
                    b.array(new long[] {0L}))));
        // @formatter:on
    }

    public static PELangRootNode simpleMultiArrayRead() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.return$(
                b.readArray(
                    b.array(new long[][] {
                        {6L, 8L, 10L},
                        {4L, 6L, 8L},
                        {2L, 4L, 6L}
                    }),
                    b.array(new long[] {0L, 2L}))));
        // @formatter:on
    }

    public static PELangRootNode simpleArrayWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("var", b.array(new long[] {0L, 5L, 10L})),
                b.writeArray(
                    b.readLocal("var"),
                    b.array(new long[] {0L}),
                    b.long$(10L)),
                b.return$(
                    b.readArray(
                        b.readLocal("var"),
                        b.array(new long[] {0L})))));
        // @formatter:on
    }

    public static PELangRootNode simpleMultiArrayWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("var", b.array(new long[][] {
                                        {6L, 8L, 10L},
                                        {4L, 6L, 8L},
                                        {2L, 4L, 6L}})),
                b.writeArray(
                    b.readLocal("var"),
                    b.array(new long[] {1L, 2L}),
                    b.long$(10L)),
                b.return$(
                    b.readArray(
                        b.readLocal("var"),
                        b.array(new long[] {1L, 2L})))));
        // @formatter:on
    }

    public static PELangRootNode complexStringArray() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("var", b.array(new String[][][] {
                                            {
                                                {"Foo", "Bar"},
                                                {"Aaa", "Bbb"},
                                                {"Ccc", "Ddd"}
                                            },
                                            {
                                                {"Xxxx", "Yyyy"},
                                            },
                                            {
                                                {"ZZ", "AA"},
                                                {"AA", "ZZ"}
                                            }})),
                b.writeArray(
                    b.readLocal("var"),
                    b.array(new long[] {2L, 0L, 1L}),
                    b.string("Foo")),
                b.return$(
                    b.readArray(
                        b.readLocal("var"),
                        b.array(new long[] {2L, 0L, 1L})))));
        // @formatter:on
    }

    public static PELangRootNode invalidBranch() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(0L)),
                b.if$(
                    b.string("foo"),
                    b.writeLocal("i", b.long$(10L)),
                    b.writeLocal("i", b.long$(5L))),
                b.return$(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode invalidLoop() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("counter", b.long$(0L)),
                b.while$(
                    b.string("foo"),
                    b.incrementLocal("counter", b.long$(1L))),
                b.return$(b.readLocal("counter"))));
        // @formatter:on
    }

    public static PELangRootNode nestedAdds() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.return$(
                b.add(
                    b.add(b.long$(2L), b.long$(2L)),
                    b.add(
                        b.long$(2L),
                        b.add(b.long$(2L), b.long$(2L))))));
        // @formatter:on
    }

    public static PELangRootNode nestedBlocks() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(0L)),
                b.incrementLocal("i", b.long$(1L)),
                b.incrementLocal("i", b.long$(1L)),
                b.block(
                    b.incrementLocal("i", b.long$(1L)),
                    b.incrementLocal("i", b.long$(1L)),
                    b.incrementLocal("i", b.long$(1L)),
                    b.incrementLocal("i", b.long$(1L)),
                    b.block(
                        b.incrementLocal("i", b.long$(1L)),
                        b.incrementLocal("i", b.long$(1L))),
                    b.block(
                        b.incrementLocal("i", b.long$(1L)),
                        b.incrementLocal("i", b.long$(1L)))),
                b.block(
                    b.return$(b.readLocal("i")))));
        // @formatter:on
    }

    public static PELangRootNode nestedLocalReadWrites() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("h", b.long$(2L)),
                b.writeLocal("i", b.long$(2L)),
                b.writeLocal("j", b.long$(2L)),
                b.writeLocal("k", b.long$(2L)),
                b.writeLocal("l", b.long$(2L)),
                b.writeLocal("i", b.add(b.readLocal("h"), b.readLocal("i"))),
                b.writeLocal("j", b.add(b.readLocal("i"), b.readLocal("j"))),
                b.writeLocal("k", b.add(b.readLocal("j"), b.readLocal("k"))),
                b.writeLocal("l", b.add(b.readLocal("k"), b.readLocal("l"))),
                b.return$(b.readLocal("l"))));
        // @formatter:on
    }

    public static PELangRootNode nestedBranches() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(0L)),
                b.if$(
                    b.lt(b.readLocal("i"), b.long$(5L)),
                    b.block(
                        b.incrementLocal("i", b.long$(5L)),
                        b.if$(
                            b.lt(b.readLocal("i"), b.long$(10L)),
                            b.incrementLocal("i", b.long$(5L)),
                            b.incrementLocal("i", b.long$(1L)))),
                    b.incrementLocal("i", b.long$(1L))),
                b.return$(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode nestedLoops() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(0L)),
                b.writeLocal("j", b.long$(0L)),
                b.while$(
                    b.lt(b.readLocal("i"), b.long$(5L)),
                    b.block(
                        b.incrementLocal("i", b.long$(1L)),
                        b.while$(
                            b.lt(b.readLocal("j"), b.long$(5L)),
                            b.incrementLocal("j", b.long$(1L))))),
                b.return$(b.add(b.readLocal("i"), b.readLocal("j")))));
        // @formatter:on
    }

    public static PELangRootNode nestedSwitches() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(0L)),
                b.writeLocal("j", b.long$(0L)),
                b.switch$(
                    b.readLocal("i"),
                    b.case$(b.long$(0L), b.switch$(
                                             b.readLocal("j"),
                                             b.case$(b.long$(0L), b.incrementLocal("i", b.long$(10L))))),
                    b.case$(b.long$(5L), b.switch$(
                                             b.readLocal("j"),
                                             b.case$(b.long$(5L), b.incrementLocal("i", b.long$(5L)))))),
                b.return$(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode nestedObjectProperties() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("foo", b.newObject()),
                b.writeProperty(b.readLocal("foo"), "bar", b.newObject()),
                b.writeProperty(b.readProperty(b.readLocal("foo"), "bar"), "value", b.long$(10L)),
                b.return$(b.readProperty(b.readProperty(b.readLocal("foo"), "bar"), "value"))));
        // @formatter:on
    }

    public static PELangRootNode branchWithGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeGlobal("g", b.long$(0L)),
                b.if$(
                    b.lt(b.readGlobal("g"), b.long$(10L)),
                    b.incrementGlobal("g", b.long$(10L)),
                    b.incrementGlobal("g", b.long$(5L))),
                b.return$(b.readGlobal("g"))));
        // @formatter:on
    }

    public static PELangRootNode loopWithGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeGlobal("g", b.long$(0L)),
                b.while$(
                    b.lt(b.readGlobal("g"), b.long$(10L)),
                    b.incrementGlobal("g", b.long$(1L))),
                b.return$(b.readGlobal("g"))));
        // @formatter:on
    }

    public static PELangRootNode nestedLoopsWithMultipleBackEdges() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("i", b.long$(0L)),
                b.writeLocal("j", b.long$(0L)),
                b.writeLocal("k", b.long$(0L)),
                b.while$(
                    b.lt(b.readLocal("i"), b.long$(5L)),
                    b.block(
                        b.incrementLocal("i", b.long$(1L)),
                        b.while$(
                            b.lt(b.readLocal("j"), b.long$(5L)),
                            b.if$(
                                b.lt(b.readLocal("j"), b.long$(3L)),
                                b.block(
                                    b.incrementLocal("j", b.long$(1L)),
                                    b.incrementLocal("k", b.long$(1L))),
                                b.incrementLocal("j", b.long$(1L)))))),
                b.return$(b.add(b.readLocal("i"), b.readLocal("j")))));
        // @formatter:on
    }

    public static PELangRootNode irreducibleLoop() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.dispatch(
                /* 0 */ b.basicBlock(b.writeLocal("i", b.long$(0L)), 1),
                /* 1 */ b.basicBlock(b.writeLocal("j", b.long$(0L)), 2),
                /* 2 */ b.basicBlock(b.eq(b.readLocal("i"), b.long$(0L)), 6, 3),
                /* 3 */ b.basicBlock(b.lt(b.readLocal("j"), b.long$(10L)), 4, 5),
                /* 4 */ b.basicBlock(b.incrementLocal("j", b.long$(1L)), 3),
                /* 5 */ b.basicBlock(b.incrementLocal("i", b.long$(1L)), 7),
                /* 6 */ b.basicBlock(b.incrementLocal("i", b.long$(1L)), 4),
                /* 7 */ b.basicBlock(b.return$(b.readLocal("j")), PELangBasicBlockNode.NO_SUCCESSOR)));
        // @formatter:on
    }

    public static PELangRootNode invokeObjectFunctionProperty() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("obj", b.newObject()),
                b.writeProperty(b.readLocal("obj"), "p1", b.function(
                    f -> f.header("add", "a", "b"),
                    f -> f.return$(f.add(f.readLocal("a"), f.readLocal("b"))))),
                b.return$(
                    b.invoke(
                        b.readProperty(b.readLocal("obj"), "p1"),
                        b.long$(5L),
                        b.long$(5L)))));
        // @formatter:on
    }

    public static PELangRootNode binaryTrees() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeGlobal("bottomUpTree", b.function(
                    f -> f.header("bottomUpTree", "depth"),
                    f -> f.if$(
                        f.lt(f.long$(0L), f.readLocal("depth")),
                        f.return$(f.invoke(
                            f.readGlobal("createNode"),
                            f.invoke(f.readGlobal("bottomUpTree"), f.add(f.readLocal("depth"), f.long$(-1L))),
                            f.invoke(f.readGlobal("bottomUpTree"), f.add(f.readLocal("depth"), f.long$(-1L))))),
                        f.return$(f.invoke(f.readGlobal("createNode"), f.null$(), f.null$()))))),
                b.writeGlobal("createNode", b.function(
                    f -> f.header("createNode", "left", "right"),
                    f -> f.block(
                         f.writeLocal("node", f.newObject()),
                         f.writeProperty(f.readLocal("node"), "left", f.readLocal("left")),
                         f.writeProperty(f.readLocal("node"), "right", f.readLocal("right")),
                         f.return$(f.readLocal("node"))))),
                b.writeGlobal("itemCheck", b.function(
                    f -> f.header("itemCheck", "node"),
                    f -> f.if$(
                        f.eq(f.readProperty(f.readLocal("node"), "left"), f.null$()),
                        f.return$(f.long$(1L)),
                        f.return$(f.add(
                            f.long$(1L),
                            f.invoke(f.readGlobal("itemCheck"), f.readProperty(f.readLocal("node"), "left")),
                            f.invoke(f.readGlobal("itemCheck"), f.readProperty(f.readLocal("node"), "right"))))))),
                b.writeLocal("n", b.readArgument(0)),
                b.writeLocal("MIN_DEPTH", b.long$(4L)),
                b.if$(
                    b.lt(b.readLocal("n"), b.add(b.readLocal("MIN_DEPTH"), b.long$(2L))),
                    b.writeLocal("maxDepth", b.add(b.readLocal("MIN_DEPTH"), b.long$(2L))),
                    b.writeLocal("maxDepth", b.readLocal("n"))),
                b.writeLocal("stretchDepth", b.add(b.readLocal("maxDepth"), b.long$(1L))),
                b.writeLocal("stretchNode", b.invoke(b.readGlobal("bottomUpTree"), b.readLocal("stretchDepth"))),
                b.print(
                    b.add(
                        b.string("stretch tree of depth "),
                        b.readLocal("stretchDepth"),
                        b.string("\t check: "),
                        b.invoke(b.readGlobal("itemCheck"), b.readLocal("stretchNode")))),
                b.writeLocal("longLivedTree", b.invoke(b.readGlobal("bottomUpTree"), b.readLocal("maxDepth"))),
                b.writeLocal("resultsSize", b.add(
                    b.div(
                        b.minus(
                            b.readLocal("maxDepth"),
                            b.readLocal("MIN_DEPTH")),
                        b.long$(2L)),
                    b.long$(1L))),
                b.writeLocal("resultsDimensions", b.array(new long[1])),
                b.writeArray(b.readLocal("resultsDimensions"), b.array(new long[] {0L}), b.readLocal("resultsSize")),
                b.writeLocal("results", b.newArray(PELangArrayType.String, b.readLocal("resultsDimensions"))),
                b.writeLocal("d", b.readLocal("MIN_DEPTH")),
                b.while$(
                    b.not(b.gt(b.readLocal("d"), b.readLocal("maxDepth"))),
                    b.block(
                        b.writeLocal("depth", b.readLocal("d")),
                        b.writeLocal("check", b.long$(0L)),
                        b.writeLocal("iterations", b.leftShift(b.long$(1L),
                            b.add(
                                b.minus(
                                    b.readLocal("maxDepth"),
                                    b.readLocal("depth")),
                                b.readLocal("MIN_DEPTH")))),
                        b.writeLocal("i", b.long$(1L)),
                        b.while$(
                            b.not(b.gt(b.readLocal("i"), b.readLocal("iterations"))),
                            b.block(
                                b.writeLocal("treeNode1", b.invoke(b.readGlobal("bottomUpTree"), b.readLocal("depth"))),
                                b.incrementLocal("check", b.invoke(b.readGlobal("itemCheck"), b.readLocal("treeNode1"))),
                                b.incrementLocal("i", b.long$(1L)))),
                        b.writeLocal("resultsIndex", b.array(new long[1])),
                        b.writeArray(b.readLocal("resultsIndex"), b.array(new long[] {0L}), b.div(b.minus(b.readLocal("depth"), b.readLocal("MIN_DEPTH")), b.long$(2L))),
                        b.writeArray(
                            b.readLocal("results"),
                            b.readLocal("resultsIndex"),
                            b.add(
                                b.readLocal("iterations"),
                                b.string("\t trees of depth "),
                                b.readLocal("depth"),
                                b.string("\t check: "),
                                b.readLocal("check"))),
                        b.incrementLocal("d", b.long$(2L)))),
                b.writeLocal("j", b.long$(0L)),
                b.while$(
                    b.lt(b.readLocal("j"), b.readLocal("resultsSize")),
                    b.block(
                        b.writeArray(b.readLocal("resultsIndex"), b.array(new long[] {0L}), b.readLocal("j")),
                        b.print(b.readArray(b.readLocal("results"), b.readLocal("resultsIndex"))),
                        b.incrementLocal("j", b.long$(1L)))),
                b.print(
                    b.add(
                        b.string("long lived tree of depth "),
                        b.readLocal("maxDepth"),
                        b.string("\t check: "),
                        b.invoke(b.readGlobal("itemCheck"), b.readLocal("longLivedTree"))))));
        // @formatter:on
    }

    public static PELangRootNode arraySum() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("array", b.array(new long[] {1, 2, 3, 4})),
                b.writeLocal("length", b.long$(4L)),
                b.writeLocal("sum", b.long$(0L)),
                b.writeLocal("i", b.long$(0L)),
                b.while$(
                    b.lt(b.readLocal("i"), b.readLocal("length")),
                    b.block(
                        b.incrementLocal("sum", b.readArray(b.readLocal("array"), b.readLocal("i"))),
                        b.incrementLocal("i", b.long$(1L)))),
                b.return$(b.readLocal("sum"))));
        // @formatter:on
    }

    public static PELangRootNode arrayCompare() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("array1", b.array(new long[] {1, 2, 3, 4})),
                b.writeLocal("array2", b.array(new long[] {1, 2, 3, 5})),
                b.writeLocal("length", b.long$(4L)),
                b.writeLocal("i", b.long$(0L)),
                b.while$(
                    b.lt(b.readLocal("i"), b.readLocal("length")),
                    b.block(
                        b.if$(
                            b.not(b.eq(
                                b.readArray(b.readLocal("array1"), b.readLocal("i")),
                                b.readArray(b.readLocal("array2"), b.readLocal("i")))),
                            b.return$(b.long$(1L))),
                        b.incrementLocal("i", b.long$(1L)))),
                b.return$(b.long$(0L))));
        // @formatter:on
    }

    public static PELangRootNode arrayCopy() {
        PELangBuilder b = new PELangBuilder();
        // TODO: implement body

        // @formatter:off
        return b.root(
            "main",
            b.block(
                b.writeLocal("array1", b.array(new long[] {1, 2, 3, 4})),
                b.writeLocal("array2", b.newArray(PELangArrayType.Long, b.long$(4L))),
                b.writeLocal("length", b.long$(4L)),
                b.writeLocal("i", b.long$(0L)),
                b.while$(
                    b.lt(b.readLocal("i"), b.readLocal("length")),
                    b.block(
                        b.if$(
                            b.not(b.eq(
                                b.readArray(b.readLocal("array1"), b.readLocal("i")),
                                b.readArray(b.readLocal("array2"), b.readLocal("i")))),
                            b.return$(b.long$(10L))),
                        b.incrementLocal("i", b.long$(1L)))),
                b.return$(b.long$(0L))));
        // @formatter:on
    }

}
