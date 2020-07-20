/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AsCollectionsTest {
    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private Context context;
    private Env env;

    @Before
    public void enterContext() {
        context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(Env contextEnv) {
                env = contextEnv;
                return super.createContext(contextEnv);
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                if (object instanceof ListBasedTO) {
                    return true;
                } else if (object instanceof MapBasedTO) {
                    return true;
                }
                return super.isObjectOfLanguage(object);
            }

            @Override
            protected String toString(LanguageContext c, Object value) {
                if (value instanceof ListBasedTO) {
                    return ((ListBasedTO) value).list.toString();
                } else if (value instanceof MapBasedTO) {
                    return ((MapBasedTO) value).map.toString();
                }
                return super.toString(c, value);
            }
        });
        context.initialize(ProxyLanguage.ID);
        context.enter();
    }

    @After
    public void leaveContext() {
        context.leave();
        context.close();
    }

    protected <T> T asJavaObject(Class<T> type, TruffleObject truffleObject) {
        return context.asValue(truffleObject).as(type);
    }

    protected TruffleObject asTruffleObject(Object javaObj) {
        return (TruffleObject) env.asGuestValue(javaObj);
    }

    @Test
    public void testAsList() {
        List origList = Arrays.asList(new String[]{"a", "b", "c"});
        TruffleObject to = new ListBasedTO(origList);
        assertTrue(INTEROP.hasArrayElements(to));
        List interopList = asJavaObject(List.class, to);
        assertEquals(origList.size(), interopList.size());
        assertEquals(origList.toString(), new ArrayList<>(interopList).toString());
        assertEquals(origList.toString(), interopList.toString());
        // Test get out of bounds
        try {
            interopList.get(1000);
            fail();
        } catch (IndexOutOfBoundsException ioobex) {
            // O.K.
        }
        // Test set out of bounds
        try {
            interopList.set(1000, "1000");
            fail();
        } catch (IndexOutOfBoundsException ioobex) {
            // O.K.
        }
        Object old = interopList.set(1, "bbb");
        assertEquals("b", old);
        assertEquals("bbb", interopList.get(1));
    }

    @Test
    public void testAsMap() {
        Map<String, String> origMap = new LinkedHashMap<>();
        for (int i = 10; i <= 100; i += 10) {
            origMap.put(Integer.toString(i), Integer.toHexString(i));
        }
        TruffleObject to = new MapBasedTO(origMap);
        assertFalse(INTEROP.hasArrayElements(to));
        Map interopMap = asJavaObject(Map.class, to);
        assertEquals(origMap.size(), interopMap.size());
        assertEquals(origMap.toString(), new LinkedHashMap<>(interopMap).toString());
        assertEquals(origMap.toString(), interopMap.toString());
        assertNull(interopMap.get("unknown"));
        Object old = interopMap.put("10", "101010");
        assertEquals("a", old);
        assertEquals("101010", interopMap.get("10"));
        old = interopMap.put("new", "news");
        assertNull(old);
        assertEquals("news", interopMap.get("new"));

    }

    @Test
    public void testAsJavaObjectMapArray() {
        MapBasedTO to = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        MapBasedTO[] array = new MapBasedTO[]{to};
        Map<?, ?>[] result = asJavaObject(Map[].class, asTruffleObject(array));
        assertEquals(1, result.length);
        assertEquals(1, result[0].size());
        assertEquals("bar", result[0].get("foo"));
    }

    @Test
    public void testAsJavaObjectListArray() {
        ListBasedTO to = new ListBasedTO(Collections.singletonList("bar"));
        ListBasedTO[] array = new ListBasedTO[]{to};
        List<?>[] result = asJavaObject(List[].class, asTruffleObject(array));
        assertEquals(1, result.length);
        assertEquals(1, result[0].size());
        assertEquals("bar", result[0].get(0));
    }

    public static class Validator {
        @HostAccess.Export
        public boolean validateMap(Map<String, Object> mapGen, Map mapRaw) {
            for (Map map : new Map[]{mapGen, mapRaw}) {
                assertEquals(1, map.size());
                assertEquals("bar", map.get("foo"));
            }
            return true;
        }

        @HostAccess.Export
        public boolean validateArrayOfMap(Map<String, Object>[] arrayOfMapGen, Map[] arrayOfMapRaw) {
            for (Map[] arrayOfMap : new Map[][]{arrayOfMapGen, arrayOfMapRaw}) {
                assertEquals(1, arrayOfMap.length);
                Map map = arrayOfMap[0];
                assertEquals(1, map.size());
                assertEquals("bar", map.get("foo"));
            }
            return true;
        }

        @HostAccess.Export
        public boolean validateListOfMap(List<Map<String, Object>> listOfMapGen, List<Map> listOfMapRaw) {
            for (List<Map> listOfMap : new List[]{listOfMapGen, listOfMapRaw}) {
                assertEquals(1, listOfMap.size());
                Map map = listOfMap.get(0);
                assertEquals(1, map.size());
                assertEquals("bar", map.get("foo"));
            }
            return true;
        }
    }

    @Test
    public void testInvokeMap() throws InteropException {
        TruffleObject validator = asTruffleObject(new Validator());
        MapBasedTO mapTO = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        assertEquals(Boolean.TRUE, INTEROP.invokeMember(validator, "validateMap", mapTO, mapTO));
    }

    @Test
    public void testInvokeArrayOfMap() throws InteropException {
        TruffleObject validator = asTruffleObject(new Validator());
        MapBasedTO mapTO = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        TruffleObject listOfMapTO = new ListBasedTO(Arrays.asList(mapTO));
        assertEquals(Boolean.TRUE, INTEROP.invokeMember(validator, "validateArrayOfMap", listOfMapTO, listOfMapTO));
    }

    @Test
    public void testInvokeListOfMap() throws InteropException {
        TruffleObject validator = asTruffleObject(new Validator());
        MapBasedTO mapTO = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        TruffleObject listOfMapTO = new ListBasedTO(Arrays.asList(mapTO));
        assertEquals(Boolean.TRUE, INTEROP.invokeMember(validator, "validateListOfMap", listOfMapTO, listOfMapTO));
    }

    public interface ProxyValidator {
        @HostAccess.Export
        boolean test(List<Map> list);
    }

    @Test
    public void testInvokeListOfMapProxy() throws InteropException {
        TruffleObject validator = asTruffleObject(
                        Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{ProxyValidator.class}, (p, method, args) -> {
                            List<Map> listOfMap = (List<Map>) args[0];
                            assertEquals(1, listOfMap.size());
                            Map map = listOfMap.get(0);
                            assertEquals(1, map.size());
                            assertEquals("bar", map.get("foo"));
                            return true;
                        }));
        MapBasedTO mapTO = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        TruffleObject listOfMapTO = new ListBasedTO(Arrays.asList(mapTO));
        assertEquals(Boolean.TRUE, INTEROP.invokeMember(validator, "test", listOfMapTO));
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static final class ListBasedTO implements TruffleObject {

        final List list;

        ListBasedTO(List list) {
            this.list = list;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            try {
                return list.get((int) index);
            } catch (IndexOutOfBoundsException ioob) {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        @TruffleBoundary
        Object writeArrayElement(long index, Object value) throws UnsupportedMessageException, InvalidArrayIndexException {
            try {
                list.set((int) index, value);
                return value;
            } catch (IndexOutOfBoundsException ioob) {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            return list.size();
        }

        @ExportMessage(name = "isArrayElementReadable")
        @ExportMessage(name = "isArrayElementModifiable")
        @ExportMessage(name = "isArrayElementInsertable")
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }

    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportLibrary(InteropLibrary.class)
    static final class MapBasedTO implements TruffleObject {

        final Map map;

        MapBasedTO(Map map) {
            this.map = map;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return new MapKeysTO(map.keySet().toArray());
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            Object value = map.get(member);
            if (value == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                return value;
            }
        }

        @ExportMessage
        @TruffleBoundary
        void writeMember(String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException {
            map.put(member, value);
        }

        @ExportMessage(name = "isMemberModifiable")
        @ExportMessage(name = "isMemberReadable")
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return member.contains(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberInsertable(String member) {
            return !member.contains(member);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class MapKeysTO implements TruffleObject {

        private final Object[] keys;

        MapKeysTO(Object[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            try {
                return keys[(int) index];
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
        }
    }
}
