/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core;

import java.util.concurrent.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.objects.*;
import com.oracle.truffle.ruby.runtime.subsystems.*;

/**
 * Represents the Ruby {@code Fiber} class. The current implementation uses Java threads and message
 * passing. Note that the relationship between Java threads, Ruby threads and Ruby fibers is
 * complex. A Java thread might be running a fiber that on difference resumptions is representing
 * different Ruby threads. Take note of the lock contracts on {@link #waitForResume} and
 * {@link #resume}.
 */
public class RubyFiber extends RubyObject {

    public static class RubyFiberClass extends RubyClass {

        public RubyFiberClass(RubyClass objectClass) {
            super(null, objectClass, "Fiber");
        }

        @Override
        public RubyBasicObject newInstance() {
            return new RubyFiber(this, getContext().getFiberManager(), getContext().getThreadManager());
        }

    }

    private interface FiberMessage {
    }

    private class FiberResumeMessage implements FiberMessage {

        private final RubyThread thread;
        private final RubyFiber sendingFiber;
        private final Object arg;

        public FiberResumeMessage(RubyThread thread, RubyFiber sendingFiber, Object arg) {
            this.thread = thread;
            this.sendingFiber = sendingFiber;
            this.arg = arg;
        }

        public RubyThread getThread() {
            return thread;
        }

        public RubyFiber getSendingFiber() {
            return sendingFiber;
        }

        public Object getArg() {
            return arg;
        }

    }

    private class FiberExitMessage implements FiberMessage {
    }

    public class FiberExitException extends ControlFlowException {

        private static final long serialVersionUID = 1522270454305076317L;

    }

    private final FiberManager fiberManager;
    private final ThreadManager threadManager;

    private BlockingQueue<FiberMessage> messageQueue = new ArrayBlockingQueue<>(1);
    public RubyFiber lastResumedByFiber = null;

    public RubyFiber(RubyClass rubyClass, FiberManager fiberManager, ThreadManager threadManager) {
        super(rubyClass);
        this.fiberManager = fiberManager;
        this.threadManager = threadManager;
    }

    public void initialize(RubyProc block) {
        final RubyFiber finalFiber = this;
        final RubyProc finalBlock = block;

        new Thread(new Runnable() {

            @Override
            public void run() {
                fiberManager.registerFiber(finalFiber);

                try {
                    try {
                        final Object arg = finalFiber.waitForResume();
                        final Object result = finalBlock.call(null, arg);
                        finalFiber.lastResumedByFiber.resume(finalFiber, result);
                    } catch (FiberExitException e) {
                        // Naturally exit the thread on catching this
                    }
                } finally {
                    fiberManager.unregisterFiber(finalFiber);
                }
            }

        }).start();
    }

    /**
     * Send the Java thread that represents this fiber to sleep until it recieves a resume or exit
     * message. On entry, assumes that the GIL is not held. On exit, holding the GIL.
     */
    public Object waitForResume() {
        FiberMessage message = null;

        do {
            try {
                // TODO(cs) what is a suitable timeout?
                message = messageQueue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Poll again
            }
        } while (message == null);

        if (message instanceof FiberExitMessage) {
            throw new FiberExitException();
        }

        final FiberResumeMessage resumeMessage = (FiberResumeMessage) message;

        threadManager.enterGlobalLock(resumeMessage.getThread());

        fiberManager.setCurrentFiber(this);

        lastResumedByFiber = resumeMessage.getSendingFiber();
        return resumeMessage.getArg();
    }

    /**
     * Send a message to a fiber by posting into a message queue. Doesn't explicitly notify the Java
     * thread (although the queue implementation may) and doesn't wait for the message to be
     * received. On entry, assumes the the GIL is held. On exit, not holding the GIL.
     */
    public void resume(RubyFiber sendingFiber, Object... args) {
        Object arg;

        if (args.length == 0) {
            arg = NilPlaceholder.INSTANCE;
        } else if (args.length == 1) {
            arg = args[0];
        } else {
            arg = RubyArray.specializedFromObjects(getRubyClass().getContext().getCoreLibrary().getArrayClass(), args);
        }

        final RubyThread runningThread = threadManager.leaveGlobalLock();

        messageQueue.add(new FiberResumeMessage(runningThread, sendingFiber, arg));
    }

    public void shutdown() {
        messageQueue.add(new FiberExitMessage());
    }

}
