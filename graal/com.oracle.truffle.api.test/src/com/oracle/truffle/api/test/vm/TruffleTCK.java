/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import com.oracle.truffle.api.vm.TruffleVM;
import java.util.Random;
import org.junit.Test;

/**
 * A collection of tests that can certify language implementaiton to be complient with most recent
 * requirements of the Truffle infrastructure and tooling. Subclass, implement abstract methods and
 * include in your test suite.
 */
public class TruffleTCK { // abstract
    private TruffleVM tckVM;

    public TruffleTCK() { // protected
    }

    /**
     * This methods is called before first test is executed. It's purpose is to set a TruffleVM with
     * your language up, so it is ready for testing.
     * {@link TruffleVM#eval(java.lang.String, java.lang.String) Execute} any scripts you need, and
     * prepare global symbols with proper names. The symbols will then be looked up by the
     * infastructure (using the names provided by you from methods like {@link #plusInt()}) and used
     * for internal testing.
     *
     * @return initialized Truffle virtual machine
     * @throws java.lang.Exception thrown when the VM preparation fails
     */
    protected TruffleVM prepareVM() throws Exception { // abstract
        return null;
    }

    /**
     * Name of function which will return value 42 as a number. The return value of the method
     * should be instance of {@link Number} and its {@link Number#intValue()} should return
     * <code>42</code>.
     *
     * @return name of globally exported symbol
     */
    protected String fourtyTwo() { // abstract
        return null;
    }

    /**
     * Name of function to add two integer values together. The symbol will be invoked with two
     * parameters of type {@link Integer} and expects result of type {@link Number} which's
     * {@link Number#intValue()} is equivalent of <code>param1 + param2</code>.
     *
     * @return name of globally exported symbol
     */
    protected String plusInt() {  // abstract
        return null;
    }

    private TruffleVM vm() throws Exception {
        if (tckVM == null) {
            tckVM = prepareVM();
        }
        return tckVM;
    }

    //
    // The tests
    //

    @Test
    public void testFortyTwo() throws Exception {
        if (getClass() == TruffleTCK.class) {
            return;
        }
        TruffleVM.Symbol fourtyTwo = findGlobalSymbol(fourtyTwo());

        Object res = fourtyTwo.invoke(null);

        assert res instanceof Number : "should yield a number, but was: " + res;

        Number n = (Number) res;

        assert 42 == n.intValue() : "The value is 42 =  " + n.intValue();
    }

    @Test
    public void testPlusWithInts() throws Exception {
        if (getClass() == TruffleTCK.class) {
            return;
        }
        Random r = new Random();
        int a = r.nextInt(100);
        int b = r.nextInt(100);

        TruffleVM.Symbol plus = findGlobalSymbol(plusInt());

        Object res = plus.invoke(null, a, b);

        assert res instanceof Number : "+ on two ints should yield a number, but was: " + res;

        Number n = (Number) res;

        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    private TruffleVM.Symbol findGlobalSymbol(String name) throws Exception {
        TruffleVM.Symbol s = vm().findGlobalSymbol(name);
        assert s != null : "Symbol " + name + " is not found!";
        return s;
    }
}
