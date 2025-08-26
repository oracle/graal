/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = JDWPInstrument.ID, name = "Java debug wire protocol", services = DebuggerInstrumentController.class)
public final class JDWPInstrument extends TruffleInstrument {

    public static final String ID = "jdwp";

    @Override
    protected void onCreate(Env instrumentEnv) {
        // It is the DebuggerInstrumentController that handles the complete lifecycle of a JDWP
        // session. Here we simply create a new controller instance, provide it as a service for
        // lookup and attaches a context listener to assist in setup and shutdown hooks.
        DebuggerInstrumentController controller = new DebuggerInstrumentController(instrumentEnv.getLogger(ID));
        instrumentEnv.registerService(controller);
        instrumentEnv.getInstrumenter().attachContextsListener(controller, false);
    }
}
