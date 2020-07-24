/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Represents resource limits configuration that is used to configure contexts. Resource limit
 * instances are created using the {@link #newBuilder() builder} and activated by
 * {@link Context.Builder#resourceLimits(ResourceLimits) enabling} them for a {@link Context
 * context}. All configured limits are applied per context instance. Resource limits may be
 * {@link Context#resetLimits() reset}. If a resource limit is triggered the context that triggered
 * the limit will automatically be {@link Context#close(boolean) closed} and can no longer be used
 * to evaluate code.
 * <p>
 * The following resource limits are supported:
 * <ul>
 * <li>{@link Builder#statementLimit(long, Predicate) Statement count} limit per context. Allows to
 * limit the amount of statements executed per context.
 * </ul>
 * <p>
 * <h3>Statement Limit Example</h3> <code>
 * <pre>
 * ResourceLimits limits = ResourceLimits.newBuilder()
 *                       .statementLimit(500, null)
 *                   .build();
 * try (Context context = Context.newBuilder("js")
 *                            .resourceLimits(limits)
 *                        .build();) {
 *     try {
 *         context.eval("js", "while(true);");
 *         assert false;
 *     } catch (PolyglotException e) {
 *         // triggered after 500 iterations of while(true);
 *         // context is closed and can no longer be used
 *         assert e.isCancelled();
 *     }
 * }
 * </pre>
 * </code>
 *
 * @see #newBuilder()
 * @since 19.3
 */
public final class ResourceLimits {

    private static final ResourceLimits EMPTY = new ResourceLimits(null);

    final Object impl;

    ResourceLimits(Object impl) {
        this.impl = impl;
    }

    /**
     * Creates a new builder to construct {@link ResourceLimits} instances.
     *
     * @since 19.3
     */
    public static Builder newBuilder() {
        return EMPTY.new Builder();
    }

    /**
     * A builder used to construct resource limits. Builder instances are not thread-safe and may
     * not be used from multiple threads at the same time.
     *
     * @since 19.3
     */
    public final class Builder {

        long statementLimit;
        Predicate<Source> statementLimitSourceFilter;
        Duration timeLimit;
        Duration timeLimitAccuracy;
        Consumer<ResourceLimitEvent> onLimit;

        Builder() {
        }

        /**
         * Specifies the maximum number of statements a context may execute until the onLimit event
         * is notified and the context will be {@link Context#close() closed}. After the statement
         * limit was triggered for a context, it is no longer usable and every use of the context
         * will throw a {@link PolyglotException} that returns <code>true</code> for
         * {@link PolyglotException#isCancelled()}. The statement limit is independent of the number
         * of threads executing and is applied per context. Invoking this method multiple times
         * overwrites previous statement limit configurations. If the statement limit is exceeded
         * then the {@link #onLimit(Consumer) onLimit} listener is notified.
         * <p>
         * By default there is no statement limit applied. The limit may be set to 0 to disable it.
         * In addition to the limit a source filter may be set to indicate for which sources the
         * limit should be applied. If the source filter is <code>null</code> then it will be
         * applied to all {@link Source#isInternal() internal} and public sources. The provided
         * limit must not be negative otherwise an {@link IllegalArgumentException} is thrown. If a
         * {@link Context.Builder#engine(Engine) shared engine} is used then the same source filter
         * instance must be used for all contexts of an engine. Otherwise an
         * {@link IllegalArgumentException} is thrown when the context is
         * {@link Context.Builder#build() built}. The limit itself may vary between contexts.
         * <p>
         * The statement limit is applied to the context and all inner contexts it spawns.
         * Therefore, new inner contexts cannot be used to exceed the statement limit.
         * <p>
         * Note that attaching a statement limit to a context reduces the throughput of all guest
         * applications with the same engine. The statement counter needs to be updated with every
         * statement that is executed. It is recommended to benchmark the use of the statement limit
         * before it is used in production.
         * <p>
         * Note that the complexity of a single statement may not be constant time depending on the
         * guest language. For example, statements that execute JavaScript builtins, like
         * <code>Array.sort</code>, may account for a single statement, but its execution time is
         * dependent on the size of the array. The statement count limit is therefore not suitable
         * to perform time boxing and must be combined with other more reliable measures.
         *
         * @see ResourceLimits Example Usage
         * @since 19.3
         */
        @SuppressWarnings("hiding")
        public Builder statementLimit(long limit, Predicate<Source> sourceFilter) {
            if (limit < 0) {
                throw new IllegalArgumentException("The statement limit must not be negative.");
            }
            this.statementLimit = limit;
            this.statementLimitSourceFilter = sourceFilter;
            return this;
        }

        /**
         * Specifies the maximum {@link ThreadMXBean#getThreadCpuTime(long) CPU time} a context may
         * be active until the onLimit event is notified and the context will be
         * {@link Context#close() closed}. The {@link Duration time limit} may be specified
         * alongside an {@link Duration accuracy} with which the time limit is enforced. Both time
         * limit and accuracy must be positive. Both parameters may be set to <code>null</code> to
         * disable the time limit for a builder. By default no time limit is configured. Invoking
         * this method multiple times overwrites previous time limit configurations. If the time
         * limit is exceeded then the {@link #onLimit(Consumer) onLimit} listener is notified. The
         * minimal accuracy is 10 milliseconds, values below that will be rounded up.
         * <p>
         * The used CPU time of a context typically does not include time spent waiting for
         * synchronization or IO. The CPU time of all threads will be added and checked against the
         * CPU time limit. This can mean that if two threads execute the same context then the time
         * limit will be exceeded twice as fast.
         * <p>
         * The time limit is enforced by a separate high-priority thread that will be woken
         * regularly. There is no guarantee that the context will be cancelled within the accuracy
         * specified. The accuracy may be significantly missed, e.g. if the host VM causes a full
         * garbage collection. If the time limit is never exceeded then the throughput of the guest
         * context is not affected. If the time limit is exceeded for one context then it may slow
         * down the throughput for other contexts of an engine temporarily.
         * <p>
         * The time limit is applied to the context and all inner contexts it spawns. Therefore, new
         * inner contexts cannot be used to exceed the time limit.
         *
         * <p>
         * <h3>Time Limit Example</h3>
         *
         * <code>
         * <pre>
         * ResourceLimits limits = ResourceLimits.newBuilder()
         *                   .timeLimit(Duration.ofMillis(500),
         *                              Duration.ofMillis(5))
         *                   .build();
         * try (Context context = Context.newBuilder("js")
         *                            .resourceLimits(limits)
         *                        .build();) {
         *     try {
         *         context.eval("js", "while(true);");
         *         assert false;
         *     } catch (PolyglotException e) {
         *         // triggered after 500ms;
         *         // context is closed and can no longer be used
         *         assert e.isCancelled();
         *     }
         * }
         * </pre>
         * </code>
         *
         * @see ThreadMXBean#getThreadCpuTime(long)
         * @see ResourceLimits Example Usage
         * @since 19.3
         */
        @SuppressWarnings("hiding")
        /*
         * Not ready for prime time yet. We need to solve GR-18742.
         */
        Builder cpuTimeLimit(Duration timeLimit, Duration accuracy) {
            if (timeLimit == null && accuracy == null) {
                // fall through to allow reset
            } else if (timeLimit == null || accuracy == null) {
                throw new IllegalArgumentException("If a timeLimit is specified accuracy must be specified as well.");
            } else if (timeLimit.isNegative() || timeLimit.isZero()) {
                throw new IllegalArgumentException("Time limit must not be negative or zero.");
            } else if (accuracy.isNegative() || accuracy.isZero()) {
                throw new IllegalArgumentException("Accuracy must not be negative or zero.");
            }
            this.timeLimit = timeLimit;
            this.timeLimitAccuracy = accuracy;
            return this;
        }

        /**
         * Notified when a resource limit is reached. Default is <code>null</code>. May be set to
         * <code>null</code> to disable events.
         *
         * @since 19.3
         */
        public Builder onLimit(@SuppressWarnings("hiding") Consumer<ResourceLimitEvent> onLimit) {
            this.onLimit = onLimit;
            return this;
        }

        /**
         * Builds the limit configuration object.
         *
         * @see ResourceLimits Example Usage
         * @since 19.3
         */
        public ResourceLimits build() {
            return new ResourceLimits(Engine.getImpl().buildLimits(statementLimit, statementLimitSourceFilter, timeLimit, timeLimitAccuracy, onLimit));
        }
    }
}
