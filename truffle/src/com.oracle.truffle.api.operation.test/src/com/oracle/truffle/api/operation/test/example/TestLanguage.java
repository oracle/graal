package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.operation.OperationsNode;

@Registration(id = "test-operations", name = "test-operations")
public class TestLanguage extends TruffleLanguage<TestContext> {

    @Override
    protected TestContext createContext(TruffleLanguage.Env env) {
        return new TestContext();
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        OperationsNode[] nodes = TestOperationsBuilder.parse(this, request.getSource());
        return nodes[nodes.length - 1].createRootNode(this, "test").getCallTarget();
    }
}

class TestContext {
    TestContext() {
    }
}