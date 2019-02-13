/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
