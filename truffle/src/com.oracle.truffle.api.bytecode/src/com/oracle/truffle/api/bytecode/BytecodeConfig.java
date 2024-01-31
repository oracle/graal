/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.instrumentation.Tag;

/**
 * The configuration to use while generating bytecode. To reduce interpreter footprint, source
 * sections and instrumentation information can be lazily re-parsed when it is needed.
 *
 * @since 24.1
 */
public final class BytecodeConfig {

    /**
     * Retain no sources or instrumentation information.
     *
     * @since 24.1
     */
    public static final BytecodeConfig DEFAULT = new BytecodeConfig(false, false, null, null, null);
    /**
     * Retain source information.
     *
     * @since 24.1
     */
    public static final BytecodeConfig WITH_SOURCE = new BytecodeConfig(true, false, null, null, null);

    /**
     * Retain all information.
     *
     * @since 24.1
     */
    public static final BytecodeConfig COMPLETE = new BytecodeConfig(true, true, null, null, null);

    final boolean addSource;
    final boolean addAllInstrumentationData;
    final Class<?>[] addInstrumentations;
    final Class<?>[] removeInstrumentations;
    final Class<?>[] addTags;

    BytecodeConfig(boolean withSource, boolean addAllInstrumentationData, Class<?>[] addInstrumentations, Class<?>[] removeInstrumentations, Class<?>[] tags) {
        this.addSource = withSource;
        this.addAllInstrumentationData = addAllInstrumentationData;
        this.addInstrumentations = addInstrumentations;
        this.removeInstrumentations = removeInstrumentations;
        this.addTags = tags;
    }

    /**
     * Produces a new {@link Builder} that can be used to programmatically build a
     * {@link BytecodeConfig}.
     *
     * @since 24.1
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder to generate a {@link BytecodeConfig} programmatically.
     *
     * @since 24.1
     */
    public static class Builder {
        private boolean addSource;
        private boolean addAllInstrumentationData;
        private Class<?>[] addTags;
        private Class<?>[] addInstrumentations;
        private Class<?>[] removeInstrumentations;

        /**
         * Default constructor.
         *
         * @since 24.1
         */
        Builder() {
        }

        /**
         * Sets whether to include sources.
         *
         * @since 24.1
         */
        public Builder addSource() {
            this.addSource = true;
            return this;
        }

        /**
         * Sets whether all instrumentation data should be included. This value, if {@code true},
         * supersedes the tag and instrumentation values.
         *
         * @since 24.1
         */
        public Builder addAllInstrumentationData() {
            this.addAllInstrumentationData = true;
            return this;
        }

        /**
         * Sets a specific set of tags to be included.
         *
         * @since 24.1
         */
        @SuppressWarnings("unchecked")
        public Builder addTags(Class<? extends Tag>... tags) {
            this.addTags = tags;
            return this;
        }

        /**
         * Sets a specific set of instrumentations to be added.
         *
         * @since 24.1
         */
        public Builder addInstrumentations(Class<?>... instrumentations) {
            this.addInstrumentations = instrumentations;
            return this;
        }

        /**
         * Sets a specific set of instrumentations to be removed.
         *
         * @since 24.1
         */

        public Builder removeInstrumentations(Class<?>... instrumentations) {
            this.removeInstrumentations = instrumentations;
            return this;
        }

        /**
         * Builds the config.
         *
         * @since 24.1
         */
        public BytecodeConfig build() {
            return new BytecodeConfig(addSource, addAllInstrumentationData, addInstrumentations, removeInstrumentations, addTags);
        }
    }
}
