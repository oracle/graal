/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.fail;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.test.CachedLibraryTest.SimpleDispatchedNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.AbstractLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

@SuppressWarnings("unused")
public class GenerateLibraryTest extends AbstractLibraryTest {

    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class SampleLibrary extends Library {

        public String call(Object receiver) {
            return "default";
        }

        public abstract void abstractMethod(Object receiver);

        public abstract void abstractMethodWithFrame(Object receiver, VirtualFrame frame);

    }

    @ExportLibrary(SampleLibrary.class)
    public static class Sample {

        private final String name;

        Sample() {
            this(null);
        }

        Sample(String name) {
            this.name = name;
        }

        @ExportMessage
        void abstractMethod() {
        }

        @ExportMessage
        void abstractMethodWithFrame(VirtualFrame frame) {
        }

        @ExportMessage
        boolean accepts(@Cached(value = "this") Sample cachedS) {
            // use identity caches to make it easier to overflow
            return this == cachedS;
        }

        @ExportMessage
        static final String call(Sample s, @Cached(value = "0", uncached = "1", neverDefault = false) int cached) {
            if (cached == 0) {
                if (s.name != null) {
                    return s.name + "_cached";
                } else {
                    return "cached";
                }
            } else {
                if (s.name != null) {
                    return s.name + "_uncached";
                } else {
                    return "uncached";
                }
            }

        }

    }

    private abstract static class InvalidLibrary extends Library {
    }

    @Test
    public void testDispatched() {
        SampleLibrary uncached;
        SampleLibrary cached;
        Sample s1 = new Sample("s1");
        Sample s2 = new Sample("s2");
        Sample s3 = new Sample("s3");

        try {
            getUncachedDispatch(null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        try {
            getUncachedDispatch(InvalidLibrary.class);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        uncached = getUncachedDispatch(SampleLibrary.class);
        assertEquals("s1_uncached", uncached.call(s1));
        assertEquals("s1_uncached", uncached.call(s1));
        assertEquals("s2_uncached", uncached.call(s2));
        assertEquals("s3_uncached", uncached.call(s3));

        try {
            createCachedDispatch(null, 0);
            fail();
        } catch (NullPointerException e) {
            // expected
        }

        // not really useful but shouldn't fail
        createCachedDispatch(SampleLibrary.class, -1);

        try {
            createCachedDispatch(InvalidLibrary.class, 0);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        cached = createCachedDispatch(SampleLibrary.class, 0);
        assertEquals("s1_uncached", cached.call(s1));

        cached = createCachedDispatch(SampleLibrary.class, 1);
        assertEquals("s1_cached", cached.call(s1));
        assertEquals("s1_cached", cached.call(s1));
        assertEquals("s2_uncached", cached.call(s2));
        assertEquals("s3_uncached", cached.call(s3));
        assertEquals("s1_uncached", cached.call(s1));

        cached = createCachedDispatch(SampleLibrary.class, 2);
        assertEquals("s1_cached", cached.call(s1));
        assertEquals("s1_cached", cached.call(s1));
        assertEquals("s2_cached", cached.call(s2));
        assertEquals("s3_uncached", cached.call(s3));
        assertEquals("s2_uncached", cached.call(s2));
        assertEquals("s1_uncached", cached.call(s1));

        SimpleDispatchedNode.limit = 3;
        cached = createCachedDispatch(SampleLibrary.class, 3);
        assertEquals("s1_cached", cached.call(s1));
        assertEquals("s1_cached", cached.call(s1));
        assertEquals("s2_cached", cached.call(s2));
        assertEquals("s3_cached", cached.call(s3));

    }

    @GenerateLibrary
    @ExpectError("Declared library classes must exactly extend the type com.oracle.truffle.api.library.Library.")
    public static class ErrorLibrary1 {

    }

    @GenerateLibrary
    @ExpectError("Declared library classes must exactly extend the type com.oracle.truffle.api.library.Library.")
    public static class ErrorLibrary2 extends Node {
    }

    @GenerateLibrary
    public abstract static class ErrorLibrary3 extends Library {

        @ExpectError("Not enough arguments specified for a library message. The first argument of a library method must be of type Object. Add a receiver argument with type Object resolve " +
                        "this.If this method is not intended to be a library message then add the private or final modifier to ignore it.")
        public void foobar() {
        }

    }

    @GenerateLibrary
    public abstract static class ErrorLibrary4 extends Library {

        public void bar(String a) {
        }

        @ExpectError("Invalid first argument type Integer specified. The first argument of a library method must be of the same type for all methods. " +
                        "If this method is not intended to be a library message then add the private or final modifier to ignore it.")
        public void baz(Integer a) {
        }

    }

    @GenerateLibrary
    @ExpectError("Primitive receiver type found. Only reference types are supported.")
    public abstract static class ErrorLibrary6 extends Library {

        public abstract void messageVoid(int receiver);

    }

    @GenerateLibrary
    public abstract static class ValidObjectLibrary extends Library {

        public abstract void messageVoid1(Object receiver);

        public abstract void messageVoid2(Object receiver, int arg0);

        public abstract boolean messageBoolean1(Object receiver);

        public abstract boolean messageBoolean2(Object receiver, boolean arg0);

        public abstract byte messageByte1(Object receiver);

        public abstract byte messageByte2(Object receiver, byte arg0);

        public abstract short messageShort1(Object receiver);

        public abstract short messageShort2(Object receiver, short arg0);

        public abstract int messageInt1(Object receiver);

        public abstract int messageInt2(Object receiver, int arg0);

        public abstract long messageLong1(Object receiver);

        public abstract long messageLong2(Object receiver, long arg0);

        public abstract float messageFloat1(Object receiver);

        public abstract float messageFloat2(Object receiver, float arg0);

        public abstract double messageDouble1(Object receiver);

        public abstract double messageDouble2(Object receiver, double arg0);

        public abstract String messageString1(Object receiver);

        public abstract String messageString2(Object receiver, String arg0);

    }

    @GenerateLibrary
    public abstract static class ValidStringLibrary extends Library {

        public abstract void messageVoid(CharSequence receiver);

        public abstract boolean messageBoolean(CharSequence receiver);

        public abstract byte messageByte(CharSequence receiver);

        public abstract short messageShort(CharSequence receiver);

        public abstract int messageInt(CharSequence receiver);

        public abstract long messageLong(CharSequence receiver);

        public abstract float messageFloat(CharSequence receiver);

        public abstract double messageDouble(CharSequence receiver);

        public abstract String messageString(CharSequence receiver);

    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReplacementMessageReflection() {
        assertEquals(String.class, Message.resolve(ReplacementsLibrary.class, "readMember").getParameterType(1));
        assertTrue(Message.resolve(ReplacementsLibrary.class, "readMember").isDeprecated());
    }

    @GenerateLibrary
    public abstract static class ReplacementsLibrary extends Library {

        @Deprecated
        public int readMember(Object receiver, String name) {
            throw new UnsupportedOperationException();
        }

        @Abstract(replacementOf = "readMember(Object, String)")
        public int readMember(Object receiver, Object name) {
            if (name instanceof String stringName) {
                return readMember(receiver, stringName);
            }
            throw new UnsupportedOperationException();
        }

        @Deprecated
        public int read(Object receiver, int index) {
            throw new UnsupportedOperationException();
        }

        @Abstract(replacementOf = "read(Object, int)")
        public int read(Object receiver, long index) {
            if (Integer.MIN_VALUE <= index && index <= Integer.MAX_VALUE) {
                return read(receiver, (int) index);
            }
            throw new UnsupportedOperationException();
        }

        @Deprecated
        public int readUnsigned(Object receiver, int index) {
            throw new UnsupportedOperationException();
        }

        @Abstract(replacementOf = "readUnsigned(Object, int)", replacementMethod = "readUnsignedLegacy")
        public int readUnsigned(Object receiver, long index) {
            if (0 <= index && index <= 0xFFFFFFFFL) {
                return readUnsigned(receiver, (int) (0xFFFFFFFFL & index));
            }
            throw new UnsupportedOperationException();
        }

        protected final int readUnsignedLegacy(Object receiver, int index) {
            long unsignedIndex = Integer.toUnsignedLong(index);
            return read(receiver, unsignedIndex);
        }
    }

    @ExportLibrary(ReplacementsLibrary.class)
    @SuppressWarnings({"deprecation", "static-method"})
    public static class ReplacementLegacy {

        @ExportMessage
        final int readMember(String name) {
            return 1;
        }

        @ExportMessage
        final int read(int index) {
            return Integer.toString(index).length();
        }

        @ExportMessage
        final int readUnsigned(int index) {
            return Integer.toUnsignedString(index).length();
        }
    }

    @ExportLibrary(ReplacementsLibrary.class)
    @SuppressWarnings("static-method")
    public static class ReplacementNew {

        @ExportMessage
        final int readMember(Object name) {
            return 100;
        }

        @ExportMessage
        final int read(long index) {
            return Long.toString(index).length();
        }

        @ExportMessage
        final int readUnsigned(long index) {
            return Long.toUnsignedString(index).length();
        }
    }

    @ExportLibrary(ReplacementsLibrary.class)
    @SuppressWarnings("static-method")
    public static class ReplacementLegacyAndNew {

        @ExportMessage
        final int readMember(Object name) {
            return 100;
        }

        @ExpectError("Cannot export both a deprecated message and a new message 'read' that declares a replacement for it. " +
                        "Remove the @ExportMessage annotation from the deprecated methods to resolve this problem.")
        @ExportMessage
        final int read(int index) {
            return Long.toString(index).length();
        }

        @ExpectError("Cannot export both a deprecated message and a new message 'read' that declares a replacement for it. " +
                        "Remove the @ExportMessage annotation from the deprecated methods to resolve this problem.")
        @ExportMessage
        final int read(long index) {
            return Long.toString(index).length();
        }

        @ExpectError("Cannot export both a deprecated message and a new message 'readUnsigned' that declares a replacement for it. " +
                        "Remove the @ExportMessage annotation from the deprecated methods to resolve this problem.")
        @ExportMessage
        final int readUnsigned(int index) {
            return Integer.toUnsignedString(index).length();
        }

        @ExpectError("Cannot export both a deprecated message and a new message 'readUnsigned' that declares a replacement for it. " +
                        "Remove the @ExportMessage annotation from the deprecated methods to resolve this problem.")
        @ExportMessage
        final int readUnsigned(long index) {
            return Long.toUnsignedString(index).length();
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReplacements() {
        ReplacementsLibrary lib = LibraryFactory.resolve(ReplacementsLibrary.class).getUncached();
        Object legacyObj = new ReplacementLegacy();
        Object newObj = new ReplacementNew();

        assertEquals(1, lib.readMember(legacyObj, "string"));
        assertEquals(1, lib.readMember(legacyObj, (Object) "string"));
        assertEquals(2, lib.read(legacyObj, 10));
        assertEquals(2, lib.read(legacyObj, 10L));
        assertEquals(10, lib.readUnsigned(legacyObj, -10));
        assertEquals(10, lib.readUnsigned(legacyObj, 0XFFFFFFFA));

        assertEquals(100, lib.readMember(newObj, "string"));
        assertEquals(100, lib.readMember(newObj, (Object) "string"));
        assertEquals(2, lib.read(newObj, 10));
        assertEquals(2, lib.read(newObj, 10L));
        assertEquals(10, lib.readUnsigned(newObj, -10));
        assertEquals(20, lib.readUnsigned(newObj, -10L));
    }

    @GenerateLibrary
    public abstract static class ReplacementsLibrary2 extends Library {

        @Deprecated
        @Abstract(ifExported = "getLanguage")
        public boolean hasLanguage(Object receiver) {
            return false;
        }

        @Deprecated
        @Abstract(ifExported = "hasLanguage")
        public Class<? extends TruffleLanguage<?>> getLanguage(Object receiver) {
            throw new UnsupportedOperationException();
        }

        @Abstract(replacementOf = "hasLanguage(Object)", ifExported = "getLanguageId")
        public boolean hasLanguageId(Object receiver) {
            return hasLanguage(receiver);
        }

        @Abstract(replacementOf = "getLanguage(Object)", replacementMethod = "getLanguageImpl", ifExported = "hasLanguageId")
        public String getLanguageId(Object receiver) {
            return TestLanguage.ID;
        }

        @SuppressWarnings("static-method")
        protected final Class<? extends TruffleLanguage<?>> getLanguageImpl(Object receiver) {
            return TestLanguage.class;
        }

        public String asString(Object receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    private static final class TestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = "test-language-id";

        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    @ExportLibrary(ReplacementsLibrary2.class)
    @SuppressWarnings({"deprecation", "static-method"})
    public static class ReplacementLegacy2 {

        @ExportMessage
        public boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        public Class<? extends TruffleLanguage<?>> getLanguage() {
            return TestLanguage.class;
        }
    }

    @ExportLibrary(ReplacementsLibrary2.class)
    @SuppressWarnings("static-method")
    public static class ReplacementNew2 {

        @ExportMessage
        public boolean hasLanguageId() {
            return true;
        }

        @ExportMessage
        public String getLanguageId() {
            return TestLanguage.ID;
        }
    }

    @ExportLibrary(ReplacementsLibrary2.class)
    @SuppressWarnings({"deprecation", "static-method"})
    public static class ReplacementLegacyAndNew2 {

        @ExpectError("Cannot export both a deprecated message 'getLanguage' and a new message 'getLanguageId' that declares a replacement for it. " +
                        "Remove the @ExportMessage annotation from the deprecated methods to resolve this problem.")
        @ExportMessage
        public Class<? extends TruffleLanguage<?>> getLanguage() {
            return TestLanguage.class;
        }

        @ExportMessage
        public String getLanguageId() {
            return TestLanguage.ID;
        }
    }

    @ExportLibrary(ReplacementsLibrary2.class)
    public static final class ReplacementBase2 {

        @ExportMessage
        @SuppressWarnings("static-method")
        String asString() {
            return ReplacementBase2.class.getSimpleName();
        }
    }

    @ExportLibrary(value = ReplacementsLibrary2.class, delegateTo = "delegate")
    @SuppressWarnings({"deprecation", "static-method"})
    public static final class ReplacementWrapperLegacy2 {

        final Object delegate;

        ReplacementWrapperLegacy2(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        public boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        public Class<? extends TruffleLanguage<?>> getLanguage() {
            return TestLanguage.class;
        }
    }

    @ExportLibrary(value = ReplacementsLibrary2.class, delegateTo = "delegate")
    @SuppressWarnings("static-method")
    public static final class ReplacementWrapperNew2 {

        final Object delegate;

        ReplacementWrapperNew2(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        public boolean hasLanguageId() {
            return true;
        }

        @ExportMessage
        public String getLanguageId() {
            return TestLanguage.ID;
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReplacementWithDelegateTo() throws Exception {
        ReplacementWrapperNew2 newImplementation = new ReplacementWrapperNew2(new ReplacementBase2());
        ReplacementsLibrary2 lib = LibraryFactory.resolve(ReplacementsLibrary2.class).getUncached();
        assertEquals(ReplacementBase2.class.getSimpleName(), lib.asString(newImplementation));
        assertTrue(lib.hasLanguageId(newImplementation));
        assertEquals(TestLanguage.ID, lib.getLanguageId(newImplementation));
        assertTrue(lib.hasLanguage(newImplementation));
        assertEquals(TestLanguage.class, lib.getLanguage(newImplementation));

        ReplacementWrapperLegacy2 legacyImplementation = new ReplacementWrapperLegacy2(new ReplacementBase2());
        lib = LibraryFactory.resolve(ReplacementsLibrary2.class).getUncached();
        assertEquals(ReplacementBase2.class.getSimpleName(), lib.asString(legacyImplementation));
        assertTrue(lib.hasLanguageId(legacyImplementation));
        assertEquals(TestLanguage.ID, lib.getLanguageId(legacyImplementation));
        assertTrue(lib.hasLanguage(legacyImplementation));
        assertEquals(TestLanguage.class, lib.getLanguage(legacyImplementation));
    }

    @GenerateLibrary
    public abstract static class ReplacementsLibraryErrors1 extends Library {

        @Deprecated
        public int readMember(Object receiver, String name) {
            throw new UnsupportedOperationException();
        }

        @ExpectError("The replaced message readMember(Object, int) was not found. Specify an existing message with optional type arguments.")
        @Abstract(replacementOf = "readMember(Object, int)")
        public int readMember(Object receiver, Object name) {
            if (name instanceof String stringName) {
                return readMember(receiver, stringName);
            }
            throw new UnsupportedOperationException();
        }
    }

    @GenerateLibrary
    @SuppressWarnings({"deprecation", "static-method"})
    public abstract static class ReplacementsLibraryErrors2 extends Library {

        @Deprecated
        public int readUnsigned(Object receiver, int index) {
            throw new UnsupportedOperationException();
        }

        @ExpectError("The replacement method readUnsignedLegacy does not have signature and thrown types equal to the message readUnsigned(Object, int) it replaces.")
        @Abstract(replacementOf = "readUnsigned(Object, int)", replacementMethod = "readUnsignedLegacy")
        public int readUnsigned(Object receiver, long index) {
            if (0 <= index && index <= 0xFFFFFFFFL) {
                return readUnsigned(receiver, (int) (0xFFFFFFFFL & index));
            }
            throw new UnsupportedOperationException();
        }

        protected final int readUnsignedLegacy(Object receiver, String index) {
            return index.length();
        }
    }

    @GenerateLibrary
    @SuppressWarnings({"deprecation", "static-method"})
    public abstract static class ReplacementsLibraryErrors3 extends Library {

        @Deprecated
        public int readUnsigned(Object receiver, int index) throws Exception {
            throw new UnsupportedOperationException();
        }

        @ExpectError("The replacement method readUnsignedLegacy does not have signature and thrown types equal to the message readUnsigned(Object, int) it replaces.")
        @Abstract(replacementOf = "readUnsigned(Object, int)", replacementMethod = "readUnsignedLegacy")
        public int readUnsigned(Object receiver, long index) throws Exception {
            if (0 <= index && index <= 0xFFFFFFFFL) {
                return readUnsigned(receiver, (int) (0xFFFFFFFFL & index));
            }
            throw new UnsupportedOperationException();
        }

        protected final int readUnsignedLegacy(Object receiver, int index) throws ArrayIndexOutOfBoundsException {
            return index;
        }
    }

    @GenerateLibrary
    @SuppressWarnings({"deprecation", "static-method"})
    public abstract static class ReplacementsLibraryErrors4 extends Library {

        @Deprecated
        public int readUnsigned(Object receiver, int index) {
            throw new UnsupportedOperationException();
        }

        @ExpectError("The replacement method readUnsignedLegacy does not have signature and thrown types equal to the message readUnsigned(Object, int) it replaces.")
        @Abstract(replacementOf = "readUnsigned(Object, int)", replacementMethod = "readUnsignedLegacy")
        public int readUnsigned(Object receiver, long index) {
            if (0 <= index && index <= 0xFFFFFFFFL) {
                return readUnsigned(receiver, (int) (0xFFFFFFFFL & index));
            }
            throw new UnsupportedOperationException();
        }

        protected final long readUnsignedLegacy(Object receiver, int index) {
            return index;
        }
    }

    @GenerateLibrary
    public abstract static class ReplacementsLibraryErrors5 extends Library {

        @Abstract
        public boolean isType(Object receiver) {
            return false;
        }

        @ExpectError("The 'replacementMethod' attribute is only valid when 'replacementOf' is also specified.")
        @Abstract(replacementMethod = "isType")
        public Object replacedErr(Object receiver) {
            return receiver;
        }

        @ExpectError("The replacement method doReplaceNone does not exist.")
        @Abstract(replacementOf = "isType", replacementMethod = "doReplaceNone")
        public boolean replaceWithNonexisting(Object receiver) {
            return receiver != null;
        }

        @ExpectError("Message replace2 is a replacement of multiple messages. Arguments to replacementOf annotation have to be unique.")
        @Abstract(replacementOf = "isType")
        public boolean replace2(Object receiver) {
            return receiver != null;
        }
    }

    interface ExportsType {
    }

    interface ExportsGenericInterface<T> {
    }

    class ExportsClass {
    }

    class ExportsGenericClass<T> {
    }

    @ExpectError("Invalid type. Valid declared type expected.")
    @GenerateLibrary(receiverType = int.class)
    public abstract static class ExportsTypeLibraryError1 extends Library {

        public abstract void foo(Object receiver);

    }

    @ExpectError("Redundant receiver type. This receiver type could be inferred from the method signatures. Remove the explicit receiver type to resolve this redundancy.")
    @GenerateLibrary(receiverType = Object.class)
    public abstract static class ExportsTypeLibraryError2 extends Library {

        public abstract void foo(Object receiver);

    }

    @GenerateLibrary(receiverType = ExportsType.class)
    @DefaultExport(ExportsTypeDefaultLibrary.class)
    public abstract static class ExportsTypeLibrary extends Library {

        public abstract void foo(Object receiver);

    }

    @ExportLibrary(value = ExportsTypeLibrary.class, receiverType = Integer.class)
    public static class ExportsTypeDefaultLibrary {
        @ExportMessage
        static void foo(Integer receiver) {
        }
    }

    @ExpectError("Type InvalidExportsTypeImpl is not compatible with the receiver type 'ExportsType' of exported library 'ExportsTypeLibrary'. Inhert from type 'ExportsType' to resolve this.")
    @ExportLibrary(value = ExportsTypeLibrary.class)
    public static class InvalidExportsTypeImpl {
        @ExportMessage
        void foo() {
        }
    }

    @ExpectError("Using explicit receiver types is only supported%")
    @ExportLibrary(value = ExportsTypeLibrary.class, receiverType = Double.class)
    public static class InvalidDefaultTypeImpl {
        @ExportMessage
        static void foo(Double receiver) {
        }
    }

    @GenerateLibrary()
    @DefaultExport(ExportsGenericInterfaceDefaultLibrary.class)
    public abstract static class ExportsGenericInterfaceLibrary extends Library {

        public abstract void foo(ExportsGenericInterface<?> receiver);

    }

    @ExportLibrary(value = ExportsGenericInterfaceLibrary.class, receiverType = ExportsGenericInterface.class)
    public static class ExportsGenericInterfaceDefaultLibrary {
        @ExportMessage
        static void foo(ExportsGenericInterface<?> receiver) {
        }
    }

    @DefaultExport(ExportsClassDefaultLibrary.class)
    @GenerateLibrary(receiverType = ExportsClass.class)
    public abstract static class ExportsClassLibrary extends Library {
        public void foo(Object receiver) {
        }
    }

    // Tests also that ExportsClassDefaultLibraryGen has methods with proper signatures.
    @ExportLibrary(value = ExportsClassLibrary.class, receiverType = ExportsClass.class)
    public static class ExportsClassDefaultLibrary {
        @ExportMessage
        static void foo(ExportsClass receiver) {
        }
    }

    @GenerateLibrary()
    @DefaultExport(ExportsGenericClassDefaultLibrary.class)
    public abstract static class ExportsGenericClassLibrary extends Library {

        public abstract void foo(ExportsGenericClass<?> receiver);

    }

    @ExportLibrary(value = ExportsGenericClassLibrary.class, receiverType = ExportsGenericClass.class)
    public static class ExportsGenericClassDefaultLibrary {
        @ExportMessage
        static void foo(ExportsGenericClass<?> receiver) {
        }
    }

    @GenerateLibrary
    @DefaultExport(InvalidDefaultReceiverType.class)
    public abstract static class InvalidDefaultReceiverTypeLibrary extends Library {

        public abstract void foo(Double receiver);

    }

    @ExpectError("The export receiver type Integer is not compatible with the library receiver type 'Double' of library 'InvalidDefaultReceiverTypeLibrary'. ")
    @ExportLibrary(value = InvalidDefaultReceiverTypeLibrary.class, receiverType = Integer.class)
    public static class InvalidDefaultReceiverType {
        @ExportMessage
        static void foo(Integer receiver) {
        }
    }

    @GenerateLibrary
    public abstract static class AbstractErrorLibrary1 extends Library {
        @Abstract
        public int messageVoid(Object receiver) {
            return 42;
        }
    }

    @ExpectError({"The following message(s) of library AbstractErrorLibrary1 are abstract and should be exported using:%",
                    "Exported library AbstractErrorLibrary1 does not export any messages and therefore has no effect. Remove the export declaration to resolve this."
    })
    @ExportLibrary(AbstractErrorLibrary1.class)
    public static class AbstractErrorTest1 {
    }

    @GenerateLibrary
    public abstract static class AbstractErrorLibrary2 extends Library {

        public boolean isType(Object receiver) {
            return false;
        }

        @Abstract(ifExported = "isType")
        public Object asType(Object receiver) {
            return receiver;
        }

    }

    // should compile no abstract methods
    @ExpectError("Exported library AbstractErrorLibrary2 does not export any messages and therefore has no effect. Remove the export declaration to resolve this.")
    @ExportLibrary(AbstractErrorLibrary2.class)
    public static class AbstractErrorTest2 {
    }

    // should now have an abstract error message
    @ExpectError("The following message(s) of library AbstractErrorLibrary2 are abstract and should be exported usin%")
    @ExportLibrary(AbstractErrorLibrary2.class)
    public static class AbstractErrorTest3 {
        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean isType() {
            return false;
        }
    }

    @GenerateLibrary
    public abstract static class AbstractErrorLibrary4 extends Library {

        @Abstract
        public boolean isType(Object receiver) {
            return false;
        }

        @ExpectError("The ifExported condition links to an unknown message 'asdf'. Only valid library messages may be linked.")
        @Abstract(ifExported = "asdf")
        public Object asType(Object receiver) {
            return receiver;
        }

    }

    @GenerateLibrary
    public abstract static class AbstractErrorLibrary5 extends Library {

        @Abstract
        public boolean isType(Object receiver) {
            return false;
        }

        @ExpectError("The ifExported condition links to itself. Remove that condition to resolve this problem.")
        @Abstract(ifExported = "asType")
        public Object asType(Object receiver) {
            return receiver;
        }

    }

    @GenerateLibrary
    @ExpectError("Declared library classes must exactly extend the type com.oracle.truffle.api.library.Library.")
    public abstract static class AbstractErrorLibrary6 extends SampleLibrary {

        @Override
        public String call(Object receiver) {
            return "default";
        }

    }

    // test that final methods are ignored
    @GenerateLibrary
    public abstract static class AbstractErrorLibrary7 extends Library {
        public String call(Object receiver, String arg) {
            return "default";
        }

        @SuppressWarnings("static-method")
        public final String call(Object receiver) {
            return "default";
        }

    }

    // test that private methods are ignored
    @GenerateLibrary
    public abstract static class AbstractErrorLibrary8 extends Library {
        public String call(Object receiver, String arg) {
            return "default";
        }

        @SuppressWarnings("static-method")
        private String call(Object receiver) {
            return "default";
        }

    }

    // test that package-protected duplicate method leads to error
    @GenerateLibrary
    public abstract static class AbstractErrorLibrary9 extends Library {
        @ExpectError("Library message must have a unique name. %")
        public String call(Object receiver, String arg) {
            return "default";
        }

        @ExpectError("Library message must have a unique name. %")
        public String call(Object receiver) {
            return "default";
        }

    }

    // test that protected duplicate method leads to error
    @GenerateLibrary
    public abstract static class AbstractErrorLibrary10 extends Library {
        @ExpectError("Library message must have a unique name. %")
        public String call(Object receiver, String arg) {
            return "default";
        }

        @ExpectError("Library message must have a unique name. %")
        protected String call(Object receiver) {
            return "default";
        }

    }

    // test that protected duplicate method leads to error
    @GenerateLibrary
    public abstract static class AbstractErrorLibrary11 extends Library {

        @SuppressWarnings("static-method")
        @ExpectError("Library messages must be public or protected.%")
        String call(Object receiver) {
            return "default";
        }

    }

    // test that non static inner class must be static
    @GenerateLibrary
    @ExpectError("Declared inner library classes must be static.")
    public abstract class AbstractErrorLibrary12 extends Library {

        @SuppressWarnings("static-method")
        public String call(Object receiver) {
            return "default";
        }

    }

}
