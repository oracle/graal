/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;

public class InstrumentRegistrationTest {

    @ExpectError("Registered instrument class must be at least package protected.")
    @Registration(id = "NonPublicInstrument")
    private static final class PrivateInstrument extends ProxyInstrument {
    }

    @ExpectError("Registered instrument class must subclass TruffleInstrument.")
    @Registration(id = "WrongSuperClassInstrument")
    public static final class WrongSuperClassInstrument {
    }

    @Registration(id = "MyInstrumentGood", name = "MyInstrumentGood")
    public static final class MyInstrumentGood extends ProxyInstrument {
    }

    @Registration(id = "MyInstrumentGoodWithServices", name = "MyInstrumentGoodWithServices", services = Service1.class)
    public static final class MyInstrumentGoodWithServices extends ProxyInstrument {

    }

    @ExpectError("Registered defaultExportProviders must be subclass of com.oracle.truffle.api.library.DefaultExportProvider. " +
                    "To resolve this, implement DefaultExportProvider.")
    @Registration(id = "tooldefaultexportprovider1", name = "tooldefaultexportprovider1", defaultExportProviders = DefaultExportProviderRegistration1.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration1 extends ProxyInstrument {
        public static class DefaultExportProviderImpl {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.InstrumentRegistrationTest.DefaultExportProviderRegistration2.DefaultExportProviderImpl " +
                    "must be a static inner-class or a top-level class. To resolve this, make the DefaultExportProviderImpl static or top-level class.")
    @Registration(id = "tooldefaultexportprovider2", name = "tooldefaultexportprovider2", defaultExportProviders = DefaultExportProviderRegistration2.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration2 extends ProxyInstrument {
        abstract class DefaultExportProviderImpl extends LanguageRegistrationTest.ProxyDefaultExportProvider {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.InstrumentRegistrationTest.DefaultExportProviderRegistration3.DefaultExportProviderImpl " +
                    "must have a no argument public constructor. To resolve this, add public DefaultExportProviderImpl() constructor.")
    @Registration(id = "tooldefaultexportprovider3", name = "tooldefaultexportprovider3", defaultExportProviders = DefaultExportProviderRegistration3.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration3 extends ProxyInstrument {
        abstract static class DefaultExportProviderImpl extends LanguageRegistrationTest.ProxyDefaultExportProvider {

            @SuppressWarnings("unused")
            DefaultExportProviderImpl(String unused) {
            }

            @SuppressWarnings("unused")
            DefaultExportProviderImpl(long unused) {
            }

            @SuppressWarnings("unused")
            private DefaultExportProviderImpl() {
            }
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.InstrumentRegistrationTest.DefaultExportProviderRegistration4.DefaultExportProviderImpl " +
                    "must be a public class or package protected class in com.oracle.truffle.api.dsl.test.processor package. " +
                    "To resolve this, make the DefaultExportProviderImpl public or move it to com.oracle.truffle.api.dsl.test.processor.")
    @Registration(id = "tooldefaultexportprovider4", name = "tooldefaultexportprovider4", defaultExportProviders = DefaultExportProviderRegistration4.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration4 extends ProxyInstrument {
        private static class DefaultExportProviderImpl extends LanguageRegistrationTest.ProxyDefaultExportProvider {
            @SuppressWarnings("unused")
            DefaultExportProviderImpl() {
            }
        }
    }

    @Registration(id = "tooldefaultexportprovider5", name = "tooldefaultexportprovider5", defaultExportProviders = DefaultExportProviderRegistration5.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration5 extends ProxyInstrument {
        static class DefaultExportProviderImpl extends LanguageRegistrationTest.ProxyDefaultExportProvider {

            @SuppressWarnings("unused")
            DefaultExportProviderImpl(String unused) {
            }

            @SuppressWarnings("unused")
            DefaultExportProviderImpl(long unused) {
            }

            DefaultExportProviderImpl() {
            }
        }
    }

    @Registration(id = "tooldefaultexportprovider6", name = "tooldefaultexportprovider6", defaultExportProviders = {
                    DefaultExportProviderRegistration6.DefaultExportProviderImpl1.class,
                    DefaultExportProviderRegistration6.DefaultExportProviderImpl2.class
    })
    public static class DefaultExportProviderRegistration6 extends ProxyInstrument {
        static class DefaultExportProviderImpl1 extends LanguageRegistrationTest.ProxyDefaultExportProvider {
        }

        static class DefaultExportProviderImpl2 extends LanguageRegistrationTest.ProxyDefaultExportProvider {
        }
    }

    @ExpectError("Registered eagerExportProviders must be subclass of com.oracle.truffle.api.library.EagerExportProvider. " +
                    "To resolve this, implement EagerExportProvider.")
    @Registration(id = "tooleagerexportprovider1", name = "tooleagerexportprovider1", eagerExportProviders = EagerExportProviderRegistration1.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration1 extends ProxyInstrument {
        public static class EagerExportProviderImpl {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.InstrumentRegistrationTest.EagerExportProviderRegistration2.EagerExportProviderImpl" +
                    " must be a static inner-class or a top-level class. To resolve this, make the EagerExportProviderImpl static or top-level class.")
    @Registration(id = "tooleagerexportprovider2", name = "tooleagerexportprovider2", eagerExportProviders = EagerExportProviderRegistration2.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration2 extends ProxyInstrument {
        abstract class EagerExportProviderImpl extends LanguageRegistrationTest.ProxyEagerExportProvider {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.InstrumentRegistrationTest.EagerExportProviderRegistration3.EagerExportProviderImpl " +
                    "must have a no argument public constructor. To resolve this, add public EagerExportProviderImpl() constructor.")
    @Registration(id = "tooleagerexportprovider3", name = "tooleagerexportprovider3", eagerExportProviders = EagerExportProviderRegistration3.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration3 extends ProxyInstrument {
        abstract static class EagerExportProviderImpl extends LanguageRegistrationTest.ProxyEagerExportProvider {

            @SuppressWarnings("unused")
            EagerExportProviderImpl(String unused) {
            }

            @SuppressWarnings("unused")
            EagerExportProviderImpl(long unused) {
            }

            @SuppressWarnings("unused")
            private EagerExportProviderImpl() {
            }
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.InstrumentRegistrationTest.EagerExportProviderRegistration4.EagerExportProviderImpl " +
                    "must be a public class or package protected class in com.oracle.truffle.api.dsl.test.processor package. " +
                    "To resolve this, make the EagerExportProviderImpl public or move it to com.oracle.truffle.api.dsl.test.processor.")
    @Registration(id = "tooleagerexportprovider4", name = "tooleagerexportprovider4", eagerExportProviders = EagerExportProviderRegistration4.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration4 extends ProxyInstrument {
        private static class EagerExportProviderImpl extends LanguageRegistrationTest.ProxyEagerExportProvider {

            @SuppressWarnings("unused")
            EagerExportProviderImpl() {
            }
        }
    }

    @Registration(id = "tooleagerexportprovider5", name = "tooleagerexportprovider5", eagerExportProviders = EagerExportProviderRegistration5.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration5 extends ProxyInstrument {
        static class EagerExportProviderImpl extends LanguageRegistrationTest.ProxyEagerExportProvider {

            @SuppressWarnings("unused")
            EagerExportProviderImpl(String unused) {
            }

            @SuppressWarnings("unused")
            EagerExportProviderImpl(long unused) {
            }

            EagerExportProviderImpl() {
            }
        }
    }

    @Registration(id = "tooleagerexportprovider6", name = "tooleagerexportprovider6", eagerExportProviders = {
                    EagerExportProviderRegistration6.EagerExportProviderImpl1.class,
                    EagerExportProviderRegistration6.EagerExportProviderImpl2.class
    })
    public static class EagerExportProviderRegistration6 extends ProxyInstrument {
        static class EagerExportProviderImpl1 extends LanguageRegistrationTest.ProxyEagerExportProvider {
        }

        static class EagerExportProviderImpl2 extends LanguageRegistrationTest.ProxyEagerExportProvider {
        }
    }

    interface Service1 {
    }

    interface Service2 {
    }
}
