/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation should only be used for testing to generate multiple variants of the interpreter
 * with slightly different {@link GenerateBytecode configurations}.
 *
 * Importantly, all of the variants' Builders share a common superclass, which allows tests to be
 * written once and then executed with multiple configurations.
 *
 * In order for the variants and their Builders to be compatible, the configurations must agree on
 * specific fields. In particular, the {@link GenerateBytecode#languageClass} must match, and fields
 * that generate new builder methods (e.g. {@link GenerateBytecode#enableYield()}) must agree. These
 * properties are checked by the Bytecode DSL processor.
 *
 * @since 24.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface GenerateBytecodeTestVariants {
    /**
     * The variants to generate.
     *
     * @since 24.2
     */
    Variant[] value();

    /**
     * The annotation used to declare a variant.
     *
     * @since 24.2
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface Variant {
        /**
         * The class name suffix for this variant.
         *
         * @since 24.2
         */
        String suffix();

        /**
         * The configuration for this variant.
         *
         * @since 24.2
         */
        GenerateBytecode configuration();
    }
}
