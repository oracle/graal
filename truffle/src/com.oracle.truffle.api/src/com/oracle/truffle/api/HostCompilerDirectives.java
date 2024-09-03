/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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
     *
     * Indicates whether a branch is executed only in the interpreter. In addition to
     * {@link CompilerDirectives#inInterpreter()}, this method instructs the host compiler to treat
     * the positive branch as frequently executed code of high importance. Branches protected by
     * this method should be treated as if they were runtime compiled code paths, even if they may
     * never actually be compiled. This means that slow-paths must be protected using either
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate()} or {@link TruffleBoundary}.
     * <p>
     * A common use case for this directive is in counting condition profiles, where counters must
     * be protected by {@link CompilerDirectives#inInterpreter()} but also need to be optimized as
     * fast-path by the host compiler. Without this directive, the host compiler may treat the
     * branch as a slow-path branch.
     *
     * @return {@code true} if executed in the interpreter, {@code false} in compiled code.
     * @since 23.0
     */
    public static boolean inInterpreterFastPath() {
        /*
         * Within guest compilations this returns false.
         */
        return true;
    }

    /**
     * Marks a method that is an implementation of a Truffle interpreter, and which should receive
     * additional optimization budget.
     * <p>
     * This annotation is used to annotate the root method of a bytecode interpreter, and it hints
     * the compiler to invest extra effort into optimizing such methods. Language implementers are
     * advised to inspect the IR of the interpreter when using this.
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
     * @deprecated use is no longer needed. boundaries for {@link BytecodeInterpreterSwitch} are
     *             mostly determined automatically. To migrate remove all usages.
     * @since 21.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Deprecated(since = "22.2")
    public @interface BytecodeInterpreterSwitchBoundary {
    }

    /**
     * Hints to Truffle host inlining that a particular method is partial evaluatable, but it would
     * be a good place for a cutoff when performing host inlining. A host compiler may use this
     * information as a hint to take trade-offs optimizing the code. Good examples of cutoffs are:
     * <ul>
     * <li>Methods related to instrumentation or tracing. Instrumentation and tracing are typically
     * not critical for interpreter performance.
     * <li>Methods raising guest language exceptions. Such paths must often partially evaluate for
     * good peak performance, but are not a priority to optimize during interpreter execution.
     * <li>Methods related to Truffle interoperability behavior. Such paths are typically only used
     * exceptionally, as the vast majority of code is likely non-interop code.
     * <li>Methods that are very complex and would only deny other more important methods to be
     * inlined.
     * </ul>
     * If a method is already annotated with {@link TruffleBoundary} or is dominated by a call to
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() transferToInterpreter()} then
     * this method has no effect, as any path that is not designed for partial evaluation is already
     * considered a slow-path in hosted inlining.
     * <p>
     * This annotation may be used to tune Truffle hosted inlining decisions. It is useful in cases
     * where the host inliner did not have enough budget to exhaustively inline the entire partial
     * evaluatable fast-path. In such a case it might be worthwhile to annotate rarely executed
     * methods with {@link InliningCutoff} to reduce their priority to make room for more important
     * methods.
     * <p>
     * For more details on host inlining see the <a href=
     * "https://github.com/oracle/graal/blob/master/truffle/docs/HostCompilation.md">documentation</a>
     *
     * @since 22.3
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface InliningCutoff {
    }

}
