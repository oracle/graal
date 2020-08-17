/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.test.otherPackage.ErrorOtherPackageBaseObject1;
import com.oracle.truffle.api.library.test.otherPackage.ErrorOtherPackageBaseObject2;
import com.oracle.truffle.api.library.test.otherPackage.ErrorOtherPackageBaseObject3;
import com.oracle.truffle.api.library.test.otherPackage.OtherPackageBaseObject;
import com.oracle.truffle.api.library.test.otherPackage.OtherPackageLibrary;
import com.oracle.truffle.api.test.AbstractLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

public class ExportSubclassTest extends AbstractLibraryTest {
    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class ExportSubclassLibrary extends Library {

        public String m0(Object receiver) {
            return "m0_default";
        }

        public abstract String m1(Object receiver);
    }

    @ExportLibrary(ExportSubclassLibrary.class)
    static class BaseClass {

        // directly inherit to SubClass1 and SubClass2
        @ExportMessage
        String m0() {
            return "base_m0";
        }

        @ExportMessage
        static class M1 {
            @Specialization
            static String doDefault(@SuppressWarnings("unused") BaseClass receiver) {
                return "base_m1";
            }
        }

    }

    // subclass that re-exports
    @ExportLibrary(ExportSubclassLibrary.class)
    static class SubClass1 extends BaseClass {

        @ExportMessage
        static class M0 {
            @Specialization
            static String doDefault(@SuppressWarnings("unused") SubClass1 receiver) {
                return "sub1_m0";
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final String m1() {
            return "sub1_m1";
        }

    }

    // subclass that does not re-export
    static class SubClass2 extends BaseClass {

        @Override
        String m0() {
            return "sub2_m0";
        }

    }

    @ExportLibrary(ExportSubclassLibrary.class)
    static class ErrorRedirectionBaseClass {
        @ExportMessage
        @ExpectError("Expected parameter count 1 for exported message, but was 0.%")
        static final String m1() {
            return null;
        }
    }

    @ExpectError("Message redirected from element ExportSubclassTest.ErrorRedirectionBaseClass.m1():%")
    @ExportLibrary(ExportSubclassLibrary.class)
    static class ErrorRedirectionSubClass extends ErrorRedirectionBaseClass {
    }

    @ExportLibrary(OtherPackageLibrary.class)
    @ExpectError("Found invisible exported elements in super type 'ErrorOtherPackageBaseObject1': %n" +
                    "   - com.oracle.truffle.api.library.test.otherPackage.ErrorOtherPackageBaseObject1.m0()%n" +
                    "Increase their visibility to resolve this problem.")
    static class InvisibleBaseElement1 extends ErrorOtherPackageBaseObject1 {
    }

    @ExportLibrary(OtherPackageLibrary.class)
    @ExpectError("Found invisible exported elements in super type 'ErrorOtherPackageBaseObject2': %n" +
                    "   - com.oracle.truffle.api.library.test.otherPackage.ErrorOtherPackageBaseObject2.M0.doDefault(ErrorOtherPackageBaseObject2)%n" +
                    "Increase their visibility to resolve this problem.")
    static class InvisibleBaseElement2 extends ErrorOtherPackageBaseObject2 {
    }

    @ExpectError("Found invisible exported elements in super type 'ErrorOtherPackageBaseObject3': %n" +
                    "   - com.oracle.truffle.api.library.test.otherPackage.ErrorOtherPackageBaseObject3.M0%n" +
                    "Increase their visibility to resolve this problem.")
    @ExportLibrary(OtherPackageLibrary.class)
    static class InvisibleBaseElement3 extends ErrorOtherPackageBaseObject3 {
    }

    @ExportLibrary(OtherPackageLibrary.class)
    static class OtherPackageSubClass extends OtherPackageBaseObject {

        @Override
        @ExportMessage
        public String m0() {
            return "m0_sub";
        }

        @ExportMessage
        public String m4() {
            return "m4_sub";
        }
    }

    @Test
    public void testOtherPackageSubclass() {
        OtherPackageSubClass subclass = new OtherPackageSubClass();
        OtherPackageLibrary lib = createCachedDispatch(OtherPackageLibrary.class, 4);
        assertEquals("m0_sub", lib.m0(subclass));
        assertEquals("m1_base", lib.m1(subclass));
        assertEquals("m2_base", lib.m2(subclass));
        assertEquals("m3_base", lib.m3(subclass));
        assertEquals("m4_sub", lib.m4(subclass));
        assertEquals("m5_default", lib.m5(subclass));

        OtherPackageBaseObject baseClass = new OtherPackageBaseObject();
        assertEquals("m0_base", lib.m0(baseClass));
        assertEquals("m1_base", lib.m1(baseClass));
        assertEquals("m2_base", lib.m2(baseClass));
        assertEquals("m3_base", lib.m3(baseClass));
        assertEquals("m4_default", lib.m4(baseClass));
        assertEquals("m5_default", lib.m5(baseClass));
    }

    @ExportLibrary(ExportSubclassLibrary.class)
    static class SubClass3 extends BaseClass {

        @ExportMessage(library = ExportSubclassLibrary.class, name = "m0")
        @ExportMessage(library = ExportSubclassLibrary.class, name = "m1")
        String m01(@SuppressWarnings("unused") @Exclusive @Cached("42") int subValue) {
            return "sub3_m01";
        }

    }

    @Test
    public void testSubclass() {
        for (int i = 0; i < 4; i++) {
            ExportSubclassLibrary lib = createCachedDispatch(ExportSubclassLibrary.class, i);

            assertEquals("base_m0", lib.m0(new BaseClass()));
            assertEquals("sub1_m0", lib.m0(new SubClass1()));
            assertEquals("sub2_m0", lib.m0(new SubClass2()));
            assertEquals("sub3_m01", lib.m0(new SubClass3()));

            assertEquals("base_m1", lib.m1(new BaseClass()));
            assertEquals("sub1_m1", lib.m1(new SubClass1()));
            assertEquals("base_m1", lib.m1(new SubClass2()));
            assertEquals("sub3_m01", lib.m0(new SubClass3()));
        }
    }

    @Test
    public void testMergedLibraryInheritance() {
        ExportSubclassLibrary lib = createCachedDispatch(ExportSubclassLibrary.class, 5);
        assertEquals("m0_default", lib.m0(new MergedLibraryBase()));
        assertEquals("base_m1", lib.m1(new MergedLibraryBase()));

        assertEquals("sub_m0", lib.m0(new MergedLibrarySub()));
        assertEquals("base_m1", lib.m1(new MergedLibrarySub()));

    }

    @ExportLibrary(ExportSubclassLibrary.class)
    static class MergedLibraryBase implements TruffleObject {

        final Object member = "";

        ExportSubclassLibrary firstLib;

        @ExportMessage
        final String m1(@CachedLibrary("this.member") ExportSubclassLibrary lib) {
            if (firstLib == null) {
                firstLib = lib;
            }
            assert firstLib == lib : "merged library is not shared";
            return "base_m1";
        }

    }

    @ExportLibrary(ExportSubclassLibrary.class)
    static class MergedLibrarySub extends MergedLibraryBase {

        @ExportMessage
        final String m0(@CachedLibrary("this.member") ExportSubclassLibrary lib) {
            if (firstLib == null) {
                firstLib = lib;
            }
            assert firstLib == lib : "merged library is not shared";
            return "sub_m0";
        }
    }

}
