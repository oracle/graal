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
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;

public class PELangSample {

    public static PELangRootNode simpleAdd() {
        PELangBuilder b = new PELangBuilder();
        return b.root(b.return_(b.add(b.lit(5L), b.lit(5L))));
    }

    public static PELangRootNode simpleBlock() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "i"),
                b.incrementLocal(b.lit(2L), "i"),
                b.incrementLocal(b.lit(2L), "i"),
                b.incrementLocal(b.lit(2L), "i"),
                b.incrementLocal(b.lit(2L), "i"),
                b.incrementLocal(b.lit(2L), "i"),
                b.return_(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleLocalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(10L), "i"),
                b.return_(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeGlobal(b.lit(10L), "i"),
                b.return_(b.readGlobal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleBranch() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "i"),
                b.if_(
                    b.lt(b.readLocal("i"), b.lit(10L)),
                    b.writeLocal(b.lit(10L), "i"),
                    b.writeLocal(b.lit(5L), "i")),
                b.return_(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleLoop() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "counter"),
                b.while_(
                    b.lt(b.readLocal("counter"), b.lit(10L)),
                    b.incrementLocal(b.lit(1L), "counter")),
                b.return_(b.readLocal("counter"))));
        // @formatter:on
    }

    public static PELangRootNode simpleSwitch() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "counter"),
                b.switch_(
                    b.readLocal("counter"),
                    b.case_(
                        b.lit(0L),
                        b.incrementLocal(b.lit(10L), "counter")),
                    b.case_(
                        b.lit(5L),
                        b.incrementLocal(b.lit(5L), "counter"))),
                b.return_(b.readLocal("counter"))));
        // @formatter:on
    }

    public static PELangRootNode simpleInvoke() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.return_(
                b.invoke(
                    b.lit(
                        b.fn(
                            f -> f.header("a", "b"),
                            f -> f.return_(f.add(f.readLocal("a"), f.readLocal("b"))))),
                    b.lit(5L),
                    b.lit(5L))));
        // @formatter:on
    }

    public static PELangRootNode simpleObject() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(
                    b.lit(b.object()),
                    "obj"),
                b.writeProperty(
                    b.readLocal("obj"),
                    b.lit("p1"),
                    b.lit(10L)),
                b.return_(
                    b.readProperty(
                        b.readLocal("obj"),
                        b.lit("p1")))));
        // @formatter:on
    }

    public static PELangRootNode invalidBranch() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "i"),
                b.if_(
                    b.lit("foo"),
                    b.writeLocal(b.lit(10L), "i"),
                    b.writeLocal(b.lit(5L), "i")),
                b.return_(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode invalidLoop() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "counter"),
                b.while_(
                    b.lit("foo"),
                    b.incrementLocal(b.lit(1L), "counter")),
                b.return_(b.readLocal("counter"))));
        // @formatter:on
    }

    public static PELangRootNode nestedAdds() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.return_(
                b.add(
                    b.add(b.lit(2L), b.lit(2L)),
                    b.add(
                        b.lit(2L),
                        b.add(b.lit(2L), b.lit(2L))))));
        // @formatter:on
    }

    public static PELangRootNode nestedBlocks() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "i"),
                b.incrementLocal(b.lit(1L), "i"),
                b.incrementLocal(b.lit(1L), "i"),
                b.block(
                    b.incrementLocal(b.lit(1L), "i"),
                    b.incrementLocal(b.lit(1L), "i"),
                    b.incrementLocal(b.lit(1L), "i"),
                    b.incrementLocal(b.lit(1L), "i"),
                    b.block(
                        b.incrementLocal(b.lit(1L), "i"),
                        b.incrementLocal(b.lit(1L), "i")),
                    b.block(
                        b.incrementLocal(b.lit(1L), "i"),
                        b.incrementLocal(b.lit(1L), "i"))),
                b.block(
                    b.return_(b.readLocal("i")))));
        // @formatter:on
    }

    public static PELangRootNode nestedLocalReadWrites() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(2L), "h"),
                b.writeLocal(b.lit(2L), "i"),
                b.writeLocal(b.lit(2L), "j"),
                b.writeLocal(b.lit(2L), "k"),
                b.writeLocal(b.lit(2L), "l"),
                b.writeLocal(
                    b.add(
                        b.readLocal("h"),
                        b.readLocal("i")),
                    "i"),
                b.writeLocal(
                    b.add(
                        b.readLocal("i"),
                        b.readLocal("j")),
                    "j"),
                b.writeLocal(
                    b.add(
                        b.readLocal("j"),
                        b.readLocal("k")),
                    "k"),
                b.writeLocal(
                    b.add(
                        b.readLocal("k"),
                        b.readLocal("l")),
                    "l"),
                b.return_(b.readLocal("l"))));
        // @formatter:on
    }

    public static PELangRootNode nestedBranches() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "i"),
                b.if_(
                    b.lt(b.readLocal("i"), b.lit(5L)),
                    b.block(
                        b.incrementLocal(b.lit(5L), "i"),
                        b.if_(
                            b.lt(b.readLocal("i"), b.lit(10L)),
                            b.incrementLocal(b.lit(5L), "i"),
                            b.incrementLocal(b.lit(1L), "i"))),
                    b.incrementLocal(b.lit(1L), "i")),
                b.return_(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode nestedLoops() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "i"),
                b.writeLocal(b.lit(0L), "j"),
                b.while_(
                    b.lt(b.readLocal("i"), b.lit(5L)),
                    b.block(
                        b.incrementLocal(b.lit(1L), "i"),
                        b.while_(
                            b.lt(b.readLocal("j"), b.lit(5L)),
                            b.block(
                                b.incrementLocal(b.lit(1L), "j"))))),
                b.return_(
                    b.add(
                        b.readLocal("i"),
                        b.readLocal("j")))));
        // @formatter:on
    }

    public static PELangRootNode nestedSwitches() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "i"),
                b.writeLocal(b.lit(0L), "j"),
                b.switch_(
                    b.readLocal("i"),
                    b.case_(
                        b.lit(0L),
                        b.switch_(
                            b.readLocal("j"),
                            b.case_(
                                b.lit(0L),
                                b.incrementLocal(b.lit(10L), "i")))),
                    b.case_(
                        b.lit(5L),
                        b.switch_(
                            b.readLocal("j"),
                            b.case_(
                                b.lit(5L),
                                b.incrementLocal(b.lit(5L), "i"))))),
                b.return_(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode branchWithGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeGlobal(b.lit(0L), "g"),
                b.if_(
                    b.lt(b.readGlobal("g"), b.lit(10L)),
                    b.incrementGlobal(b.lit(10L), "g"),
                    b.incrementGlobal(b.lit(5L), "g")),
                b.return_(b.readGlobal("g"))));
        // @formatter:on
    }

    public static PELangRootNode loopWithGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeGlobal(b.lit(0L), "g"),
                b.while_(
                    b.lt(b.readGlobal("g"), b.lit(10L)),
                    b.block(b.incrementGlobal(b.lit(1L), "g"))),
                b.return_(b.readGlobal("g"))));
        // @formatter:on
    }

    public static PELangRootNode nestedLoopsWithMultipleBackEdges() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(b.lit(0L), "i"),
                b.writeLocal(b.lit(0L), "j"),
                b.writeLocal(b.lit(0L), "k"),
                b.while_(
                    b.lt(b.readLocal("i"), b.lit(5L)),
                    b.block(
                        b.incrementLocal(b.lit(1L), "i"),
                        b.while_(
                            b.lt(b.readLocal("j"), b.lit(5L)),
                            b.if_(
                                b.lt(b.readLocal("j"), b.lit(3L)),
                                b.block(
                                    b.incrementLocal(b.lit(1L), "j"),
                                    b.incrementLocal(b.lit(1L), "k")),
                                b.incrementLocal(b.lit(1L), "j"))))),
                b.return_(
                    b.add(
                        b.readLocal("i"),
                        b.readLocal("j")))));
        // @formatter:on
    }

    public static PELangRootNode irreducibleLoop() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.dispatch(
                /* 0 */ b.basicBlock(b.writeLocal(b.lit(0L), "i"), 1),
                /* 1 */ b.basicBlock(b.writeLocal(b.lit(0L), "j"), 2),
                /* 2 */ b.basicBlock(b.eq(b.readLocal("i"), b.lit(0L)), 6, 3),
                /* 3 */ b.basicBlock(b.lt(b.readLocal("j"), b.lit(10L)), 4, 5),
                /* 4 */ b.basicBlock(b.incrementLocal(b.lit(1L), "j"), 3),
                /* 5 */ b.basicBlock(b.incrementLocal(b.lit(1L), "i"), 7),
                /* 6 */ b.basicBlock(b.incrementLocal(b.lit(1L), "i"), 4),
                /* 7 */ b.basicBlock(b.return_(b.readLocal("j")), PELangBasicBlockNode.NO_SUCCESSOR)));
        // @formatter:on
    }

    public static PELangRootNode invokeObjectFunctionProperty() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(
                    b.lit(b.object()),
                    "obj"),
                b.writeProperty(
                    b.readLocal("obj"),
                    b.lit("p1"),
                    b.lit(
                        b.fn(
                            f -> f.header("a", "b"),
                            f -> f.return_(
                                f.add(
                                    f.readLocal("a"),
                                    f.readLocal("b")))))),
                b.return_(
                    b.invoke(
                        b.readProperty(
                            b.readLocal("obj"),
                            b.lit("p1")),
                        b.lit(5L),
                        b.lit(5L)))));
        // @formatter:on
    }

}
