package com.oracle.truffle.api.operation.test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;

@ProvidedTags(ExpressionTag.class)
@TruffleLanguage.Registration(id = TestOperationsLanguage.ID)
public class TestOperationsLanguage extends TruffleLanguage<Object> {
    public static final String ID = "TestOperationsLanguage";

    @Override
    protected Object createContext(Env env) {
        return new Object();
    }
}