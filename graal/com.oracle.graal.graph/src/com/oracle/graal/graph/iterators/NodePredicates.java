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
    private static final FalsePredicate FALSE = new FalsePredicate();
    private static final IsNullPredicate IS_NULL = new IsNullPredicate();
    private static final IsNotNullPredicate IS_NOT_NULL = new IsNotNullPredicate();

    public static NodePredicate alwaysTrue() {
        return TAUTOLOGY;
    }

    public static NodePredicate alwaysFalse() {
        return FALSE;
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
        if (a == TAUTOLOGY) {
            return FALSE;
        }
        if (a == FALSE) {
            return TAUTOLOGY;
        }
        if (a == IS_NULL) {
            return IS_NOT_NULL;
        }
        if (a == IS_NOT_NULL) {
            return IS_NULL;
        }
        if (a instanceof NotPredicate) {
            return ((NotPredicate) a).a;
        }
        if (a instanceof PositiveTypePredicate) {
            return new NegativeTypePredicate((PositiveTypePredicate) a);
        }
        if (a instanceof NegativeTypePredicate) {
            return new PositiveTypePredicate((NegativeTypePredicate) a);
        }
        if (a instanceof EqualsPredicate) {
            return new NotEqualsPredicate(((EqualsPredicate) a).u);
        }
        if (a instanceof NotEqualsPredicate) {
            return new EqualsPredicate(((NotEqualsPredicate) a).u);
        }
        return new NotPredicate(a);
    }

    public static NodePredicate and(NodePredicate a, NodePredicate b) {
        if (a == TAUTOLOGY) {
            return b;
        }
        if (b == TAUTOLOGY) {
            return a;
        }
        if (a == FALSE || b == FALSE) {
            return FALSE;
        }
        return new AndPredicate(a, b);
    }

    public static NodePredicate or(NodePredicate a, NodePredicate b) {
        if (a == FALSE) {
            return b;
        }
        if (b == FALSE) {
            return a;
        }
        if (a == TAUTOLOGY || b == TAUTOLOGY) {
            return TAUTOLOGY;
        }
        return new OrPredicate(a, b);
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

    private static final class TautologyPredicate extends NodePredicate {

        @Override
        public boolean apply(Node n) {
            return true;
        }
    }

    private static final class FalsePredicate extends NodePredicate {

        @Override
        public boolean apply(Node n) {
            return false;
        }
    }

    private static final class AndPredicate extends NodePredicate {

        private final NodePredicate a;
        private final NodePredicate b;

        private AndPredicate(NodePredicate a, NodePredicate b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean apply(Node n) {
            return a.apply(n) && b.apply(n);
        }
    }

    private static final class NotPredicate extends NodePredicate {

        private final NodePredicate a;

        private NotPredicate(NodePredicate n) {
            this.a = n;
        }

        @Override
        public boolean apply(Node n) {
            return !a.apply(n);
        }
    }

    private static final class OrPredicate extends NodePredicate {

        private final NodePredicate a;
        private final NodePredicate b;

        private OrPredicate(NodePredicate a, NodePredicate b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean apply(Node n) {
            return a.apply(n) || b.apply(n);
        }
    }

    private static final class IsNullPredicate extends NodePredicate {

        @Override
        public boolean apply(Node n) {
            return n == null;
        }
    }

    private static final class IsNotNullPredicate extends NodePredicate {

        @Override
        public boolean apply(Node n) {
            return n != null;
        }
    }

    private static final class EqualsPredicate extends NodePredicate {

        private final Node u;

        public EqualsPredicate(Node u) {
            this.u = u;
        }

        @Override
        public boolean apply(Node n) {
            return u == n;
        }
    }

    private static final class NotEqualsPredicate extends NodePredicate {

        private final Node u;

        public NotEqualsPredicate(Node u) {
            this.u = u;
        }

        @Override
        public boolean apply(Node n) {
            return u != n;
        }
    }

    public static final class PositiveTypePredicate extends NodePredicate {

        private final Class<?> type;
        private PositiveTypePredicate or;

        public PositiveTypePredicate(Class<?> type) {
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
    }

    public static final class NegativeTypePredicate extends NodePredicate {

        private final Class<?> type;
        private NegativeTypePredicate nor;

        public NegativeTypePredicate(Class<?> type) {
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
    }
}
