/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import org.graalvm.polyglot.Engine;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

public class InternalInstrumentTest {

    private static final String INTERNAL_ID = "internalInstrumentId";
    private static final String EXTERNAL_ID = "externalInstrumentId";

    @Test
    public void testInternalInstrument() {
        Engine engine = Engine.create();

        Assert.assertFalse(engine.getInstruments().containsKey(INTERNAL_ID));
        Assert.assertTrue(engine.getInstruments().containsKey(EXTERNAL_ID));

        Env env = engine.getInstruments().get(EXTERNAL_ID).lookup(Lookup.class).getEnv();

        Assert.assertTrue(env.getInstruments().containsKey(INTERNAL_ID));
        Assert.assertTrue(env.getInstruments().containsKey(EXTERNAL_ID));
    }

    @Registration(name = INTERNAL_ID, id = INTERNAL_ID, internal = true)
    public static class InternalInstrument extends TruffleInstrument {
        @Override
        protected void onCreate(Env env) {
        }
    }

    @Registration(name = EXTERNAL_ID, id = EXTERNAL_ID, internal = false, services = Lookup.class)
    public static class ExternalInstrument extends TruffleInstrument implements Lookup {

        private Env env;

        @Override
        protected void onCreate(@SuppressWarnings("hiding") Env env) {
            this.env = env;
            env.registerService(this);
        }

        public Env getEnv() {
            return env;
        }
    }

    interface Lookup {

        Env getEnv();

    }
}
