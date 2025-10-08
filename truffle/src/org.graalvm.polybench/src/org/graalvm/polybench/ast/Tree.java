/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.ast;

import java.util.ArrayList;

public interface Tree {

    record Program(Decl[] declarations, Decl.Main main) implements Tree {

        public static class Builder extends Tree.Builder {
            private final ArrayList<Decl> members;
            private final Decl.Main main;

            public Builder(Tree.Builder parent, Decl.Main main) {
                super(parent);
                this.main = main;
                this.members = new ArrayList<>();
            }

            @Override
            public void append(Tree member) {
                if (member instanceof Decl decl) {
                    members.add(decl);
                } else {
                    throw new IllegalArgumentException("Cannot append objects that are not an instance of " + Decl.class.getName());
                }
            }

            @Override
            public Program build() {
                return new Program(members.toArray(new Decl[members.size()]), main);
            }
        }
    }

    abstract class Builder implements AutoCloseable {
        Tree.Builder parent;

        Builder(Tree.Builder parent) {
            this.parent = parent;
        }

        abstract void append(Tree member);

        abstract Tree build();

        @Override
        public void close() {
            if (parent != null) {
                parent.append(this.build());
            }
        }
    }
}
