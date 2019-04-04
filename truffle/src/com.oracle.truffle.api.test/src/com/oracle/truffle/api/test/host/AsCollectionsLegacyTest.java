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
package com.oracle.truffle.api.test.host;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.polyglot.HostAccess;

@SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
public class AsCollectionsLegacyTest {

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
        assertTrue(HostInteropTest.isArray(to));
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
        assertFalse(HostInteropTest.isArray(to));
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

        TruffleObject badMapObject = new HostInteropTest.HasKeysObject(false);
        try {
            interopMap = asJavaObject(Map.class, badMapObject);
            fail();
        } catch (Exception ex) {
            assertThat(ex, instanceOf(ClassCastException.class));
        }
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
        assertEquals(Boolean.TRUE, com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), validator, "validateMap", mapTO, mapTO));
    }

    @Test
    public void testInvokeArrayOfMap() throws InteropException {
        TruffleObject validator = asTruffleObject(new Validator());
        MapBasedTO mapTO = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        TruffleObject listOfMapTO = new ListBasedTO(Arrays.asList(mapTO));
        assertEquals(Boolean.TRUE,
                        com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), validator, "validateArrayOfMap", listOfMapTO, listOfMapTO));
    }

    @Test
    public void testInvokeListOfMap() throws InteropException {
        TruffleObject validator = asTruffleObject(new Validator());
        MapBasedTO mapTO = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        TruffleObject listOfMapTO = new ListBasedTO(Arrays.asList(mapTO));
        assertEquals(Boolean.TRUE,
                        com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), validator, "validateListOfMap", listOfMapTO, listOfMapTO));
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
        assertEquals(Boolean.TRUE, com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), validator, "test", listOfMapTO));
    }

    static final class ListBasedTO implements TruffleObject {

        final List list;

        ListBasedTO(List list) {
            this.list = list;
        }

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return ListBasedMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof ListBasedTO;
        }

        @com.oracle.truffle.api.interop.MessageResolution(receiverType = ListBasedTO.class)
        static class ListBasedMessageResolution {

            @com.oracle.truffle.api.interop.Resolve(message = "HAS_SIZE")
            abstract static class ListBasedHasSizeNode extends Node {

                @SuppressWarnings("unused")
                public Object access(ListBasedTO lbto) {
                    return true;
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "GET_SIZE")
            abstract static class ListBasedGetSizeNode extends Node {

                public Object access(ListBasedTO lbto) {
                    return lbto.list.size();
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "READ")
            abstract static class ListBasedReadNode extends Node {

                @TruffleBoundary
                public Object access(ListBasedTO lbto, int index) {
                    try {
                        return lbto.list.get(index);
                    } catch (IndexOutOfBoundsException ioob) {
                        throw UnknownIdentifierException.raise(Integer.toString(index));
                    }
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "WRITE")
            abstract static class ListBasedWriteNode extends Node {

                @TruffleBoundary
                public Object access(ListBasedTO lbto, int index, Object value) {
                    try {
                        lbto.list.set(index, value);
                        return value;
                    } catch (IndexOutOfBoundsException ioob) {
                        throw UnknownIdentifierException.raise(Integer.toString(index));
                    }
                }
            }
        }
    }

    static final class MapBasedTO implements TruffleObject {

        final Map map;

        MapBasedTO(Map map) {
            this.map = map;
        }

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return MapBasedMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof MapBasedTO;
        }

        @com.oracle.truffle.api.interop.MessageResolution(receiverType = MapBasedTO.class)
        static class MapBasedMessageResolution {

            @com.oracle.truffle.api.interop.Resolve(message = "HAS_KEYS")
            abstract static class MapBasedHasKeysNode extends Node {

                @SuppressWarnings("unused")
                public Object access(MapBasedTO mbto) {
                    return true;
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "KEYS")
            abstract static class MapBasedKeysNode extends Node {

                public Object access(MapBasedTO mbto) {
                    return new MapKeysTO(mbto.map.keySet());
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "READ")
            abstract static class MapBasedReadNode extends Node {

                @TruffleBoundary
                public Object access(MapBasedTO mbto, String name) {
                    Object value = mbto.map.get(name);
                    if (value == null) {
                        throw UnknownIdentifierException.raise(name);
                    } else {
                        return value;
                    }
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "KEY_INFO")
            abstract static class KeyInfoNode extends Node {

                @TruffleBoundary
                public Object access(MapBasedTO mbto, String name) {
                    if (mbto.map.containsKey(name)) {
                        return com.oracle.truffle.api.interop.KeyInfo.MODIFIABLE | com.oracle.truffle.api.interop.KeyInfo.READABLE;
                    } else {
                        return com.oracle.truffle.api.interop.KeyInfo.INSERTABLE;
                    }
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "WRITE")
            abstract static class MapBasedWriteNode extends Node {

                @TruffleBoundary
                public Object access(MapBasedTO mbto, String name, Object value) {
                    mbto.map.put(name, value);
                    return value;
                }
            }

        }
    }

    static final class MapKeysTO implements TruffleObject {

        private final List keys;

        private MapKeysTO(Set keys) {
            this.keys = new ArrayList(keys);
        }

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return MapKeysMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof MapKeysTO;
        }

        @com.oracle.truffle.api.interop.MessageResolution(receiverType = MapKeysTO.class)
        static class MapKeysMessageResolution {

            @com.oracle.truffle.api.interop.Resolve(message = "HAS_SIZE")
            abstract static class MapKeysHasSizeNode extends Node {

                @SuppressWarnings("unused")
                public Object access(MapKeysTO lbto) {
                    return true;
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "GET_SIZE")
            abstract static class MapKeysGetSizeNode extends Node {

                public Object access(MapKeysTO mkto) {
                    return mkto.keys.size();
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "READ")
            abstract static class MapKeysReadNode extends Node {

                @TruffleBoundary
                public Object access(MapKeysTO mkto, int index) {
                    try {
                        return mkto.keys.get(index);
                    } catch (IndexOutOfBoundsException ioob) {
                        throw UnknownIdentifierException.raise(Integer.toString(index));
                    }
                }
            }
        }
    }
}
