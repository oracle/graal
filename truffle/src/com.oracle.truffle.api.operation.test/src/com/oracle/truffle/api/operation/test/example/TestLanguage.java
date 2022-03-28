package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.operation.OperationsNode;

@Registration(id = "test", name = "test")
public class TestLanguage extends TruffleLanguage<TestContext> {

    @Override
    protected TestContext createContext(TruffleLanguage.Env env) {
        return new TestContext(env);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        OperationsNode[] nodes = TestOperationsBuilder.parse(this, request.getSource());
        return nodes[nodes.length - 1].createRootNode().getCallTarget();
    }
}

class TestContext {
    private final TruffleLanguage.Env env;

    public TestContext(TruffleLanguage.Env env) {
        this.env = env;
    }
}