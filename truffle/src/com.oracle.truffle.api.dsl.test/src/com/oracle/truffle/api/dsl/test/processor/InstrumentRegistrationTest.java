/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.processor;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.GenerateLegacyRegistration;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.Assert;
import org.junit.Test;

public class InstrumentRegistrationTest {

    @ExpectError("Registered instrument class must be public")
    @Registration(id = "NonPublicInstrument")
    static final class NonPublicInstrument extends ProxyInstrument {
    }

    @ExpectError("Registered instrument class must subclass TruffleInstrument")
    @Registration(id = "WrongSuperClassInstrument")
    public static final class WrongSuperClassInstrument {
    }

    @Registration(id = "MyInstrumentGood", name = "MyInstrumentGood")
    public static final class MyInstrumentGood extends ProxyInstrument {
    }

    @Registration(id = "MyInstrumentGoodWithServices", name = "MyInstrumentGoodWithServices", services = Service1.class)
    public static final class MyInstrumentGoodWithServices extends ProxyInstrument {

    }

    @GenerateLegacyRegistration
    @Registration(id = "legacyregistration", name = "LegacyRegistration", version = "1.0.0", services = {Service1.class, Service2.class})
    public static final class LegacyRegistration extends ProxyInstrument {

    }

    interface Service1 {
    }

    interface Service2 {
    }

    @Test
    public void testLoadLegacyRegistrations() {
        try (Engine eng = Engine.create()) {
            Instrument instrument = eng.getInstruments().get("legacyregistration");
            Assert.assertNotNull(instrument);
            Assert.assertEquals("LegacyRegistration", instrument.getName());
            Assert.assertEquals("1.0.0", instrument.getVersion());
        }
    }
}
