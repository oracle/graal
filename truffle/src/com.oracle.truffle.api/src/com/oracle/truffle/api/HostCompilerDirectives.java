/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Directives that influence the optimizations of the host compiler. These operations affect how the
 * Truffle interpreter is itself compiled.
 *
 * @since 21.0
 */
public final class HostCompilerDirectives {

    /**
     * This object is a placeholder for the static methods that implement compiler directives, and
     * cannot be constructed.
     *
     * @since 21.0
     */
    private HostCompilerDirectives() {
    }

    /**
     * Marks a method that is an implementation of a Truffle interpreter, and which should receive
     * additional optimization budget.
     * <p>
     * This annotation is used to annotate the root method of a bytecode interpreter, and it hints
     * the compiler to invest extra effort into optimizing such methods. Language implementers are
     * advised to inspect the IR of the interpreter when using this.
     *
     * @see BytecodeInterpreterSwitchBoundary to control the boundaries of inlining
     *
     * @since 21.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface BytecodeInterpreterSwitch {
    }

    /**
     * Marks a method that is called from a Truffle interpreter, but is not called frequently and is
     * not important for interpreter performance.
     * <p>
     * This annotation is used to annotate methods that are called from a bytecode interpreter, but
     * should generally not be inlined into the body of the bytecode interpreter. Language
     * implementers are advised to inspect the IR of the interpreter when using this.
     *
     * @see BytecodeInterpreterSwitch to annotate the root method of a bytecode interpreter
     *
     * @since 21.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface BytecodeInterpreterSwitchBoundary {
    }
}
