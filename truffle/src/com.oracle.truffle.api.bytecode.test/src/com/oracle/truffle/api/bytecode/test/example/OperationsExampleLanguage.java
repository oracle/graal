package com.oracle.truffle.api.bytecode.test.example;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;

@ProvidedTags(ExpressionTag.class)
@TruffleLanguage.Registration(id = OperationsExampleLanguage.ID)
public class OperationsExampleLanguage extends TruffleLanguage<Object> {
    public static final String ID = "OperationsExampleLanguage";

    @Override
    protected Object createContext(Env env) {
        return new Object();
    }
}
