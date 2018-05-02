package org.graalvm.compiler.truffle.pelang.test;

import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.junit.Assert;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class PELangTest extends PartialEvaluationTest {

    protected void assertCallResult(Object expected, RootNode rootNode) {
        Assert.assertEquals(expected, Truffle.getRuntime().createCallTarget(rootNode).call());
    }

}
