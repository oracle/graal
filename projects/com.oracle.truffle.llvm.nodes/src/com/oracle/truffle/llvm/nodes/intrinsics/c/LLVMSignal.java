/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.c;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMThread;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@GenerateNodeFactory
@NodeChildren({@NodeChild(type = LLVMExpressionNode.class, value = "signal"), @NodeChild(type = LLVMExpressionNode.class, value = "handler")})
public abstract class LLVMSignal extends LLVMExpressionNode {

    // #define SIG_DFL ((__sighandler_t) 0) /* Default action. */
    private static final LLVMFunction LLVM_SIG_DFL = new LLVMFunctionHandle(0);

    // # define SIG_IGN ((__sighandler_t) 1) /* Ignore signal. */
    private static final LLVMFunction LLVM_SIG_IGN = new LLVMFunctionHandle(1);

    // #define SIG_ERR ((__sighandler_t) -1) /* Error return. */
    private static final LLVMFunction LLVM_SIG_ERR = new LLVMFunctionHandle(-1);

    @Specialization
    public LLVMFunction doSignal(int signal, LLVMFunction handler) {
        return setSignalHandler(signal, handler);
    }

    private static LLVMFunction setSignalHandler(int signalId, LLVMFunction function) {
        try {
            Signals decodedSignal = Signals.decode(signalId);
            return setSignalHandler(decodedSignal.signal(), function);
        } catch (NoSuchElementException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMLogger.error(e.getMessage());
            return LLVM_SIG_ERR;
        }
    }

    private static Lock globalSignalHandlerLock = new ReentrantLock();

    private static final Map<Integer, LLVMSignalHandler> registeredSignals = new HashMap<>();

    @TruffleBoundary
    public static int getNumberOfRegisteredSignals() {
        return registeredSignals.size();
    }

    @TruffleBoundary
    private static LLVMFunction setSignalHandler(Signal signal, LLVMFunction function) {
        int signalId = signal.getNumber();
        LLVMFunction returnFunction = LLVM_SIG_DFL;

        try {
            LLVMSignalHandler newSignalHandler = new LLVMSignalHandler(signal, function);
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
            LLVMLogger.error("could not register signal with id " + signalId + " (" + signal + ")");
            return LLVM_SIG_ERR;
        }

        return returnFunction;
    }

    // TODO: stack handling should work without predefined sizes,...
    private static final long SIGNAL_STACK_SIZE_KB = 512;
    private static final long SIGNAL_STACK_SIZE_BYTE = SIGNAL_STACK_SIZE_KB * 1024;

    /**
     * Registers a signal handler using sun.misc.SignalHandler. Unfortunately, using signals in java
     * leads to some problems which are not resolved in our implementation yet.
     *
     * One of this issue is, that signals are executed in an asynchronous way, which means raise()
     * exits before the signal was handled. Another Issue is that Java already registered some
     * signal handlers, which therefore cannot be used in sulong.
     *
     * Therefore, our implementation does not comply with the ANSI C standard and could lead to
     * timing issues when calling multiple signals in a defined sequence, or when a program has to
     * wait until the signal was handled (which is not guaranteed because of the asynchronous
     * behavior in our implementation).
     */
    private static final class LLVMSignalHandler implements SignalHandler, LLVMThread {

        private final Signal signal;
        private final LLVMFunction function;
        private final LLVMContext context;
        private RootCallTarget callTarget;
        private final LLVMStack stack = new LLVMStack();

        private final Lock lock = new ReentrantLock();
        private final AtomicBoolean isRunning = new AtomicBoolean(false);

        @TruffleBoundary
        private LLVMSignalHandler(Signal signal, LLVMFunction function) throws IllegalArgumentException {
            this.signal = signal;
            this.function = function;

            this.context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());

            lock.lock();
            try {
                if (function.equals(LLVM_SIG_DFL)) {
                    Signal.handle(signal, SignalHandler.SIG_DFL);
                    isRunning.set(true);
                    context.registerThread(this);
                    return;
                } else if (function.equals(LLVM_SIG_IGN)) {
                    Signal.handle(signal, SignalHandler.SIG_IGN);
                    isRunning.set(true);
                    context.registerThread(this);
                    return;
                }

                Signal.handle(signal, this);

                // only when we reach this point, the signal handler was registered successfully
                LLVMAddressLiteralNode signalStack = new LLVMAddressLiteralNode(stack.allocate(SIGNAL_STACK_SIZE_BYTE));
                LLVMI32LiteralNode sigNumArg = new LLVMI32LiteralNode(signal.getNumber());
                LLVMExpressionNode[] args = {signalStack, sigNumArg};

                Type argType0 = new PointerType(null);
                Type argType1 = PrimitiveType.I32;
                Type[] argsTypes = {argType0, argType1};

                LLVMExpressionNode functionNode = LLVMFunctionLiteralNodeGen.create(context.lookup(function));

                LLVMCallNode callNode = new LLVMCallNode(context, new FunctionType(VoidType.INSTANCE, argsTypes, false), functionNode, args);

                callTarget = Truffle.getRuntime().createCallTarget(
                                new LLVMFunctionStartNode(callNode,
                                                new LLVMExpressionNode[]{},
                                                new LLVMExpressionNode[]{},
                                                null,
                                                new FrameDescriptor(), null, new LLVMStackFrameNuller[0], 1));

                isRunning.set(true);
                context.registerThread(this);
            } catch (IllegalArgumentException e) {
                throw e;
            } finally {
                lock.unlock();
            }
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
                    LLVMLogger.error("could not execute signal handler. Sulong can currently only execute one signal at once!");
                    return;
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            lock.lock();
            try {
                if (isRunning.get()) {
                    callTarget.call();
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

        public LLVMFunction getFunction() {
            return function;
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

            lock.lock();
            try {
                if (!stack.isFreed()) {
                    stack.free();
                }
            } finally {
                lock.unlock();
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

        @TruffleBoundary
        public static Signals decode(int code) throws NoSuchElementException {
            for (Signals currentSignal : values()) {
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
