/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.debug;

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.InstructionTracer;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * An {@link InstructionTracer} implementation that prints a textual trace of executed bytecode
 * instructions.
 * <p>
 * Each time an instruction is about to execute, this tracer formats a single line containing:
 * <ul>
 * <li>a monotonically increasing instruction counter,
 * <li>the qualified root name of the {@link BytecodeNode} being interpreted, and
 * <li>a human readable representation of the instruction at the current bytecode index.
 * </ul>
 *
 * Output is delivered to a user-supplied {@link Consumer Consumer}&lt;String&gt; (for example,
 * {@code System.out::println}) or to a {@link java.io.PrintStream} via the
 * {@link #newBuilder(PrintStream)} convenience factory.
 * <p>
 * A tracer may optionally be restricted to a subset of bytecode roots by installing a filter
 * predicate. If a {@link BytecodeNode} does not match the filter, its instructions are skipped.
 * <p>
 * This tracer is intended for debugging and profiling during development. It runs on the language
 * execution thread and executes on the hot path before every instruction; it should not be left
 * enabled in production code.
 *
 * @since 25.1
 */
public final class PrintInstructionTracer implements InstructionTracer {

    private final Consumer<String> out;
    private final Predicate<BytecodeNode> filter;
    private final AtomicLong executedInstructions = new AtomicLong();
    private volatile LastTraceCache cache;

    PrintInstructionTracer(Consumer<String> out, Predicate<BytecodeNode> filter) {
        this.out = out;
        this.filter = filter;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation formats and emits a single trace line for the current instruction, unless
     * the {@link BytecodeNode} is excluded by the active filter.
     *
     * @since 25.1
     */
    public void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame) {
        traceInstruction(access, bytecode, bytecodeIndex);
    }

    /**
     * Resets the internal instruction counter back to zero.
     * <p>
     * This only affects the counter included in the printed trace lines. It does not clear any
     * output sink or filter configuration.
     *
     * @since 25.1
     */
    public void reset() {
        executedInstructions.set(0);
    }

    /**
     * Creates a new {@link Builder} that will emit trace lines to the given consumer.
     * <p>
     * Each generated line is already formatted. The consumer is typically a logger or
     * {@code System.out::println}, but any {@link Consumer} is accepted.
     *
     * @param out sink for formatted trace lines
     * @return a new builder
     * @since 25.1
     */
    public static Builder newBuilder(Consumer<String> out) {
        return new Builder(out);
    }

    /**
     * Convenience overload of {@link #newBuilder(Consumer)} that writes to a {@link PrintStream}.
     * <p>
     * The resulting tracer will call {@link PrintStream#println(String)} for every traced
     * instruction.
     *
     * @param out a print stream (for example {@code System.out})
     * @return a new builder
     * @since 25.1
     */
    public static Builder newBuilder(PrintStream out) {
        return newBuilder((s) -> out.println(s));
    }

    private LastTraceCache updateCache(BytecodeNode bytecode) {
        RootNode rootNode = bytecode.getRootNode();
        String name = rootNode.getQualifiedName();
        if (name == null) {
            name = "<unnamed>";
        }
        boolean included = filter != null ? filter.test(bytecode) : true;
        return new LastTraceCache(bytecode, name, included);
    }

    @TruffleBoundary
    private void traceInstruction(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex) {
        LastTraceCache c = this.cache;
        if (c == null || c.bytecodeNode != bytecode) {
            c = updateCache(bytecode);
            this.cache = c;
        }
        if (!c.included) {
            return;
        }
        StringBuilder b = new StringBuilder();
        String counter = String.valueOf(executedInstructions.incrementAndGet());
        spaces(b, 6 - counter.length());
        b.append(counter);
        b.append(":");
        b.append(c.rootName);
        b.append(":");
        b.append(access.getTracedInstruction(bytecode, bytecodeIndex).toString());
        out.accept(b.toString());
    }

    private static void spaces(StringBuilder b, int count) {
        for (int i = 0; i < count; i++) {
            b.append(' ');
        }
    }

    /**
     * Builder for {@link PrintInstructionTracer} instances.
     * <p>
     * Call {@link #filter(Predicate)} to restrict tracing to selected {@link BytecodeNode}s, then
     * {@link #build()} to create the tracer.
     *
     * @since 25.1
     */
    public static final class Builder {

        private final Consumer<String> out;
        private Predicate<BytecodeNode> filter;

        private Builder(Consumer<String> out) {
            Objects.requireNonNull(out);
            this.out = out;
        }

        /**
         * Sets an optional filter that decides which {@link BytecodeNode}s should be traced.
         * <p>
         * If the predicate returns {@code false} for a given node, instructions executed in that
         * node are not printed.
         *
         * @param filterClause predicate that returns true if tracing should be enabled for the
         *            given node
         * @return this builder
         * @since 25.1
         */
        public Builder filter(Predicate<BytecodeNode> filterClause) {
            this.filter = filterClause;
            return this;
        }

        /**
         * Creates a new {@link PrintInstructionTracer} instance using the configured output sink
         * and optional filter.
         *
         * @return a new tracer
         * @since 25.1
         */
        public PrintInstructionTracer build() {
            return new PrintInstructionTracer(out, filter);
        }
    }

    /**
     * Small cache record holding metadata for the most recently traced {@link BytecodeNode}.
     * <p>
     * This avoids recomputing node name and filter status for every single instruction of the same
     * node.
     */
    private record LastTraceCache(BytecodeNode bytecodeNode, String rootName, boolean included) {
    }
}
