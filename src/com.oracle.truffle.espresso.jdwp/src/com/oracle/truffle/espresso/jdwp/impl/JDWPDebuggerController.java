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
import com.oracle.truffle.api.debug.DebugException;
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
import com.oracle.truffle.espresso.jdwp.api.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPOptions;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.VMEventListeners;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

public class JDWPDebuggerController {

    private static final StepConfig STEP_CONFIG = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).build();

    private JDWPOptions options;
    private DebuggerSession debuggerSession;
    private final JDWPInstrument instrument;
    private Map<Object, Object> suspendLocks = new HashMap<>();
    private Map<Object, SuspendedInfo> suspendedInfos = new HashMap<>();
    private Map<Object, Integer> commandRequestIds = new HashMap<>();
    private Ids ids;
    private Method suspendMethod;
    private Method resumeMethod;

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

        try {
            suspendMethod = DebuggerSession.class.getDeclaredMethod("suspend", Thread.class);
            suspendMethod.setAccessible(true);

            resumeMethod = DebuggerSession.class.getDeclaredMethod("resume", Thread.class);
            resumeMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unable to obtain thread suspend method", e);
        }
    }

    public JDWPContext getContext() {
        return instrument.getContext();
    }

    public SuspendedInfo getSuspendedInfo(Object thread) {
        return suspendedInfos.get(thread);
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

    public void setCommandRequestId(Object thread, int commandRequestId) {
        commandRequestIds.put(thread, commandRequestId);
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
            int ignoreCount = command.getBreakpointInfo().getFilter().getIgnoreCount();
            if (ignoreCount > 0) {
                bp.setIgnoreCount(ignoreCount);
            }
            mapBrekpoint(bp, command.getBreakpointInfo());
            debuggerSession.install(bp);
            //System.out.println("Breakpoint submitted at " + bp.getLocationDescription());
        } catch (NoSuchSourceLineException ex) {
            // perhaps the debugger's view on the source is out of sync, in which case
            // the bytecode and source does not match.
        }
    }

    public void submitExceptionBreakpoint(DebuggerCommand command) {
        Breakpoint bp = Breakpoint.newExceptionBuilder(command.getBreakpointInfo().isCaught(), command.getBreakpointInfo().isUnCaught()).build();
        bp.setEnabled(true);
        int ignoreCount = command.getBreakpointInfo().getFilter().getIgnoreCount();
        if (ignoreCount > 0) {
            bp.setIgnoreCount(ignoreCount);
        }
        mapBrekpoint(bp, command.getBreakpointInfo());
        debuggerSession.install(bp);
    }

    @CompilerDirectives.TruffleBoundary
    private void mapBrekpoint(Breakpoint bp, BreakpointInfo info) {
        breakpointInfos.put(bp, info);
        info.setBreakpoint(bp);
    }

    public void stepOver(Object thread) {
        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null) {
            susp.getEvent().prepareStepOver(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_OVER);
        }
    }

    public void stepInto(Object thread) {
        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null) {
            susp.getEvent().prepareStepInto(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_INTO);
        }
    }

    public void stepOut(Object thread) {
        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null) {
            susp.getEvent().prepareStepOut(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_OUT);
        }
    }

    public void resume(Object thread) {
        ThreadSuspension.resumeThread(thread);
        int suspensionCount = ThreadSuspension.getSuspensionCount(thread);

        if (suspensionCount == 0) {
            // only resume when suspension count reaches 0
            Object lock = getSuspendLock(thread);
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    public void resumeAll(boolean sessionClosed) {
        // first clear the suspension counts on all threads
        ThreadSuspension.resumeAll();

        for (Object thread : getContext().getAllGuestThreads()) {
            resume(thread);
            if (!sessionClosed) {
                // TODO(Gregersen) - call method directly when it becomes available
                try {
                    resumeMethod.invoke(debuggerSession, getContext().getGuest2HostThread(thread));
                    // also clear the suspension info for this thread
                    suspendedInfos.put(thread, null);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to resume thread: " + thread, e);
                }
            }
        }
    }

    public boolean suspend(Object thread) {
        try {
            // TODO(Gregersen) - call method directly when it becomes available
            suspendMethod.invoke(debuggerSession, getContext().getGuest2HostThread(thread));

            // wait up to below timeout for the thread to become suspended before
            // returning, thus sending a reply packet
            long timeout = System.currentTimeMillis() + 5000;
            while (suspendedInfos.get(thread) == null && System.currentTimeMillis() < timeout) {
                Thread.sleep(10);
            }
            return suspendedInfos.get(thread) != null;
        } catch (Exception e) {
            System.err.println("not able to suspend thread: " + thread);
            return false;
        }
    }

    public void suspendAll() {
        for (Object thread : getContext().getAllGuestThreads()) {
            suspend(thread);
        }
    }

    private Object getSuspendLock(Object thread) {
        Object lock = suspendLocks.get(thread);
        if (lock == null) {
            lock = new Object();
            suspendLocks.put(thread, lock);
        }
        return lock;
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

            //System.out.println("Suspended at: " + event.getSourceSection().toString() + " in thread: " + currentThread);

            if (commandRequestIds.get(currentThread) != null) {
                if (checkExclusionFilters(event, currentThread)) {
                    //System.out.println("not suspending here: " + event.getSourceSection());
                    return;
                }
            }

            JDWPCallFrame[] callFrames = createCallFrames(ids.getIdAsLong(currentThread), event.getStackFrames());
            SuspendedInfo suspendedInfo = new SuspendedInfo(event, callFrames, currentThread);
            suspendedInfos.put(currentThread, suspendedInfo);

            byte suspendPolicy = SuspendStrategy.EVENT_THREAD;

            boolean hit = false;
            for (Breakpoint bp : event.getBreakpoints()) {
                //System.out.println("BP at suspension point: " + bp.getLocationDescription());

                BreakpointInfo info = breakpointInfos.get(bp);
                suspendPolicy = info.getSuspendPolicy();

                if (info.isLineBreakpoint()) {
                    // check if breakpoint request limited to a specific thread
                    Object thread = info.getThread();
                    if (thread == null || thread == currentThread) {
                        if (!hit) {
                            hit = true;
                            // First hit, so we can increase the thread suspension.
                            // Register the thread as suspended before sending the breakpoint hit event.
                            // The debugger will verify thread status as part of registering if a breakpoint is hit
                            ThreadSuspension.suspendThread(currentThread);
                            VMEventListeners.getDefault().breakpointHit(info, currentThread);
                        }
                    }
                } else if (info.isExceptionBreakpoint()) {
                    // get the specific exception type if any
                    KlassRef klass = info.getKlass();
                    Throwable exception = getRawException(event.getException());
                    Object guestException = getContext().getGuestException(exception);

                    // TODO(Gregersen) - rewrite this when /browse/GR-19337 is done
                    // Currently, the Truffle Debug API doesn't filter on type, so we end up here having to check
                    // also, the ignore count set on the breakpoint will not work properly due to this.
                    if (klass == null || klass.getTypeAsString().equals(guestException.toString())) {
                        // check filters if we should not suspend
                        Pattern[] positivePatterns = info.getFilter().getIncludePatterns();
                        // verify include patterns
                        if (positivePatterns == null || matchLocation(positivePatterns, callFrames[0])) {
                            // verify exclude patterns
                            Pattern[] negativePatterns = info.getFilter().getExcludePatterns();
                            if (negativePatterns == null || !matchLocation(negativePatterns, callFrames[0])) {
                                hit = true;
                            }
                        }
                    }
                    if (hit) {
                        ThreadSuspension.suspendThread(currentThread);
                        VMEventListeners.getDefault().exceptionThrown(info, currentThread, guestException, callFrames[0]);
                    } else {
                        // don't suspend here
                        suspendedInfos.put(currentThread, null);
                        return;
                    }
                }
            }

            // now, suspend the current thread until resumed by e.g. a debugger command
            suspend(callFrames[0], currentThread, hit, suspendPolicy);
        }

        private boolean matchLocation(Pattern[] positivePatterns, JDWPCallFrame callFrame) {
            KlassRef klass = (KlassRef) getContext().getIds().fromId((int) callFrame.getClassId());

            for (Pattern positivePattern : positivePatterns) {
                if (positivePattern.pattern().matches(klass.getNameAsString().replace('/', '.')))
                    return true;
            }
            return false;
        }

        private Throwable getRawException(DebugException exception) {
            try {
                Method method = DebugException.class.getDeclaredMethod("getRawException");
                method.setAccessible(true);
                return (Throwable) method.invoke(exception);
            } catch (Exception e) {
                e.printStackTrace();
                return exception;
            }
        }

        private boolean checkExclusionFilters(SuspendedEvent event, Object thread) {
            Integer id = commandRequestIds.get(thread);

            if (id != null) {
                RequestFilter requestFilter = EventFilters.getDefault().getRequestFilter(id);

                if (requestFilter != null && requestFilter.isStepping()) {
                    // we're currently stepping, so check if suspension point
                    // matches any exclusion filters

                    DebugStackFrame topFrame = event.getTopStackFrame();

                    if (topFrame.getSourceSection() != null) {
                        RootNode root = findCurrentRoot(topFrame);

                        KlassRef klass = getContext().getKlassFromRootNode(root);

                        if (klass != null && requestFilter.isKlassExcluded(klass)) {
                            // should not suspend here then, tell the event to keep going
                            continueStepping(event, thread);
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private void continueStepping(SuspendedEvent event, Object thread) {
            switch (suspendedInfos.get(thread).getStepKind()) {
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

                    long codeIndex = method.getBCIFromLine(line);

                    DebugScope scope = frame.getScope();

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
                                Object realValue = getRealValue(var);
                                realVariables.add(realValue);
                            }
                        }
                    }
                    //System.out.println("collected frame info for method: " + klass.getNameAsString() + "." + method.getNameAsString() + "(" + line + ") : BCI(" + codeIndex + ")") ;
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

        private void suspend(JDWPCallFrame currentFrame, Object thread, boolean alreadySuspended, byte suspendPolicy) {

            switch(suspendPolicy) {
                case SuspendStrategy.NONE:
                    break;
                case SuspendStrategy.EVENT_THREAD:
                    suspendEventThread(currentFrame, thread, alreadySuspended);
                    break;
                case SuspendStrategy.ALL:
                    Thread suspendThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // suspend other threads
                            for (Object activeThread : getContext().getAllGuestThreads()) {
                                if (activeThread != thread) {
                                    JDWPDebuggerController.this.suspend(activeThread);
                                }
                            }
                        }
                    });
                    suspendThread.start();
                    suspendEventThread(currentFrame, thread, alreadySuspended);
                    break;
            }
        }

        private void suspendEventThread(JDWPCallFrame currentFrame, Object thread, boolean alreadySuspended) {
            Object lock = getSuspendLock(thread);
            try {
                if (!alreadySuspended) {
                    ThreadSuspension.suspendThread(thread);
                }

                // if during stepping, send a step completed event back to the debugger
                Integer id = commandRequestIds.get(thread);
                if (id != null) {
                    VMEventListeners.getDefault().stepCompleted(id, currentFrame);
                }
                // reset
                commandRequestIds.put(thread, null);

                synchronized (lock) {
                    lock.wait();
                }
            } catch (InterruptedException e) {

            }
        }
    }
}
