/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.Tag;

/**
 * The configuration to use while generating bytecode. The configuration determines what optional
 * information (source sections, instrumentation instructions, etc.) should be materialized during
 * parsing. The interpreter memory footprint can be improved by omitting this information by default
 * and lazily re-parsing it when it is needed.
 * <p>
 * Instances of this class should be stored as static final constants. It is important for them to
 * be constant so partial evaluation can detect when reparsing is unnecessary.
 *
 * @since 24.2
 */
public final class BytecodeConfig {

    private static final long SOURCE_ENCODING = 0b1L;

    /**
     * Do not materialize any source or instrumentation information.
     *
     * @since 24.2
     */
    public static final BytecodeConfig DEFAULT = new BytecodeConfig(null, 0L);

    /**
     * Materialize source information.
     *
     * @since 24.2
     */
    public static final BytecodeConfig WITH_SOURCE = new BytecodeConfig(null, SOURCE_ENCODING);

    /**
     * Materialize all information.
     *
     * @since 24.2
     */
    public static final BytecodeConfig COMPLETE = new BytecodeConfig(null, 0xFFFF_FFFF_FFFF_FFFFL);

    final BytecodeConfigEncoder encoder;
    final long encoding;

    BytecodeConfig(BytecodeConfigEncoder encoder, long encoding) {
        this.encoder = encoder;
        this.encoding = encoding;
    }

    /**
     * Produces a new {@link Builder} that can be used to programmatically build a
     * {@link BytecodeConfig}.
     * <p>
     * Note this method is not intended to be used directly. Use the generated method, for example
     * <code>MyBytecodeRootNodeGen.newConfigBuilder()</code> instead.
     *
     * @since 24.2
     */
    public static Builder newBuilder(BytecodeConfigEncoder encoder) {
        return new Builder(encoder);
    }

    /**
     * Builder to generate a {@link BytecodeConfig} programmatically.
     *
     * @since 24.2
     */
    public static class Builder {
        private final BytecodeConfigEncoder encoder;
        private long encoding;

        Builder(BytecodeConfigEncoder encoder) {
            Objects.requireNonNull(encoder);
            this.encoder = encoder;
        }

        /**
         * Sets whether to materialize sources.
         *
         * @since 24.2
         */
        public Builder addSource() {
            CompilerAsserts.neverPartOfCompilation();
            this.encoding |= SOURCE_ENCODING;
            return this;
        }

        /**
         * Sets a specific set of tags to be materialized.
         *
         * @since 24.2
         */
        public Builder addTag(Class<? extends Tag> tag) {
            CompilerAsserts.neverPartOfCompilation();
            Objects.requireNonNull(tag);
            long encodedTag = encoder.encodeTag(tag);
            assert encodedTag != SOURCE_ENCODING && Long.bitCount(encodedTag) == 1 : "generated code invariant violated";
            this.encoding |= encodedTag;
            return this;
        }

        /**
         * Sets a specific set of instrumentations to be materialized.
         *
         * @since 24.2
         */
        public Builder addInstrumentation(Class<?> instrumentation) {
            CompilerAsserts.neverPartOfCompilation();
            Objects.requireNonNull(instrumentation);
            long encodedTag = encoder.encodeInstrumentation(instrumentation);
            assert encodedTag != SOURCE_ENCODING && Long.bitCount(encodedTag) == 1 : "generated code invariant violated";
            this.encoding |= encodedTag;
            return this;
        }

        /**
         * Builds the config.
         *
         * @since 24.2
         */
        @TruffleBoundary
        public BytecodeConfig build() {
            return new BytecodeConfig(encoder, encoding);
        }
    }

}
