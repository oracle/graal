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
package com.oracle.truffle.espresso.debugger.jdwp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.StepConfig;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.debugger.BreakpointInfo;
import com.oracle.truffle.espresso.debugger.SourceLocation;
import com.oracle.truffle.espresso.debugger.SuspendStrategy;
import com.oracle.truffle.espresso.debugger.VMEventListeners;
import com.oracle.truffle.espresso.debugger.exception.ClassNotLoadedException;
import com.oracle.truffle.espresso.debugger.exception.NoSuchSourceLineException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.GuestClassLoadingNotifier;
import com.oracle.truffle.espresso.runtime.GuestClassLoadingSubscriber;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class JDWPDebuggerController implements GuestClassLoadingSubscriber {

    private static final StepConfig STEP_CONFIG = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).build();

    private final ArrayList<SourceLocation> notInstalled = new ArrayList<>(16);

    private EspressoOptions.JDWPOptions options;
    private DebuggerSession debuggerSession;
    private final JDWPInstrument instrument;
    private Object suspendLock = new Object();
    private SuspendedInfo suspendedInfo;

    // justification for this being a map is that lookups only happen when at a breakpoint
    private Map<Breakpoint, BreakpointInfo> breakpointInfos = new HashMap<>();

    public JDWPDebuggerController(JDWPInstrument instrument) {
        this.instrument = instrument;
    }

    public void initialize(EspressoOptions.JDWPOptions options, EspressoContext context) {
        this.options = options;
        instrument.init(context);
        GuestClassLoadingNotifier.getInstance().subscribe(this);

        // setup the debugger session object early to make sure instrumentable nodes are materialized
        TruffleLanguage.Env languageEnv = instrument.getContext().getEnv();
        Debugger debugger = languageEnv.lookup(languageEnv.getInstruments().get("debugger"), Debugger.class);
        debuggerSession = debugger.startSession(new SuspendedCallbackImpl(), SourceElement.ROOT, SourceElement.STATEMENT);
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).build());
        debuggerSession.suspendNextExecution();
    }

    public EspressoContext getContext() {
        return instrument.getContext();
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
     * Installs a line breakpoint within a given method.
     * @param command the command that represents the
     * breakpoint
     */
    public void submitLineBreakpoint(DebuggerCommand command) {
        SourceLocation location = command.getSourceLocation();
        try {
            Breakpoint bp = Breakpoint.newBuilder(location.getSource()).lineIs(location.getLineNumber()).build();
            bp.setEnabled(true);
            mapBrekpoint(bp, command.getInfo());
            debuggerSession.install(bp);
            //System.out.println("Breakpoint submitted at " + bp.getLocationDescription());
        } catch (ClassNotLoadedException e) {
            installOnClassLoad(location);
            return;
        } catch (NoSuchSourceLineException ex) {
            // perhaps the debugger's view on the source is out of sync, in which case
            // the bytecode and source does not match.
            // TODO(Gregersen) - do nothing for now, but probably should handle this nicely later
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void mapBrekpoint(Breakpoint bp, BreakpointInfo info) {
        breakpointInfos.put(bp, info);
    }

    public void resume() {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getEvent().prepareContinue();
            doResume();
        }
    }

    public void stepOver() {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getEvent().prepareStepOver(STEP_CONFIG);
            doResume();
        }
    }

    public void stepInto() {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getEvent().prepareStepInto(STEP_CONFIG);
            doResume();
        }
    }

    public void stepIntoSpecific(SourceLocation location) {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            // this can be implemented in multiple ways. One way that doesn't
            // require changes to the Truffle Debug API is to use a one-shot breakpoint
            Source source = null;
            try {
                source = location.getSource();
                Breakpoint bp = Breakpoint.newBuilder(source).oneShot().lineIs(location.getLineNumber()).build();
                debuggerSession.install(bp);
            } catch (ClassNotLoadedException e) {
                e.printStackTrace();
            } catch (NoSuchSourceLineException e) {
                e.printStackTrace();
            }
            doResume();
        }
    }

    public void stepOut() {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getEvent().prepareStepOut(STEP_CONFIG);
            doResume();
        }
    }

    private void doResume() {
        synchronized (suspendLock) {
            suspendLock.notifyAll();
        }
    }

    public void installOnClassLoad(SourceLocation location) {
        notInstalled.add(location);
    }

    public void notifyClassLoaded(Symbol<Symbol.Type> type) {
        for (SourceLocation location : notInstalled) {
            if (type.equals(location.getType())) {
                //submitLineBreakpoint(location);
                return;
            }
        }
    }

    private class SuspendedCallbackImpl implements SuspendedCallback {

        @CompilerDirectives.CompilationFinal
        private boolean firstSuspensionCalled;

        @Override
        public void onSuspend(SuspendedEvent event) {
            if (!firstSuspensionCalled) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                firstSuspensionCalled = true;
                return;
            }
            //System.out.println("Suspended at: " + event.getSourceSection().toString());

            byte strategy = SuspendStrategy.EVENT_THREAD;
            for (Breakpoint bp : event.getBreakpoints()) {
                // TODO(Gregersen) - look for the BP suspend strategy
                // TODO(Gregersen) - if multiple BPs at this location we have to adhere to
                // TODO(Gregersen) - the strongest suspension policy ALL -> THREAD -> NONE
                if (!bp.isOneShot()) {
                    //System.out.println("BP at suspension point: " + bp.getLocationDescription());
                    VMEventListeners.getDefault().breakpointHit(breakpointInfos.get(bp));
                }
            }

            suspendedInfo = new SuspendedInfo(event, strategy);

            // now, suspend the current thread until resumed by e.g. a debugger command
            suspend(strategy);
        }

        private void suspend(byte strategy) {
            switch(strategy) {
                case SuspendStrategy.NONE:
                    // nothing to suspend
                    break;
                case SuspendStrategy.EVENT_THREAD:
                    synchronized (suspendLock) {
                        try {
                            suspendLock.wait();
                        } catch (InterruptedException e) {

                        }
                    }
                    break;
                case SuspendStrategy.ALL:
                    //TODO(Gregersen) - not implemented
                    break;
                default:
                    break;
            }
        }
    }
}
