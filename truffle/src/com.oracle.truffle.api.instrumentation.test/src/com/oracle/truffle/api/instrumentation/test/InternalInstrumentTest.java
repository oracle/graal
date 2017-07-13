/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
