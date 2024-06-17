/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Thread local reference class to remember the current encapsulating node of an interpreter on the
 * stack. The current encapsulating node is used whenever the code transitions from cached to
 * uncached cases. This typically happens when the maximum limit for an inline cache was reached and
 * the uncached version of a node will be used behind a {@link TruffleBoundary}. To correctly
 * produce exception stack traces it is necessary to remember the node that transitioned to the
 * uncached case. Uncached calls to {@link CallTarget} will automatically use the value of the
 * encapsulating node. Truffle DSL produces such transitions automatically if Truffle libraries are
 * used and they transition to uncached.
 * <p>
 * Usage example for pushing and restoring the current node to the stack.
 *
 * <pre>
 * EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
 * Node encapsulatingNode = encapsulating.set(currentNode);
 * try {
 *     // follow uncached path
 * } finally {
 *     encapsulating.set(encapsulatingNode);
 * }
 * </pre>
 * <p>
 * The current encapsulating node can also be useful when constructing guest exceptions in
 * slow-paths and the location of the error needs to be found out reliably. Usage example:
 *
 * <pre>
 * final class LanguageException extends AbstractTruffleException {
 *
 *     private LanguageException(Node locationNode) {
 *         super(locationNode);
 *     }
 *
 *     static LanguageException create(Node locationNode) {
 *         CompilerAsserts.partialEvaluationConstant(locationNode);
 *         if (locationNode == null || !locationNode.isAdoptable()) {
 *             return new LanguageException(EncapsulatingNodeReference.getCurrent().get());
 *         } else {
 *             return new LanguageException(locationNode);
 *         }
 *     }
 *
 *     // ...
 * }
 * </pre>
 *
 * Note that {@link Node#isAdoptable()} is a way to find out whether a node was used in an uncached
 * scenario or not.
 *
 *
 * @since 20.2
 */
public final class EncapsulatingNodeReference {

    /*
     * Fallback thread local used only if there is no current polyglot context entered.
     */
    private static final ThreadLocal<EncapsulatingNodeReference> CURRENT = new ThreadLocal<>() {

        @Override
        protected EncapsulatingNodeReference initialValue() {
            return new EncapsulatingNodeReference(Thread.currentThread());
        }

    };

    @CompilationFinal private static volatile boolean seenNullContext;

    private final WeakReference<Thread> thread;
    private Node reference;

    EncapsulatingNodeReference(Thread t) {
        this.thread = new WeakReference<>(t);
    }

    /**
     * Sets the current encapsulating node for the current thread and returns its previous value.
     * The passed node may be <code>null</code>. The set node must be {@link Node#isAdoptable()
     * adoptable} and be adopted by a {@link RootNode} otherwise an {@link AssertionError} is
     * thrown. This method must only be used from the thread it was {@link #getCurrent() requested}
     * for. This method is safe to be called from compiled code paths.
     *
     * @since 20.2
     */
    public Node set(Node node) {
        assert node == null || node.isAdoptable() : "Node must be adoptable to be pushed as encapsulating node.";
        assert node == null || node.getRootNode() != null : "Node must be adopted by a RootNode to be pushed as encapsulating node.";
        assert Thread.currentThread() == this.thread.get();
        Node old = this.reference;
        this.reference = node;
        return old;
    }

    /**
     * Returns the current encapsulating node for the current thread. Returned node may be
     * <code>null</code>. If the returned node is non-null then it is guaranteed to be
     * {@link Node#isAdoptable() adoptable} and adopted by a {@link RootNode}. This method must only
     * be used from the thread it was {@link #getCurrent() requested} for. This method is safe to be
     * called from compiled code paths.
     *
     * @since 20.2
     */
    public Node get() {
        assert Thread.currentThread() == this.thread.get();
        return reference;
    }

    /**
     * Returns the encapsulating node reference of the {@link Thread#currentThread() current}
     * thread. The returned value is never <code>null</code>. This method is safe to be called from
     * compiled code paths.
     *
     * @since 20.2
     */
    public static EncapsulatingNodeReference getCurrent() {
        /*
         * If we have never seen an out of context access (common-case) then we can deoptimize early
         * when reading while not entered in a context. This flag has only an effect in compiled
         * code paths, so we safe the read for the interpreter.
         */
        boolean invalidateOnNull = CompilerDirectives.inCompiledCode() ? !seenNullContext : false;
        EncapsulatingNodeReference ref = NodeAccessor.ENGINE.getEncapsulatingNodeReference(invalidateOnNull);
        if (ref != null) {
            // fast path inside contexts
            return ref;
        }
        // use outside of a polyglot context
        if (!seenNullContext) {
            /*
             * We deliberately do not use an assumption here, as an assumption would be too
             * footprint heavy. Basically all compilation units that use encapsulating node
             * references would be registered here. Non-eager deoptimization seems to be fine
             * trade-off for this use-case as it is extremely unlikely that usages of this profile
             * are compiled in cases where there is either a context and no context.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            seenNullContext = true;
        }
        return getThreadLocal();
    }

    @TruffleBoundary
    private static EncapsulatingNodeReference getThreadLocal() {
        return CURRENT.get();
    }

}
