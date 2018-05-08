package org.graalvm.compiler.truffle.pelang.test;

import org.graalvm.compiler.truffle.pelang.PELangBuilder;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;
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
            b.ret(b.add(5L, 5L))
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
                /* block 0 */ b.basicBlock(b.ret(b.add(5L, 5L)), PELangBasicBlockNode.NO_SUCCESSOR)
            )
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

}
