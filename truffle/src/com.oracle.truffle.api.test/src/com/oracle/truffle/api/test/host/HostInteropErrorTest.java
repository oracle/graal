/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.ComparisonFailure;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class HostInteropErrorTest extends ProxyLanguageEnvTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    public static class MyHostObj {
        public int field;
        public final int finalField;

        public MyHostObj(int a) {
            this.finalField = a;
        }

        public void foo(@SuppressWarnings("unused") int a) {
            fail("foo called");
        }

        public void cce(Object human) {
            Value cannotCast = (Value) human;
            cannotCast.invokeMember("hello");
        }

        @Override
        public String toString() {
            return "MyHostObj";
        }
    }

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testHostMethodArityError() throws InteropException {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "foo");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo"), ArityException.class,
                        "Arity error - expected: 1 actual: 0");
        assertFails(() -> INTEROP.execute(foo), ArityException.class,
                        "Arity error - expected: 1 actual: 0");
    }

    @Test
    public void testHostMethodArgumentTypeError() throws InteropException {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "foo");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.execute(foo, env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Invalid or lossy primitive coercion.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");
        assertFails(() -> INTEROP.execute(foo, env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.execute(foo, new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Invalid or lossy primitive coercion.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
        assertFails(() -> INTEROP.execute(foo, new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostConstructorArgumentTypeError() {
        Object hostClass = env.asHostSymbol(MyHostObj.class);

        assertFails(() -> INTEROP.instantiate(hostClass, env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.instantiate(hostClass, env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.instantiate(hostClass, new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.instantiate(hostClass, new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostArrayTypeError() {
        Object hostArray = env.asGuestValue(new int[]{0, 0, 0, 0});

        assertFails(() -> INTEROP.writeArrayElement(hostArray, 0, env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.writeArrayElement(hostArray, 0, env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.writeArrayElement(hostArray, 0, new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.writeArrayElement(hostArray, 0, new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostFieldTypeError() {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        assertFails(() -> INTEROP.writeMember(hostObj, "field", env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.writeMember(hostObj, "field", env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.writeMember(hostObj, "field", new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.writeMember(hostObj, "field", new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostFinalFieldError() {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", 42), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");

        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", env.asGuestValue(null)), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");
        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", env.asGuestValue(Collections.emptyMap())), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");
    }

    @Test
    public void testClassCastExceptionInHostMethod() throws InteropException {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "cce");

        AbstractPolyglotTest.assertFails(() -> INTEROP.invokeMember(hostObj, "cce", 42), RuntimeException.class, (e) -> {
            assertTrue(env.isHostException(e));
        });
        AbstractPolyglotTest.assertFails(() -> INTEROP.execute(foo, 42), RuntimeException.class, (e) -> {
            assertTrue(env.isHostException(e));
        });
    }

    @Test
    public void testIterator() throws StopIterationException, UnsupportedMessageException {
        Collection<Integer> c = Collections.singleton(42);
        Object iterator = env.asGuestValue(c.iterator());
        assertTrue(INTEROP.hasIteratorNextElement(iterator));
        INTEROP.getIteratorNextElement(iterator);
        assertFalse(INTEROP.hasIteratorNextElement(iterator));
        assertFails(() -> INTEROP.getIteratorNextElement(iterator), StopIterationException.class, null);

    }

    @Test
    @SuppressWarnings("serial")
    public void testHostObjectThrowsHostException() {
        List<Object> list = Collections.singletonList(1);
        Value listValue = context.asValue(list);
        assertTrue(listValue.hasMembers());
        AbstractPolyglotTest.assertFails(() -> {
            listValue.setArrayElement(0, 2);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        AbstractPolyglotTest.assertFails(() -> {
            listValue.removeArrayElement(0);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        list = new ArrayList<Object>() {
            @Override
            public Object get(int index) {
                throw new NullPointerException();
            }
        };
        list.add(1);
        Value listValueGet = context.asValue(list);
        assertTrue(listValueGet.hasMembers());
        AbstractPolyglotTest.assertFails(() -> {
            listValueGet.getArrayElement(0);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        list = new ArrayList<Object>() {
            @Override
            public int size() {
                throw new NullPointerException();
            }
        };
        Value listValueSize = context.asValue(list);
        assertTrue(listValueSize.hasMembers());
        AbstractPolyglotTest.assertFails(() -> {
            listValueSize.getArraySize();
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });

        Map.Entry<Object, Object> entry = new AbstractMap.SimpleImmutableEntry<>(1, 1);
        Value entryValue = context.asValue(entry);
        assertTrue(entryValue.hasMembers());
        AbstractPolyglotTest.assertFails(() -> {
            entryValue.setArrayElement(1, 2);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });

        entry = new Map.Entry<Object, Object>() {
            @Override
            public Object getKey() {
                throw new NullPointerException();
            }

            @Override
            public Object getValue() {
                throw new NullPointerException();
            }

            @Override
            public Object setValue(Object value) {
                throw new UnsupportedOperationException();
            }
        };
        Value entryValueGet = context.asValue(entry);
        assertTrue(entryValueGet.hasMembers());
        AbstractPolyglotTest.assertFails(() -> {
            entryValueGet.getArrayElement(0);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        AbstractPolyglotTest.assertFails(() -> {
            entryValueGet.getArrayElement(1);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });

        Iterable<Object> iterable = new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                throw new NullPointerException();
            }
        };
        Value iterableValue = context.asValue(iterable);
        assertTrue(iterableValue.hasIterator());
        AbstractPolyglotTest.assertFails(() -> {
            iterableValue.getIterator();
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        Iterator<Object> iterator = new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                throw new NullPointerException();
            }

            @Override
            public Object next() {
                throw new NullPointerException();
            }
        };
        Value iteratorValue = context.asValue(iterator);
        assertTrue(iteratorValue.isIterator());
        AbstractPolyglotTest.assertFails(() -> {
            iteratorValue.hasIteratorNextElement();
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        AbstractPolyglotTest.assertFails(() -> {
            iteratorValue.getIteratorNextElement();
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });

        Map<Object, Object> map = Collections.singletonMap(1, 1);
        Value mapValue = context.asValue(map);
        assertTrue(mapValue.hasHashEntries());
        AbstractPolyglotTest.assertFails(() -> {
            mapValue.putHashEntry(2, 2);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        AbstractPolyglotTest.assertFails(() -> {
            mapValue.removeHashEntry(1);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        map = new HashMap<Object, Object>() {
            @Override
            public int size() {
                throw new NullPointerException();
            }
        };
        Value mapValueSize = context.asValue(map);
        assertTrue(mapValueSize.hasHashEntries());
        AbstractPolyglotTest.assertFails(() -> {
            mapValueSize.getHashSize();
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        map = new HashMap<Object, Object>() {
            @Override
            public Object getOrDefault(Object key, Object defaultValue) {
                throw new NullPointerException();
            }
        };
        Value mapValueGet = context.asValue(map);
        assertTrue(mapValueGet.hasHashEntries());
        AbstractPolyglotTest.assertFails(() -> {
            mapValueGet.getHashValue(1);
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
        map = new HashMap<Object, Object>() {
            @Override
            public Set<Entry<Object, Object>> entrySet() {
                throw new NullPointerException();
            }
        };
        Value mapValueIterator = context.asValue(map);
        assertTrue(mapValueIterator.hasHashEntries());
        AbstractPolyglotTest.assertFails(() -> {
            mapValueIterator.getHashEntriesIterator();
        }, PolyglotException.class, (pe) -> {
            assertTrue(pe.isHostException());
        });
    }

    @ExportLibrary(InteropLibrary.class)
    class OtherObject implements TruffleObject {
        @ExportMessage
        final boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        final Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "Other";
        }

        @ExportMessage
        final boolean hasMetaObject() {
            return true;
        }

        @ExportMessage
        final Object getMetaObject() {
            return new OtherType();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    class OtherNull implements TruffleObject {
        @ExportMessage
        final boolean isNull() {
            return true;
        }

        @ExportMessage
        final boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        final Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "null";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    class OtherType implements TruffleObject {
        @ExportMessage
        final boolean isMetaObject() {
            return true;
        }

        @ExportMessage(name = "getMetaQualifiedName")
        @ExportMessage(name = "getMetaSimpleName")
        final Object getMetaName() {
            return "OtherType";
        }

        @ExportMessage
        final boolean isMetaInstance(Object instance) {
            return instance instanceof OtherObject;
        }
    }

    @FunctionalInterface
    interface InteropRunnable {
        void run() throws Exception;
    }

    private static void assertFails(InteropRunnable r, Class<?> hostExceptionType, String message) {
        try {
            r.run();
            fail("No error but expected " + hostExceptionType);
        } catch (Exception e) {
            if (!hostExceptionType.isInstance(e)) {
                fail(String.format("Expected %s: \"%s\" but got %s: \"%s\"", hostExceptionType.getName(), message, e.getClass().getName(), e.getMessage()));
            }
            if (message != null && !message.equals(e.getMessage())) {
                ComparisonFailure f = new ComparisonFailure(e.getMessage(), message, e.getMessage());
                f.initCause(e);
                throw f;
            }
        }
    }
}
