/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPOptions;
import com.oracle.truffle.espresso.jdwp.api.VMEventListener;

/**
 * Controller bound to the JDWP instrument that manages controllers for individual contexts.
 */
public final class DebuggerInstrumentController implements ContextsListener {

    private final TruffleLogger jdwpLogger;
    private final Map<TruffleContext, DebuggerController> controllers = new ConcurrentHashMap<>();

    public DebuggerInstrumentController(TruffleLogger logger) {
        this.jdwpLogger = logger;
    }

    public DebuggerController createContextController(Debugger debug, JDWPOptions jdwpOptions, TruffleContext truffleContext, JDWPContext jdwpContext, Object thread, VMEventListener vmEventListener) {
        DebuggerController controller = new DebuggerController(jdwpLogger, debug, jdwpOptions, jdwpContext, thread, vmEventListener);
        controllers.put(truffleContext, controller);
        return controller;
    }

    public void disposeController(TruffleContext context) {
        DebuggerController controller = controllers.remove(context);
        if (controller != null) {
            controller.disposeDebugger(false);
        }
    }

    public void replaceController(TruffleContext context, DebuggerController newController) {
        controllers.put(context, newController);
    }

    @Override
    public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
        if (!"java".equals(language.getId())) {
            return;
        }
        DebuggerController controller = controllers.get(context);
        if (controller != null) {
            controller.onLanguageContextInitialized();
        }
    }

    @Override
    public void onContextCreated(TruffleContext context) {
    }

    @Override
    public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
    }

    @Override
    public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
    }

    @Override
    public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
    }

    @Override
    public void onContextClosed(TruffleContext context) {
    }

}
