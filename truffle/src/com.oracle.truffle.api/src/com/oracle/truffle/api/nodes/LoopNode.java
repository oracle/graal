/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * <p>
 * A loop node calls {@link RepeatingNode#executeRepeating(VirtualFrame) repeating nodes} as long as
 * it returns <code>true</code>. Using the loop node in a guest language implementation allows the
 * Truffle runtime to optimize loops in a better way. For example a Truffle runtime implementation
 * might decide to optimize loop already during its first execution (also called on stack
 * replacement OSR). Loop nodes are not intended to be implemented by Truffle runtime
 * implementations and not by guest language implementations.
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
     * Invokes one loop invocation by repeatedly call
     * {@link RepeatingNode#executeRepeating(VirtualFrame) execute)} on the repeating node the loop
     * was initialized with. Any exceptions that occur in the execution of the repeating node will
     * just be forwarded to this method and will cancel the current loop invocation.
     *
     * @param frame the current execution frame or null if the repeating node does not require a
     *            frame
     * @since 0.8 or earlier
     */
    public abstract void executeLoop(VirtualFrame frame);

    /**
     * Returns the repeating node the loop node was created with.
     *
     * @since 0.8 or earlier
     */
    public abstract RepeatingNode getRepeatingNode();

    /**
     * <p>
     * Reports the execution count of a loop for which a no {@link LoopNode} was used. The
     * optimization heuristics can use the loop count from non Truffle loops to guide compilation
     * and inlining better. Do not use {@link LoopNode} and {@link #reportLoopCount(Node, int)} at
     * the same time for one loop.
     * </p>
     *
     * <p>
     * Example usage with a custom loop: <code>
     * <pre>
     * public int executeCustomLoopSum(int[] data) {
     *     try {
     *         int sum = 0;
     *         for (int i = 0; i < data.length; i++) {
     *             sum += data[i];
     *         }
     *         return sum;
     *     } finally {
     *         LoopNode.reportLoopCount(this, data.length);
     *     }
     * }
     *
     * </pre>
     * </code>
     * </p>
     *
     * @param source the Node which invoked the loop.
     * @param iterations the number iterations to report to the runtime system
     * @since 0.12
     */
    public static void reportLoopCount(Node source, int iterations) {
        if (CompilerDirectives.inInterpreter()) {
            Node.ACCESSOR.onLoopCount(source, iterations);
        }
    }

}
