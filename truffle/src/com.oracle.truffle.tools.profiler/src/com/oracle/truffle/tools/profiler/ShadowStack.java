/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.tools.profiler;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.tools.profiler.impl.SourceLocation;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom more efficient stack representations for profilers.
 * 
 * @since 0.29
 */
final class ShadowStack {

    private final ConcurrentHashMap<Thread, ThreadLocalStack> stacks = new ConcurrentHashMap<>();
    private final int stackLimit;

    ShadowStack(int stackLimit) {
        this.stackLimit = stackLimit;
    }

    ThreadLocalStack getStack(Thread thread) {
        return stacks.get(thread);
    }

    Collection<ThreadLocalStack> getStacks() {
        return stacks.values();
    }

    EventBinding<?> install(Instrumenter instrumenter, SourceSectionFilter filter, boolean compiledOnly) {
        return instrumenter.attachFactory(filter, new ExecutionEventNodeFactory() {
            public ExecutionEventNode create(EventContext context) {
                boolean isRoot = instrumenter.queryTags(context.getInstrumentedNode()).contains(StandardTags.RootTag.class);
                return new StackPushPopNode(ShadowStack.this, new SourceLocation(instrumenter, context), compiledOnly, isRoot);
            }
        });
    }

    private static class StackPushPopNode extends ExecutionEventNode {

        private final ShadowStack profilerStack;
        private final SourceLocation location;

        private final Thread cachedThread;
        private final ThreadLocalStack cachedStack;

        @CompilationFinal private boolean seenOtherThreads;
        @CompilationFinal final boolean isAttachedToRootNode;
        @CompilationFinal final boolean compiled;

        StackPushPopNode(ShadowStack profilerStack, SourceLocation location, boolean compiled, boolean isAttachedToRootNode) {
            this.profilerStack = profilerStack;
            this.cachedThread = Thread.currentThread();
            this.location = location;
            this.cachedStack = getStack();
            this.isAttachedToRootNode = isAttachedToRootNode;
            this.compiled = compiled;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (CompilerDirectives.inCompiledCode() && compiled && isAttachedToRootNode && !CompilerDirectives.inCompilationRoot()) {
                return;
            }
            doOnEnter();
        }

        private void doOnEnter() {
            if (seenOtherThreads) {
                pushSlow(CompilerDirectives.inCompiledCode());
            } else if (cachedThread == Thread.currentThread()) {
                cachedStack.push(location, CompilerDirectives.inCompiledCode());
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenOtherThreads = true;
                pushSlow(false);
            }
        }

        @TruffleBoundary
        private void pushSlow(boolean inCompiledCode) {
            getStack().push(location, inCompiledCode);
        }

        @Override
        protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            onReturnValue(frame, null);
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            if (CompilerDirectives.inCompiledCode() && compiled && isAttachedToRootNode && !CompilerDirectives.inCompilationRoot()) {
                return;
            }
            doOnReturnValue();
        }

        private void doOnReturnValue() {
            if (seenOtherThreads) {
                popSlow();
            } else if (cachedThread == Thread.currentThread()) {
                cachedStack.pop();
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenOtherThreads = true;
                popSlow();
            }
        }

        @TruffleBoundary
        private void popSlow() {
            getStack().pop();
        }

        @TruffleBoundary
        private ThreadLocalStack getStack() {
            Thread currentThread = Thread.currentThread();
            ThreadLocalStack stack = profilerStack.stacks.get(currentThread);
            if (stack == null) {
                stack = new ThreadLocalStack(currentThread, profilerStack.stackLimit);
                ThreadLocalStack prevStack = profilerStack.stacks.putIfAbsent(currentThread, stack);
                if (prevStack != null) {
                    stack = prevStack;
                }
            }
            return stack;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }

    }

    static final class ThreadLocalStack {

        /*
         * Window in which we look ahead and before the current stack index to find the potentially
         * changed top of stack index, after copying.
         */
        private static final int CORRECTION_WINDOW = 5;

        private final Thread thread;
        private final SourceLocation[] stack;
        private final boolean[] compiledStack;

        private boolean stackOverflowed = false;
        @CompilationFinal private Assumption noStackOverflowedAssumption = Truffle.getRuntime().createAssumption();

        private int stackIndex = -1;

        ThreadLocalStack(Thread thread, int stackLimit) {
            this.thread = thread;
            this.stack = new SourceLocation[stackLimit];
            this.compiledStack = new boolean[stackLimit];
            // In case we are running in CompiledOnly mode, the assumption is never checked in the
            // Interpreter so call is needed to resolve the method.
            noStackOverflowedAssumption.isValid();
        }

        void push(SourceLocation element, boolean inCompiledCode) {
            if (noStackOverflowedAssumption.isValid()) {
                int index = stackIndex + 1;
                if (index < stack.length) {
                    assert index >= 0;
                    stack[index] = element;
                    compiledStack[index] = inCompiledCode;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    noStackOverflowedAssumption.invalidate();
                    stackOverflowed = true;
                }
                stackIndex = index;
            }
        }

        void pop() {
            if (noStackOverflowedAssumption.isValid()) {
                int index = stackIndex;
                if (index >= 0 && index < stack.length) {
                    stack[index] = null;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    noStackOverflowedAssumption.invalidate();
                    stackOverflowed = true;
                }
                stackIndex = index - 1;
            }
        }

        SourceLocation[] getStack() {
            return stack;
        }

        Thread getThread() {
            return thread;
        }

        boolean[] getCompiledStack() {
            return compiledStack;
        }

        int getStackIndex() {
            return stackIndex;
        }

        boolean hasStackOverflowed() {
            return stackOverflowed;
        }

        static final class CorrectedStackInfo {

            static CorrectedStackInfo build(ThreadLocalStack stack) {
                SourceLocation[] localStack = stack.getStack();
                boolean[] localCompiled = stack.getCompiledStack();
                int localStackIndex = stack.getStackIndex();
                if (localStackIndex == -1) {
                    // nothing on the stack
                    return null;
                }

                int length = localStackIndex + 1;
                if (length > localStack.length) {
                    // stack was out of stack limit
                    length = localStack.length;
                }

                // make a quick copy to minimize retries
                localStack = Arrays.copyOf(localStack, Math.min(length + CORRECTION_WINDOW, localStack.length));
                localCompiled = Arrays.copyOf(localCompiled, Math.min(length + CORRECTION_WINDOW, localStack.length));

                for (int i = 0; i < localStack.length; i++) {
                    // find the first null hole in the stack and use it as new corrected top of
                    // stack index
                    if (localStack[i] == null) {
                        length = i;
                        break;
                    }
                }
                return new CorrectedStackInfo(localStack, localCompiled, length);
            }

            private CorrectedStackInfo(SourceLocation[] stack, boolean[] compiledStack, int length) {
                this.stack = stack;
                this.compiledStack = compiledStack;
                this.length = length;
            }

            SourceLocation[] getStack() {
                return stack;
            }

            boolean[] getCompiledStack() {
                return compiledStack;
            }

            int getLength() {
                return length;
            }

            SourceLocation[] stack;
            boolean[] compiledStack;
            int length;
        }
    }
}
