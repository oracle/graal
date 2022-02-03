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
package com.oracle.truffle.api.test.utilities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.utilities.UnionAssumption;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class UnionAssumptionTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testIsValid() {
        final Assumption first = Truffle.getRuntime().createAssumption("first");
        final Assumption second = Truffle.getRuntime().createAssumption("second");
        final UnionAssumption union = new UnionAssumption(first, second);
        assertTrue(union.isValid());
    }

    @Test
    public void testCheck() throws InvalidAssumptionException {
        final Assumption first = Truffle.getRuntime().createAssumption("first");
        final Assumption second = Truffle.getRuntime().createAssumption("second");
        final UnionAssumption union = new UnionAssumption(first, second);
        union.check();
    }

    @Test
    public void testFirstInvalidateIsValid() {
        final Assumption first = Truffle.getRuntime().createAssumption("first");
        final Assumption second = Truffle.getRuntime().createAssumption("second");
        final UnionAssumption union = new UnionAssumption(first, second);

        first.invalidate();

        assertFalse(union.isValid());
    }

    @Test(expected = InvalidAssumptionException.class)
    public void testFirstInvalidateCheck() throws InvalidAssumptionException {
        final Assumption first = Truffle.getRuntime().createAssumption("first");
        final Assumption second = Truffle.getRuntime().createAssumption("second");
        final UnionAssumption union = new UnionAssumption(first, second);

        first.invalidate();

        union.check();
    }

    @Test
    public void testSecondInvalidateIsValid() {
        final Assumption first = Truffle.getRuntime().createAssumption("first");
        final Assumption second = Truffle.getRuntime().createAssumption("second");
        final UnionAssumption union = new UnionAssumption(first, second);

        second.invalidate();

        assertFalse(union.isValid());
    }

    @Test(expected = InvalidAssumptionException.class)
    public void testSecondInvalidateCheck() throws InvalidAssumptionException {
        final Assumption first = Truffle.getRuntime().createAssumption("first");
        final Assumption second = Truffle.getRuntime().createAssumption("second");
        final UnionAssumption union = new UnionAssumption(first, second);

        second.invalidate();

        union.check();
    }

    @Test
    public void testBothInvalidateIsValid() {
        final Assumption first = Truffle.getRuntime().createAssumption("first");
        final Assumption second = Truffle.getRuntime().createAssumption("second");
        final UnionAssumption union = new UnionAssumption(first, second);

        first.invalidate();
        second.invalidate();

        assertFalse(union.isValid());
    }

    @Test(expected = InvalidAssumptionException.class)
    public void testBothInvalidateCheck() throws InvalidAssumptionException {
        final Assumption first = Truffle.getRuntime().createAssumption("first");
        final Assumption second = Truffle.getRuntime().createAssumption("second");
        final UnionAssumption union = new UnionAssumption(first, second);

        first.invalidate();
        second.invalidate();

        union.check();
    }

}
