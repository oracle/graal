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

/**
 * Instrument for registered for the debugger. Do not use directly.
 *
 * @since 0.17
 */
@Registration(name = "Debugger", id = DebuggerInstrument.ID, services = Debugger.class)
public final class DebuggerInstrument extends TruffleInstrument {

    static final String ID = "debugger";
    private static DebuggerFactory factory;

    static {
        // Be sure that the factory is initialized:
        try {
            Class.forName(Debugger.class.getName(), true, Debugger.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            // Can not happen
        }
    }

    /**
     * @since 0.17
     */
    public DebuggerInstrument() {
    }

    @Override
    protected void onCreate(Env env) {
        env.registerService(factory.create(env));
    }

    /**
     * @since 0.27
     */
    public static void setFactory(DebuggerFactory factory) {
        if (factory == null || !factory.getClass().getName().startsWith("com.oracle.truffle.api.debug")) {
            throw new IllegalArgumentException("Wrong factory: " + factory);
        }
        DebuggerInstrument.factory = factory;
    }

    /**
     * @since 0.17
     */
    public interface DebuggerFactory {
        /**
         * @since 0.17
         */
        Debugger create(Env env);
    }

}
