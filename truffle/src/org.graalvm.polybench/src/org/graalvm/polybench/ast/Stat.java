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

import org.graalvm.polybench.ast.Decl.Variable;

import java.util.ArrayList;

public interface Stat extends Tree {
    record Comment(String text) implements Stat, Decl {
    }

    record Illegal() implements Stat {
    }

    record Block(Stat[] stats) implements Stat {
        public static class Builder extends Tree.Builder {
            final ArrayList<Stat> stats;

            public Builder(Tree.Builder parent) {
                super(parent);
                this.stats = new ArrayList<>();
            }

            @Override
            public void append(Tree member) {
                if (member instanceof Stat stat) {
                    this.stats.add(stat);
                } else {
                    throw new IllegalArgumentException("Cannot append objects that are not an instance of " + Stat.class.getName());
                }
            }

            @Override
            public Stat build() {
                return new Block(stats.toArray(new Stat[stats.size()]));
            }

            public Block.Builder enterBlock() {
                return new Block.Builder(this);
            }
        }
    }

    record If(Expr expr, Stat thenPart, Stat elsePart) implements Stat {
    }

    record For(Stat init, Expr cond, Stat update, Stat body) implements Stat {
        public static class Builder extends Block.Builder {
            private final Stat init;
            private final Expr cond;
            private final Stat update;

            public Builder(Tree.Builder parent, Stat init, Expr cond, Stat update) {
                super(parent);
                this.init = init;
                this.cond = cond;
                this.update = update;
            }

            @Override
            public Stat build() {
                return new For(init, cond, update, super.build());
            }
        }
    }

    record Foreach(Variable iterator, Expr collection, Stat body) implements Stat {
        public static class Builder extends Block.Builder {
            private final Variable iterator;
            private final Expr collection;

            public Builder(Tree.Builder parent, Variable iterator, Expr collection) {
                super(parent);
                this.iterator = iterator;
                this.collection = collection;
            }

            @Override
            public Stat build() {
                return new Foreach(iterator, collection, super.build());
            }
        }
    }

    record Assign(Expr lhs, Expr rhs) implements Stat {
    }

    record Return(Expr expr) implements Stat {
    }

    record Throw(Expr message) implements Stat {
    }
}
