/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.AbstractLibraryTest;

@SuppressWarnings("unused")
public class AcceptsTransitionTest extends AbstractLibraryTest {

    @GenerateLibrary
    abstract static class TransitionTestLibrary extends Library {

        public String transition(Object receiver, Strategy strategy) {
            return "transition";
        }

    }

    enum Strategy {
        STRATEGY1,
        STRATEGY2,
        STRATEGY3;
    }

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(value = TransitionTestLibrary.class, transitionLimit = "LIMIT")
    static class StrategyObject implements TruffleObject {
        static final int LIMIT = 1;
        protected Strategy strategy = Strategy.STRATEGY1;
        protected final Object mergeKey = "testMerged";

        StrategyObject(Strategy s) {
            this.strategy = s;
        }

        @ExportMessage(library = TransitionTestLibrary.class)
        boolean accepts(@Shared("strategy") @Cached("this.strategy") Strategy cached) {
            return this.strategy == cached;
        }

        @ExportMessage
        String transition(Strategy s,
                        @Shared("strategy") @Cached("this.strategy") Strategy cached,
                        @CachedLibrary("this") TransitionTestLibrary thisLibrary,
                        @CachedLibrary("cached.toString()") InteropLibrary lib) {
            assertSame(cached, this.strategy);
            try {
                // testing that we can use a library in the transition
                // and all nodes adopted properly
                assertEquals(cached.toString(), lib.asString(cached.toString()));
            } catch (UnsupportedMessageException e) {
                throw new AssertionError(e);
            }
            Strategy old = this.strategy;
            this.strategy = s;
            return "transition_" + old + "_" + s + "_" + (thisLibrary.isAdoptable() ? "cached" : "uncached");
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean hasMembers(@Cached("this.strategy") Strategy cached) {
            return true;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        final Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        @ExportMessage
        final boolean isMemberInvocable(String member) {
            return "transition".equals(member);
        }

        @TruffleBoundary
        @ExportMessage
        final Object invokeMember(String member, Object[] arguments,
                        @CachedLibrary("this") TransitionTestLibrary lib,
                        @CachedLibrary(limit = "3") InteropLibrary strings) throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
            if (!isMemberInvocable(member)) {
                throw UnknownIdentifierException.create(member);
            } else if (arguments.length != 1) {
                throw ArityException.create(1, arguments.length);
            } else if (!strings.isString(arguments[0])) {
                throw UnsupportedTypeException.create(arguments);
            }
            return lib.transition(this, Strategy.valueOf(strings.asString(arguments[0])));
        }
    }

    @Test
    public void testTransitionsUncached() {
        StrategyObject o = new StrategyObject(Strategy.STRATEGY1);
        TransitionTestLibrary lib = getUncached(TransitionTestLibrary.class, o);
        assertTrue(lib.accepts(o));
        assertEquals("transition_STRATEGY1_STRATEGY2_uncached", lib.transition(o, Strategy.STRATEGY2));
        assertTrue(lib.accepts(o));
        assertEquals("transition_STRATEGY2_STRATEGY3_uncached", lib.transition(o, Strategy.STRATEGY3));
        assertTrue(lib.accepts(o));
        assertEquals("transition_STRATEGY3_STRATEGY1_uncached", lib.transition(o, Strategy.STRATEGY1));
        assertTrue(lib.accepts(o));
    }

    @Test
    public void testTransitionsCached() {
        StrategyObject o = new StrategyObject(Strategy.STRATEGY1);
        TransitionTestLibrary lib = createCached(TransitionTestLibrary.class, o);
        assertTrue(lib.accepts(o));
        assertEquals("transition_STRATEGY1_STRATEGY2_cached", lib.transition(o, Strategy.STRATEGY2));
        assertFalse(lib.accepts(o));
        assertEquals("transition_STRATEGY2_STRATEGY3_cached", lib.transition(o, Strategy.STRATEGY3));
        assertFalse(lib.accepts(o));
        assertEquals("transition_STRATEGY3_STRATEGY1_uncached", lib.transition(o, Strategy.STRATEGY1));
        assertTrue(lib.accepts(o));
    }

    @Test
    public void testTransitionsCachedMerged() throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        StrategyObject o = new StrategyObject(Strategy.STRATEGY1);
        InteropLibrary lib = createCached(InteropLibrary.class, o);
        assertTrue(lib.accepts(o));
        assertEquals("transition_STRATEGY1_STRATEGY2_cached", lib.invokeMember(o, "transition", "STRATEGY2"));
        assertFalse(lib.accepts(o));
        assertEquals("transition_STRATEGY2_STRATEGY3_cached", lib.invokeMember(o, "transition", "STRATEGY3"));
        assertFalse(lib.accepts(o));
        assertEquals("transition_STRATEGY3_STRATEGY1_uncached", lib.invokeMember(o, "transition", "STRATEGY1"));
        assertTrue(lib.accepts(o));
    }

}
