/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

@SuppressWarnings("unused")
public class MessageDeprecationTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED);
    }

    static class A {
    }

    static class B_extends_A extends A {
    }

    @GenerateLibrary
    public abstract static class MoreGenericType2 extends Library {

        @Deprecated
        public String m0(Object receiver, B_extends_A p0) {
            return "m0_default_deprecated";
        }

        // more generic
        public String m0(Object receiver, A p0) {
            return "m0_default";
        }

    }

    @ExportLibrary(MoreGenericType2.class)
    static class MoreGenericType2MethodOld implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object, B_extends_A)' of library 'MoreGenericType2' is deprecated and should be updated to be compatible with its new signature 'm0(Object, A)'.%")
        @ExportMessage
        String m0(B_extends_A parameter) {
            return "m0_old";
        }

    }

    @ExportLibrary(MoreGenericType2.class)
    static class MoreGenericType2MethodNew implements TruffleObject {

        @ExportMessage
        String m0(A p) {
            return "m0_new";
        }

    }

    @ExportLibrary(MoreGenericType2.class)
    static class MoreGenericType2NodeOld implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object, B_extends_A)' of library 'MoreGenericType2' is deprecated and should be updated to be compatible with its new signature 'm0(Object, A)'.%")
        @ExportMessage
        static class M0 {

            @Specialization
            static String doDefault(MoreGenericType2NodeOld receiver, B_extends_A p) {
                return "m0_old";
            }
        }

    }

    @ExportLibrary(MoreGenericType2.class)
    static class MoreGenericType2NodeNew implements TruffleObject {

        @ExportMessage
        static class M0 {

            @Specialization
            static String doDefault(MoreGenericType2NodeNew receiver, A p) {
                return "m0_new";
            }
        }

    }

    @Test
    public void testMoreGenericType2Method() {
        MoreGenericType2MethodOld oldObject = new MoreGenericType2MethodOld();
        MoreGenericType2MethodNew newObject = new MoreGenericType2MethodNew();

        MoreGenericType2 oldLibrary = createLibrary(MoreGenericType2.class, oldObject);
        assertEquals("m0_default", oldLibrary.m0(oldObject, new A()));
        assertEquals("m0_old", oldLibrary.m0(oldObject, new B_extends_A()));

        MoreGenericType2 newLibrary = createLibrary(MoreGenericType2.class, newObject);
        assertEquals("m0_new", newLibrary.m0(newObject, new A()));
        assertEquals("m0_default_deprecated", newLibrary.m0(newObject, new B_extends_A()));
    }

    @Test
    public void testMoreGenericType2Node() {
        MoreGenericType2NodeOld oldObject = new MoreGenericType2NodeOld();
        MoreGenericType2NodeNew newObject = new MoreGenericType2NodeNew();

        MoreGenericType2 oldLibrary = createLibrary(MoreGenericType2.class, oldObject);
        assertEquals("m0_default", oldLibrary.m0(oldObject, new A()));
        assertEquals("m0_old", oldLibrary.m0(oldObject, new B_extends_A()));

        MoreGenericType2 newLibrary = createLibrary(MoreGenericType2.class, newObject);
        assertEquals("m0_new", newLibrary.m0(newObject, new A()));
        assertEquals("m0_default_deprecated", newLibrary.m0(newObject, new B_extends_A()));
    }

    @ExportLibrary(MoreGenericType2.class)
    static class MoreGenericType2NodeSpecializationsOld implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object, B_extends_A)' of library 'MoreGenericType2' is deprecated and should be updated to be compatible with its new signature 'm0(Object, A)'. %")
        @ExportMessage
        static class M0 {

            @Specialization(guards = "cache == p", limit = "3")
            static String s0(MoreGenericType2NodeSpecializationsOld receiver, B_extends_A p, @Cached("p") B_extends_A cache) {
                return "m0_old";
            }

            @Specialization
            static String s1(MoreGenericType2NodeSpecializationsOld receiver, B_extends_A p) {
                return "m0_old";
            }

        }

    }

    @ExportLibrary(MoreGenericType2.class)
    static class MoreGenericType2NodeSpecializationsNew implements TruffleObject {
        @ExportMessage
        static class M0 {

            @Specialization(guards = "cache == p", limit = "3")
            static String s0(MoreGenericType2NodeSpecializationsNew receiver, B_extends_A p, @Cached("p") B_extends_A cache) {
                return "m0_new";
            }

            @Specialization(replaces = "s0")
            static String s1(MoreGenericType2NodeSpecializationsNew receiver, A p) {
                return "m0_new";
            }

        }
    }

    @Test
    public void testMoreGenericType2NodeSpecializations() {
        MoreGenericType2NodeSpecializationsOld oldObject = new MoreGenericType2NodeSpecializationsOld();
        MoreGenericType2NodeSpecializationsNew newObject = new MoreGenericType2NodeSpecializationsNew();

        MoreGenericType2 oldLibrary = createLibrary(MoreGenericType2.class, oldObject);
        assertEquals("m0_default", oldLibrary.m0(oldObject, new A()));
        assertEquals("m0_old", oldLibrary.m0(oldObject, new B_extends_A()));

        MoreGenericType2 newLibrary = createLibrary(MoreGenericType2.class, newObject);
        assertEquals("m0_new", newLibrary.m0(newObject, new A()));
        assertEquals("m0_default_deprecated", newLibrary.m0(newObject, new B_extends_A()));

    }

    @GenerateLibrary
    public abstract static class MoreGenericType3 extends Library {

        @Deprecated
        public String m0(Object receiver, A p0) {
            return "m0_A";
        }

        public String m0(Object receiver, Object p0) {
            return "m0_default";
        }

        @Deprecated
        public String m0(Object receiver, B_extends_A p0) {
            return "m0_B_extends_A";
        }

    }

    @ExportLibrary(MoreGenericType3.class)
    static class MoreGenericType3NodeOld1 implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object, B_extends_A)' of library 'MoreGenericType3' is deprecated and should be updated to be compatible with its new signature 'm0(Object, Object)'.%")
        @ExportMessage
        static class M0 {

            @Specialization(guards = "cache == p", limit = "3")
            static String s0(MoreGenericType3NodeOld1 receiver, B_extends_A p, @Cached("p") B_extends_A cache) {
                return "m0_old1";
            }

            @Specialization(replaces = "s0")
            static String s1(MoreGenericType3NodeOld1 receiver, B_extends_A p) {
                return "m0_old1";
            }

        }

    }

    @ExportLibrary(MoreGenericType3.class)
    static class MoreGenericType3NodeOld2 implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object, A)' of library 'MoreGenericType3' is deprecated and should be updated to be compatible with its new signature 'm0(Object, Object)'.%")
        @ExportMessage
        static class M0 {

            @Specialization(guards = "cache == p", limit = "3")
            static String s0(MoreGenericType3NodeOld2 receiver, B_extends_A p, @Cached("p") B_extends_A cache) {
                return "m0_old2";
            }

            @Specialization(replaces = "s0")
            static String s1(MoreGenericType3NodeOld2 receiver, A p) {
                return "m0_old2";
            }

        }

    }

    @ExportLibrary(MoreGenericType3.class)
    static class MoreGenericType3NodeNew implements TruffleObject {

        @ExportMessage
        static class M0 {

            @Specialization(guards = "cache == p", limit = "3")
            static String s0(MoreGenericType3NodeNew receiver, B_extends_A p, @Cached("p") B_extends_A cache) {
                return "m0_new";
            }

            @Specialization(replaces = "s0")
            static String s1(MoreGenericType3NodeNew receiver, Object p) {
                return "m0_new";
            }

        }
    }

    @Test
    public void testMoreGenericType3Node() {
        MoreGenericType3NodeOld1 oldObject1 = new MoreGenericType3NodeOld1();
        MoreGenericType3NodeOld2 oldObject2 = new MoreGenericType3NodeOld2();
        MoreGenericType3NodeNew newObject = new MoreGenericType3NodeNew();

        MoreGenericType3 oldLibrary1 = createLibrary(MoreGenericType3.class, oldObject1);
        assertEquals("m0_old1", oldLibrary1.m0(oldObject1, new B_extends_A()));
        assertEquals("m0_A", oldLibrary1.m0(oldObject1, new A()));
        assertEquals("m0_default", oldLibrary1.m0(oldObject1, new Object()));

        MoreGenericType3 oldLibrary2 = createLibrary(MoreGenericType3.class, oldObject2);
        assertEquals("m0_B_extends_A", oldLibrary2.m0(oldObject2, new B_extends_A()));
        assertEquals("m0_old2", oldLibrary2.m0(oldObject2, new A()));
        assertEquals("m0_default", oldLibrary2.m0(oldObject2, new Object()));

        MoreGenericType3 newLibrary = createLibrary(MoreGenericType3.class, newObject);
        assertEquals("m0_B_extends_A", newLibrary.m0(newObject, new B_extends_A()));
        assertEquals("m0_A", newLibrary.m0(newObject, new A()));
        assertEquals("m0_new", newLibrary.m0(newObject, new Object()));

    }

    @Test
    public void testMoreGenericType3NodeReflection() throws Exception {
        List<Message> messages = LibraryFactory.resolve(MoreGenericType3.class).getMessages();

        assertEquals(3, messages.size());
        Message m0Object = messages.get(0);
        Message m0A = messages.get(1);
        Message m0BextendsA = messages.get(2);
        assertEquals(Object.class, m0Object.getParameterType(1));
        assertEquals("m0", m0Object.getSimpleName());

        assertEquals(A.class, m0A.getParameterType(1));
        assertEquals("m0", m0A.getSimpleName());

        assertEquals(B_extends_A.class, m0BextendsA.getParameterType(1));
        assertEquals("m0", m0BextendsA.getSimpleName());

        MoreGenericType3NodeOld1 oldObject1 = new MoreGenericType3NodeOld1();
        MoreGenericType3NodeOld2 oldObject2 = new MoreGenericType3NodeOld2();
        MoreGenericType3NodeNew newObject = new MoreGenericType3NodeNew();

        ReflectionLibrary oldLibrary1 = createLibrary(ReflectionLibrary.class, oldObject1);
        assertEquals("m0_old1", oldLibrary1.send(oldObject1, m0BextendsA, new B_extends_A()));
        assertEquals("m0_A", oldLibrary1.send(oldObject1, m0A, new A()));
        assertEquals("m0_default", oldLibrary1.send(oldObject1, m0Object, new Object()));

        ReflectionLibrary oldLibrary2 = createLibrary(ReflectionLibrary.class, oldObject2);
        assertEquals("m0_B_extends_A", oldLibrary2.send(oldObject2, m0BextendsA, new B_extends_A()));
        assertEquals("m0_old2", oldLibrary2.send(oldObject2, m0A, new A()));
        assertEquals("m0_default", oldLibrary2.send(oldObject2, m0Object, new Object()));

        ReflectionLibrary newLibrary = createLibrary(ReflectionLibrary.class, newObject);
        assertEquals("m0_B_extends_A", newLibrary.send(newObject, m0BextendsA, new B_extends_A()));
        assertEquals("m0_A", newLibrary.send(newObject, m0A, new A()));
        assertEquals("m0_new", newLibrary.send(newObject, m0Object, new Object()));

    }

    @GenerateLibrary
    public abstract static class AllDeprecated extends Library {

        @Deprecated
        public String m0(Object receiver, Object p0) {
            return "m0_1";
        }

        @Deprecated
        public String m0(Object receiver, String p0) {
            return "m0_0";
        }

    }

    @GenerateLibrary
    public abstract static class FewerParameters3 extends Library {

        @Deprecated
        @SuppressWarnings("unused")
        public String m0(Object receiver, String p0) {
            return m0(receiver);
        }

        public abstract String m0(Object receiver);

        @Deprecated
        @SuppressWarnings("unused")
        public String m0(Object receiver, String p0, String p1) {
            return m0(receiver, p0);
        }

    }

    @Test
    public void testMessageResolution() {
        assertEquals(A.class, Message.resolve(MoreGenericType2.class, "m0").getParameterTypes().get(1));
        assertEquals(Object.class, Message.resolve(MoreGenericType3.class, "m0").getParameterTypes().get(1));
        assertEquals(Object.class, Message.resolve(AllDeprecated.class, "m0").getParameterTypes().get(1));
        assertTrue(Message.resolve(AllDeprecated.class, "m0").isDeprecated());
        assertEquals(1, Message.resolve(FewerParameters2.class, "m0").getParameterTypes().size());
        assertEquals(1, Message.resolve(FewerParameters3.class, "m0").getParameterTypes().size());
        assertTrue(Message.resolve(SimpleDeprecated.class, "m0").isDeprecated());
    }

    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class SimpleDeprecated extends Library {

        @Deprecated
        public String m0(Object receiver) {
            return "m0_default";
        }

        public String m1(Object receiver) {
            return "m1_default";
        }

    }

    @ExportLibrary(SimpleDeprecated.class)
    static class SimpleDeprecatedOld implements TruffleObject {

        @ExpectError("The message 'm0' from library 'SimpleDeprecated' is deprecated. Please refer to the library documentation on how to resolve this problem.%")
        @ExportMessage
        String m0() {
            return "m0_old";
        }

    }

    @ExportLibrary(SimpleDeprecated.class)
    static class SimpleDeprecatedNew implements TruffleObject {

        @ExportMessage
        String m1() {
            return "m1_new";
        }

    }

    @Test
    public void testSimpleDeprecated() {
        SimpleDeprecatedOld oldObject = new SimpleDeprecatedOld();
        SimpleDeprecatedNew newObject = new SimpleDeprecatedNew();

        SimpleDeprecated oldLibrary = createLibrary(SimpleDeprecated.class, oldObject);
        assertEquals("m0_old", oldLibrary.m0(oldObject));
        assertEquals("m1_default", oldLibrary.m1(oldObject));

        SimpleDeprecated newLibrary = createLibrary(SimpleDeprecated.class, newObject);
        assertEquals("m0_default", newLibrary.m0(newObject));
        assertEquals("m1_new", newLibrary.m1(newObject));
    }

    @GenerateLibrary
    public abstract static class FewerParameters2 extends Library {

        public String m0(Object receiver) {
            return "m0_default";
        }

        @Deprecated
        @SuppressWarnings("unused")
        public String m0(Object receiver, String parameter) {
            return "m0_default_deprecated";
        }

    }

    @ExportLibrary(FewerParameters2.class)
    static class FewerParameters2MethodOld implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object, String)' of library 'FewerParameters2' is deprecated and should be updated to be compatible with its new signature 'm0(Object)'.%")
        @ExportMessage
        String m0(String parameter) {
            return "m0_old";
        }

    }

    @ExportLibrary(FewerParameters2.class)
    static class FewerParameters2MethodNew implements TruffleObject {

        @ExportMessage
        String m0() {
            return "m0_new";
        }

    }

    @ExportLibrary(FewerParameters2.class)
    static class FewerParameters2NodeOld implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object, String)' of library 'FewerParameters2' is deprecated and should be updated to be compatible with its new signature 'm0(Object)'.%")
        @ExportMessage
        static class M0 {

            @Specialization
            static String doDefault(FewerParameters2NodeOld receiver, String p) {
                return "m0_old";
            }
        }

    }

    @ExportLibrary(FewerParameters2.class)
    static class FewerParameters2NodeNew implements TruffleObject {

        @ExportMessage
        static class M0 {

            @Specialization
            static String doDefault(FewerParameters2NodeNew receiver) {
                return "m0_new";
            }
        }

    }

    @Test
    public void testFewerParameters2Method() {
        FewerParameters2MethodOld oldObject = new FewerParameters2MethodOld();
        FewerParameters2MethodNew newObject = new FewerParameters2MethodNew();

        FewerParameters2 oldLibrary = createLibrary(FewerParameters2.class, oldObject);
        assertEquals("m0_default", oldLibrary.m0(oldObject));
        assertEquals("m0_old", oldLibrary.m0(oldObject, ""));

        FewerParameters2 newLibrary = createLibrary(FewerParameters2.class, newObject);
        assertEquals("m0_new", newLibrary.m0(newObject));
        assertEquals("m0_default_deprecated", newLibrary.m0(newObject, ""));
    }

    @Test
    public void testFewerParameters2Node() {
        FewerParameters2NodeOld oldObject = new FewerParameters2NodeOld();
        FewerParameters2NodeNew newObject = new FewerParameters2NodeNew();

        FewerParameters2 oldLibrary = createLibrary(FewerParameters2.class, oldObject);
        assertEquals("m0_default", oldLibrary.m0(oldObject));
        assertEquals("m0_old", oldLibrary.m0(oldObject, ""));

        FewerParameters2 newLibrary = createLibrary(FewerParameters2.class, newObject);
        assertEquals("m0_new", newLibrary.m0(newObject));
        assertEquals("m0_default_deprecated", newLibrary.m0(newObject, ""));
    }

    @GenerateLibrary
    public abstract static class MoreParameters2 extends Library {

        @Deprecated
        public String m0(Object receiver) {
            return "m0_default_deprecated";
        }

        public String m0(Object receiver, String parameter) {
            return "m0_default";
        }

    }

    @ExportLibrary(MoreParameters2.class)
    static class MoreParameters2MethodOld implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object)' of library 'MoreParameters2' is deprecated and should be updated to be compatible with its new signature 'm0(Object, String)'.%")
        @ExportMessage
        String m0() {
            return "m0_old";
        }

    }

    @ExportLibrary(MoreParameters2.class)
    static class MoreParameters2MethodNew implements TruffleObject {

        @ExportMessage
        String m0(String parameter) {
            return "m0_new";
        }

    }

    @ExportLibrary(MoreParameters2.class)
    static class MoreParameters2NodeOld implements TruffleObject {

        @ExpectError("The message with signature 'm0(Object)' of library 'MoreParameters2' is deprecated and should be updated to be compatible with its new signature 'm0(Object, String)'.%")
        @ExportMessage
        static class M0 {

            @Specialization
            static String doDefault(MoreParameters2NodeOld receiver) {
                return "m0_old";
            }
        }

    }

    @ExportLibrary(MoreParameters2.class)
    static class MoreParameters2NodeNew implements TruffleObject {
        @ExportMessage
        static class M0 {

            @Specialization
            static String doDefault(MoreParameters2NodeNew receiver, String p) {
                return "m0_new";
            }
        }
    }

    @Test
    public void testMoreParameters2Method() {
        MoreParameters2MethodOld oldObject = new MoreParameters2MethodOld();
        MoreParameters2MethodNew newObject = new MoreParameters2MethodNew();

        MoreParameters2 oldLibrary = createLibrary(MoreParameters2.class, oldObject);
        assertEquals("m0_old", oldLibrary.m0(oldObject));
        assertEquals("m0_default", oldLibrary.m0(oldObject, ""));

        MoreParameters2 newLibrary = createLibrary(MoreParameters2.class, newObject);
        assertEquals("m0_default_deprecated", newLibrary.m0(newObject));
        assertEquals("m0_new", newLibrary.m0(newObject, ""));
    }

    @Test
    public void testMoreParameters2Node() {
        MoreParameters2NodeOld oldObject = new MoreParameters2NodeOld();
        MoreParameters2NodeNew newObject = new MoreParameters2NodeNew();

        MoreParameters2 oldLibrary = createLibrary(MoreParameters2.class, oldObject);
        assertEquals("m0_old", oldLibrary.m0(oldObject));
        assertEquals("m0_default", oldLibrary.m0(oldObject, ""));

        MoreParameters2 newLibrary = createLibrary(MoreParameters2.class, newObject);
        assertEquals("m0_default_deprecated", newLibrary.m0(newObject));
        assertEquals("m0_new", newLibrary.m0(newObject, ""));
    }

    @GenerateLibrary
    public abstract static class ErrorInvalidDeprecation1 extends Library {

        @ExpectError("Could not determine primary overload for all deprecated messages.%")
        @Deprecated
        public String m0(Object receiver, int parameter) {
            return "m0_default_deprecated";
        }

        @ExpectError("Could not determine primary overload for all deprecated messages.%")
        @Deprecated
        public String m0(Object receiver, String parameter) {
            return "m0_default";
        }

    }

    @GenerateLibrary
    public abstract static class ErrorInvalidDeprecation2 extends Library {

        public String m0(Object receiver, int parameter) {
            return "m0_default_deprecated";
        }

        @ExpectError("Could not delegate from this deprecated message to method m0(Object, int). Method parameters are not compatible.")
        @Deprecated
        public String m0(Object receiver, String parameter) {
            return "m0_default";
        }

    }

    @GenerateLibrary
    public abstract static class ErrorInvalidDeprecation3 extends Library {

        @ExpectError("A deprecated message must not be abstract. Deprecated messages need a default implementation.")
        @Deprecated
        public abstract String m0(Object receiver, int parameter);

    }

    @GenerateLibrary
    public abstract static class ErrorInvalidDeprecation4 extends Library {

        @ExpectError("Library message must have a unique name. Two methods with the same name found. " +
                        "If this method is not intended to be a library message then add the private or final modifier to ignore it. " +
                        "Note it is also possible to deprecate all messages except one using the @Deprecated annotation in order to evolve APIs in a compatible way.")
        public abstract String m0(Object receiver, int parameter);

        @ExpectError("Library message must have a unique name. Two methods with the same name found. " +
                        "If this method is not intended to be a library message then add the private or final modifier to ignore it. " +
                        "Note it is also possible to deprecate all messages except one using the @Deprecated annotation in order to evolve APIs in a compatible way.")
        public abstract String m0(Object receiver, String parameter);

    }

    @GenerateLibrary
    public abstract static class AbstractMessageLibrary extends Library {

        @Abstract
        public String m0(Object receiver, Object parameter) {
            return "m0_default_deprecated";
        }

        @Deprecated
        @Abstract
        public String m0(Object receiver, String parameter) {
            return "m0_default";
        }

    }

    @ExportLibrary(AbstractMessageLibrary.class)
    public static class AbstractMessageNew {

        @ExportMessage
        String m0(Object parameter) {
            return "m0_new";
        }

    }

    @ExportLibrary(AbstractMessageLibrary.class)
    public static class AbstractMessageOld {

        @ExpectError("The message with signature 'm0(Object, String)' of library 'AbstractMessageLibrary' is deprecated%")
        @ExportMessage
        String m0(String parameter) {
            return "m0_old";
        }

    }

    @ExportLibrary(AbstractMessageLibrary.class)
    public static class ErrorAbstractMessageBoth {

        @ExportMessage
        @ExpectError("Duplicate exported library message m0.")
        String m0(Object parameter) {
            return "m0_new";
        }

        @ExportMessage
        @ExpectError("Duplicate exported library message m0.")
        String m0(String parameter) {
            return "m0_old";
        }

    }
}
