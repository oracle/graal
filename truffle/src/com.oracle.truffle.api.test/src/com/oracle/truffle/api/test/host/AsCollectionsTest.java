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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AsCollectionsTest {

    private Context context;
    private Env env;

    @Before
    public void enterContext() {
        context = Context.create();
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
        public boolean validateMap(Map<String, Object> mapGen, Map mapRaw) {
            for (Map map : new Map[]{mapGen, mapRaw}) {
                assertEquals(1, map.size());
                assertEquals("bar", map.get("foo"));
            }
            return true;
        }

        public boolean validateArrayOfMap(Map<String, Object>[] arrayOfMapGen, Map[] arrayOfMapRaw) {
            for (Map[] arrayOfMap : new Map[][]{arrayOfMapGen, arrayOfMapRaw}) {
                assertEquals(1, arrayOfMap.length);
                Map map = arrayOfMap[0];
                assertEquals(1, map.size());
                assertEquals("bar", map.get("foo"));
            }
            return true;
        }

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
        assertEquals(Boolean.TRUE, ForeignAccess.sendInvoke(Message.INVOKE.createNode(), validator, "validateMap", mapTO, mapTO));
    }

    @Test
    public void testInvokeArrayOfMap() throws InteropException {
        TruffleObject validator = asTruffleObject(new Validator());
        MapBasedTO mapTO = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        TruffleObject listOfMapTO = new ListBasedTO(Arrays.asList(mapTO));
        assertEquals(Boolean.TRUE, ForeignAccess.sendInvoke(Message.INVOKE.createNode(), validator, "validateArrayOfMap", listOfMapTO, listOfMapTO));
    }

    @Test
    public void testInvokeListOfMap() throws InteropException {
        TruffleObject validator = asTruffleObject(new Validator());
        MapBasedTO mapTO = new MapBasedTO(Collections.singletonMap("foo", "bar"));
        TruffleObject listOfMapTO = new ListBasedTO(Arrays.asList(mapTO));
        assertEquals(Boolean.TRUE, ForeignAccess.sendInvoke(Message.INVOKE.createNode(), validator, "validateListOfMap", listOfMapTO, listOfMapTO));
    }

    public interface ProxyValidator {
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
        assertEquals(Boolean.TRUE, ForeignAccess.sendInvoke(Message.INVOKE.createNode(), validator, "test", listOfMapTO));
    }

    static final class ListBasedTO implements TruffleObject {

        final List list;

        ListBasedTO(List list) {
            this.list = list;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ListBasedMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof ListBasedTO;
        }

        @MessageResolution(receiverType = ListBasedTO.class)
        static class ListBasedMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class ListBasedHasSizeNode extends Node {

                @SuppressWarnings("unused")
                public Object access(ListBasedTO lbto) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class ListBasedGetSizeNode extends Node {

                public Object access(ListBasedTO lbto) {
                    return lbto.list.size();
                }
            }

            @Resolve(message = "READ")
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

            @Resolve(message = "WRITE")
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
        public ForeignAccess getForeignAccess() {
            return MapBasedMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof MapBasedTO;
        }

        @MessageResolution(receiverType = MapBasedTO.class)
        static class MapBasedMessageResolution {

            @Resolve(message = "HAS_KEYS")
            abstract static class MapBasedHasKeysNode extends Node {

                @SuppressWarnings("unused")
                public Object access(MapBasedTO mbto) {
                    return true;
                }
            }

            @Resolve(message = "KEYS")
            abstract static class MapBasedKeysNode extends Node {

                public Object access(MapBasedTO mbto) {
                    return new MapKeysTO(mbto.map.keySet());
                }
            }

            @Resolve(message = "READ")
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

            @Resolve(message = "WRITE")
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
        public ForeignAccess getForeignAccess() {
            return MapKeysMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof MapKeysTO;
        }

        @MessageResolution(receiverType = MapKeysTO.class)
        static class MapKeysMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class MapKeysHasSizeNode extends Node {

                @SuppressWarnings("unused")
                public Object access(MapKeysTO lbto) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class MapKeysGetSizeNode extends Node {

                public Object access(MapKeysTO mkto) {
                    return mkto.keys.size();
                }
            }

            @Resolve(message = "READ")
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
