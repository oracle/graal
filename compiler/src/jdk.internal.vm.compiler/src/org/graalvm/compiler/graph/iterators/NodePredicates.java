/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph.iterators;

import org.graalvm.compiler.graph.Node;

public abstract class NodePredicates {

    private static final TautologyPredicate TAUTOLOGY = new TautologyPredicate();

    public static NodePredicate alwaysTrue() {
        return TAUTOLOGY;
    }

    public static NegativeTypePredicate isNotA(Class<? extends Node> clazz) {
        return new NegativeTypePredicate(clazz);
    }

    public static PositiveTypePredicate isA(Class<? extends Node> clazz) {
        return new PositiveTypePredicate(clazz);
    }

    static final class TautologyPredicate implements NodePredicate {

        @Override
        public boolean apply(Node n) {
            return true;
        }

        @Override
        public NodePredicate and(NodePredicate np) {
            return np;
        }
    }

    static final class AndPredicate implements NodePredicate {

        private final NodePredicate a;
        private final NodePredicate b;

        AndPredicate(NodePredicate a, NodePredicate b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean apply(Node n) {
            return a.apply(n) && b.apply(n);
        }
    }

    public static final class PositiveTypePredicate implements NodePredicate {

        private final Class<?> type;
        private PositiveTypePredicate or;

        PositiveTypePredicate(Class<?> type) {
            this.type = type;
        }

        public PositiveTypePredicate(NegativeTypePredicate a) {
            type = a.type;
            if (a.nor != null) {
                or = new PositiveTypePredicate(a.nor);
            }
        }

        @Override
        public boolean apply(Node n) {
            return type.isInstance(n) || (or != null && or.apply(n));
        }

        public PositiveTypePredicate or(Class<? extends Node> clazz) {
            if (or == null) {
                or = new PositiveTypePredicate(clazz);
            } else {
                or.or(clazz);
            }
            return this;
        }

        @Override
        public NodePredicate negate() {
            return new NegativeTypePredicate(this);
        }
    }

    public static final class NegativeTypePredicate implements NodePredicate {

        private final Class<?> type;
        private NegativeTypePredicate nor;

        NegativeTypePredicate(Class<?> type) {
            this.type = type;
        }

        public NegativeTypePredicate(PositiveTypePredicate a) {
            type = a.type;
            if (a.or != null) {
                nor = new NegativeTypePredicate(a.or);
            }
        }

        @Override
        public boolean apply(Node n) {
            return !type.isInstance(n) && (nor == null || nor.apply(n));
        }

        public NegativeTypePredicate nor(Class<? extends Node> clazz) {
            if (nor == null) {
                nor = new NegativeTypePredicate(clazz);
            } else {
                nor.nor(clazz);
            }
            return this;
        }

        @Override
        public NodePredicate negate() {
            return new PositiveTypePredicate(this);
        }
    }
}
