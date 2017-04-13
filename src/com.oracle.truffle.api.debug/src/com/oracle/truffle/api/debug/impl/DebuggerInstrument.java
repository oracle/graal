/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.debug.impl;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotRuntime.Instrument;

/**
 * Instrument for registered for the debugger. Do not use directly.
 *
 * @since 0.17
 */
@Registration(name = "Debugger", id = DebuggerInstrument.ID)
public final class DebuggerInstrument extends TruffleInstrument {

    static final String ID = "debugger";

    private Env environment;
    private Debugger debugger;

    /**
     * @since 0.17
     */
    public DebuggerInstrument() {
    }

    @Override
    protected void onCreate(Env env) {
        this.environment = env;
        env.registerService(this);
    }

    /**
     * @since 0.17
     */
    public static Debugger getDebugger(PolyglotEngine engine, DebuggerFactory factory) {
        Instrument instrument = engine.getRuntime().getInstruments().get(ID);
        if (instrument == null) {
            throw new IllegalStateException("Debugger is not installed.");
        }
        instrument.setEnabled(true);
        DebuggerInstrument instrumentImpl = instrument.lookup(DebuggerInstrument.class);
        synchronized (instrumentImpl) {
            if (instrumentImpl.debugger == null) {
                instrumentImpl.debugger = factory.create(engine, instrumentImpl.environment);
            }
        }
        return instrumentImpl.debugger;
    }

    /**
     * @since 0.17
     */
    public interface DebuggerFactory {
        /**
         * @since 0.17
         */
        Debugger create(PolyglotEngine engine, Env env);
    }

}
