/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.spi;

import java.lang.foreign.MemorySegment;

import jdk.graal.compiler.core.common.util.PhasePlan;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Implements the logic that decides whether a field read should be constant folded.
 */
public interface ConstantFieldProvider {

    interface ConstantFieldTool<T> {

        OptionValues getOptions();

        JavaConstant readValue();

        JavaConstant getReceiver();

        /**
         * The reason why this constant folding was attempted. Ideally this is a
         * {@link jdk.vm.ci.code.BytecodePosition}, where available, or a {@link String}
         * description, however it can be {@code null}.
         */
        Object getReason();

        T foldConstant(JavaConstant ret);

        T foldStableArray(JavaConstant ret, int stableDimensions, boolean isDefaultStable);
    }

    /**
     * Decide whether a read from the {@code field} should be constant folded. This should return
     * {@link ConstantFieldTool#foldConstant} or {@link ConstantFieldTool#foldStableArray} if the
     * read should be constant folded, or {@code null} otherwise.
     */
    <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool);

    /**
     * Returns {@code true} if a field may be constant folded even though it is not declared as
     * {@code final}. This applies to well-known fields such as {@code String#hash}.
     */
    default boolean maybeFinal(@SuppressWarnings("unused") ResolvedJavaField field) {
        return false;
    }

    /**
     * Returns {@code true} if {@code field} is final and considered "trusted". A trusted final
     * field is immutable after the initialization of the holder object. This allows us to schedule
     * a load from {@code field} freely up until the point at which we can guarantee that the object
     * is initialized. It also allows us to assume that there is no store to the field after it is
     * read, skipping the anti-dependency calculation while scheduling.
     * <p>
     * Example 1:
     * {@snippet :
     * class Holder {
     *     final int x;
     * }
     *
     * void test(Holder h) {
     *     for (int i = 0; i < 100; i++) {
     *         nonInlinedCall(h.x);
     *     }
     * }
     * }
     * Without further contexts, we cannot assume that {@code nonInlineCall} will not modify
     * {@code h.x}, even if it is a final field, because {@code Unsafe} and the core reflection API
     * can be used to modify a final field even after initialization. Additionally, there's a risk
     * that a constructor of {@code Holder} may use {@code Holder.x} as a flag for inter-thread
     * communication. As a result, the load from {@code h.x} needs to be executed in each iteration.
     * However, if we can trust that {@code h} should be initialized before being passed to
     * {@code test} and {@code x} should not be modified post-initialization, then we can hoist the
     * load outside the loop.
     * <p>
     * When a {@link FloatingReadNode} reads from a field for which this method returns
     * {@code true}, we rewire its memory input to the earliest location possible in the CFG. In the
     * example above, we rewire the memory input of the load {@code h.x} to the {@link StartNode} of
     * the method {@code test}. This allows the load to have the maximum freedom in scheduling, as
     * well as enables all the loads from {@code h.x} to be GVN-ed by making their memory inputs
     * identical. Note that the load is still not immutable, this is important because if the method
     * {@code test} is later inlined into another graph, it may turn out that {@code h} is created
     * and {@code h.x} is assigned in the caller. In those cases, marking the load node as immutable
     * would be incorrect. On the other hand, since the memory input of the node is the
     * {@link StartNode} of the method {@code test}, after inlining, this node is expanded into the
     * memory node corresponding to the input memory state of the invocation to {@code test} in the
     * caller graph, we can still ensure that the load is not scheduled too freely which can bypass
     * the initialization in the caller. This is generally not a concern because
     * {@link FloatingReadNode}s are only introduced after the high tier, while inlining is only
     * performed during high tier. However, there may be other mechanisms such as snippets which can
     * inline code in a limited manner after high tier, and it is more future-proof to not rely on
     * the {@link PhasePlan} for the correctness of this transformation.
     * <p>
     * To ensure that the load has the maximum freedom in scheduling, we mark that the load has no
     * anti-dependency. In other word, it aliases with no store that is dominated by its memory
     * input, and we do not have to take anti-dependency into consideration while scheduling the
     * load. This is necessary both in terms of correctness and efficiency. As moving the memory
     * input may introduce incorrect anti-dependency with nodes between the old and the new memory
     * input in the CFG, potentially leading to an unschedulable graph.
     * <p>
     * This transformation can be performed if the holder object is:
     * <ul>
     * <li>A parameter that is not the receiver of a constructor, the earliest memory input can be
     * the {@link StartNode} of the graph.
     * <li>A value returned from a method invocation, the earliest memory input can be the
     * {@link Invoke} corresponding to the invocation (to do).
     * </ul>
     * It can be trivially extended to other trusted final fields in the JDK (e.g.
     * {@link MemorySegment}). However, extending it to general uses would risk violating the JVM
     * semantics.
     */
    boolean isTrustedFinal(CanonicalizerTool tool, ResolvedJavaField field);
}
