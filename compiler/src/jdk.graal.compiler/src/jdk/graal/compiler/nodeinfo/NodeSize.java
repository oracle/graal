/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodeinfo;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * Constants representing the abstract number of CPU instructions needed to represent a node. This
 * ignores byte sizes of instructions (i.e., 32/64/variable-length).
 *
 * Note that the sizes specified are only estimations and should only be used to enhance performance
 * of generated code. They should not be used to change any semantics of nodes.
 *
 * If a specific node has vastly different sizes based on different CPUs or how it might be
 * potentially lowered (e.g., 1 versus 100 instructions), it should override
 * {@code Node.estimatedNodeSize()}. Otherwise, just use the average of the different values (e.g.,
 * if AMD64 size is 5 and AArch64 is 1, use 3).
 *
 * In general, the current sizes for nodes are based on domain knowledge and experience, including
 * machine-learning based tweaking. When adding a new node, it's best to look at the cost model for
 * similar nodes if unsure what values to specify. Benchmarking is also useful to tune model
 * parameters.
 */
public enum NodeSize {

    /**
     * The default value of the {@link NodeInfo#size()} property.
     * <p>
     * For further information about the use of {@code SIZE_UNSET} see {@link NodeInfo#size()}.
     */
    SIZE_UNSET(0),

    /**
     * Nodes for which, due to arbitrary reasons, no estimation can be made either (1) statically
     * without inspecting the properties of a node or (2) at all (like e.g. for an invocation).
     * <p>
     * Nodes annotated with {@code SIZE_UNKNOWN} should specify the {@link NodeInfo#sizeRationale()}
     * property to clarify why an estimation cannot be done.
     */
    SIZE_UNKNOWN(0),

    /**
     * Nodes for which code size information is irrelevant and can be ignored, e.g. for test nodes.
     */
    SIZE_IGNORED(0),

    /**
     * Nodes that do not require any code to be generated in order to be "executed", e.g. a PiNode.
     */
    SIZE_0(0),

    SIZE_1(1),
    SIZE_2(2),
    SIZE_4(4),
    SIZE_8(8),
    SIZE_16(16),
    SIZE_32(32),
    SIZE_64(64),
    SIZE_128(128),
    SIZE_256(256),
    SIZE_512(512),
    SIZE_1024(1024);

    private static final NodeSize[] VALUES = values();

    public final int value;

    NodeSize(int value) {
        this.value = value;
    }

    public static final int IGNORE_SIZE_CONTRACT_FACTOR = 0xFFFF;

    public static NodeSize compute(NodeSize base, int opCount) {
        assert NumUtil.assertNonNegativeInt(opCount);
        if (opCount == 0) {
            return SIZE_0;
        }
        assert base.ordinal() > SIZE_0.ordinal() : base;
        int log2 = log2(base.value * opCount);
        for (int i = base.ordinal(); i < VALUES.length; i++) {
            if (log2(VALUES[i].value) == log2) {
                return VALUES[i];
            }
        }
        return SIZE_1024;
    }

    public static NodeSize compute(int rawValue) {
        assert NumUtil.assertNonNegativeInt(rawValue);
        if (rawValue == 0) {
            return SIZE_0;
        }
        assert NumUtil.assertPositiveInt(rawValue);
        for (int i = SIZE_0.ordinal(); i < VALUES.length - 1; i++) {
            if (VALUES[i].value >= rawValue && rawValue <= VALUES[i + 1].value) {
                int r1 = VALUES[i].value;
                int r2 = VALUES[i + 1].value;
                int diff = r2 - r1;
                return rawValue - r1 > diff / 2 ? VALUES[i + 1] : VALUES[i];
            }
        }
        return SIZE_1024;
    }

    private static int log2(int val) {
        return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(val);
    }
}
