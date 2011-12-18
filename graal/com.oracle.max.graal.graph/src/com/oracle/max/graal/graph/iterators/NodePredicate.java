/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.graph.iterators;

import com.oracle.max.graal.graph.*;

public abstract class NodePredicate {
    public static final TautologyPredicate TAUTOLOGY = new TautologyPredicate();

    public static final IsNullPredicate IS_NULL = new IsNullPredicate();

    public static final class TautologyPredicate extends NodePredicate {
        @Override
        public boolean apply(Node n) {
            return true;
        }
    }

    public static final class AndPredicate extends NodePredicate {
        private final NodePredicate a;
        private final NodePredicate b;
        private AndPredicate(NodePredicate np, NodePredicate thiz) {
            this.a = np;
            this.b = thiz;
        }
        @Override
        public boolean apply(Node n) {
            return b.apply(n) && a.apply(n);
        }
    }

    public static final class OrPredicate extends NodePredicate {
        private final NodePredicate a;
        private final NodePredicate b;
        private OrPredicate(NodePredicate np, NodePredicate thiz) {
            this.a = np;
            this.b = thiz;
        }
        @Override
        public boolean apply(Node n) {
            return b.apply(n) || a.apply(n);
        }
    }

    public static final class IsNullPredicate extends NodePredicate {
        @Override
        public boolean apply(Node n) {
            return n == null;
        }
    }

    public static final class EqualsPredicate<T extends Node> extends NodePredicate {
        private final T u;
        public EqualsPredicate(T u) {
            this.u = u;
        }
        @Override
        public boolean apply(Node n) {
            return u == n;
        }
    }

    public abstract boolean apply(Node n);

    public NodePredicate and(final NodePredicate np) {
        if (this instanceof TautologyPredicate) {
            return np;
        }
        return new AndPredicate(this, np);
    }

    public NodePredicate or(final NodePredicate np) {
        if (this instanceof TautologyPredicate) {
            return this;
        }
        return new OrPredicate(this, np);
    }

    public static <T extends Node> EqualsPredicate<T> equals(T u) {
        return new EqualsPredicate<>(u);
    }
}
