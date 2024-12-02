/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.StepConfig;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPOptions;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.VMEventListener;

public final class DebuggerController implements ContextsListener {

    private static final StepConfig STEP_CONFIG = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).build();

    // justification for all of the hash maps is that lookups only happen when at a breakpoint
    private final Map<Object, SimpleLock> suspendLocks = Collections.synchronizedMap(new HashMap<>());
    private final Map<Object, SuspendedInfo> suspendedInfos = Collections.synchronizedMap(new HashMap<>());
    private final Map<Object, SteppingInfo> commandRequestIds = new HashMap<>();
    private final Map<Object, InvokeJob<?>> invokeJobs = new HashMap<>();
    private final Map<Object, FieldBreakpointEvent> fieldBreakpointExpected = new HashMap<>();
    private final Map<Object, MethodBreakpointEvent> methodBreakpointExpected = new HashMap<>();
    private final Map<Breakpoint, BreakpointInfo> breakpointInfos = new HashMap<>();

    private JDWPOptions options;
    private DebuggerSession debuggerSession;
    private final JDWPInstrument instrument;
    private Ids<Object> ids;
    private JDWPContext context;
    private final VirtualMachine vm;
    private Debugger debugger;
    private final GCPrevention gcPrevention;
    private final ThreadSuspension threadSuspension;
    private final EventFilters eventFilters;
    private VMEventListener eventListener;
    private TruffleContext truffleContext;
    private Object initialThread;
    private final TruffleLogger jdwpLogger;
    private DebuggerConnection connection;
    private volatile SetupState setupState = null;

    // Field used to signal a fatal startup error that can happen e.g. if the handshake with the
    // debugger fails. This field is only used when suspend=y. Before the main thread suspends
    // itself, it must check this field and exit the context if set.
    private volatile Throwable lateStartupError;

    public DebuggerController(JDWPInstrument instrument, TruffleLogger logger) {
        this.instrument = instrument;
        this.vm = new VirtualMachineImpl();
        this.gcPrevention = new GCPrevention();
        this.threadSuspension = new ThreadSuspension();
        this.eventFilters = new EventFilters();
        this.jdwpLogger = logger;
    }

    public void initialize(Debugger debug, JDWPOptions jdwpOptions, JDWPContext jdwpContext, Object thread, VMEventListener vmEventListener) {
        this.debugger = debug;
        this.options = jdwpOptions;
        this.context = jdwpContext;
        this.ids = jdwpContext.getIds();
        this.eventListener = vmEventListener;
        this.initialThread = thread;

        // set up the debug session object early to make sure instrumentable nodes are materialized
        debuggerSession = debug.startSession(new SuspendedCallbackImpl(), SourceElement.ROOT, SourceElement.STATEMENT);
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).build());

        instrument.init(jdwpContext);
    }

    public void reInitialize() {
        initialize(debugger, options, context, initialThread, eventListener);
        assert setupState != null;

        if (setupState.fatalConnectionError) {
            fine(() -> "Failed debugger setup due to initial connection issue.");
            // OK, give up on trying to reconnect
            return;
        }
        // On reconnect, we just pass a placeholder CountDownLatch object which we don't ever wait
        // for. This avoids tedious null checks in the connection method.
        DebuggerConnection.establishDebuggerConnection(this, setupState, true, new CountDownLatch(1));
    }

    void setDebuggerConnection(DebuggerConnection connection) {
        this.connection = connection;
    }

    void setSetupState(SetupState state) {
        this.setupState = state;
    }

    public void dispose() {
        if (connection != null) {
            connection.dispose();
        }
    }

    public void closeSocket() {
        if (connection != null) {
            connection.closeSocket();
        }
    }

    public void addDebuggerReceiverThread(Thread thread) {
        instrument.addDebuggerReceiverThread(thread);
    }

    public void addDebuggerSenderThread(Thread thread) {
        instrument.addDebuggerSenderThread(thread);
    }

    public void markLateStartupError(Throwable t) {
        lateStartupError = t;
    }

    public boolean isClosing() {
        return instrument.isClosing();
    }

    public Lock getResettingLock() {
        return instrument.getResettingLock();
    }

    static final class SetupState {
        final Socket socket;
        final ServerSocket serverSocket;
        private boolean fatalConnectionError;

        SetupState(Socket socket, ServerSocket serverSocket, boolean fatalConnectionError) {
            this.socket = socket;
            this.serverSocket = serverSocket;
            this.fatalConnectionError = fatalConnectionError;
        }
    }

    public JDWPContext getContext() {
        return context;
    }

    public Ids<Object> getIds() {
        return ids;
    }

    public SuspendedInfo getSuspendedInfo(Object thread) {
        return suspendedInfos.get(thread);
    }

    public boolean isSuspend() {
        return options.suspend;
    }

    public boolean isServer() {
        return options.server;
    }

    public int getListeningPort() {
        return Integer.parseInt(options.port);
    }

    public String getHost() {
        return options.host;
    }

    public void setCommandRequestId(Object thread, int commandRequestId, byte suspendPolicy, boolean isPopFrames, boolean isForceEarlyReturn, DebuggerCommand.Kind stepKind) {
        fine(() -> "Adding step command request in thread " + getThreadName(thread) + " with ID: " + commandRequestId);
        SteppingInfo steppingInfo = new SteppingInfo(commandRequestId, suspendPolicy, isPopFrames, isForceEarlyReturn, stepKind);
        commandRequestIds.put(thread, steppingInfo);
        context.steppingInProgress(getContext().asHostThread(thread), true);
    }

    /**
     * Installs a line breakpoint within a given method.
     *
     * @param command the command that represents the breakpoint
     */
    void submitLineBreakpoint(DebuggerCommand command) {
        SourceLocation location = command.getSourceLocation();
        try {
            Breakpoint bp = Breakpoint.newBuilder(location.getSource()).lineIs(location.getLineNumber()).build();
            bp.setEnabled(true);
            int ignoreCount = command.getRequestFilter().getIgnoreCount();
            if (ignoreCount > 0) {
                bp.setIgnoreCount(ignoreCount);
            }
            mapBreakpoint(bp, command.getBreakpointInfo());
            fine(() -> "Submitting breakpoint at " + bp.getLocationDescription());
            debuggerSession.install(bp);
            fine(() -> "Breakpoint submitted at " + bp.getLocationDescription());

        } catch (NoSuchSourceLineException ex) {
            // perhaps the debugger's view on the source is out of sync, in which case
            // the bytecode and source does not match.
            warning(() -> "Failed submitting breakpoint at non-existing location: " + location);
        }
    }

    void submitExceptionBreakpoint(DebuggerCommand command) {
        Breakpoint bp = Breakpoint.newExceptionBuilder(command.getBreakpointInfo().isCaught(), command.getBreakpointInfo().isUnCaught()).build();
        bp.setEnabled(true);
        int ignoreCount = command.getBreakpointInfo().getFilter().getIgnoreCount();
        if (ignoreCount > 0) {
            bp.setIgnoreCount(ignoreCount);
        }
        mapBreakpoint(bp, command.getBreakpointInfo());
        debuggerSession.install(bp);
        fine(() -> "exception breakpoint submitted");
    }

    @CompilerDirectives.TruffleBoundary
    private void mapBreakpoint(Breakpoint bp, BreakpointInfo info) {
        breakpointInfos.put(bp, info);
        info.addBreakpoint(bp);
    }

    public void clearBreakpoints() {
        for (Breakpoint breakpoint : debuggerSession.getBreakpoints()) {
            breakpoint.dispose();
        }
    }

    void stepOut(RequestFilter filter) {
        Object thread = filter.getStepInfo().getGuestThread();
        fine(() -> "STEP_OUT for thread: " + getThreadName(thread));

        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null && !(susp instanceof UnknownSuspendedInfo)) {
            doStepOut(susp);
        } else {
            fine(() -> "not STEPPING OUT for thread: " + getThreadName(thread));
        }
    }

    private void doStepOut(SuspendedInfo susp) {
        RootNode callerRoot = susp.getCallerRootNode();
        int stepOutBCI = context.getNextBCI(callerRoot, susp.getCallerFrame());
        SteppingInfo steppingInfo = commandRequestIds.get(susp.getThread());
        if (steppingInfo != null && stepOutBCI != -1) {
            // record the location that we'll land on after the step out completes
            MethodRef method = context.getMethodFromRootNode(callerRoot);
            if (method != null) {
                KlassRef klass = method.getDeclaringKlassRef();
                steppingInfo.setStepOutBCI(context.getIds().getIdAsLong(klass), context.getIds().getIdAsLong(method), stepOutBCI);
            }
        }
    }

    public void clearStepCommand(StepInfo stepInfo) {
        commandRequestIds.remove(stepInfo.getGuestThread());
    }

    public boolean popFrames(Object guestThread, CallFrame frameToPop, int packetId) {
        SuspendedInfo susp = suspendedInfos.get(guestThread);
        if (susp != null && !(susp instanceof UnknownSuspendedInfo)) {
            susp.getEvent().prepareUnwindFrame(frameToPop.getDebugStackFrame());
            setCommandRequestId(guestThread, packetId, SuspendStrategy.EVENT_THREAD, true, false, DebuggerCommand.Kind.SPECIAL_STEP);
            resume(guestThread);
            return true;
        }
        return false;
    }

    public boolean forceEarlyReturn(SuspendedInfo susp, Object guestThread, CallFrame frame, Object returnValue) {
        assert susp != null;
        assert frame != null;

        if (!(susp instanceof UnknownSuspendedInfo)) {
            // Truffle unwind will take us to exactly the right location in the caller method
            susp.getEvent().prepareUnwindFrame(frame.getDebugStackFrame(), frame.asDebugValue(returnValue));
            susp.setForceEarlyReturnInProgress();
            setCommandRequestId(guestThread, -1, SuspendStrategy.NONE, false, true, DebuggerCommand.Kind.SPECIAL_STEP);
            return true;
        }
        return false;
    }

    public boolean resume(Object thread) {
        SimpleLock lock = getSuspendLock(thread);
        synchronized (lock) {
            fine(() -> "Called resume thread: " + getThreadName(thread) + " with suspension count: " + threadSuspension.getSuspensionCount(thread));

            if (threadSuspension.getSuspensionCount(thread) == 0) {
                // already running, so nothing to do
                return true;
            }
            threadSuspension.resumeThread(thread);
            int suspensionCount = threadSuspension.getSuspensionCount(thread);

            if (suspensionCount == 0) {
                // only resume when suspension count reaches 0
                SuspendedInfo suspendedInfo = getSuspendedInfo(thread);
                SteppingInfo steppingInfo = commandRequestIds.get(thread);
                if (steppingInfo != null && !steppingInfo.isSubmitted()) {
                    // we're currently stepping, so make sure to
                    // commit the recorded step kind to Truffle
                    if (suspendedInfo != null && !suspendedInfo.isForceEarlyReturnInProgress()) {
                        DebuggerCommand.Kind stepKind = steppingInfo.getStepKind();
                        if (stepKind != null) {
                            switch (stepKind) {
                                // force early return doesn't trigger any events, so the debugger
                                // will send out a step command, to reach the next location, in this
                                // case the caller method after the return value has been obtained.
                                // Truffle handles this by Unwind and we will already reach the
                                // given location without performing the explicit step command, so
                                // we shouldn't prepare the event here.
                                case STEP_INTO:
                                    suspendedInfo.getEvent().prepareStepInto(STEP_CONFIG);
                                    steppingInfo.submit();
                                    break;
                                case STEP_OVER:
                                    suspendedInfo.getEvent().prepareStepOver(STEP_CONFIG);
                                    steppingInfo.submit();
                                    break;
                                case STEP_OUT:
                                    suspendedInfo.getEvent().prepareStepOut(STEP_CONFIG);
                                    steppingInfo.submit();
                                    break;
                                case SUBMIT_EXCEPTION_BREAKPOINT:
                                case SUBMIT_LINE_BREAKPOINT:
                                case SPECIAL_STEP:
                                    break;
                                default:
                                    throw new RuntimeException("should not reach here");
                            }
                        }
                    }
                }
                fine(() -> "resume call, clearing suspended info on: " + getThreadName(thread));

                suspendedInfos.put(thread, null);

                fine(() -> "Waking up thread: " + getThreadName(thread));
                threadSuspension.removeHardSuspendedThread(thread);
                lock.release();
                lock.notifyAll();
                return true;
            } else {
                fine(() -> "Not resuming thread: " + getThreadName(thread) + " with suspension count: " + threadSuspension.getSuspensionCount(thread));
                return false;
            }
        }
    }

    public Object[] getVisibleGuestThreads() {
        Object[] allThreads = context.getAllGuestThreads();
        ArrayList<Object> visibleThreads = new ArrayList<>(allThreads.length);
        for (Object thread : allThreads) {
            if (!instrument.isDebuggerThread(context.asHostThread(thread))) {
                visibleThreads.add(thread);
            }
        }
        return visibleThreads.toArray(new Object[0]);
    }

    void forceResumeAll() {
        ids.unpinAll();
        for (Object thread : getVisibleGuestThreads()) {
            boolean resumed = false;
            SimpleLock suspendLock = getSuspendLock(thread);
            synchronized (suspendLock) {
                while (!resumed) {
                    resumed = resume(thread);
                }
            }
        }
    }

    public void resumeAll() {
        ids.unpinAll();
        for (Object thread : getVisibleGuestThreads()) {
            SimpleLock suspendLock = getSuspendLock(thread);
            synchronized (suspendLock) {
                resume(thread);
            }
        }
    }

    public void suspend(Object guestThread) {
        SimpleLock suspendLock = getSuspendLock(guestThread);
        synchronized (suspendLock) {
            fine(() -> "suspend called for guestThread: " + getThreadName(guestThread) + " with suspension count " + threadSuspension.getSuspensionCount(guestThread));

            if (threadSuspension.getSuspensionCount(guestThread) > 0) {
                // already suspended, so only increase the suspension count
                threadSuspension.suspendThread(guestThread);
                return;
            }

            try {
                fine(() -> "State: " + getContext().asHostThread(guestThread).getState());
                fine(() -> "calling underlying suspend method for guestThread: " + getThreadName(guestThread));
                debuggerSession.suspend(getContext().asHostThread(guestThread));

                // quite often the Debug API will not call back the onSuspend method in time,
                // even if the guestThread is executing. If the guestThread is blocked or waiting we
                // still need to suspend it, thus we manage this with a hard suspend mechanism
                threadSuspension.addHardSuspendedThread(guestThread);
                suspendedInfos.put(guestThread, new UnknownSuspendedInfo(context, guestThread));
            } catch (Exception e) {
                fine(() -> "not able to suspend guestThread: " + getThreadName(guestThread));
            }
        }
    }

    /**
     * Immediately suspend the current guest thread at its current location. Typically, this method
     * is used in response to class prepare events.
     *
     * @param eventThread the guest thread which must correspond to the current host thread
     * @param suspendPolicy the policy for which threads to suspend
     * @param callBack a callback that is to be run when threads have been marked as suspended
     */
    public void immediateSuspend(Object eventThread, byte suspendPolicy, Callable<Void> callBack) {
        assert eventThread == context.asGuestThread(Thread.currentThread());

        switch (suspendPolicy) {
            case SuspendStrategy.ALL:
                // suspend all but the current thread
                // at next execution point
                for (Object thread : getVisibleGuestThreads()) {
                    if (context.asGuestThread(Thread.currentThread()) != thread) {
                        suspend(thread);
                    }
                }
                // pin all objects when VM in suspended state
                ids.pinAll();

                // immediately suspend the event thread
                suspend(eventThread, SuspendStrategy.EVENT_THREAD, Collections.singletonList(callBack), true);
                break;
            case SuspendStrategy.EVENT_THREAD:
                // immediately suspend the event thread
                suspend(eventThread, SuspendStrategy.EVENT_THREAD, Collections.singletonList(callBack), true);
                break;
        }
    }

    public void suspendAll() {
        fine(() -> "Called suspendAll");

        for (Object thread : getVisibleGuestThreads()) {
            suspend(thread);
        }
        // pin all objects
        ids.pinAll();
    }

    private synchronized SimpleLock getSuspendLock(Object thread) {
        SimpleLock lock = suspendLocks.get(thread);
        if (lock == null) {
            lock = new SimpleLock();
            suspendLocks.put(thread, lock);
        }
        return lock;
    }

    String getThreadName(Object thread) {
        return getContext().getThreadName(thread);
    }

    public void disposeDebugger(boolean prepareReconnect) {
        if (!prepareReconnect) {
            // OK, we're closing down the context which is equivalent
            // to a dead VM from a JDWP client point of view
            if (eventListener.vmDied()) {
                // we're asked to suspend
                suspend(context.asGuestThread(Thread.currentThread()), SuspendStrategy.EVENT_THREAD, Collections.emptyList(), true);
            }
        }
        instrument.reset(prepareReconnect);
    }

    public void endSession() {
        try {
            debuggerSession.close();
        } catch (IllegalStateException ex) {
            // already closed, ignore
        }
    }

    public JDWPOptions getOptions() {
        return options;
    }

    public void prepareFieldBreakpoint(FieldBreakpointEvent event) {
        fieldBreakpointExpected.put(Thread.currentThread(), event);
    }

    public void prepareMethodBreakpoint(MethodBreakpointEvent event) {
        methodBreakpointExpected.put(Thread.currentThread(), event);
    }

    public VirtualMachine getVirtualMachine() {
        return vm;
    }

    public GCPrevention getGCPrevention() {
        return gcPrevention;
    }

    public ThreadSuspension getThreadSuspension() {
        return threadSuspension;
    }

    public EventFilters getEventFilters() {
        return eventFilters;
    }

    public VMEventListener getEventListener() {
        return eventListener;
    }

    public Object enterTruffleContext() {
        if (truffleContext != null) {
            return truffleContext.enter(null);
        }
        return null;
    }

    public void leaveTruffleContext(Object previous) {
        if (truffleContext != null) {
            // pass null as previous since we know the jdwp thread only ever enters one context
            truffleContext.leave(null, previous);
        }
    }

    @Override
    public void onLanguageContextInitialized(TruffleContext con, @SuppressWarnings("unused") LanguageInfo language) {
        truffleContext = con;

        // With the Espresso context initialized, we can now complete the JDWP setup and establish
        // the connection.
        assert setupState != null;

        if (setupState.fatalConnectionError) {
            // OK, during JDWP initialization we failed to establish a connection,
            // so we have to abort the context
            System.err.println("JDWP exit error AGENT_ERROR_TRANSPORT_INIT(197): No transports initialized");
            context.exit(2);
            return; // return here for readability. Context.exit will terminate this thread.
        }
        CountDownLatch latch = new CountDownLatch(1);
        DebuggerConnection.establishDebuggerConnection(this, setupState, false, latch);

        // If we're told to suspend, or we're not operating in server mode, we wait until we're
        // sure that we have either established a working connection or failed to set one up.
        if (isSuspend() || !isServer()) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("JDWP exit error AGENT_ERROR_TRANSPORT_INIT(197): No transports initialized");
                context.exit(2);
                return; // return here for readability. Context.exit will terminate this thread.
            }
        }
        // make sure we have a working connection. If not, we exit the context.
        if (lateStartupError != null) {
            System.err.println("ERROR: transport error 202: connect failed: " + lateStartupError.getMessage());
            System.err.println("ERROR: JDWP Transport dt_socket failed to initialize, TRANSPORT_INIT(510)");
            System.err.println("JDWP exit error AGENT_ERROR_TRANSPORT_INIT(197): No transports initialized");
            context.exit(2);
            return; // return here for readability. Context.exit will terminate this thread.
        }
        if (isSuspend()) {
            // only a JDWP resume/resumeAll command can resume this thread
            suspend(context.asGuestThread(Thread.currentThread()), SuspendStrategy.EVENT_THREAD, Collections.singletonList(() -> {
                // By passing this as a job to the suspend method, we're making sure we only
                // send the vm started event after the thread suspension has been bumped.
                // For the suspend=n case the VM started event is sent as soon as a debugger
                // connection has been established, which might be long after this method returns.
                getEventListener().vmStarted(true);
                return null;
            }), true);
        }
    }

    public void suspend(Object thread, byte suspendPolicy, List<Callable<Void>> jobs, boolean forceSuspend) {
        fine(() -> "suspending from callback in thread: " + getThreadName(thread));

        // before sending any events to debugger, make sure to mark
        // the thread lock as locked, in case a resume command happens
        // shortly thereafter, with the risk of a race (lost notify)
        SimpleLock suspendLock = getSuspendLock(thread);
        synchronized (suspendLock) {
            suspendLock.acquire();
        }

        switch (suspendPolicy) {
            case SuspendStrategy.NONE:
                runJobs(jobs);
                break;
            case SuspendStrategy.EVENT_THREAD:
                fine(() -> "Suspend EVENT_THREAD");
                suspendEventThread(thread, forceSuspend, jobs);
                break;
            case SuspendStrategy.ALL:
                fine(() -> "Suspend ALL");

                Thread suspendThread = new Thread(() -> {
                    // suspend other threads
                    for (Object activeThread : getVisibleGuestThreads()) {
                        if (activeThread != thread) {
                            fine(() -> "Request thread suspend for other thread: " + getThreadName(activeThread));
                            DebuggerController.this.suspend(activeThread);
                        }
                    }
                });
                suspendThread.start();
                // pin all objects
                ids.pinAll();
                suspendEventThread(thread, forceSuspend, jobs);
                break;
        }
    }

    private static void runJobs(List<Callable<Void>> jobs) {
        for (Callable<Void> job : jobs) {
            try {
                job.call();
            } catch (Exception e) {
                throw new RuntimeException("failed to send event to debugger", e);
            }
        }
    }

    private void suspendEventThread(Object thread, boolean forceSuspend, List<Callable<Void>> jobs) {
        fine(() -> "Suspending event thread: " + getThreadName(thread) + " with new suspension count: " + threadSuspension.getSuspensionCount(thread));
        lockThread(thread, forceSuspend, jobs);
    }

    private void lockThread(Object thread, boolean forceSuspend, List<Callable<Void>> jobs) {
        SimpleLock lock = getSuspendLock(thread);
        synchronized (lock) {
            if (!forceSuspend && !threadSuspension.isHardSuspended(thread)) {
                // thread was resumed from other command, so don't suspend now
                return;
            }

            if (lock.isLocked()) {
                threadSuspension.suspendThread(thread);
                runJobs(jobs);
            }
        }
        while (!Thread.currentThread().isInterrupted()) {
            try {
                synchronized (lock) {
                    if (!lock.isLocked()) {
                        // released from other thread, so break loop
                        break;
                    }
                    // no reason to hold a hard suspension status, since now
                    // we have the actual suspension status and suspended information
                    threadSuspension.removeHardSuspendedThread(thread);
                    fine(() -> "lock.wait() for thread: " + getThreadName(thread));
                    // Having the thread lock, we can check if an invoke job was posted outside of
                    // locking, and if so, we postpone blocking the thread until next time around.
                    if (!invokeJobs.containsKey(thread)) {
                        lock.wait();
                    }
                }
            } catch (InterruptedException e) {
                // the thread was interrupted, so let it run dry
                // make sure the interrupted flag is set though
                Thread.currentThread().interrupt();
            }
            checkInvokeJobsAndRun(thread);
        }
        fine(() -> "lock wakeup for thread: " + getThreadName(thread));
    }

    private void checkInvokeJobsAndRun(Object thread) {
        if (invokeJobs.containsKey(thread)) {
            InvokeJob<?> job = invokeJobs.remove(thread);
            job.runJob(this);
        }
    }

    public void postInvokeJobForThread(InvokeJob<?> job) {
        SimpleLock lock = getSuspendLock(job.getThread());
        synchronized (lock) {
            invokeJobs.put(job.getThread(), job);
            lock.notifyAll();
        }
    }

    public CallFrame[] getCallFrames(Object guestThread) {
        List<CallFrame> callFrames = new ArrayList<>();
        Truffle.getRuntime().iterateFrames(frameInstance -> {
            KlassRef klass;
            MethodRef method;
            RootNode root = getRootNode(frameInstance);
            if (root == null) {
                return null;
            }
            method = getContext().getMethodFromRootNode(root);
            if (method == null) {
                return null;
            }

            klass = method.getDeclaringKlassRef();
            long klassId = ids.getIdAsLong(klass);
            long methodId = ids.getIdAsLong(method);
            byte typeTag = TypeTag.getKind(klass);

            Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE);
            // for bytecode-based languages (Espresso) we can read the precise bci from the
            // frame
            long codeIndex = -1;
            try {
                codeIndex = context.readBCIFromFrame(root, frame);
            } catch (Throwable t) {
                fine(() -> "Unable to read current BCI from frame in method: " + klass.getNameAsString() + "." + method.getNameAsString());
            }
            if (codeIndex == -1) {
                // fall back to start of the method then
                codeIndex = 0;
            }

            // check if current bci is higher than the first index on the last line,
            // in which case we must report the last line index instead
            long lastLineBCI = method.getBCIFromLine(method.getLastLine());
            if (codeIndex > lastLineBCI) {
                codeIndex = lastLineBCI;
            }
            Node currentNode = frameInstance.getCallNode();
            if (currentNode == null) {
                CallTarget callTarget = frameInstance.getCallTarget();
                if (callTarget instanceof RootCallTarget) {
                    currentNode = ((RootCallTarget) callTarget).getRootNode();
                }
            }
            if (currentNode instanceof RootNode) {
                currentNode = context.getInstrumentableNode((RootNode) currentNode);
            }
            callFrames.add(new CallFrame(context.getIds().getIdAsLong(guestThread), typeTag, klassId, method, methodId, codeIndex, frame, currentNode, root, null, context,
                            DebuggerController.this));
            return null;
        });
        return callFrames.toArray(new CallFrame[0]);
    }

    private RootNode getRootNode(FrameInstance frameInstance) {
        CallTarget callTarget = frameInstance.getCallTarget();
        if (callTarget == null) {
            return null;
        }
        if (callTarget instanceof RootCallTarget rootCallTarget) {
            RootNode rootNode = rootCallTarget.getRootNode();
            // check if we can read the current bci to validate
            try {
                context.readBCIFromFrame(rootNode, frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY));
            } catch (Throwable t) {
                // cannot use this root node for reading bci
                return null;
            }
            return rootNode;
        }
        return null;
    }

    private class SuspendedCallbackImpl implements SuspendedCallback {

        @Override
        public void onSuspend(SuspendedEvent event) {
            Thread hostThread = Thread.currentThread();
            if (instrument.isDebuggerThread(hostThread)) {
                // always allow VM threads to run guest code without
                // the risk of being suspended
                return;
            }
            if (connection == null || !connection.isOpen()) {
                return;
            }

            Object currentThread = getContext().asGuestThread(hostThread);
            fine(() -> "Suspended at: " + event.getSourceSection() + " in thread: " + getThreadName(currentThread));

            SteppingInfo steppingInfo = commandRequestIds.remove(currentThread);
            if (steppingInfo != null) {
                if (steppingInfo.isForceEarlyReturn()) {
                    fine(() -> "not suspending here due to force early return: " + event.getSourceSection());
                    context.steppingInProgress(hostThread, false);
                    return;
                }
                CallFrame[] callFrames = createCallFrames(ids.getIdAsLong(currentThread), event.getStackFrames(), 1, steppingInfo);
                // get the top frame for checking instance filters
                CallFrame callFrame = null;
                if (callFrames.length > 0) {
                    callFrame = callFrames[0];
                }
                if (checkExclusionFilters(steppingInfo, event, currentThread, callFrame)) {
                    fine(() -> "not suspending here: " + event.getSourceSection());
                    // continue stepping until completed
                    commandRequestIds.put(currentThread, steppingInfo);
                    return;
                }
            }

            CallFrame[] callFrames = createCallFrames(ids.getIdAsLong(currentThread), event.getStackFrames(), -1, steppingInfo);
            RootNode callerRootNode = callFrames.length > 1 ? callFrames[1].getRootNode() : null;

            SuspendedInfo suspendedInfo = new SuspendedInfo(context, event, callFrames, currentThread, callerRootNode);
            suspendedInfos.put(currentThread, suspendedInfo);

            // collect any events that need to be sent to the debugger once we're done here
            List<Callable<Void>> jobs = new ArrayList<>();
            BreakpointHitResult result = new BreakpointHitResult(false, SuspendStrategy.EVENT_THREAD, false);

            if (steppingInfo != null) {
                if (event.isStep() || event.isUnwind()) {
                    fine(() -> "step was completed");
                    jobs.add(() -> {
                        eventListener.stepCompleted(steppingInfo, callFrames[0]);
                        return null;
                    });
                } else {
                    fine(() -> "step not completed - check for breakpoints");

                    result = checkForBreakpoints(event, jobs, currentThread, callFrames);
                    if (!result.breakpointHit) {
                        // no breakpoint
                        continueStepping(event, steppingInfo, currentThread);
                        commandRequestIds.put(currentThread, steppingInfo);
                    }
                }
            } else {
                result = checkForBreakpoints(event, jobs, currentThread, callFrames);
            }
            if (!commandRequestIds.containsKey(currentThread)) {
                // we're done stepping then
                context.steppingInProgress(hostThread, false);
            }
            if (result.skipSuspend) {
                return;
            }
            // now, suspend the current thread until resumed by e.g. a debugger command
            suspend(currentThread, result.suspendPolicy, jobs, result.breakpointHit || event.isStep() || event.isUnwind());
        }

        private BreakpointHitResult checkForBreakpoints(SuspendedEvent event, List<Callable<Void>> jobs, Object currentThread, CallFrame[] callFrames) {
            boolean handledLineBreakpoint = false;
            boolean hit = false;
            byte suspendPolicy = SuspendStrategy.EVENT_THREAD;
            HashSet<Breakpoint> handled = new HashSet<>(event.getBreakpoints().size());
            for (Breakpoint bp : event.getBreakpoints()) {
                if (handled.contains(bp)) {
                    continue;
                }
                BreakpointInfo info = breakpointInfos.get(bp);
                suspendPolicy = info.getSuspendPolicy();

                if (info instanceof LineBreakpointInfo lineBreakpointInfo) {
                    // only allow one line breakpoint to avoid confusing the debugger
                    if (handledLineBreakpoint) {
                        continue;
                    }

                    if (!callFrames[0].getMethod().hasLine((int) lineBreakpointInfo.getLine())) {
                        return new BreakpointHitResult(false, suspendPolicy, true);
                    }

                    handledLineBreakpoint = true;
                    hit = true;
                    // check if breakpoint request limited to a specific thread
                    Object thread = info.getThread();
                    if (thread == null || thread == currentThread) {
                        jobs.add(() -> {
                            eventListener.breakpointHit(info, callFrames[0], currentThread);
                            return null;
                        });
                    }
                } else if (info.isExceptionBreakpoint()) {
                    // get the specific exception type if any
                    Throwable exception = event.getException().getRawException(context.getLanguageClass());
                    if (exception == null) {
                        fine(() -> "Unable to retrieve raw exception for " + event.getException());
                        // failed to get the raw exception, so don't suspend here.
                        suspendedInfos.put(currentThread, null);
                        return new BreakpointHitResult(false, suspendPolicy, true);
                    }
                    Object guestException = getContext().getGuestException(exception);
                    fine(() -> "checking exception breakpoint for exception: " + exception);
                    // TODO(Gregersen) - rewrite this when instanceof implementation in
                    // Truffle is completed See /browse/GR-10371
                    // Currently, the Truffle Debug API doesn't filter on
                    // type, so we end up here having to check also, the
                    // ignore count set on the breakpoint will not work
                    // properly due to this.
                    // we need to do a real type check here, since subclasses
                    // of the specified exception should also hit.
                    KlassRef klass = info.getKlass(); // null means no filtering
                    if (klass == null) {
                        // always hit when broad exception filter is used
                        hit = true;
                    } else if (getContext().isInstanceOf(guestException, klass)) {
                        fine(() -> "Exception type matched the klass type: " + klass.getNameAsString());
                        // check filters if we should not suspend
                        Pattern[] positivePatterns = info.getFilter().getIncludePatterns();
                        // verify include patterns
                        if (positivePatterns == null || positivePatterns.length == 0 || matchLocation(positivePatterns, callFrames[0])) {
                            // verify exclude patterns
                            Pattern[] negativePatterns = info.getFilter().getExcludePatterns();
                            if (negativePatterns == null || negativePatterns.length == 0 || !matchLocation(negativePatterns, callFrames[0])) {
                                hit = true;
                            }
                        }
                    }
                    if (hit) {
                        fine(() -> "Breakpoint hit in thread: " + getThreadName(currentThread));

                        jobs.add(() -> {
                            eventListener.exceptionThrown(info, currentThread, guestException, callFrames);
                            return null;
                        });
                    } else {
                        // don't suspend here
                        suspendedInfos.put(currentThread, null);
                        return new BreakpointHitResult(false, suspendPolicy, true);
                    }
                }
                handled.add(bp);
            }

            // check if suspended for a field breakpoint
            FieldBreakpointEvent fieldEvent = fieldBreakpointExpected.remove(Thread.currentThread());
            if (fieldEvent != null) {
                FieldBreakpointInfo info = fieldEvent.getInfo();
                if (info.isAccessBreakpoint()) {
                    hit = true;
                    jobs.add(() -> {
                        eventListener.fieldAccessBreakpointHit(fieldEvent, currentThread, callFrames[0]);
                        return null;
                    });
                } else if (info.isModificationBreakpoint()) {
                    hit = true;
                    jobs.add(() -> {
                        eventListener.fieldModificationBreakpointHit(fieldEvent, currentThread, callFrames[0]);
                        return null;
                    });
                }
            }
            // check if suspended for a method breakpoint
            MethodBreakpointEvent methodEvent = methodBreakpointExpected.remove(Thread.currentThread());
            if (methodEvent != null) {
                hit = true;
                jobs.add(() -> {
                    eventListener.methodBreakpointHit(methodEvent, currentThread, callFrames[0]);
                    return null;
                });
            }

            return new BreakpointHitResult(hit, suspendPolicy, false);
        }

        private boolean matchLocation(Pattern[] patterns, CallFrame callFrame) {
            KlassRef klass = (KlassRef) ids.fromId((int) callFrame.getClassId());

            for (Pattern pattern : patterns) {
                fine(() -> "Matching klass: " + klass.getNameAsString() + " against pattern: " + pattern.pattern());
                if (pattern.pattern().matches(klass.getNameAsString().replace('/', '.'))) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkExclusionFilters(SteppingInfo info, SuspendedEvent event, Object thread, CallFrame frame) {
            if (info != null) {
                if (isSingleSteppingSuspended()) {
                    continueStepping(event, info, thread);
                    return true;
                }
                if (frame == null) {
                    return false;
                }
                RequestFilter requestFilter = eventFilters.getRequestFilter(info.getRequestId());

                if (requestFilter != null && requestFilter.getStepInfo() != null) {
                    // we're currently stepping, so check if suspension point
                    // matches any exclusion filters
                    if (requestFilter.getThisFilterId() != 0) {
                        Object filterObject = context.getIds().fromId((int) requestFilter.getThisFilterId());
                        Object thisObject = frame.getThisValue();
                        if (filterObject != thisObject) {
                            continueStepping(event, info, thread);
                            return true;
                        }
                    }

                    KlassRef klass = (KlassRef) context.getIds().fromId((int) frame.getClassId());

                    if (klass != null && requestFilter.isKlassExcluded(klass)) {
                        // should not suspend here then, tell the event to keep going
                        continueStepping(event, info, thread);
                        return true;
                    }
                }
            }
            return false;
        }

        private void continueStepping(SuspendedEvent event, SteppingInfo steppingInfo, Object thread) {
            switch (steppingInfo.getStepKind()) {
                case STEP_INTO:
                    // stepping into unwanted code which was filtered
                    // so step out and try step into again
                    event.prepareStepOut(STEP_CONFIG).prepareStepInto(STEP_CONFIG);
                    break;
                case STEP_OVER:
                    event.prepareStepOver(STEP_CONFIG);
                    break;
                case STEP_OUT:
                    SuspendedInfo info = getSuspendedInfo(thread);
                    if (info != null) {
                        doStepOut(info);
                    }
                    break;
                default:
                    break;
            }
        }

        private CallFrame[] createCallFrames(long threadId, Iterable<DebugStackFrame> stackFrames, int frameLimit, SteppingInfo steppingInfo) {
            LinkedList<CallFrame> list = new LinkedList<>();
            int frameCount = 0;
            for (DebugStackFrame frame : stackFrames) {
                if (frame.getSourceSection() == null) {
                    continue;
                }

                long klassId;
                long methodId;
                byte typeTag;
                long codeIndex;

                Node rawNode = frame.getRawNode(context.getLanguageClass());
                if (rawNode == null) {
                    continue;
                }
                RootNode root = rawNode.getRootNode();
                if (root == null) {
                    // since we can't lookup the root node, we have to
                    // construct a jdwp-like location from the frame
                    // TODO(Gregersen) - add generic polyglot jdwp frame representation
                    continue;
                }

                Frame rawFrame = frame.getRawFrame(context.getLanguageClass(), FrameInstance.FrameAccess.READ_WRITE);
                MethodRef method = context.getMethodFromRootNode(root);
                KlassRef klass = method.getDeclaringKlassRef();

                klassId = ids.getIdAsLong(klass);
                methodId = ids.getIdAsLong(method);
                typeTag = TypeTag.getKind(klass);

                // check if we have a dedicated step out code index on the top frame
                if (frameCount == 0 && steppingInfo != null && steppingInfo.isStepOutFrame(methodId, klassId)) {
                    codeIndex = steppingInfo.getStepOutBCI();
                } else {
                    codeIndex = context.getBCI(rawNode, rawFrame);
                }

                list.addLast(new CallFrame(threadId, typeTag, klassId, method, methodId, codeIndex, rawFrame, rawNode, root, frame, context, DebuggerController.this));
                frameCount++;
                if (frameLimit != -1 && frameCount >= frameLimit) {
                    return list.toArray(new CallFrame[0]);
                }
            }
            return list.toArray(new CallFrame[0]);
        }

        private static final class BreakpointHitResult {
            private final boolean breakpointHit;
            private final byte suspendPolicy;
            private final boolean skipSuspend;

            BreakpointHitResult(boolean breakpointHit, byte suspendPolicy, boolean skipSuspend) {
                this.breakpointHit = breakpointHit;
                this.suspendPolicy = suspendPolicy;
                this.skipSuspend = skipSuspend;
            }
        }
    }

    private boolean isSingleSteppingSuspended() {
        return context.isSingleSteppingDisabled();
    }

    // Truffle logging
    public void info(Supplier<String> supplier) {
        jdwpLogger.info(supplier);
    }

    public void fine(Supplier<String> supplier) {
        jdwpLogger.fine(supplier);
    }

    public void finest(Supplier<String> supplier) {
        jdwpLogger.finest(supplier);
    }

    public void warning(Supplier<String> supplier) {
        jdwpLogger.warning(supplier);
    }

    public void severe(Supplier<String> supplier) {
        jdwpLogger.severe(supplier);
    }

    public void severe(String message, Throwable error) {
        jdwpLogger.log(Level.SEVERE, message, error);
    }

    @Override
    public void onContextCreated(@SuppressWarnings("unused") TruffleContext con) {

    }

    @Override
    public void onLanguageContextCreated(@SuppressWarnings("unused") TruffleContext con, @SuppressWarnings("unused") LanguageInfo language) {

    }

    @Override
    public void onLanguageContextFinalized(@SuppressWarnings("unused") TruffleContext con, @SuppressWarnings("unused") LanguageInfo language) {

    }

    @Override
    public void onLanguageContextDisposed(@SuppressWarnings("unused") TruffleContext con, @SuppressWarnings("unused") LanguageInfo language) {

    }

    @Override
    public void onContextClosed(@SuppressWarnings("unused") TruffleContext con) {

    }

}
