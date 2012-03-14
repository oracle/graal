/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.spi.types;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

public interface TypeCanonicalizable {

    Node[] EMPTY_ARRAY = new Node[0];

    public static class Result {
        public final ValueNode replacement;
        public final Node[] dependencies;

        public Result(ValueNode replacement) {
            this.replacement = replacement;
            this.dependencies = EMPTY_ARRAY;
        }

        public Result(ValueNode replacement, TypeQuery query) {
            assert query != null;
            this.replacement = replacement;
            if (query.dependency() != null) {
                this.dependencies = new Node[] {query.dependency()};
            } else {
                this.dependencies = EMPTY_ARRAY;
            }
        }

        public Result(ValueNode replacement, TypeQuery... queries) {
            this.replacement = replacement;
            HashSet<Node> deps = new HashSet<>();
            for (TypeQuery query : queries) {
                if (query.dependency() != null) {
                    deps.add(query.dependency());
                }
            }
            this.dependencies = deps.toArray(new Node[deps.size()]);
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder().append('[');
            str.append(replacement);
            if (dependencies.length > 0) {
                str.append(" @");
                for (Node dep : dependencies) {
                    str.append(' ').append(dep);
                }
            }
            return str.append(']').toString();
        }
    }

    Result canonical(TypeFeedbackTool tool);
}
