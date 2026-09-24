/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import java.util.Objects;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.word.WordFactory;

/**
 * Configuration for {@link VMRuntime#trimRuntimeState}.
 *
 * <p>
 * This configuration selects a high-level trim mode and optional callbacks that run before
 * and after the trim. The runtime translates the selected mode into the concrete actions
 * performed at safepoint.
 *
 * @since 25.5
 */
public final class RuntimeStateTrimConfig {

    /**
     * Selects the overall trim goal.
     *
     * <p>
     * {@link #LATENCY} minimizes the amount of work performed at the safepoint. {@link #BALANCED}
     * performs a moderate amount of memory reduction. {@link #SIZE} minimizes the memory usage.
     */
    public enum Mode {

        /**
         * Minimize safepoint work and latency.
         *
         * It does not explicitly clean retained heap memory, although normal GC policy may
         * still release unused heap memory.
         */
        LATENCY,

        /**
         * Balance safepoint latency and memory reduction.
         *
         * Performs a complete collection and cleans unused memory in retained chunks. The
         * collection may release excess heap chunks and uncommit their mappings.
         */
        BALANCED,

        /**
         * Minimize memory usage.
         *
         * Performs a complete collection, releases unused heap chunks, uncommits unused heap
         * memory, and cleans unused and filler-object memory.
         */
        SIZE
    }

    /**
     * Overall trim goal.
     */
    private final Mode mode;

    /**
     * Callback invoked immediately before runtime-state trim begins.
     *
     * <p>
     * Before the trim begins, the callback is executed at a VM safepoint, where:
     * <ul>
     * <li>other application threads are blocked from executing Java code</li>
     * <li>threads already running in native code may continue until they return to Java</li>
     * <li>Java synchronization is not permitted</li>
     * <li>Java heap allocation is not permitted</li>
     * </ul>
     *
     * <p>
     * This callback can be used to observe or prepare the runtime state before the selected
     * optimizations are applied.
     */
    private final BeforeRuntimeStateTrim beforeRuntimeStateTrim;

    /**
     * Callback invoked immediately after runtime-state trim completes.
     *
     * <p>
     * After the trim has completed, the callback is executed at a VM safepoint, where:
     * <ul>
     * <li>other application threads are blocked from executing Java code</li>
     * <li>threads already running in native code may continue until they return to Java</li>
     * <li>Java synchronization is not permitted</li>
     * <li>Java heap allocation is not permitted</li>
     * </ul>
     *
     * <p>
     * This callback can be used to observe the final trimmed runtime state.
     */
    private final AfterRuntimeStateTrim afterRuntimeStateTrim;

    private RuntimeStateTrimConfig(Builder builder) {
        this.mode = builder.mode;
        this.beforeRuntimeStateTrim = builder.beforeRuntimeStateTrim;
        this.afterRuntimeStateTrim = builder.afterRuntimeStateTrim;
    }

    /**
     * Returns the selected trim mode.
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Invoked before runtime trim begins.
     */
    public BeforeRuntimeStateTrim beforeRuntimeStateTrim() {
        return beforeRuntimeStateTrim;
    }

    /**
     * Invoked after runtime trim completes.
     */
    public AfterRuntimeStateTrim afterRuntimeStateTrim() {
        return afterRuntimeStateTrim;
    }

    /**
     * Returns a builder for configuring runtime-state trim.
     *
     * @param mode the overall trim goal
     */
    public static Builder builder(Mode mode) {
        return new Builder(mode);
    }

    /**
     * Builder for runtime-state trim configurations.
     */
    public static final class Builder {
        private Mode mode;
        private BeforeRuntimeStateTrim beforeRuntimeStateTrim;
        private AfterRuntimeStateTrim afterRuntimeStateTrim;

        private Builder(Mode mode) {
            this.mode = Objects.requireNonNull(mode, "Mode must be non null");
            this.beforeRuntimeStateTrim = WordFactory.nullPointer();
            this.afterRuntimeStateTrim = WordFactory.nullPointer();
        }

        /**
         * Selects the overall trim goal.
         */
        public Builder mode(Mode value) {
            mode = Objects.requireNonNull(value, "Mode must be non null");
            return this;
        }

        /**
         * Sets the callback invoked before runtime trim begins.
         */
        public Builder beforeRuntimeStateTrim(BeforeRuntimeStateTrim value) {
            beforeRuntimeStateTrim = value;
            return this;
        }

        /**
         * Sets the callback invoked after runtime trim completes.
         */
        public Builder afterRuntimeStateTrim(AfterRuntimeStateTrim value) {
            afterRuntimeStateTrim = value;
            return this;
        }

        /**
         * Creates the configuration.
         */
        public RuntimeStateTrimConfig build() {
            Objects.requireNonNull(mode, "Mode must be non null");
            return new RuntimeStateTrimConfig(this);
        }
    }

    /**
     * Invoked before runtime trim begins.
     * <p>
     * Returns zero on success, or a nonzero status code to abort runtime-state trimming.
     *
     * @since 25.5
     */
    public interface BeforeRuntimeStateTrim extends CFunctionPointer {
        @InvokeCFunctionPointer
        int invoke();
    }

    /**
     * Invoked after runtime trim completes.
     * <p>
     * Returns zero on success, or a nonzero status code to report failure.
     *
     * @since 25.5
     */
    public interface AfterRuntimeStateTrim extends CFunctionPointer {
        @InvokeCFunctionPointer
        int invoke();
    }
}
