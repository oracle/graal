/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.compiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.Supplier;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * A Truffle AST that can be compiled by a {@link TruffleCompiler}.
 */
public interface TruffleCompilable {
    /**
     * Gets this AST as a compiler constant.
     */
    JavaConstant asJavaConstant();

    /**
     * Gets a speculation log to be used for a single Truffle compilation. The returned speculation
     * log provides access to all relevant failed speculations as well as support for making
     * speculation during a single compilation.
     */
    SpeculationLog getCompilationSpeculationLog();

    /**
     * Notifies this object that a compilation of the AST it represents failed.
     *
     * @param serializedException serializedException a serialized representation of the exception
     *            representing the reason for compilation failure. See
     *            {@link #serializeException(Throwable)}.
     * @param suppressed specifies whether the failure was suppressed and should be silent. Use the
     *            {@link TruffleCompilerRuntime#isSuppressedFailure(TruffleCompilable, Supplier)} to
     *            determine if the failure should be suppressed.
     * @param bailout specifies whether the failure was a bailout or an error in the compiler. A
     *            bailout means the compiler aborted the compilation based on some of property of
     *            the AST (e.g., too big). A non-bailout means an unexpected error in the compiler
     *            itself.
     * @param permanentBailout specifies if a bailout is due to a condition that probably won't
     *            change if this AST is compiled again. This value is meaningless if
     *            {@code bailout == false}.
     * @param graphTooBig graph was too big
     */
    void onCompilationFailed(Supplier<String> serializedException, boolean suppressed, boolean bailout, boolean permanentBailout, boolean graphTooBig);

    /**
     * Called after a successful compilation of a call target.
     *
     * @param compilationTier which tier this compilation is compiled with
     * @param lastTier {@code true} if there is no next compilation tier (no next tier exists, or it
     *            is disabled)
     */
    @SuppressWarnings("unused")
    default void onCompilationSuccess(int compilationTier, boolean lastTier) {
    }

    /**
     * Invoked when installed code associated with this AST was invalidated due to assumption
     * invalidation. This method is not invoked across isolation boundaries, so can throw an error
     * in such a case. Note that this method may be invoked multiple times, if multiple installed
     * codes were active for this AST.
     */
    boolean onInvalidate(Object source, CharSequence reason, boolean wasActive);

    /**
     * Gets a descriptive name for this call target.
     */
    String getName();

    /**
     * Returns the estimate of the Truffle node count in this AST.
     */
    int getNonTrivialNodeCount();

    /**
     * Returns the number of direct calls of a call target. This may be used by an inlining
     * heuristic to inform exploration.
     */
    int countDirectCallNodes();

    /**
     * Return the total number of calls to this target.
     */
    int getCallCount();

    /**
     * Cancel the compilation of this truffle ast.
     */
    boolean cancelCompilation(CharSequence reason);

    /**
     * @param ast the ast to compare to
     * @return true if this ast and the argument are the same, one is a split of the other or they
     *         are both splits of the same ast. False otherwise.
     */
    boolean isSameOrSplit(TruffleCompilable ast);

    /**
     * @return How many direct callers is this ast known to have.
     */
    int getKnownCallSiteCount();

    /**
     * Called before call target is used for runtime compilation, either as root compilation or via
     * inlining.
     *
     * @deprecated use {@link #prepareForCompilation(boolean, int, boolean)} instead.
     */
    @Deprecated
    default void prepareForCompilation() {
        prepareForCompilation(true, 2, true);
    }

    /**
     * Called before call target is used for runtime compilation, either as root compilation or via
     * inlining.
     *
     * @param rootCompilation whether this compilation is compiled as root method
     * @param compilationTier which tier this compilation is compiled with
     * @param lastTier {@code true} if there is no next compilation tier (no next tier exists, or it
     *            is disabled).
     */
    default boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
        prepareForCompilation();
        return true;
    }

    /**
     * Returns {@code e} serialized as a string. The format of the returned string is:
     *
     * <pre>
     *  (class_name ":")+ "\n" stack_trace
     * </pre>
     * <p>
     * where the first {@code class_name} is {@code e.getClass().getName()} and every subsequent
     * {@code class_name} is the super class of the previous one up to but not including
     * {@code Throwable}. For example:
     *
     * <pre>
     * "java.lang.NullPointerException:java.lang.RuntimeException:java.lang.Exception:\n" +
     *                 "java.lang.NullPointerException: compiler error\n\tat MyClass.mash(MyClass.java:9)\n\tat MyClass.main(MyClass.java:6)"
     * </pre>
     */
    static String serializeException(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Class<?> c = e.getClass();
        while (c != Throwable.class) {
            pw.print(c.getName() + ':');
            c = c.getSuperclass();
        }
        pw.print('\n');
        e.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * @return <code>true</code> is the root nodes of this AST trivial, <code>false</code>
     *         otherwise.
     */
    boolean isTrivial();

    /**
     * Returns a process-unique id for the underlying engine. This may be used to cache the
     * {@link #getCompilerOptions() compiler options} as they are guaranteed to be the same per
     * engine.
     */
    long engineId();

    /**
     * Returns a set of compiler options that where specified by the user. The compiler options are
     * immutable for each {@link #engineId() engine}.
     */
    Map<String, String> getCompilerOptions();

    /**
     * Returns the number of successful compilations of this compilable. All compilation tiers are
     * counted together.
     */
    default int getSuccessfulCompilationCount() {
        return 0;
    }

    /**
     * Returns <code>true</code> if this compilable can be inlined. Please note that this does not
     * mean it will always be inlined, as inlining is a complex process that takes many factors into
     * account. If this method returns <code>false</code>, it will never be inlined. This typically
     * means that compilation with this compilable as the root has failed.
     * 
     * 
     */
    default boolean canBeInlined() {
        return true;
    }
}
