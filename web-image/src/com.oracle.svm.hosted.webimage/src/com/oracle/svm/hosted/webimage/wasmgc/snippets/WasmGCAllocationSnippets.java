/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc.snippets;

import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCAllocationSupport;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;
import jdk.vm.ci.meta.JavaKind;

public class WasmGCAllocationSnippets implements Snippets {

    /**
     * Allocates an array for given statically unknown component type.
     * <p>
     * {@code componentClass} must be non-null and {@code length} must be non-negative.
     *
     * @see WasmGCAllocationSupport#dynamicNewArray(DynamicHub, int)
     */
    @Snippet(allowMissingProbabilities = true)
    public static Object dynamicNewArraySnippet(Class<?> componentClass, int length) {
        Class<?> nonNullComponentClass = PiNode.piCastNonNullClass(componentClass, SnippetAnchorNode.anchor());
        int nonNegativeLength = PiNode.piCastPositive(length, SnippetAnchorNode.anchor());

        if (nonNullComponentClass == int.class) {
            return new int[length];
        } else if (nonNullComponentClass == boolean.class) {
            return new boolean[length];
        } else if (nonNullComponentClass == byte.class) {
            return new byte[length];
        } else if (nonNullComponentClass == short.class) {
            return new short[length];
        } else if (nonNullComponentClass == char.class) {
            return new char[length];
        } else if (nonNullComponentClass == long.class) {
            return new long[length];
        } else if (nonNullComponentClass == float.class) {
            return new float[length];
        } else if (nonNullComponentClass == double.class) {
            return new double[length];
        } else {
            // We can do code gen for DynamicNewArrayNode if we know it's not a primitive array.
            return DynamicNewArrayNode.newArray(nonNullComponentClass, nonNegativeLength, JavaKind.Object);
        }
    }

    public static class Templates extends SubstrateTemplates {
        public final SnippetTemplate.SnippetInfo dynamicNewArraySnippet;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Providers providers) {
            super(options, providers);
            this.dynamicNewArraySnippet = snippet(providers, WasmGCAllocationSnippets.class, "dynamicNewArraySnippet");
        }
    }
}
