/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.Node;

/**
 * Holds an {@link Assumption}, and knows how to recreate it with the same properties on
 * invalidation. Used as an Assumption factory that safely invalidates the previous Assumption and
 * creates a new Assumption on invalidation.
 * <p>
 * Note that you should be careful that repeated invalidations do not cause a deoptimization loop in
 * that same way that you would with any other assumption.
 * <p>
 * The Assumption instance should be obtained before doing the operation depending on it. In other
 * words:
 * <ol>
 * <li>Obtain the current Assumption with {@link CyclicAssumption#getAssumption()}</li>
 * <li>Perform the operation/lookup which depends on the assumption</li>
 * <li>Cache the result and the Assumption</li>
 * </ol>
 * On the fast-path, first check if the assumption is valid and in that case return the cached
 * result. When invalidating, first write the new value, then invalidate the CyclicAssumption.
 *
 * {@codesnippet cyclicassumption}
 *
 * @since 0.8 or earlier
 */
public class CyclicAssumption {

    private final String name;
    private volatile Assumption assumption;

    private static final AtomicReferenceFieldUpdater<CyclicAssumption, Assumption> ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(CyclicAssumption.class, Assumption.class, "assumption");

    /** @since 0.8 or earlier */
    public CyclicAssumption(String name) {
        this.name = name;
        this.assumption = Truffle.getRuntime().createAssumption(name);
    }

    /** @since 0.8 or earlier */
    @TruffleBoundary
    public void invalidate() {
        invalidate("");
    }

    /** @since 0.33 */
    @TruffleBoundary
    public void invalidate(String message) {
        Assumption newAssumption = Truffle.getRuntime().createAssumption(name);
        Assumption oldAssumption = ASSUMPTION_UPDATER.getAndSet(this, newAssumption);
        oldAssumption.invalidate(message);
    }

    /** @since 0.8 or earlier */
    public Assumption getAssumption() {
        CompilerAsserts.neverPartOfCompilation("Cache the Assumption and do not call getAssumption() on the fast path");
        return assumption;
    }

}

class CyclicAssumptionSnippets {
    // BEGIN: cyclicassumption
    class MyContext {
        CyclicAssumption symbolsRedefined = new CyclicAssumption("symbols");
        Map<String, Object> symbols = new ConcurrentHashMap<>();

        public void redefineSymbol(String symbol, Object value) {
            symbols.put(symbol, value);
            symbolsRedefined.invalidate();
        }
    }

    class SymbolLookupNode extends Node {
        final MyContext context;
        final String symbol;

        @CompilationFinal volatile LookupResult cachedLookup;

        SymbolLookupNode(MyContext context, String symbol) {
            this.context = context;
            this.symbol = symbol;
        }

        public Object execute() {
            LookupResult lookup = cachedLookup;
            if (lookup == null || !lookup.assumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedLookup = doLookup(symbol);
            }
            return cachedLookup.value;
        }

        private LookupResult doLookup(String name) {
            // First get the Assumption
            CyclicAssumption symbolsRedefined = context.symbolsRedefined;
            Assumption assumption = symbolsRedefined.getAssumption();
            // Then lookup the value
            Object value = context.symbols.get(name);
            return new LookupResult(assumption, value);
        }
    }

    class LookupResult {
        final Assumption assumption;
        final Object value;

        LookupResult(Assumption assumption, Object value) {
            this.assumption = assumption;
            this.value = value;
        }
    }
    // END: cyclicassumption
}
