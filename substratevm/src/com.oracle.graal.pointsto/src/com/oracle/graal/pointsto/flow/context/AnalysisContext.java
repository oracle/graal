/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow.context;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AnalysisContext {

    private static final AtomicInteger nextId = new AtomicInteger();

    /**
     * Each context chain has an unique id, however these might not be consecutive due to how we
     * create unique context chains.
     */
    protected final int id;

    protected AnalysisContext() {
        this.id = nextId.incrementAndGet();
    }

    public int getId() {
        return id;
    }

    /** Must implement value equality for analysis context. */
    protected abstract boolean valueEquals(AnalysisContext obj);

    /** Must implement value hash code for analysis context. */
    protected abstract int valueHashCode();

    /**
     * Creates a wrapper for the context that performs value equality instead of identity equality.
     * The wrapper object is used as a key in the map that keeps track of the contexts to create
     * unique contexts.
     */
    public AnalysisContextKey asKey() {
        return new AnalysisContextKey();
    }

    public final class AnalysisContextKey {

        protected AnalysisContext context() {
            return AnalysisContext.this;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AnalysisContextKey) {
                AnalysisContextKey that = (AnalysisContextKey) obj;
                return this.context().valueEquals(that.context());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.context().valueHashCode();
        }
    }

    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public final int hashCode() {
        // the context id is unique
        return this.id;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(id);
        return result.toString();
    }
}
