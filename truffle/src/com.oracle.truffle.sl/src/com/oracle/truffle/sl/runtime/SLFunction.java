/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.nodes.SLUndefinedFunctionRootNode;

/**
 * Represents a SL function. On the Truffle level, a callable element is represented by a
 * {@link SLRootNode root node}. This class encapsulates a root node, and adds version support:
 * functions in SL can be redefined, i.e. changed at run time. When a function is redefined, the
 * root node managed by this function object is changed (and {@link #rootNode} is therefore not a
 * final field).
 * <p>
 * Function redefinition is expected to be rare, therefore optimized call nodes want to speculate
 * that the root node is stable. This is possible with the help of a Truffle {@link Assumption}: a
 * call node can keep the root node returned by {@link #getRootNode()} cached until the assumption
 * returned by {@link #getCallTargetStable()} is valid.
 * <p>
 * To ensure that only one {@link SLFunction} instance per name exists, the
 * {@link SLFunctionRegistry} creates an instance also when performing name lookup. A function that
 * has been looked up, i.e., used, but not defined, has an {@link SLUndefinedFunctionRootNode}.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class SLFunction implements TruffleObject {

    public static final int INLINE_CACHE_SIZE = 2;

    private static final TruffleLogger LOG = TruffleLogger.getLogger(SLLanguage.ID, SLFunction.class);

    /** The name of the function. */
    private final TruffleString name;

    /** The current implementation of this function. */
    private SLRootNode rootNode;

    /**
     * Manages the assumption that the {@link #rootNode} is stable. We use the utility class
     * {@link CyclicAssumption}, which automatically creates a new {@link Assumption} when the old
     * one gets invalidated.
     */
    private final CyclicAssumption callTargetStable;

    protected SLFunction(SLLanguage language, TruffleString name) {
        this(name, language.getOrCreateUndefinedFunction(name));
    }

    protected SLFunction(TruffleString name, RootCallTarget callTarget) {
        this.name = name;
        this.callTargetStable = new CyclicAssumption(name.toJavaStringUncached());
        setCallTarget(callTarget);
    }

    public TruffleString getName() {
        return name;
    }

    protected void setCallTarget(RootCallTarget callTarget) {
        boolean wasNull = this.rootNode == null;
        this.rootNode = (SLRootNode) callTarget.getRootNode();
        /*
         * We have a new root node. Invalidate all code that speculated that the old root node was
         * stable.
         */
        LOG.log(Level.FINE, "Installed call target for: {0}", name);
        if (!wasNull) {
            callTargetStable.invalidate();
        }
    }

    public RootCallTarget getCallTarget() {
        return rootNode.getCallTarget();
    }

    public SLRootNode getRootNode() {
        return rootNode;
    }

    public Assumption getCallTargetStable() {
        return callTargetStable.getAssumption();
    }

    /**
     * This method is, e.g., called when using a function literal in a string concatenation. So
     * changing it has an effect on SL programs.
     */
    @Override
    public String toString() {
        return name.toJavaStringUncached();
    }

    @ExportMessage
    boolean hasLanguageId() {
        return true;
    }

    @ExportMessage
    String getLanguageId() {
        return SLLanguage.ID;
    }

    /**
     * {@link SLFunction} instances are always visible as executable to other languages.
     */
    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    SourceSection getSourceLocation() {
        return rootNode.ensureSourceSection();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasSourceLocation() {
        return true;
    }

    /**
     * {@link SLFunction} instances are always visible as executable to other languages.
     */
    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    Object getMetaObject() {
        return SLType.FUNCTION;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doSLFunction(SLFunction receiver, SLFunction other) {
            /*
             * SLFunctions are potentially identical to other SLFunctions.
             */
            return receiver == other ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(SLFunction receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    static int identityHashCode(SLFunction receiver) {
        return System.identityHashCode(receiver);
    }

    @ExportMessage
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return name;
    }

    /**
     * We allow languages to execute this function. We implement the interop execute message that
     * forwards to a function dispatch.
     *
     * Since invocations are potentially expensive (result in an indirect call, which is expensive
     * by itself but also limits function inlining which can hinder other optimisations) if the node
     * turns megamorphic (i.e. cache limit is exceeded) we annotate it with
     * {@link ReportPolymorphism}. This ensures that the runtime is notified when this node turns
     * polymorphic. This, in turn, may, under certain conditions, cause the runtime to attempt to
     * make node monomorphic again by duplicating the entire AST containing that node and
     * specialising it for a particular call site.
     */
    @ReportPolymorphism
    @ExportMessage
    abstract static class Execute {

        /**
         * Dispatches through a cached root and direct call node to enable inlining. Function
         * redefinition invalidates the cached specialization through {@code callTargetStable}.
         *
         * @param function the function to invoke
         * @param arguments the call arguments
         * @param callTargetStable the assumption that the function has not been redefined
         * @param cachedRoot the cached root node
         * @param callNode the direct call node for {@code cachedRoot}
         * @return the call result
         */
        @Specialization(limit = "INLINE_CACHE_SIZE", //
                        guards = "function.getRootNode() == cachedRoot", //
                        assumptions = "callTargetStable")
        @SuppressWarnings("unused")
        protected static Object doDirect(SLFunction function, Object[] arguments,
                        @Cached("function.getCallTargetStable()") Assumption callTargetStable,
                        @Cached(value = "function.getRootNode()", adopt = false) SLRootNode cachedRoot,
                        @Cached("create(cachedRoot.getCallTarget())") DirectCallNode callNode) {

            /* Inline cache hit, we are safe to execute the cached root node. */
            return callNode.call(SLRootNode.class, -1, arguments);
        }

        /**
         * Slow-path code for a call, used when the polymorphic inline cache exceeded its maximum
         * size specified in <code>INLINE_CACHE_SIZE</code>. Such calls are not optimized any
         * further, e.g., no method inlining is performed.
         */
        @Specialization(replaces = "doDirect")
        protected static Object doIndirect(SLFunction function, Object[] arguments,
                        @Cached IndirectCallNode callNode) {
            /*
             * SL has a quite simple call lookup: just ask the function for the current root node,
             * and call it.
             */
            return callNode.call(function.getRootNode(), -1, arguments);
        }
    }

}
