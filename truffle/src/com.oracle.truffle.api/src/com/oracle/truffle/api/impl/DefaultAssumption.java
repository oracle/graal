/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

/**
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link TruffleRuntime#createAssumption()} to create an {@link Assumption}.
 */
final class DefaultAssumption extends AbstractAssumption {

    DefaultAssumption(String name) {
        super(name);
    }

    private DefaultAssumption(Object name) {
        super(name);
    }

    @Override
    public void check() throws InvalidAssumptionException {
        if (!isValid) {
            throw new InvalidAssumptionException();
        }
    }

    @Override
    public void invalidate() {
        invalidate("");
    }

    @Override
    public void invalidate(String message) {
        if (name != Lazy.ALWAYS_VALID_NAME) {
            isValid = false;
        } else {
            throw new UnsupportedOperationException("Cannot invalidate this assumption - it is always valid");
        }
    }

    @Override
    public boolean isValid() {
        return isValid;
    }

    static DefaultAssumption createAlwaysValid() {
        return new DefaultAssumption(Lazy.ALWAYS_VALID_NAME);
    }

    /*
     * We use a lazy class as this is already needed when the assumption is initialized.
     */
    static class Lazy {
        /*
         * We use an Object instead of a String here to avoid accidently handing out the always
         * valid string object in getName().
         */
        static final Object ALWAYS_VALID_NAME = new Object() {
            @Override
            public String toString() {
                return "<always valid>";
            }
        };
    }
}
