/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMThread;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@NodeChild(type = LLVMExpressionNode.class, value = "signal")
@NodeChild(type = LLVMExpressionNode.class, value = "handler")
public abstract class LLVMSignal extends LLVMExpressionNode {

    @Specialization
    protected LLVMPointer doSignal(int signal, LLVMPointer handler,
                    @CachedContext(LLVMLanguage.class) LLVMContext context,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        return setSignalHandler(context, signal, toNative.executeWithTarget(handler));
    }

    private static LLVMPointer setSignalHandler(LLVMContext context, int signalId, LLVMNativePointer function) {
        try {
            Signals decodedSignal = Signals.decode(signalId);
            return setSignalHandler(context, decodedSignal.signal(), function);
        } catch (NoSuchElementException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return context.getSigErr();
        }
    }

    private static final Lock globalSignalHandlerLock = new ReentrantLock();

    private static final Map<Integer, LLVMSignalHandler> registeredSignals = new HashMap<>();

    @TruffleBoundary
    public static int getNumberOfRegisteredSignals() {
        return registeredSignals.size();
    }

    @TruffleBoundary
    private static LLVMPointer setSignalHandler(LLVMContext context, Signal signal, LLVMNativePointer function) {
        int signalId = signal.getNumber();
        LLVMPointer returnFunction = context.getSigDfl();

        try {
            LLVMSignalHandler newSignalHandler = new LLVMSignalHandler(context, signal, function);
            synchronized (registeredSignals) {
                if (registeredSignals.containsKey(signalId)) {

                    LLVMSignalHandler currentFunction = registeredSignals.get(signalId);

                    if (currentFunction.isRunning()) {
                        returnFunction = currentFunction.getFunction();

                        /*
                         * the new signal handler already manages this signal, so we can safely
                         * deactivate the old one.
                         */
                        currentFunction.setStopped();
                    }
                }

                registeredSignals.put(signalId, newSignalHandler);
            }
        } catch (IllegalArgumentException e) {
            return context.getSigErr();
        }

        return returnFunction;
    }

    /**
     * Registers a signal handler using sun.misc.SignalHandler. Unfortunately, using signals in Java
     * leads to some problems which are not resolved in our implementation yet.
     *
     * One of these issues is that signals are executed in an asynchronous way which means raise()
     * exits before the signal was handled. Another issue is that Java already registers some signal
     * handlers, which therefore cannot be used in Sulong.
     *
     * Therefore, our implementation does not comply with the ANSI C standard and could lead to
     * timing issues when calling multiple signals in a defined sequence, or when a program has to
     * wait until the signal was handled (which is not guaranteed because of the asynchronous
     * behavior in our implementation). E.g., for a SIGINT handler that does some cleanup work and
     * exits the application afterwards, we will get race conditions between the application and the
     * asynchronously executed cleanup code.
     */
    private static final class LLVMSignalHandler implements SignalHandler, LLVMThread {

        private final Signal signal;
        private final LLVMContext context;
        private final LLVMPointer handler;

        private final Lock lock = new ReentrantLock();
        private final AtomicBoolean isRunning = new AtomicBoolean(false);

        @TruffleBoundary
        private LLVMSignalHandler(LLVMContext context, Signal signal, LLVMPointer function) throws IllegalArgumentException {
            this.signal = signal;
            this.context = context;

            lock.lock();
            try {
                if (function.isSame(context.getSigDfl())) {
                    this.handler = function;
                    Signal.handle(signal, SignalHandler.SIG_DFL);
                } else if (function.isSame(context.getSigIgn())) {
                    this.handler = function;
                    Signal.handle(signal, SignalHandler.SIG_IGN);
                } else {
                    this.handler = function;
                    Signal.handle(signal, this);
                }

                isRunning.set(true);
                context.registerThread(this);
            } catch (IllegalArgumentException e) {
                throw e;
            } finally {
                lock.unlock();
            }
        }

        public LLVMPointer getFunction() {
            return handler;
        }

        @TruffleBoundary
        public boolean isRunning() {
            return isRunning.get();
        }

        @Override
        @TruffleBoundary
        protected void finalize() throws Throwable {
            super.finalize();
            stop();
            unregisterFromContext();
        }

        private static final long HANDLE_MAX_WAITING_TIME = 250; // ms

        @Override
        @TruffleBoundary
        public void handle(Signal arg0) {
            try {
                if (!globalSignalHandlerLock.tryLock(HANDLE_MAX_WAITING_TIME, TimeUnit.MILLISECONDS)) {
                    System.err.println("could not execute signal handler. Sulong can currently only execute one signal at once!");
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            lock.lock();
            try {
                if (isRunning.get()) {
                    try {
                        TruffleContext truffleContext = context.getEnv().getContext();
                        Object p = truffleContext.enter();
                        try {
                            InteropLibrary.getFactory().getUncached().execute(handler, signal.getNumber());
                        } finally {
                            truffleContext.leave(p);
                        }
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                        throw new AssertionError(e);
                    }
                }
            } finally {
                lock.unlock();
                globalSignalHandlerLock.unlock();
            }

            // probably, this was our last turn for the signal handler
            if (!isRunning.get()) {
                unregisterFromContext();
            }
        }

        /**
         * Required to call if a new LLVMSignalHandler take over the signal. Otherwise it would
         * unregister the signal when this Object is going to be deallocated or stopped.
         */
        @TruffleBoundary
        private void setStopped() {
            isRunning.set(false);

            /*
             * Either no handler is currently running, or it would unregister after finishing it's
             * execution.
             */
            tryUnregisterFromContext();
        }

        @Override
        @TruffleBoundary
        public void stop() {
            if (isRunning.getAndSet(false)) {
                /*
                 * it seems like we don't want to catch this signal anymore, as well as there is no
                 * other signal handler which want to catch it. So we simply reset it to look like
                 * no signal handler was registered at all.
                 */
                Signal.handle(signal, SignalHandler.SIG_DFL);
            }

            /*
             * Either no handler is currently running, or it would unregister after finishing it's
             * execution.
             */
            tryUnregisterFromContext();
        }

        @Override
        @TruffleBoundary
        public void awaitFinish() {
            stop();

            // wait until handle is finished
            lock.lock();
            lock.unlock();

            // this thread wouldn't start anymore, so we can assume it's stopped
            unregisterFromContext();
        }

        /**
         * Unregister this SignalHandler from context.
         */
        @TruffleBoundary
        private void unregisterFromContext() {
            assert !isRunning.get();

            context.unregisterThread(this);

            int signalId = signal.getNumber();
            synchronized (registeredSignals) {
                if (registeredSignals.get(signalId) == this) {
                    registeredSignals.remove(signalId);
                }
            }
        }

        /**
         * Only unregister this SignalHandler, if there is currently no lock held.
         */
        @TruffleBoundary
        private boolean tryUnregisterFromContext() {
            assert !isRunning.get();

            if (lock.tryLock()) {
                try {
                    unregisterFromContext();
                } finally {
                    lock.unlock();
                }
                return true;
            }
            return false;
        }

        @Override
        @TruffleBoundary
        public String toString() {
            return "LLVMSignalHandler [signal=" + signal + ", lock=" + lock + ", isRunning=" + isRunning + "]";
        }
    }

    /**
     * Handling conversation from Signal number to Signal Object in a clean way.
     *
     * documentation: http://man7.org/linux/man-pages/man7/signal.7.html
     */
    private enum Signals {

        // POSIX.1-1990
        SIG_HUP("HUP"),
        SIG_INT("INT"),
        SIG_QUIT("QUIT"),
        SIG_ILL("ILL"),
        SIG_ABRT("ABRT"),
        SIG_FPE("FPE"),
        SIG_KILL("KILL"),
        SIG_SEGV("SEGV"),
        SIG_PIPE("PIPE"),
        SIG_ALRM("ALRM"),
        SIG_TERM("TERM"),
        SIG_USR1("USR1"),
        SIG_USR2("USR2"),
        SIG_CHLD("CHLD"),
        SIG_CONT("CONT"),
        SIG_STOP("STOP"),
        SIG_TSTP("TSTP"),
        SIG_TTIN("TTIN"),

        // SUSv2 and POSIX.1-2001
        SIG_BUS("BUS"),
        SIG_POLL("POLL"),
        SIG_PROF("PROF"),
        SIG_SYS("SYS"),
        SIG_TRAP("TRAP"),
        SIG_URG("URG"),
        SIG_VTALRM("VTALRM"),
        SIG_XCPU("XCPU"),
        SIG_XFSZ("XFSZ"),

        // various other signals
        SIG_IOT("IOT"),
        SIG_EMT("EMT"),
        SIG_STKFLT("STKFLT"),
        SIG_IO("IO"),
        SIG_CLD("CLD"),
        SIG_PWR("PWR"),
        SIG_INFO("INFO"),
        SIG_LOST("LOST"),
        SIG_WINCH("WINCH"),
        SIG_UNUSED("UNUSED");

        private static final Signals[] VALUES = values();

        @TruffleBoundary
        public static Signals decode(int code) throws NoSuchElementException {
            for (Signals currentSignal : VALUES) {
                if (currentSignal.signal() != null && currentSignal.signal().getNumber() == code) {
                    return currentSignal;
                }
            }
            throw new NoSuchElementException("signal with the id " + code + " not found");
        }

        private final Signal signal;

        Signals(String signalName) {
            Signal constructedSignal;
            try {
                constructedSignal = new Signal(signalName);
            } catch (IllegalArgumentException e) {
                // it seems like this signal is not available on your system
                constructedSignal = null;
            }
            this.signal = constructedSignal;
        }

        public Signal signal() {
            return signal;
        }
    }
}
