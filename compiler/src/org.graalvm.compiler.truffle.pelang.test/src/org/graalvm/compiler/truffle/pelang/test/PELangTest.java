package org.graalvm.compiler.truffle.pelang.test;

import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.junit.Assert;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class PELangTest extends PartialEvaluationTest {

    protected Object constant10() {
        return 10;
    }

    protected void assertConstant10CallResult(RootNode rootNode) {
        Object result = Truffle.getRuntime().createCallTarget(rootNode).call();
        Assert.assertEquals(Long.valueOf(10L), result);
    }

    protected void assertConstant10PartialEvaluationResult(RootNode rootNode) {
        assertPartialEvalEquals("constant10", rootNode);
    }

}
