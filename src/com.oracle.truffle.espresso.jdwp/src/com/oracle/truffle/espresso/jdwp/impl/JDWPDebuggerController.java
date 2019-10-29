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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.StepConfig;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPOptions;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.VMEventListeners;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class JDWPDebuggerController {

    private static final StepConfig STEP_CONFIG = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).build();

    private JDWPOptions options;
    private DebuggerSession debuggerSession;
    private final JDWPInstrument instrument;
    private Object suspendLock = new Object();
    private SuspendedInfo suspendedInfo;
    private volatile int commandRequestId = -1;
    private Ids ids;

    // justification for this being a map is that lookups only happen when at a breakpoint
    private Map<Breakpoint, BreakpointInfo> breakpointInfos = new HashMap<>();

    public JDWPDebuggerController(JDWPInstrument instrument) {
        this.instrument = instrument;
    }

    public void initialize(JDWPOptions options, JDWPContext context, boolean reconnect) {
        this.options = options;
        if (!reconnect) {
            instrument.init(context);
        }
        this.ids = context.getIds();

        // setup the debugger session object early to make sure instrumentable nodes are materialized
        TruffleLanguage.Env languageEnv = context.getEnv();
        Debugger debugger = languageEnv.lookup(languageEnv.getInstruments().get("debugger"), Debugger.class);
        debuggerSession = debugger.startSession(new SuspendedCallbackImpl(), SourceElement.ROOT, SourceElement.STATEMENT);
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).build());
        debuggerSession.suspendNextExecution();
    }

    public JDWPContext getContext() {
        return instrument.getContext();
    }

    public SuspendedInfo getSuspendedInfo() {
        return suspendedInfo;
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

    public void setCommandRequestId(int commandRequestId) {
        this.commandRequestId = commandRequestId;
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
        } catch (NoSuchSourceLineException ex) {
            // perhaps the debugger's view on the source is out of sync, in which case
            // the bytecode and source does not match.
            // TODO(Gregersen) - do nothing for now, but probably should handle this nicely later
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void mapBrekpoint(Breakpoint bp, BreakpointInfo info) {
        breakpointInfos.put(bp, info);
        info.setBreakpoint(bp);
    }

    public void resume() {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            doResume(suspendedInfo.getThread());
        }
    }

    public void stepOver() {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getEvent().prepareStepOver(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_OVER);
        }
    }

    public void stepInto() {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getEvent().prepareStepInto(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_INTO);
        }
    }

    public void stepOut() {
        SuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getEvent().prepareStepOut(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_OUT);
        }
    }

    private void doResume(Object thread) {
        synchronized (suspendLock) {
            ThreadSuspension.resumeThread(thread);
            suspendLock.notifyAll();
        }
    }

    public void disposeDebugger() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                instrument.reset();
            }
        }).start();
    }

    public void endSession() {
        debuggerSession.close();
    }

    public JDWPOptions getOptions() {
        return options;
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

            Object currentThread = getContext().getHost2GuestThread(Thread.currentThread());

            if (commandRequestId != -1) {
                if (checkExclusionFilters(event)) {
                    //System.out.println("not suspending here: " + event.getSourceSection());
                    return;
                }
            }

            //System.out.println("Suspended at: " + event.getSourceSection().toString() + " in thread: " + currentThread);

            byte strategy = SuspendStrategy.EVENT_THREAD;
            JDWPCallFrame[] callFrames = createCallFrames(ids.getIdAsLong(currentThread), event.getStackFrames());
            suspendedInfo = new SuspendedInfo(event, strategy, callFrames, currentThread);

            boolean alreadySuspended = false;
            for (Breakpoint bp : event.getBreakpoints()) {
                //System.out.println("BP at suspension point: " + bp.getLocationDescription());
                // register the thread as suspended before sending the breakpoint hit event.
                // The debugger will verify thread status as part of registering if a breakpoint is hit
                if (strategy == SuspendStrategy.EVENT_THREAD && !alreadySuspended) {
                    alreadySuspended = true;
                    ThreadSuspension.suspendThread(currentThread);
                }
                VMEventListeners.getDefault().breakpointHit(breakpointInfos.get(bp), currentThread);
            }
            // now, suspend the current thread until resumed by e.g. a debugger command
            suspend(strategy, callFrames[0], currentThread, alreadySuspended);
        }

        private boolean checkExclusionFilters(SuspendedEvent event) {
            RequestFilter requestFilter = EventFilters.getDefault().getRequestFilter(commandRequestId);

            if (requestFilter != null && requestFilter.isStepping()) {
            // we're currently stepping, so check if suspension point
            // matches any exclusion filters

                DebugStackFrame topFrame = event.getTopStackFrame();

                if (topFrame.getSourceSection() != null) {
                    RootNode root = findCurrentRoot(topFrame);

                    KlassRef klass = getContext().getKlassFromRootNode(root);

                    if (klass != null && requestFilter.isKlassExcluded(klass)) {
                        // should not suspend here then, tell the event to keep going
                        continueStepping(event);
                        return true;
                    }
                }
            }
            return false;
        }

        private void continueStepping(SuspendedEvent event) {
            switch (suspendedInfo.getStepKind()) {
                case STEP_INTO:
                    // stepping into unwanted code which was filtered
                    // so step out and try step into again
                    event.prepareStepOut(STEP_CONFIG).prepareStepInto(STEP_CONFIG);
                    break;
                case STEP_OVER:
                    event.prepareStepOver(STEP_CONFIG);
                    break;
                case STEP_OUT:
                    event.prepareStepOut(STEP_CONFIG);
                    break;
                default:
                    break;
            }
        }

        private JDWPCallFrame[] createCallFrames(long threadId, Iterable<DebugStackFrame> stackFrames) {
            LinkedList<JDWPCallFrame> list = new LinkedList<>();
            for (DebugStackFrame frame : stackFrames) {
                // byte type tag, long classId, long methodId, long codeIndex

                if (frame.getSourceSection() == null) {
                    continue;
                }

                RootNode root = findCurrentRoot(frame);
                KlassRef klass = getContext().getKlassFromRootNode(root);

                if (klass != null) {
                    MethodRef method = getContext().getMethodFromRootNode(root);

                    long klassId = ids.getIdAsLong(klass);
                    long methodId = ids.getIdAsLong(method);
                    byte typeTag = TypeTag.getKind(klass);
                    int line = frame.getSourceSection().getStartLine();

                    long codeIndex = method.getBCIFromLine(line); //  method.getLineNumberTable().getBCI(line);

                    DebugScope scope = frame.getScope();

                    //System.out.println("collected frame info for method: " + klass.getName().toString() + "." + method.getName() + "(" + line + ") : BCI(" + codeIndex + ")") ;

                    Object thisValue = null;
                    ArrayList<Object> realVariables = new ArrayList<>();

                    if (scope != null ) {
                        Iterator<DebugValue> variables = scope.getDeclaredValues().iterator();
                        while (variables.hasNext()) {
                            DebugValue var = variables.next();
                            if ("this".equals(var.getName())) {
                                // get the real object reference and register it with Id
                                thisValue = getRealValue(var);
                            } else {
                                // add to variables list
                                realVariables.add(getRealValue(var));
                            }
                        }
                    }
                    list.addLast(new JDWPCallFrame(threadId, typeTag, klassId, methodId, codeIndex, thisValue, realVariables.toArray(new Object[realVariables.size()])));

                } else {
                    throw new RuntimeException("stack walking not implemented for root node type! " + root);
                }
            }
            return list.toArray(new JDWPCallFrame[list.size()]);
        }

        private Object getRealValue(DebugValue value) {
            // TODO(Gregersen) - hacked in with reflection currently
            // awaiting a proper API for this
            try {
                java.lang.reflect.Method getMethod = DebugValue.class.getDeclaredMethod("get");
                getMethod.setAccessible(true);
                return getMethod.invoke(value);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private RootNode findCurrentRoot(DebugStackFrame frame) {
            // TODO(Gregersen) - replace with new API when available
            // for now just use reflection to get the current root
            try {
                java.lang.reflect.Method getRoot = DebugStackFrame.class.getDeclaredMethod("findCurrentRoot");
                getRoot.setAccessible(true);
                return (RootNode) getRoot.invoke(frame);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private void suspend(byte strategy, JDWPCallFrame currentFrame, Object thread, boolean alreadySuspended) {
            switch(strategy) {
                case SuspendStrategy.NONE:
                    // nothing to suspend
                    break;
                case SuspendStrategy.EVENT_THREAD:
                    synchronized (suspendLock) {
                        try {
                            if (!alreadySuspended) {
                                ThreadSuspension.suspendThread(thread);
                            }

                            // if during stepping, send a step completed event back to the debugger
                            if (commandRequestId != -1) {
                                VMEventListeners.getDefault().stepCompleted(commandRequestId, currentFrame);
                            }
                            // reset
                            commandRequestId = -1;
                            //System.out.println("suspending...");
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
