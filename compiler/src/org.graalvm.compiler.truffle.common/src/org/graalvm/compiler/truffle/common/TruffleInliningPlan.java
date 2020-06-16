/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaConstant;

/**
 * A plan to be consulted when partial evaluating or compiling a Truffle AST as to whether a given
 * call should be inlined.
 */
public interface TruffleInliningPlan extends TruffleMetaAccessProvider {

    /**
     * Gets the decision of whether or not to inline the Truffle AST called by {@code callNode}.
     *
     * @param callNode a call in the AST represented by this object
     * @return the decision for {@code callNode} or {@code null} when this object contains no
     *         decision for {@code callNode}
     */
    Decision findDecision(JavaConstant callNode);

    /**
     * Decision of whether a called Truffle AST should be inlined. If {@link #shouldInline()}
     * returns {@code true}, this object is also an inlining plan for the calls in the to-be-inlined
     * AST.
     */
    interface Decision extends TruffleInliningPlan {

        /**
         * Returns whether the Truffle AST to which this decision pertains should be inlined.
         */
        boolean shouldInline();

        /**
         * Determines if the Truffle AST to which this decision pertains did not change between AST
         * execution and computation of the inlining decision tree.
         */
        boolean isTargetStable();

        /**
         * Gets a name for the Truffle AST to which this decision pertains.
         */
        String getTargetName();

        /**
         * Gets the assumption that will be invalidated when a node is rewritten in the Truffle AST
         * to which this decision pertains.
         */
        JavaConstant getNodeRewritingAssumption();
    }
}
