package com.oracle.truffle.api.instrumentation.test;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = InputFilterTestInstrument.ID, services = {InputFilterTestInstrument.class})
public class InputFilterTestInstrument extends TruffleInstrument {

    static final String ID = "TestInputFilterInstrument";

    Env environment;

    @Override
    protected void onCreate(Env env) {
        this.environment = env;
        env.registerService(this);
    }

}