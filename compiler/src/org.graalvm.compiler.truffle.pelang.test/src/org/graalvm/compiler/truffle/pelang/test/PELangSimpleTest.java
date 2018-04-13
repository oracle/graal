package org.graalvm.compiler.truffle.pelang.test;

import org.graalvm.compiler.truffle.pelang.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.PELangExpressionBuilder;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;

public class PELangSimpleTest extends PELangTest {

    @Test
    public void simpleTest() {
        PELangExpressionBuilder b = new PELangExpressionBuilder();

        // @formatter:off
        RootNode rootNode = b.root(
            b.add(b.literal(5), b.literal(5)));
        // @formatter:on

        assertConstant10CallResult(rootNode);
        assertConstant10PartialEvaluationResult(rootNode);
    }

    @Test
    public void simpleBasicBlockTest() {
        PELangExpressionBuilder b = new PELangExpressionBuilder();

        // @formatter:off
        RootNode rootNode = b.root(
            b.dispatch(
                b.basicBlock(
                    b.add(5, 5),
                    PELangBasicBlockNode.NO_SUCCESSOR)));
        // @formatter:on

        assertConstant10CallResult(rootNode);

        // this currently fails
        // assertConstant10PartialEvaluationResult(rootNode);
    }

}
