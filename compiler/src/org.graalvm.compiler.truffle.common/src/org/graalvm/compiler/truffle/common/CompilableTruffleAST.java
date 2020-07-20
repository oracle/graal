/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Supplier;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * A Truffle AST that can be compiled by a {@link TruffleCompiler}.
 */
public interface CompilableTruffleAST {
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
     * @param bailout specifies whether the failure was a bailout or an error in the compiler. A
     *            bailout means the compiler aborted the compilation based on some of property of
     *            the AST (e.g., too big). A non-bailout means an unexpected error in the compiler
     *            itself.
     * @param permanentBailout specifies if a bailout is due to a condition that probably won't
     *            change if this AST is compiled again. This value is meaningless if
     *            {@code bailout == false}.
     */
    void onCompilationFailed(Supplier<String> serializedException, boolean bailout, boolean permanentBailout);

    /**
     * Gets a descriptive name for this call target.
     */
    String getName();

    /**
     * Invalidates any machine code attached to this call target.
     */
    default void invalidateCode() {
    }

    /**
     * Returns the estimate of the Truffle node count in this AST.
     */
    int getNonTrivialNodeCount();

    /**
     * Returns the list of call nodes in this AST.
     */
    TruffleCallNode[] getCallNodes();

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
    boolean isSameOrSplit(CompilableTruffleAST ast);

    /**
     * @return How many direct callers is this ast known to have.
     */
    int getKnownCallSiteCount();

    /**
     * @return A {@link JavaConstant} representing the assumption that the nodes of the AST were not
     *         rewritten.
     */
    JavaConstant getNodeRewritingAssumptionConstant();

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
}
