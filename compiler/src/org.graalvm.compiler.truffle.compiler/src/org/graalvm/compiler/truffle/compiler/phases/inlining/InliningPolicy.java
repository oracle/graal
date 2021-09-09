/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases.inlining;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public interface InliningPolicy {

    default void afterAddChildren(CallNode callNode) {
    }

    default void removedNode(CallNode node) {
    }

    default Object newCallNodeData(CallNode callNode) {
        return null;
    }

    default void putProperties(CallNode callNode, Map<Object, Object> properties) {
    }

    default void afterExpand(CallNode callNode) {
    }

    default void run(CallTree tree) {
    }

    /**
     * Checks if the {@link CallNode} should be compiled. The
     * {@link PolyglotCompilerOptions#InlineOnly InlineOnly} options are used to determine if the
     * root node should be compiled.
     */
    static boolean acceptForInline(CallNode rootNode, String inlineOnly) {
        if (inlineOnly == null) {
            return true;
        }
        Pair<List<String>, List<String>> value = getInlineOnly(inlineOnly);
        if (value != null) {
            String name = rootNode.getName();
            List<String> includes = value.getLeft();
            boolean included = includes.isEmpty();
            if (name != null) {
                for (int i = 0; !included && i < includes.size(); i++) {
                    if (name.contains(includes.get(i))) {
                        included = true;
                    }
                }
            }
            if (!included) {
                return false;
            }
            if (name != null) {
                for (String exclude : value.getRight()) {
                    if (name.contains(exclude)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns the include and exclude sets for the {@link PolyglotCompilerOptions#InlineOnly}
     * option. The {@link Pair#getLeft() left} value is the include set and the
     * {@link Pair#getRight() right} value is the exclude set.
     */
    static Pair<List<String>, List<String>> getInlineOnly(String inlineOnly) {
        List<String> includesList = new ArrayList<>();
        List<String> excludesList = new ArrayList<>();
        String[] items = inlineOnly.split(",");
        for (String item : items) {
            if (item.startsWith("~")) {
                excludesList.add(item.substring(1));
            } else {
                includesList.add(item);
            }
        }
        return Pair.create(includesList, excludesList);
    }
}
