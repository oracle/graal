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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Representation of a continuation closure, consisting of a resumable {@link RootNode}, the
 * interpreter state, and a yielded result. A {@link ContinuationResult} is returned when the
 * interpreter yields. It can later be resumed to continue execution. It can be resumed only once.
 * <p>
 * Below illustrates an example usage of {@link ContinuationResult}.
 *
 * <pre>
 * // Assume yieldingRootNode implements the following pseudocode:
 * //
 * // fun f(x):
 * //   y = yield (x + 1)
 * //   return x + y
 * //
 * MyBytecodeRootNode yieldingRootNode = ...;
 *
 * // The result is a ContinuationResult
 * ContinuationResult yielded = (ContinuationResult) yieldingRootNode.getCallTarget().call(42);
 * assert yielded.getResult() == 43;
 *
 * // Resume the continuation using continueWith. Pass 58 as the value for yield.
 * Integer returned = (Integer) yielded.continueWith(58);
 * assert returned == 100;
 * </pre>
 *
 * For performance reasons, a language may wish to define an inline cache over continuations. In
 * such a case, they should not call {@link #continueWith}, but instead cache and call the
 * {@link #getContinuationRootNode root node} or {@link #getContinuationCallTarget call target}
 * directly. This is necessary because continuation results are dynamic values, not partial
 * evaluation constants. Be careful to conform to the {@link #getContinuationCallTarget calling
 * convention} when calling the continuation root node directly.
 *
 * @see <a href=
 *      "https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/ContinuationsTutorial.java">Continuations
 *      tutorial</a>
 * @since 24.2
 */
@ExportLibrary(value = InteropLibrary.class, delegateTo = "result")
public final class ContinuationResult implements TruffleObject {

    private final ContinuationRootNode rootNode;
    private final MaterializedFrame frame;
    final Object result;

    /**
     * Creates a continuation.
     * <p>
     * The generated interpreter will use this constructor. Continuations should not be created
     * directly by user code.
     *
     * @since 24.2
     */
    public ContinuationResult(ContinuationRootNode rootNode, MaterializedFrame frame, Object result) {
        this.rootNode = rootNode;
        this.frame = frame;
        this.result = result;
    }

    /**
     * Resumes the continuation.
     * <p>
     * This method should generally not be used on compiled code paths. Each yield produces a unique
     * {@link ContinuationResult}, so the receiver object cannot be easily
     * {@link com.oracle.truffle.api.dsl.Cached cached}, which means partial evaluation will be
     * unable to resolve the call target. Instead, it is recommended to cache the
     * {@link #getContinuationCallTarget() continuation call target} and call it directly.
     *
     * @param value the value produced by the yield operation in the resumed execution.
     * @since 24.2
     */
    public Object continueWith(Object value) {
        return getContinuationCallTarget().call(frame, value);
    }

    /**
     * Returns the root node that resumes execution.
     * <p>
     * Note that the continuation root node has a specific calling convention. See
     * {@link #getContinuationCallTarget} for more details, or invoke the root node directly using
     * {@link #continueWith}.
     *
     * @see #getContinuationCallTarget()
     * @since 24.2
     */
    public ContinuationRootNode getContinuationRootNode() {
        return rootNode;
    }

    /**
     * Returns the call target for the {@link #getContinuationRootNode continuation root node}. The
     * call target can be invoked to resume the continuation. It is recommended to register this
     * call target in an inline cache and call it directly.
     * <p>
     * The call target takes two parameters: the materialized interpreter {@link #getFrame frame}
     * and an {@link Object} value to resume execution with. The value becomes the value produced by
     * the yield operation in the resumed execution.
     *
     * @since 24.2
     */
    public RootCallTarget getContinuationCallTarget() {
        return rootNode.getCallTarget();
    }

    /**
     * Returns the state of the interpreter at the point that it was suspended.
     *
     * @since 24.2
     */
    public MaterializedFrame getFrame() {
        return frame;
    }

    /**
     * Returns the value yielded by the yield operation.
     *
     * @since 24.2
     */
    public Object getResult() {
        return result;
    }

    /**
     * Returns the location at which the continuation was created.
     * <p>
     * This location can have a different {@link BytecodeNode} from the
     * {@link ContinuationRootNode#getSourceRootNode() source root node} if the source bytecode was
     * {@link BytecodeRootNodes#update updated} (explicitly or implicitly).
     *
     * @since 24.2
     */
    public BytecodeLocation getBytecodeLocation() {
        return rootNode.getLocation();
    }

    /**
     * Returns a string representation of a {@link ContinuationResult}.
     *
     * @since 24.2
     */
    @Override
    public String toString() {
        return String.format("ContinuationResult [location=%s, result=%s]", getBytecodeLocation(), result);
    }
}
