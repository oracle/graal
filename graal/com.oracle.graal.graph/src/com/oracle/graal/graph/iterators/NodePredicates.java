/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph.iterators;

import com.oracle.graal.graph.*;

public abstract class NodePredicates {

    private static final TautologyPredicate TAUTOLOGY = new TautologyPredicate();
    private static final ContradictionPredicate CONTRADICTION = new ContradictionPredicate();
    private static final IsNullPredicate IS_NULL = new IsNullPredicate();
    private static final IsNotNullPredicate IS_NOT_NULL = new IsNotNullPredicate();

    public static NodePredicate alwaysTrue() {
        return TAUTOLOGY;
    }

    public static NodePredicate alwaysFalse() {
        return CONTRADICTION;
    }

    public static NodePredicate isNull() {
        return IS_NULL;
    }

    public static NodePredicate isNotNull() {
        return IS_NOT_NULL;
    }

    public static NodePredicate equals(Node n) {
        return new EqualsPredicate(n);
    }

    public static NodePredicate not(NodePredicate a) {
        return a.negate();
    }

    public static NegativeTypePredicate isNotA(Class<? extends Node> clazz) {
        return new NegativeTypePredicate(clazz);
    }

    public static PositiveTypePredicate isA(Class<? extends Node> clazz) {
        return new PositiveTypePredicate(clazz);
    }

    public static NodePredicate isAInterface(Class<?> iface) {
        assert iface.isInterface();
        return new PositiveTypePredicate(iface);
    }

    public static NodePredicate isNotAInterface(Class<?> iface) {
        assert iface.isInterface();
        return new NegativeTypePredicate(iface);
    }

    static final class TautologyPredicate implements NodePredicate {

        @Override
        public boolean apply(Node n) {
            return true;
        }

        public NodePredicate and(NodePredicate np) {
            return np;
        }

        public NodePredicate negate() {
            return CONTRADICTION;
        }

        public NodePredicate or(NodePredicate np) {
            return this;
        }
    }

    static final class ContradictionPredicate implements NodePredicate {

        @Override
        public boolean apply(Node n) {
            return false;
        }

        public NodePredicate and(NodePredicate np) {
            return this;
        }

        public NodePredicate negate() {
            return TAUTOLOGY;
        }

        public NodePredicate or(NodePredicate np) {
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

    static final class NotPredicate implements NodePredicate {

        private final NodePredicate a;

        NotPredicate(NodePredicate n) {
            this.a = n;
        }

        @Override
        public boolean apply(Node n) {
            return !a.apply(n);
        }

        public NodePredicate negate() {
            return a;
        }
    }

    static final class OrPredicate implements NodePredicate {

        private final NodePredicate a;
        private final NodePredicate b;

        OrPredicate(NodePredicate a, NodePredicate b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean apply(Node n) {
            return a.apply(n) || b.apply(n);
        }
    }

    static final class IsNullPredicate implements NodePredicate {

        @Override
        public boolean apply(Node n) {
            return n == null;
        }

        public NodePredicate negate() {
            return IS_NOT_NULL;
        }
    }

    static final class IsNotNullPredicate implements NodePredicate {

        @Override
        public boolean apply(Node n) {
            return n != null;
        }

        public NodePredicate negate() {
            return IS_NULL;
        }
    }

    static final class EqualsPredicate implements NodePredicate {

        private final Node u;

        EqualsPredicate(Node u) {
            this.u = u;
        }

        @Override
        public boolean apply(Node n) {
            return u == n;
        }

        public NodePredicate negate() {
            return new NotEqualsPredicate(u);
        }
    }

    static final class NotEqualsPredicate implements NodePredicate {

        private final Node u;

        NotEqualsPredicate(Node u) {
            this.u = u;
        }

        @Override
        public boolean apply(Node n) {
            return u != n;
        }

        public NodePredicate negate() {
            return new EqualsPredicate(u);
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

        public NodePredicate negate() {
            return new PositiveTypePredicate(this);
        }
    }
}
