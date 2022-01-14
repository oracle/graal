/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.DeoptTest;
import com.oracle.svm.core.annotate.Specialize;
import com.oracle.svm.hosted.code.CompileQueue.CompileFunction;
import com.oracle.svm.hosted.code.CompileQueue.ParseFunction;
import com.oracle.svm.hosted.meta.HostedMethod;

public class CompilationInfo {

    protected final HostedMethod method;

    protected final AtomicBoolean inParseQueue = new AtomicBoolean(false);
    /**
     * No need for this flag to be atomic, because {@link CompileQueue#compilations} is used to
     * ensure each method is compiled only once.
     */
    protected boolean inCompileQueue;

    protected volatile StructuredGraph graph;

    protected boolean isTrivialMethod;

    protected boolean canDeoptForTesting;

    protected boolean modifiesSpecialRegisters;

    /**
     * The constant arguments for a {@link DeoptTest} method called by a {@link Specialize} method.
     * Note: this is only used for testing.
     */
    protected ConstantNode[] specializedArguments;

    /**
     * A link to the deoptimization target method if this method can deoptimize.
     */
    protected HostedMethod deoptTarget;

    /**
     * A link to the regular compiled method if this method is a deoptimization target.
     *
     * Note that it is important that this field is final: the {@link HostedMethod#getName() method
     * name} depends on this field (to distinguish a regular method from a deoptimization target
     * method), so mutating this field would mutate the name of a method.
     */
    protected final HostedMethod deoptOrigin;

    /* Custom parsing and compilation code that is executed instead of that of CompileQueue */
    protected ParseFunction customParseFunction;
    protected CompileFunction customCompileFunction;

    /* Statistics collected before/during compilation. */
    protected long numNodesAfterParsing;
    protected long numNodesBeforeCompilation;
    protected long numNodesAfterCompilation;
    protected long numDeoptEntryPoints;
    protected long numDuringCallEntryPoints;

    /* Statistics collected when method is put into compile queue. */
    protected final AtomicLong numDirectCalls = new AtomicLong();
    protected final AtomicLong numVirtualCalls = new AtomicLong();
    protected final AtomicLong numEntryPointCalls = new AtomicLong();

    public CompilationInfo(HostedMethod method, HostedMethod deoptOrigin) {
        this.method = method;
        this.deoptOrigin = deoptOrigin;

        if (deoptOrigin != null) {
            assert deoptOrigin.compilationInfo.deoptTarget == null;
            deoptOrigin.compilationInfo.deoptTarget = method;
        }
    }

    public boolean isDeoptTarget() {
        return deoptOrigin != null;
    }

    public boolean isDeoptEntry(int bci, boolean duringCall, boolean rethrowException) {
        return isDeoptTarget() && (deoptOrigin.compilationInfo.canDeoptForTesting || CompilationInfoSupport.singleton().isDeoptEntry(method, bci, duringCall, rethrowException));
    }

    /**
     * Returns whether this bci was registered as a potential deoptimization entrypoint via
     * {@link CompilationInfoSupport#registerDeoptEntry}.
     */
    public boolean isRegisteredDeoptEntry(int bci, boolean duringCall, boolean rethrowException) {
        return isDeoptTarget() && CompilationInfoSupport.singleton().isDeoptTarget(method) && CompilationInfoSupport.singleton().isDeoptEntry(method, bci, duringCall, rethrowException);
    }

    public boolean canDeoptForTesting() {
        return canDeoptForTesting;
    }

    public HostedMethod getDeoptTargetMethod() {
        return deoptTarget;
    }

    public void setGraph(StructuredGraph graph) {
        this.graph = graph;
    }

    public void clear() {
        graph = null;
        specializedArguments = null;
    }

    public StructuredGraph getGraph() {
        return graph;
    }

    public boolean modifiesSpecialRegisters() {
        assert SubstrateOptions.useLLVMBackend();
        return modifiesSpecialRegisters;
    }

    public boolean isTrivialMethod() {
        return isTrivialMethod;
    }

    public void setTrivialMethod(boolean trivial) {
        isTrivialMethod = trivial;
    }

    public void setCustomParseFunction(ParseFunction parseFunction) {
        this.customParseFunction = parseFunction;
    }

    public ParseFunction getCustomParseFunction() {
        return customParseFunction;
    }

    public void setCustomCompileFunction(CompileFunction compileFunction) {
        this.customCompileFunction = compileFunction;
    }

    public CompileFunction getCustomCompileFunction() {
        return customCompileFunction;
    }

    public boolean hasDefaultParseFunction() {
        return customCompileFunction == null;
    }
}
