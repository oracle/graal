package com.oracle.truffle.api.test.polyglot;

import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.ARRAY_ELEMENTS;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.BOOLEAN;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.EXECUTABLE;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.HOST_OBJECT;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.INSTANTIABLE;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.MEMBERS;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NATIVE;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NULL;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NUMBER;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;

public class ValueAssert {

    private static final TypeLiteral<List<Object>> OBJECT_LIST = new TypeLiteral<List<Object>>() {
    };
    private static final TypeLiteral<Map<Object, Object>> OBJECT_OBJECT_MAP = new TypeLiteral<Map<Object, Object>>() {
    };
    private static final TypeLiteral<Map<String, Object>> STRING_OBJECT_MAP = new TypeLiteral<Map<String, Object>>() {
    };
    private static final TypeLiteral<Map<Long, Object>> LONG_OBJECT_MAP = new TypeLiteral<Map<Long, Object>>() {
    };
    private static final TypeLiteral<Map<Integer, Object>> INTEGER_OBJECT_MAP = new TypeLiteral<Map<Integer, Object>>() {
    };
    private static final TypeLiteral<Map<Short, Object>> SHORT_OBJECT_MAP = new TypeLiteral<Map<Short, Object>>() {
    };
    private static final TypeLiteral<Map<Byte, Object>> BYTE_OBJECT_MAP = new TypeLiteral<Map<Byte, Object>>() {
    };
    private static final TypeLiteral<Map<Number, Object>> NUMBER_OBJECT_MAP = new TypeLiteral<Map<Number, Object>>() {
    };
    private static final TypeLiteral<Map<Float, Object>> FLOAT_OBJECT_MAP = new TypeLiteral<Map<Float, Object>>() {
    };
    private static final TypeLiteral<Map<Double, Object>> DOUBLE_OBJECT_MAP = new TypeLiteral<Map<Double, Object>>() {
    };
    private static final TypeLiteral<Map<CharSequence, Object>> CHAR_SEQUENCE_OBJECT_MAP = new TypeLiteral<Map<CharSequence, Object>>() {
    };

    private static final TypeLiteral<?>[] NEVER_SUPPORTED_MAPS = new TypeLiteral[]{
                    BYTE_OBJECT_MAP, SHORT_OBJECT_MAP, FLOAT_OBJECT_MAP,
                    DOUBLE_OBJECT_MAP,
                    CHAR_SEQUENCE_OBJECT_MAP
    };

    public static void assertValue(Context context, Value value) {
        assertValue(context, value, null, detectSupportedTypes(value));
    }

    public static void assertValue(Context context, Value value, Object[] arguments, Trait... expectedTypes) {
        assertNotNull(value.toString());
        assertNotNull(value.getMetaObject());
        assertHostObject(context, value.as(Object.class));

        assertSame(value, value.as(Value.class));

        Set<Trait> supported = new HashSet<>();
        for (Trait supportedType : expectedTypes) {
            supported.add(supportedType);
            switch (supportedType) {
                case NULL:
                    assertTrue(value.isNull());
                    break;
                case BOOLEAN:
                    assertTrue(value.isBoolean());
                    boolean booleanValue = value.asBoolean();
                    assertEquals(booleanValue, value.as(Boolean.class));
                    assertEquals(booleanValue, value.as(boolean.class));
                    break;
                case STRING:
                    assertTrue(value.isString());
                    String stringValue = value.asString();
                    assertEquals(stringValue, value.as(String.class));
                    if (stringValue.length() == 1) {
                        assertEquals(stringValue.charAt(0), (char) value.as(Character.class));
                        assertEquals(stringValue.charAt(0), (char) value.as(char.class));
                    }
                    break;
                case NUMBER:
                    assertValueNumber(value);
                    break;
                case ARRAY_ELEMENTS:
                    assertValueArrayElements(context, value);
                    break;
                case EXECUTABLE:
                    assertTrue(value.canExecute());
                    assertFunctionalInterfaceMapping(context, value, arguments);
                    if (arguments != null) {
                        Value result = value.execute(arguments);
                        assertValue(context, result);
                    }
                    break;
                case INSTANTIABLE:
                    assertTrue(value.canInstantiate());
                    value.as(Function.class);
                    if (arguments != null) {
                        Value result = value.newInstance(arguments);
                        assertValue(context, result, null);
                    }
                    // otherwise its ambiguous with the executable semantics.
                    if (!value.canExecute()) {
                        assertFunctionalInterfaceMapping(context, value, arguments);
                    }
                    break;

                case HOST_OBJECT:
                    assertTrue(value.isHostObject());
                    Object hostObject = value.asHostObject();
                    assertTrue(!(hostObject instanceof Proxy));
                    // TODO assert mapping to interfaces
                    break;
                case MEMBERS:
                    assertTrue(value.hasMembers());

                    for (String key : value.getMemberKeys()) {
                        assertValue(context, value.getMember(key));
                    }

                    // TODO virify setting and getting
                    break;
                case NATIVE:
                    assertTrue(value.isNativePointer());
                    value.asNativePointer();
                    break;

            }
        }

        for (Trait unsupportedType : Trait.values()) {
            if (supported.contains(unsupportedType)) {
                continue;
            }

            switch (unsupportedType) {
                case NUMBER:
                    assertFalse(value.isNumber());
                    assertFalse(value.fitsInByte());
                    assertFalse(value.fitsInShort());
                    assertFalse(value.fitsInInt());
                    assertFalse(value.fitsInLong());
                    assertFalse(value.fitsInFloat());
                    assertFalse(value.fitsInDouble());

                    assertFails(() -> value.asByte(), ClassCastException.class);
                    assertFails(() -> value.asShort(), ClassCastException.class);
                    assertFails(() -> value.asInt(), ClassCastException.class);
                    assertFails(() -> value.asLong(), ClassCastException.class);
                    assertFails(() -> value.asFloat(), ClassCastException.class);
                    assertFails(() -> value.asDouble(), ClassCastException.class);

                    assertFails(() -> value.as(Number.class), ClassCastException.class);
                    assertFails(() -> value.as(byte.class), ClassCastException.class);
                    assertFails(() -> value.as(Byte.class), ClassCastException.class);
                    assertFails(() -> value.as(short.class), ClassCastException.class);
                    assertFails(() -> value.as(Short.class), ClassCastException.class);
                    assertFails(() -> value.as(int.class), ClassCastException.class);
                    assertFails(() -> value.as(Integer.class), ClassCastException.class);
                    assertFails(() -> value.as(long.class), ClassCastException.class);
                    assertFails(() -> value.as(Long.class), ClassCastException.class);
                    assertFails(() -> value.as(float.class), ClassCastException.class);
                    assertFails(() -> value.as(Float.class), ClassCastException.class);
                    break;
                case BOOLEAN:
                    assertFalse(value.isBoolean());
                    assertFails(() -> value.asBoolean(), ClassCastException.class);
                    assertFails(() -> value.as(boolean.class), ClassCastException.class);
                    assertFails(() -> value.as(Boolean.class), ClassCastException.class);
                    break;
                case STRING:
                    assertFalse(value.isString());
                    assertFails(() -> value.asString(), ClassCastException.class);
                    assertFails(() -> value.as(String.class), ClassCastException.class);
                    assertFails(() -> value.as(Character.class), ClassCastException.class);
                    break;
                case MEMBERS:
                    assertFalse(value.hasMembers());
                    assertFails(() -> value.hasMember("asdf"), UnsupportedOperationException.class);
                    assertFails(() -> value.getMember("asdf"), UnsupportedOperationException.class);
                    assertFails(() -> value.putMember("", ""), UnsupportedOperationException.class);
                    assertFails(() -> value.getMemberKeys(), UnsupportedOperationException.class);
                    break;
                case EXECUTABLE:
                    assertFalse(value.canExecute());
                    assertFails(() -> value.execute(), UnsupportedOperationException.class);
                    if (!value.canInstantiate()) {
                        assertFails(() -> value.as(Function.class), ClassCastException.class);
                        assertFails(() -> value.as(IsFunctionalInterfaceVarArgs.class), ClassCastException.class);
                    }
                    break;
                case INSTANTIABLE:
                    assertFalse(value.canInstantiate());
                    assertFails(() -> value.newInstance(), UnsupportedOperationException.class);
                    if (!value.canExecute()) {
                        assertFails(() -> value.as(Function.class), ClassCastException.class);
                        assertFails(() -> value.as(IsFunctionalInterfaceVarArgs.class), ClassCastException.class);
                    }
                    break;
                case NULL:
                    assertFalse(value.isNull());
                    break;
                case ARRAY_ELEMENTS:
                    assertFalse(value.hasArrayElements());
                    assertFails(() -> value.getArrayElement(0), UnsupportedOperationException.class);
                    assertFails(() -> value.setArrayElement(0, null), UnsupportedOperationException.class);
                    assertFails(() -> value.getArraySize(), UnsupportedOperationException.class);
                    break;
                case HOST_OBJECT:
                    assertFalse(value.isHostObject());
                    assertFails(() -> value.asHostObject(), ClassCastException.class);
                    break;
                case NATIVE:
                    assertFalse(value.isNativePointer());
                    assertFails(() -> value.asNativePointer(), ClassCastException.class);
                    break;

            }

        }

        if ((!value.isHostObject() || !(value.asHostObject() instanceof Map)) && !value.isNull()) {
            for (TypeLiteral<?> literal : NEVER_SUPPORTED_MAPS) {
                try {
                    value.as(literal);
                    fail(literal.toString());
                } catch (ClassCastException e) {
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertValueArrayElements(Context context, Value value) {
        assertTrue(value.hasArrayElements());

        List<Object> receivedObjects = new ArrayList<>();
        Map<Long, Object> receivedObjectsLongMap = new HashMap<>();
        Map<Integer, Object> receivedObjectsIntMap = new HashMap<>();
        for (long i = 0l; i < value.getArraySize(); i++) {
            Value arrayElement = value.getArrayElement(i);
            receivedObjects.add(arrayElement.as(Object.class));
            receivedObjectsLongMap.put(i, arrayElement.as(Object.class));
            receivedObjectsIntMap.put((int) i, arrayElement.as(Object.class));
            assertValue(context, arrayElement, detectSupportedTypes(arrayElement));
        }

        List<Object> objectList1 = value.as(OBJECT_LIST);
        List<Object> objectList2 = Arrays.asList(value.as(Object[].class));
        Map<Object, Object> objectMap1 = value.as(OBJECT_OBJECT_MAP);
        Map<Long, Object> objectMap2 = value.as(LONG_OBJECT_MAP);
        Map<Integer, Object> objectMap3 = value.as(INTEGER_OBJECT_MAP);
        Map<Number, Object> objectMap4 = value.as(NUMBER_OBJECT_MAP);
        Map<Object, Object> objectMap5 = (Map<Object, Object>) value.as(Object.class);

        assertEquals(receivedObjectsLongMap, objectMap1);
        assertEquals(receivedObjectsLongMap, objectMap2);
        assertEquals(receivedObjectsLongMap, objectMap4);
        assertEquals(receivedObjectsLongMap, objectMap5);
        assertEquals(receivedObjectsIntMap, objectMap3);

        assertEquals(receivedObjects, objectList1);
        assertEquals(receivedObjects, objectList2);

        // write them all
        for (long i = 0l; i < value.getArraySize(); i++) {
            value.setArrayElement(i, value.getArrayElement(i));
        }

        for (int i = 0; i < value.getArraySize(); i++) {
            objectList1.set(i, receivedObjects.get(i));
            objectList2.set(i, receivedObjects.get(i));
        }
    }

    private static void assertFails(Runnable runnable, Class<? extends Throwable> exceptionType) {
        assertFails(() -> {
            runnable.run();
            return null;
        }, exceptionType);
    }

    private static void assertFails(Callable<?> callable, Class<? extends Throwable> exceptionType) {
        try {
            callable.call();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                throw new AssertionError("expected instanceof " + exceptionType + " was " + t.getClass(), t);
            }
        }
    }

    private static void assertValueNumber(Value value) {
        assertTrue(value.isNumber());

        if (value.fitsInByte()) {
            value.asByte();
        } else {
            assertFails(() -> value.asByte(), ClassCastException.class);
        }
        if (value.fitsInShort()) {
            value.asShort();
            assertTrue(value.fitsInByte());
        } else {
            assertFails(() -> value.asShort(), ClassCastException.class);
        }

        if (value.fitsInInt()) {
            value.asInt();
            assertTrue(value.fitsInByte());
            assertTrue(value.fitsInShort());
        } else {
            assertFails(() -> value.asInt(), ClassCastException.class);
        }

        if (value.fitsInLong()) {
            value.asLong();
            assertTrue(value.fitsInByte());
            assertTrue(value.fitsInShort());
            assertTrue(value.fitsInInt());
        } else {
            assertFails(() -> value.asLong(), ClassCastException.class);
        }

        if (value.fitsInFloat()) {
            value.asFloat();
        } else {
            assertFails(() -> value.asFloat(), ClassCastException.class);
        }

        if (value.fitsInDouble()) {
            value.asDouble();
            assertTrue(value.fitsInFloat());
        } else {
            assertFails(() -> value.asDouble(), ClassCastException.class);
        }
    }

    interface EmptyInterface {

    }

    interface NonFunctionalInterface {
        void foobarbaz();

    }

    @FunctionalInterface
    interface IsFunctionalInterfaceVarArgs {
        Object foobarbaz(Object... args);
    }

    @SuppressWarnings("unchecked")
    private static void assertFunctionalInterfaceMapping(Context context, Value value, Object[] arguments) {
        Function<Object, Object> f1 = (Function<Object, Object>) value.as(Object.class);
        Function<Object, Object[]> f2 = value.as(Function.class);
        IsFunctionalInterfaceVarArgs f3 = value.as(IsFunctionalInterfaceVarArgs.class);

        if (!value.hasMembers()) {
            assertFails(() -> value.as(EmptyInterface.class), ClassCastException.class);
            assertFails(() -> value.as(NonFunctionalInterface.class), ClassCastException.class);
        }

        if (arguments != null) {
            assertHostObject(context, f1.apply(arguments));
            assertHostObject(context, f2.apply(arguments));
            assertHostObject(context, f3.foobarbaz(arguments));
        }
    }

    private static void assertHostObject(Context context, Object value) {
        // TODO
    }

    private static Trait[] detectSupportedTypes(Value value) {
        List<Trait> valueTypes = new ArrayList<>();
        if (value.isBoolean()) {
            valueTypes.add(BOOLEAN);
        }
        if (value.isHostObject()) {
            valueTypes.add(HOST_OBJECT);
        }
        if (value.isNativePointer()) {
            valueTypes.add(NATIVE);
        }
        if (value.isString()) {
            valueTypes.add(STRING);
        }
        if (value.isNumber()) {
            valueTypes.add(NUMBER);
        }
        if (value.hasMembers()) {
            valueTypes.add(MEMBERS);
        }
        if (value.hasArrayElements()) {
            valueTypes.add(ARRAY_ELEMENTS);
        }
        if (value.canInstantiate()) {
            valueTypes.add(INSTANTIABLE);
        }
        if (value.canExecute()) {
            valueTypes.add(EXECUTABLE);
        }
        if (value.isNull()) {
            valueTypes.add(NULL);
        }
        return valueTypes.toArray(new Trait[0]);
    }

    public enum Trait {

        NULL,
        HOST_OBJECT,
        NUMBER,
        STRING,
        BOOLEAN,
        NATIVE,
        EXECUTABLE,
        INSTANTIABLE,
        MEMBERS,
        ARRAY_ELEMENTS

    }

}
