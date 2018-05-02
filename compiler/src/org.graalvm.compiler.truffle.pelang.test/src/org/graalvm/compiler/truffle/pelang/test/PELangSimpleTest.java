package org.graalvm.compiler.truffle.pelang.test;

import org.graalvm.compiler.truffle.pelang.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.PELangBuilder;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;

public class PELangSimpleTest extends PELangTest {

    protected Object constant10() {
        return 10;
    }

    @Test
    public void simpleTest() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        RootNode rootNode = b.root(
            b.ret(
                b.add(
                    b.literal(5),
                    b.literal(5)
                )
            )
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void simpleBasicBlockTest() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        RootNode rootNode = b.root(
            b.dispatch(
                b.basicBlock(
                    b.ret(
                        b.add(
                            b.literal(5),
                            b.literal(5)
                        )
                    ),
                    PELangBasicBlockNode.NO_SUCCESSOR)));
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

}
