/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.graphbuilderconf;

import com.oracle.jvmci.meta.ResolvedJavaMethod;
import com.oracle.jvmci.meta.JavaType;
import com.oracle.graal.nodes.*;

/**
 * Plugin for specifying what is inlined during graph parsing. This plugin is also
 * {@linkplain #notifyOfNoninlinedInvoke notified} of non-inlined invocations (i.e., those for which
 * an {@link Invoke} node is created).
 */
public interface InlineInvokePlugin extends GraphBuilderPlugin {

    public static class InlineInfo {

        /**
         * The method to be inlined.
         */
        public final ResolvedJavaMethod methodToInline;

        /**
         * Specifies if {@link #methodToInline} is an intrinsic for the original method (i.e., the
         * {@code method} passed to {@link InlineInvokePlugin#getInlineInfo}).
         */
        public final boolean isIntrinsic;

        public InlineInfo(ResolvedJavaMethod methodToInline, boolean isIntrinsic) {
            this.methodToInline = methodToInline;
            this.isIntrinsic = isIntrinsic;
        }
    }

    /**
     * Determines whether a call to a given method is to be inlined.
     *
     * @param method the target method of an invoke
     * @param args the arguments to the invoke
     * @param returnType the return type derived from {@code method}'s signature
     */
    default InlineInvokePlugin.InlineInfo getInlineInfo(@SuppressWarnings("unused") GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
        return null;
    }

    /**
     * @param inlinedTargetMethod
     */
    default void postInline(ResolvedJavaMethod inlinedTargetMethod) {
    }

    /**
     * Notifies this plugin of the {@link Invoke} node created for a method that was not inlined per
     * {@link #getInlineInfo}.
     *
     * @param method the method that was not inlined
     * @param invoke the invoke node created for the call to {@code method}
     */
    default void notifyOfNoninlinedInvoke(@SuppressWarnings("unused") GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
    }
}
