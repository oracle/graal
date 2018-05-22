package org.graalvm.compiler.truffle.pelang.test;

import org.graalvm.compiler.truffle.pelang.PELangBuilder;
import org.graalvm.compiler.truffle.pelang.PELangRootNode;

public class PELangSample {

    public static PELangRootNode simpleAdd() {
        PELangBuilder b = new PELangBuilder();
        return b.root(b.ret(b.add(5L, 5L)));
    }

    public static PELangRootNode simpleBlock() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(0, "i"),
                b.incrementLocal(2L, "i"),
                b.incrementLocal(2L, "i"),
                b.incrementLocal(2L, "i"),
                b.incrementLocal(2L, "i"),
                b.incrementLocal(2L, "i"),
                b.ret(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleLocalReadWrite() {
        PELangBuilder b = new PELangBuilder();
        return b.root(b.block(b.writeLocal(10L, "i"), b.ret(b.readLocal("i"))));
    }

    public static PELangRootNode simpleGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();
        return b.root(b.block(b.writeGlobal(10L, "i"), b.ret(b.readGlobal("i"))));
    }

    public static PELangRootNode simpleBranch() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(0, "i"),
                b.branch(
                    b.lessThan(b.readLocal("i"), b.literal(10L)),
                    b.writeLocal(10L, "i"),
                    b.writeLocal(5L, "i")),
                b.ret(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode simpleLoop() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(0, "counter"),
                b.loop(
                    b.lessThan(b.readLocal("counter"), b.literal(10L)),
                    b.incrementLocal(1, "counter")),
                b.ret(b.readLocal("counter"))));
        // @formatter:on
    }

    public static PELangRootNode nestedAdds() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.ret(
                b.add(
                    b.add(
                        b.literal(2L),
                        b.literal(2L)),
                    b.add(
                        b.literal(2L),
                        b.add(
                            b.literal(2L),
                            b.literal(2L))))));
        // @formatter:on
    }

    public static PELangRootNode nestedBlocks() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(0L, "i"),
                b.incrementLocal(1L, "i"),
                b.incrementLocal(1L, "i"),
                b.block(
                    b.incrementLocal(1L, "i"),
                    b.incrementLocal(1L, "i"),
                    b.incrementLocal(1L, "i"),
                    b.incrementLocal(1L, "i"),
                    b.block(
                        b.incrementLocal(1L, "i"),
                        b.incrementLocal(1L, "i")),
                    b.block(
                        b.incrementLocal(1L, "i"),
                        b.incrementLocal(1L, "i"))),
                b.block(
                    b.ret(b.readLocal("i")))));
        // @formatter:on
    }

    public static PELangRootNode nestedLocalReadWrites() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(2L, "h"),
                b.writeLocal(2L, "i"),
                b.writeLocal(2L, "j"),
                b.writeLocal(2L, "k"),
                b.writeLocal(2L, "l"),
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
                b.ret(b.readLocal("l"))));
        // @formatter:on
    }

    public static PELangRootNode nestedLoops() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(0, "i"),
                b.writeLocal(0, "j"),
                b.loop(
                    b.lessThan(b.readLocal("i"), b.literal(5L)),
                    b.block(
                        b.incrementLocal(1, "i"),
                        b.loop(
                            b.lessThan(b.readLocal("j"), b.literal(5L)),
                            b.block(
                                b.incrementLocal(1, "j"))))),
                b.ret(
                    b.add(
                        b.readLocal("i"),
                        b.readLocal("j")))));
        // @formatter:on
    }

    public static PELangRootNode nestedBranches() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeLocal(0L, "i"),
                b.branch(
                    b.lessThan(b.readLocal("i"), b.literal(5L)),
                    b.block(
                        b.incrementLocal(5L, "i"),
                        b.branch(
                            b.lessThan(b.readLocal("i"), b.literal(10L)),
                            b.incrementLocal(5L, "i"),
                            b.incrementLocal(1L, "i"))),
                    b.incrementLocal(1L, "i")),
                b.ret(b.readLocal("i"))));
        // @formatter:on
    }

    public static PELangRootNode branchWithGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeGlobal(0L, "g"),
                b.branch(
                    b.lessThan(b.readGlobal("g"), b.literal(10L)),
                    b.incrementGlobal(10L, "g"),
                    b.incrementGlobal(5L, "g")),
                b.ret(b.readGlobal("g"))));
        // @formatter:on
    }

    public static PELangRootNode loopWithGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        return b.root(
            b.block(
                b.writeGlobal(0L, "g"),
                b.loop(
                    b.lessThan(b.readGlobal("g"), b.literal(10L)),
                    b.block(b.incrementGlobal(1L, "g"))),
                b.ret(b.readGlobal("g"))));
        // @formatter:on
    }

}
