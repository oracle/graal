/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;
import com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.ProxyInternalResource;

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

    @Registration(id = "instrumentresource1", name = "instrumentresource11", internalResources = {
                    InternalResourceRegistration1.Resource1.class,
                    InternalResourceRegistration1.Resource2.class
    })
    public static class InternalResourceRegistration1 extends ProxyInstrument {
        @InternalResource.Id("test-resource-1")
        public static class Resource1 extends ProxyInternalResource {
        }

        @InternalResource.Id("test-resource-2")
        public static class Resource2 extends ProxyInternalResource {
        }
    }

    @ExpectError("The class InstrumentRegistrationTest.InternalResourceRegistration2.Resource must be a static inner-class or a top-level class. " +
                    "To resolve this, make the Resource static or top-level class.")
    @Registration(id = "instrumentresource1", name = "instrumentresource1", internalResources = {InternalResourceRegistration2.Resource.class})
    public static class InternalResourceRegistration2 extends ProxyInstrument {
        @InternalResource.Id("test-resource")
        public abstract class Resource extends ProxyInternalResource {
        }
    }

    @ExpectError("The class InstrumentRegistrationTest.InternalResourceRegistration3.Resource must have a no argument public constructor. " +
                    "To resolve this, add public Resource() constructor.")
    @Registration(id = "instrumentresource3", name = "instrumentresource3", internalResources = {InternalResourceRegistration3.Resource.class})
    public static class InternalResourceRegistration3 extends ProxyInstrument {
        @InternalResource.Id("test-resource")
        public static class Resource extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource(String unused) {
            }

            @SuppressWarnings("unused")
            Resource(long unused) {
            }

            @SuppressWarnings("unused")
            private Resource() {
            }
        }
    }

    @ExpectError("The class InstrumentRegistrationTest.InternalResourceRegistration4.Resource must be public or package protected " +
                    "in the com.oracle.truffle.api.dsl.test.processor package. To resolve this, make the " +
                    "InstrumentRegistrationTest.InternalResourceRegistration4.Resource public or move it to the " +
                    "com.oracle.truffle.api.dsl.test.processor package.")
    @Registration(id = "instrumentresource4", name = "instrumentresource4", internalResources = {InternalResourceRegistration4.Resource.class})
    public static class InternalResourceRegistration4 extends ProxyInstrument {
        @InternalResource.Id("test-resource")
        private static class Resource extends ProxyInternalResource {
            @SuppressWarnings("unused")
            Resource() {
            }
        }
    }

    @Registration(id = "instrumentresource5", name = "instrumentresource5", internalResources = {InternalResourceRegistration5.Resource.class})
    public static class InternalResourceRegistration5 extends ProxyInstrument {
        @InternalResource.Id("test-resource")
        public static class Resource extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource(String unused) {
            }

            @SuppressWarnings("unused")
            Resource(long unused) {
            }

            Resource() {
            }
        }
    }

    @ExpectError("The class InstrumentRegistrationTest.InternalResourceRegistration6.Resource must be annotated by the @Id annotation. " +
                    "To resolve this, add '@Id(\"resource-id\")' annotation.")
    @Registration(id = "instrumentresource6", name = "instrumentresource6", internalResources = {InternalResourceRegistration6.Resource.class})
    public static class InternalResourceRegistration6 extends ProxyInstrument {

        public static class Resource extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource() {
            }
        }
    }

    @ExpectError("Internal resources must have unique ids within the component. " +
                    "But InstrumentRegistrationTest.InternalResourceRegistration7.Resource1 and InstrumentRegistrationTest.InternalResourceRegistration7.Resource2 use the same id duplicated-id. " +
                    "To resolve this, change the @Id value on InstrumentRegistrationTest.InternalResourceRegistration7.Resource1 or InstrumentRegistrationTest.InternalResourceRegistration7.Resource2.")
    @Registration(id = "instrumentresource7", name = "instrumentresource7", internalResources = {InternalResourceRegistration7.Resource1.class, InternalResourceRegistration7.Resource2.class})
    public static class InternalResourceRegistration7 extends ProxyInstrument {

        @InternalResource.Id("duplicated-id")
        public static class Resource1 extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource1() {
            }
        }

        @InternalResource.Id("duplicated-id")
        public static class Resource2 extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource2() {
            }
        }
    }

    @ExpectError("The '@Id.componentId' for an required internal resources must be unset or equal to '@Registration.id'. " +
                    "To resolve this, remove the '@Id.componentId = \"other-instrument\"'.")
    @Registration(id = "instrumentresource8", name = "instrumentresource8", internalResources = {InternalResourceRegistration8.Resource1.class})
    public static class InternalResourceRegistration8 extends ProxyInstrument {

        @InternalResource.Id(value = "resource-id", componentId = "other-instrument")
        public static class Resource1 extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource1() {
            }
        }
    }

    @ExpectError("Optional internal resources must not be registered using '@Registration' annotation. To resolve this, " +
                    "remove the 'InstrumentRegistrationTest.InternalResourceRegistration9.Resource1' from 'internalResources' the or " +
                    "make the 'InstrumentRegistrationTest.InternalResourceRegistration9.Resource1' non-optional by removing 'optional = true'.")
    @Registration(id = "instrumentresource9", name = "instrumentresource9", internalResources = {InternalResourceRegistration9.Resource1.class})
    public static class InternalResourceRegistration9 extends ProxyInstrument {

        @InternalResource.Id(value = "resource-id", componentId = "instrumentresource9", optional = true)
        public static class Resource1 extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource1() {
            }
        }
    }

    interface Service1 {
    }

    interface Service2 {
    }
}
