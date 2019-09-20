/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public class JDWPDebuggerController {

    private final TruffleInstrument.Env env;
    private EspressoOptions.JDWPOptions options;
    private DebuggerSession debuggerSession;
    private final JDWPInstrument instrument;

    public JDWPDebuggerController(TruffleInstrument.Env env, JDWPInstrument instrument) {
        this.env = env;
        this.instrument = instrument;
    }

    public void initialize(EspressoOptions.JDWPOptions options, EspressoContext context) {
        this.options = options;
        instrument.init(context);
    }

    public boolean shouldWaitForAttach() {
        return options.suspend;
    }

    public int getListeningPort() {
        return Integer.parseInt(options.address);
    }

    public String getTransport() {
        return options.transport;
    }

    /**
     * Whenever a debugger attaches through jdwp a new debugger session is created
     */
    public void startDebuggerSession() {
        Debugger debugger = env.lookup(env.getInstruments().get("debugger"), Debugger.class);
        debuggerSession = debugger.startSession(new SuspendedCallbackImpl(), SourceElement.ROOT, SourceElement.STATEMENT);
        //debuggerSession.setSourcePath(getSourcePath());
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).includeInternal(false).build());
    }

    /**
     * Installs a line breakpoint within a given method.
     * @param source The source which includes the line number
     * @param line the line number in the method
     */
    public void submitLineBreakpoint(Source source, int line) {
        Breakpoint bp = Breakpoint.newBuilder(source).lineIs(line).build();
        bp.setEnabled(true);
        debuggerSession.install(bp);
    }
}
