package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;

@ProvidedTags(ExpressionTag.class)
@TruffleLanguage.Registration(id = "OperationTestLanguage")
public class OperationTestLanguage extends TruffleLanguage<Object> {
    @Override
    protected Object createContext(Env env) {
        return new Object();
    }
}