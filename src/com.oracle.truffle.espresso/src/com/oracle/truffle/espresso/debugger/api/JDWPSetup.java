package com.oracle.truffle.espresso.debugger.api;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.espresso.debugger.jdwp.JDWPDebuggerController;
import com.oracle.truffle.espresso.debugger.jdwp.JDWPInstrument;

public class JDWPSetup {

    public static void setup(JDWPOptions options, JDWPContext context) {
        TruffleLanguage.Env env = context.getEnv();
        JDWPDebuggerController controller = env.lookup(env.getInstruments().get(JDWPInstrument.ID), JDWPDebuggerController.class);
        controller.initialize(options, context);
    }
}
