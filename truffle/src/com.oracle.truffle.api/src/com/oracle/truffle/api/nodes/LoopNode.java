/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * <p>
 * A loop node calls {@link RepeatingNode#executeRepeating(VirtualFrame) repeating nodes} as long as
 * it returns <code>true</code>. Using the loop node in a guest language implementation allows the
 * Truffle runtime to optimize loops in a better way. For example a Truffle runtime implementation
 * might decide to optimize loop already during its first execution (also called on stack
 * replacement OSR). Loop nodes are intended to be implemented by Truffle runtime implementations
 * and not by guest language implementations.
 *
 * Note: The loop condition is automatically profiled by the loop node, so the {@link RepeatingNode
 * repeating node} should not use a loop condition profile.
 * </p>
 * <p>
 * Full usage example for guest language while node:
 * </p>
 *
 * <pre>
 * <code>
 * public class WhileNode extends GuestLanguageNode {
 *
 *     &#064;{@link Node.Child} private {@link LoopNode} loop;
 *
 *     public WhileNode(GuestLanguageNode conditionNode, GuestLanguageNode bodyNode) {
 *         loop = Truffle.getRuntime().createLoopNode(new WhileRepeatingNode(conditionNode, bodyNode));
 *     }
 *
 *     &#064;Override
 *     public Object execute({@link VirtualFrame} frame) {
 *         loop.executeLoop(frame);
 *         return null;
 *     }
 *
 *     private static class WhileRepeatingNode extends {@link Node} implements {@link RepeatingNode} {
 *
 *         &#064;{@link Node.Child} private GuestLanguageNode conditionNode;
 *         &#064;{@link Node.Child} private GuestLanguageNode bodyNode;
 *
 *         public WhileRepeatingNode(GuestLanguageNode conditionNode, GuestLanguageNode bodyNode) {
 *             this.conditionNode = conditionNode;
 *             this.bodyNode = bodyNode;
 *         }
 *
 *         public boolean executeRepeating({@link VirtualFrame} frame) {
 *             if ((boolean) conditionNode.execute(frame)) {
 *                 try {
 *                     bodyNode.execute(frame);
 *                 } catch (ContinueException ex) {
 *                     // the body might throw a continue control-flow exception
 *                     // continue loop invocation
 *                 } catch (BreakException ex) {
 *                     // the body might throw a break control-flow exception
 *                     // break loop invocation by returning false
 *                     return false;
 *                 }
 *                 return true;
 *             } else {
 *                 return false;
 *             }
 *         }
 *     }
 *
 * }
 *
 * // substitute with a guest language node type
 * public abstract class GuestLanguageNode extends {@link Node} {
 *
 *     public abstract Object execute({@link VirtualFrame} frame);
 *
 * }
 * // thrown by guest language continue statements
 * public final class ContinueException extends {@link ControlFlowException} {}
 * // thrown by guest language break statements
 * public final class BreakException extends {@link ControlFlowException} {}
 * </code>
 * </pre>
 *
 *
 * @see RepeatingNode
 * @see TruffleRuntime#createLoopNode(RepeatingNode)
 * @since 0.8 or earlier
 */
public abstract class LoopNode extends Node {
    /**
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     */
    protected LoopNode() {
    }

    /**
     * Invokes one loop invocation by repeatedly calling
     * {@link RepeatingNode#executeRepeating(VirtualFrame) execute)} on the repeating node the loop
     * was initialized with. Any exceptions that occur in the execution of the repeating node will
     * just be forwarded to this method and will cancel the current loop invocation.
     *
     * @param frame the current execution frame or null if the repeating node does not require a
     *            frame
     * @return a value <code>v</code> returned by
     *         {@link RepeatingNode#executeRepeating(VirtualFrame) execute} satisfying
     *         {@link RepeatingNode#shouldContinue shouldContinue(v)}<code> == false</code>, which
     *         can be used in a language-specific way (for example, to encode structured jumps)
     * @since 19.3
     */
    public Object execute(VirtualFrame frame) {
        throw new AbstractMethodError("This method must be overridden in concrete subclasses.");
    }

    /**
     * Returns the repeating node the loop node was created with.
     *
     * @since 0.8 or earlier
     */
    public abstract RepeatingNode getRepeatingNode();

    /**
     * <p>
     * Reports the execution count of a loop for which no {@link LoopNode} was used. The
     * optimization heuristics can use the loop count from non Truffle loops to guide compilation
     * and inlining better. Do not use {@link LoopNode} and {@link #reportLoopCount(Node, int)} at
     * the same time for one loop. If the number of iterations needs to be counted and can overflow,
     * only count if {@link CompilerDirectives#hasNextTier()} is <code>true</code> (reporting will
     * have no effect in the last tier) and consider reporting {@link Integer#MAX_VALUE} in case of
     * overflows. If the number is often zero and {@link #reportLoopCount(Node, int)} is called
     * frequently (e.g. in a loop), consider adding a check to avoid the overhead of redundant calls
     * with an <code>iterations</code> argument of zero in the interpreter.
     *
     * <p>
     * Example usage for a custom loop iterating over an array: <code>
     * <pre>
     * public int executeCustomLoopSum(int[] data) {
     *     int sum = 0;
     *     for (int i = 0; i &lt; data.length; i++) {
     *         sum += data[i];
     *     }
     *     LoopNode.reportLoopCount(this, data.length);
     *     return sum;
     * }
     * </pre>
     * </code>
     *
     * Example usage for a custom loop with an unknown number of iterations and a potential
     * <code>int</code> overflow: <code>
     * <pre>
     * public Object executeCustomLoopWithUnknownIterations(int[] data) {
     *     Object result;
     *     int counter = 0;
     *     while(true) {
     *         if (isResultAvailable()) {
     *             result = getResult();
     *             break;
     *         }
     *         // do some work to calculate result
     *         if (CompilerDirectives.hasNextTier()) {
     *             counter++;
     *         }
     *     }
     *     if (counter != 0) {
     *         LoopNode.reportLoopCount(this, counter > 0 ? counter : Integer.MAX_VALUE);
     *     }
     *     return result;
     * }
     * </pre>
     * </code>
     * </p>
     *
     * @param source the Node which invoked the loop.
     * @param iterations the number iterations to report to the runtime system, must be >= 0
     * @since 0.12
     */
    public static void reportLoopCount(Node source, int iterations) {
        assert iterations >= 0;
        if (CompilerDirectives.hasNextTier()) {
            if (CompilerDirectives.isPartialEvaluationConstant(source)) {
                NodeAccessor.RUNTIME.onLoopCount(source, iterations);
            } else {
                onLoopCountBoundary(source, iterations);
            }
        }
    }

    @TruffleBoundary
    private static void onLoopCountBoundary(Node source, int iterations) {
        NodeAccessor.RUNTIME.onLoopCount(source, iterations);
    }

}
