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

import org.graalvm.polybench.ast.Expr.Reference.Ident;
import org.graalvm.polybench.ast.Stat.Block;

import java.util.ArrayList;

public interface Decl extends Tree {
    record Compound(String name, Ident[] baseClasses, Decl[] members) implements Decl {
        public static class Builder extends Tree.Builder {
            private final String name;
            private final Ident[] baseClasses;
            private final ArrayList<Decl> members;

            public Builder(Tree.Builder parent, String name, Ident[] baseClasses) {
                super(parent);
                this.name = name;
                this.baseClasses = baseClasses;
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
            public Compound build() {
                return new Compound(name, baseClasses, members.toArray(new Decl[members.size()]));
            }
        }
    }

    record Subroutine(String name, Variable[] params, Stat body) implements Decl {
        public static class Builder extends Tree.Builder {
            final String name;
            final Variable[] params;
            final ArrayList<Stat> stats;

            public Builder(Tree.Builder parent, String name, Variable[] params) {
                super(parent);
                this.name = name;
                this.params = params;
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
            public Subroutine build() {
                return new Subroutine(name, params, buildBody());
            }

            Block buildBody() {
                return new Block(stats.toArray(new Stat[stats.size()]));
            }
        }

        public record Constructor(Variable[] params, Stat body) implements Decl {
            public static class Builder extends Tree.Builder {
                final Variable[] params;
                final ArrayList<Stat> stats;

                public Builder(Tree.Builder parent, Variable[] params) {
                    super(parent);
                    this.params = params;
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
                public Constructor build() {
                    return new Constructor(params, buildBody());
                }

                Block buildBody() {
                    return new Block(stats.toArray(new Stat[stats.size()]));
                }
            }
        }
    }

    record Main(Decl.Subroutine subroutine) implements Decl {
    }

    record Variable(String name, Expr initialValue) implements Decl, Stat {
        public static Variable[] list(String... names) {
            return java.util.Arrays.stream(names).map(name -> new Variable(name, null)).toArray(Variable[]::new);
        }

        public static Variable of(String name) {
            return new Variable(name, null);
        }

        public static Variable of(String name, Expr initialValue) {
            return new Variable(name, initialValue);
        }
    }

    /**
     * Code written in the target language.
     *
     * Only used for embedding the benchmark source code and providing helper functions on which the
     * PolyBench harness relies on.
     */
    record Raw(byte[] code) implements Decl, Stat {
    }
}
