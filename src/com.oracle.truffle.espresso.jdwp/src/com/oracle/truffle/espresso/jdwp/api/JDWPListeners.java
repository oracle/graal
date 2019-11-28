package com.oracle.truffle.espresso.jdwp.api;

import com.oracle.truffle.espresso.jdwp.impl.VMEventListeners;

public class JDWPListeners {

    public static JDWPListener getListener() {
        return VMEventListeners.getDefault().getEventListener();
    }
}
