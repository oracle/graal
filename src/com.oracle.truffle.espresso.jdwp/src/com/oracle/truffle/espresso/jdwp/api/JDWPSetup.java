package com.oracle.truffle.espresso.jdwp.api;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.espresso.jdwp.impl.JDWPDebuggerController;
import com.oracle.truffle.espresso.jdwp.impl.JDWPInstrument;

public class JDWPSetup {

    public static void setup(JDWPOptions options, JDWPContext context) {
        TruffleLanguage.Env env = context.getEnv();
        JDWPDebuggerController controller = env.lookup(env.getInstruments().get(JDWPInstrument.ID), JDWPDebuggerController.class);
        controller.initialize(options, context);
    }
}
