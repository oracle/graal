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
package com.oracle.truffle.llvm.nodes.impl.intrinsics.c;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode.LLVMUnresolvedCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@GenerateNodeFactory
@NodeChildren({@NodeChild(type = LLVMI32Node.class, value = "signal"), @NodeChild(type = LLVMFunctionNode.class, value = "handler")})
public abstract class LLVMSignal extends LLVMFunctionNode {

    // #define SIG_DFL ((__sighandler_t) 0) /* Default action. */
    private static final LLVMFunctionDescriptor LLVM_SIG_DFL = LLVMFunctionDescriptor.create(0);

    // # define SIG_IGN ((__sighandler_t) 1) /* Ignore signal. */
    private static final LLVMFunctionDescriptor LLVM_SIG_IGN = LLVMFunctionDescriptor.create(1);

    // #define SIG_ERR ((__sighandler_t) -1) /* Error return. */
    private static final LLVMFunctionDescriptor LLVM_SIG_ERR = LLVMFunctionDescriptor.create(-1);

    @Specialization
    public LLVMFunctionDescriptor executeAddress(int signal, LLVMFunctionDescriptor handler) {
        return setSignalHandler(signal, handler);
    }

    private static LLVMFunctionDescriptor setSignalHandler(int signalId, LLVMFunctionDescriptor function) {
        try {
            Signals decodedSignal = Signals.decode(signalId);
            return setSignalHandler(decodedSignal.signal(), function);
        } catch (NoSuchElementException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMLogger.error(e.getMessage());
            return LLVM_SIG_ERR;
        }
    }

    private static final Map<Integer, LLVMSignalHandler> registeredSignals = new HashMap<>();

    @TruffleBoundary
    private static LLVMFunctionDescriptor setSignalHandler(Signal signal, LLVMFunctionDescriptor function) {
        int signalId = signal.getNumber();
        LLVMFunctionDescriptor returnFunction = LLVM_SIG_DFL;

        if (registeredSignals.containsKey(signalId)) {
            LLVMSignalHandler currentFunction = registeredSignals.get(signalId);
            returnFunction = currentFunction.getFunction();
        }

        try {
            registeredSignals.put(signalId, new LLVMSignalHandler(signal, function));
        } catch (IllegalArgumentException e) {
            LLVMLogger.error("could not register signal with id " + signalId + " (" + signal + ")");
            return LLVM_SIG_ERR;
        }

        return returnFunction;
    }

    // TODO: stack handling should work without predefined sizes,...
    private static final long TMP_SIGNAL_STACK_SIZE_KB = 512;
    private static final long TMP_SIGNAL_STACK_SIZE_BYTE = TMP_SIGNAL_STACK_SIZE_KB * 1024;

    private static final class LLVMSignalHandler implements SignalHandler {

        private final LLVMFunctionDescriptor function;
        private RootCallTarget callTarget;
        private final LLVMStack stack = new LLVMStack();

        private LLVMSignalHandler(Signal signal, LLVMFunctionDescriptor function) throws IllegalArgumentException {
            this.function = function;
            LLVMFunctionNode functionNode = LLVMFunctionLiteralNodeGen.create(function);

            LLVMAddressLiteralNode signalStack = new LLVMAddressLiteralNode(stack.allocate(TMP_SIGNAL_STACK_SIZE_BYTE));
            LLVMI32LiteralNode sigNumArg = new LLVMI32LiteralNode(signal.getNumber());
            LLVMExpressionNode[] args = {signalStack, sigNumArg};

            LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());

            LLVMUnresolvedCallNode callNode = new LLVMUnresolvedCallNode(functionNode, args, LLVMRuntimeType.VOID, context);

            callTarget = Truffle.getRuntime().createCallTarget(
                            new LLVMFunctionStartNode(callNode,
                                            new LLVMNode[]{},
                                            new LLVMNode[]{},
                                            SourceSection.createUnavailable("", null),
                                            new FrameDescriptor(), ""));

            if (function.equals(LLVM_SIG_DFL)) {
                Signal.handle(signal, SignalHandler.SIG_DFL);
            } else if (function.equals(LLVM_SIG_IGN)) {
                Signal.handle(signal, SignalHandler.SIG_IGN);
            } else {
                Signal.handle(signal, this);
            }
        }

        @Override
        public void handle(Signal arg0) {
            /*
             * https://en.wikipedia.org/wiki/Sigaction#Replacement_of_deprecated_signal.28.29
             *
             * Signal handlers installed by the signal() interface will be uninstalled immediately
             * prior to execution of the handler.
             *
             * @note I made such a program, and I had to delete the handler manually, so this
             * sentence is probably not true
             */
            callTarget.call();
        }

        public LLVMFunctionDescriptor getFunction() {
            return function;
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
