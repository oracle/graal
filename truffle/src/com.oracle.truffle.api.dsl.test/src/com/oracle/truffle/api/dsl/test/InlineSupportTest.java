/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import org.junit.Test;

import com.oracle.truffle.api.dsl.DSLSupport.SpecializationDataNode;
import com.oracle.truffle.api.dsl.InlineSupport.BooleanField;
import com.oracle.truffle.api.dsl.InlineSupport.ByteField;
import com.oracle.truffle.api.dsl.InlineSupport.CharField;
import com.oracle.truffle.api.dsl.InlineSupport.DoubleField;
import com.oracle.truffle.api.dsl.InlineSupport.FloatField;
import com.oracle.truffle.api.dsl.InlineSupport.InlinableField;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.IntField;
import com.oracle.truffle.api.dsl.InlineSupport.LongField;
import com.oracle.truffle.api.dsl.InlineSupport.ReferenceField;
import com.oracle.truffle.api.dsl.InlineSupport.ShortField;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.nodes.Node;

public class InlineSupportTest {

    static final StateField STATE0 = StateField.create(TestNode.lookup(), "state0");
    static final StateField STATE1 = StateField.create(TestNode.lookup(), "state1");

    private static final StateField stateField = StateField.create(TestNode.lookup(), "state");

    private static final BooleanField booleanField = BooleanField.create(TestNode.lookup(), "bool");
    private static final ByteField byteField = ByteField.create(TestNode.lookup(), "b");
    private static final CharField charField = CharField.create(TestNode.lookup(), "c");
    private static final ShortField shortField = ShortField.create(TestNode.lookup(), "s");
    private static final IntField intField = IntField.create(TestNode.lookup(), "i");
    private static final FloatField floatField = FloatField.create(TestNode.lookup(), "f");
    private static final LongField longField = LongField.create(TestNode.lookup(), "l");
    private static final DoubleField doubleField = DoubleField.create(TestNode.lookup(), "d");

    private static final ReferenceField<Object> referenceObjectField = ReferenceField.create(TestNode.lookup(), "refObject", Object.class);
    private static final ReferenceField<String> referenceStringField = ReferenceField.create(TestNode.lookup(), "refString", String.class);

    static final class TestNode extends Node {
        static final int FIELD_COUNT = 11;

        static final Object OBJECT_VALUE_1 = new Object();
        static final String STRING_VALUE_1 = "42";

        static final Object OBJECT_VALUE_2 = new Object();
        static final String STRING_VALUE_2 = "42";

        int state0;
        int state1;

        final int finalState = 0;

        double invalidType;

        int state = 42;
        boolean bool = true;
        byte b = 42;
        char c = 42;
        short s = 42;
        int i = 42;
        float f = 42.0F;
        long l = 42L;
        double d = 42.0D;

        Object refObject = OBJECT_VALUE_1;
        String refString = STRING_VALUE_1;

        static Lookup lookup() {
            return MethodHandles.lookup();
        }

    }

    @Test
    public void testStateErrors() {
        assertFails(() -> STATE0.set(null, 0xFFFF_FFFF), NullPointerException.class);
        assertFails(() -> STATE0.set(new Node() {
        }, 0xFFFF_FFFF), ClassCastException.class);

        assertFails(() -> STATE0.get(null), NullPointerException.class);
        assertFails(() -> STATE0.get(new Node() {
        }), ClassCastException.class);

        assertFails(() -> StateField.create(TestNode.lookup(), null), NullPointerException.class);
        assertFails(() -> StateField.create(null, ""), NullPointerException.class);
        assertFails(() -> StateField.create(TestNode.lookup(), "doesNotExist"), IllegalArgumentException.class);
        assertFails(() -> StateField.create(TestNode.lookup(), "invalidType"), IllegalArgumentException.class);
    }

    @Test
    public void testStateSet() {
        TestNode node = new TestNode();
        STATE0.set(node, 0xFFFF_FFFF);
        assertEquals(0xFFFF_FFFF, node.state0);

        node = new TestNode();
        StateField subUpdater = STATE0.subUpdater(2, 30);
        subUpdater.set(node, 0x3FFF_FFFF);
        assertEquals(0xFFFF_FFFF & ~0b11, node.state0);

        node = new TestNode();
        StateField subSubUpdater = subUpdater.subUpdater(1, 29);
        subSubUpdater.set(node, 0x1FFF_FFFF);
        assertEquals(0xFFFF_FFFF & ~0b111, node.state0);
    }

    @Test
    public void testStateGet() {
        TestNode node = new TestNode();
        node.state0 = 0xFFFF_FFFF;
        assertEquals(0xFFFF_FFFF, STATE0.get(node));

        node = new TestNode();
        node.state0 = 0xFFFF_FFFF;
        StateField subUpdater = STATE0.subUpdater(2, 30);
        assertEquals(0xFFFF_FFFF >>> 2, subUpdater.get(node));

        node = new TestNode();
        node.state0 = 0xFFFF_FFFF;
        StateField subSubUpdater = subUpdater.subUpdater(1, 29);
        assertEquals(0xFFFF_FFFF >>> 3, subSubUpdater.get(node));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFieldsAccess() {
        TestNode node = new TestNode();
        assertTrue(stateField.get(node) == 42);
        assertTrue(booleanField.get(node) == true);
        assertTrue(byteField.get(node) == 42);
        assertTrue(charField.get(node) == 42);
        assertTrue(shortField.get(node) == 42);
        assertTrue(intField.get(node) == 42);
        assertTrue(floatField.get(node) == 42.f);
        assertTrue(longField.get(node) == 42L);
        assertTrue(doubleField.get(node) == 42.d);
        assertTrue(referenceObjectField.get(node) == TestNode.OBJECT_VALUE_1);
        assertTrue(referenceStringField.get(node) == TestNode.STRING_VALUE_1);
        assertTrue(referenceStringField.getVolatile(node) == TestNode.STRING_VALUE_1);

        stateField.set(node, 43);
        booleanField.set(node, false);
        byteField.set(node, (byte) 43);
        charField.set(node, (char) 43);
        shortField.set(node, (short) 43);
        intField.set(node, 43);
        floatField.set(node, 43);
        longField.set(node, 43);
        doubleField.set(node, 43);
        referenceObjectField.set(node, TestNode.OBJECT_VALUE_2);
        referenceStringField.compareAndSet(node, TestNode.STRING_VALUE_1, TestNode.STRING_VALUE_2);

        assertTrue(stateField.get(node) == 43);
        assertTrue(booleanField.get(node) == false);
        assertTrue(byteField.get(node) == 43);
        assertTrue(charField.get(node) == 43);
        assertTrue(shortField.get(node) == 43);
        assertTrue(intField.get(node) == 43);
        assertTrue(floatField.get(node) == 43.f);
        assertTrue(longField.get(node) == 43L);
        assertTrue(doubleField.get(node) == 43.d);
        assertTrue(referenceObjectField.get(node) == TestNode.OBJECT_VALUE_2);
        assertTrue(referenceStringField.get(node) == TestNode.STRING_VALUE_2);
        assertTrue(referenceStringField.getVolatile(node) == TestNode.STRING_VALUE_2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFieldsCrashing() {
        assertFails(() -> stateField.get(null), NullPointerException.class);
        assertFails(() -> booleanField.get(null), NullPointerException.class);
        assertFails(() -> byteField.get(null), NullPointerException.class);
        assertFails(() -> charField.get(null), NullPointerException.class);
        assertFails(() -> shortField.get(null), NullPointerException.class);
        assertFails(() -> intField.get(null), NullPointerException.class);
        assertFails(() -> floatField.get(null), NullPointerException.class);
        assertFails(() -> longField.get(null), NullPointerException.class);
        assertFails(() -> doubleField.get(null), NullPointerException.class);
        assertFails(() -> referenceObjectField.get(null), NullPointerException.class);
        assertFails(() -> referenceStringField.get(null), NullPointerException.class);
        assertFails(() -> referenceStringField.getVolatile(null), NullPointerException.class);

        assertFails(() -> stateField.set(null, 42), NullPointerException.class);
        assertFails(() -> booleanField.set(null, true), NullPointerException.class);
        assertFails(() -> byteField.set(null, (byte) 42), NullPointerException.class);
        assertFails(() -> charField.set(null, (char) 42), NullPointerException.class);
        assertFails(() -> shortField.set(null, (short) 42), NullPointerException.class);
        assertFails(() -> intField.set(null, 42), NullPointerException.class);
        assertFails(() -> floatField.set(null, 42), NullPointerException.class);
        assertFails(() -> longField.set(null, 42), NullPointerException.class);
        assertFails(() -> doubleField.set(null, 42), NullPointerException.class);
        assertFails(() -> referenceObjectField.set(null, TestNode.OBJECT_VALUE_2), NullPointerException.class);
        assertFails(() -> referenceStringField.set(null, TestNode.STRING_VALUE_2), NullPointerException.class);
        assertFails(() -> referenceStringField.compareAndSet(null, TestNode.STRING_VALUE_2, TestNode.STRING_VALUE_2), NullPointerException.class);

        Node node = new Node() {
        };
        assertFails(() -> stateField.get(node), ClassCastException.class);
        assertFails(() -> booleanField.get(node), ClassCastException.class);
        assertFails(() -> byteField.get(node), ClassCastException.class);
        assertFails(() -> charField.get(node), ClassCastException.class);
        assertFails(() -> shortField.get(node), ClassCastException.class);
        assertFails(() -> intField.get(node), ClassCastException.class);
        assertFails(() -> floatField.get(node), ClassCastException.class);
        assertFails(() -> longField.get(node), ClassCastException.class);
        assertFails(() -> doubleField.get(node), ClassCastException.class);
        assertFails(() -> referenceObjectField.get(node), ClassCastException.class);
        assertFails(() -> referenceStringField.get(node), ClassCastException.class);
        assertFails(() -> referenceStringField.getVolatile(node), ClassCastException.class);

        assertFails(() -> stateField.set(node, 42), ClassCastException.class);
        assertFails(() -> booleanField.set(node, true), ClassCastException.class);
        assertFails(() -> byteField.set(node, (byte) 42), ClassCastException.class);
        assertFails(() -> charField.set(node, (char) 42), ClassCastException.class);
        assertFails(() -> shortField.set(node, (short) 42), ClassCastException.class);
        assertFails(() -> intField.set(node, 42), ClassCastException.class);
        assertFails(() -> floatField.set(node, 42), ClassCastException.class);
        assertFails(() -> longField.set(node, 42), ClassCastException.class);
        assertFails(() -> doubleField.set(node, 42), ClassCastException.class);
        assertFails(() -> referenceObjectField.set(node, TestNode.OBJECT_VALUE_2), ClassCastException.class);
        assertFails(() -> referenceStringField.set(node, TestNode.STRING_VALUE_2), ClassCastException.class);
        assertFails(() -> referenceStringField.compareAndSet(node, TestNode.STRING_VALUE_2, TestNode.STRING_VALUE_2), ClassCastException.class);

        TestNode test = new TestNode();
        assertFails(() -> ((ReferenceField<Object>) (ReferenceField<?>) referenceStringField).set(test, 42), IllegalArgumentException.class);
    }

    @Test
    public void testInlineTarget() {
        assertFails(() -> InlineTarget.create(TestNode.class, null, null), NullPointerException.class);
        assertFails(() -> InlineTarget.create(TestNode.class, (InlinableField[]) null), NullPointerException.class);
        StateField subUpdater = stateField.subUpdater(1, 3);
        InlineTarget target = InlineTarget.create(TestNode.class, stateField, charField, referenceStringField, subUpdater);
        final int outOfBoundsIndex = 4;

        assertSame(stateField, target.getState(0, 32));
        assertSame(subUpdater, target.getState(3, 3));
        assertSame(stateField, target.getState(0, 1));
        assertSame(subUpdater, target.getState(3, 1));
        assertFails(() -> target.getState(outOfBoundsIndex, 1), IncompatibleClassChangeError.class);
        assertFails(() -> target.getState(3, 4), IncompatibleClassChangeError.class);
        assertFails(() -> target.getState(3, 32), IncompatibleClassChangeError.class);

        assertFails(() -> target.getState(-1, 1), ArrayIndexOutOfBoundsException.class);
        assertFails(() -> target.getState(0, 33), IllegalArgumentException.class);
        assertFails(() -> target.getState(0, 0), IllegalArgumentException.class);

        assertSame(charField, target.getPrimitive(1, CharField.class));
        assertFails(() -> target.getPrimitive(outOfBoundsIndex, CharField.class), IncompatibleClassChangeError.class);
        assertFails(() -> target.getPrimitive(1, IntField.class), IncompatibleClassChangeError.class);
        assertFails(() -> target.getPrimitive(0, IntField.class), IncompatibleClassChangeError.class);
        assertFails(() -> target.getPrimitive(1, ReferenceField.class), IncompatibleClassChangeError.class);
        assertFails(() -> target.getPrimitive(1, ReferenceField.class), IncompatibleClassChangeError.class);

        assertFails(() -> target.getPrimitive(-1, IntField.class), ArrayIndexOutOfBoundsException.class);
        assertFails(() -> target.getPrimitive(1, null), NullPointerException.class);

        assertSame(referenceStringField, target.getReference(2, String.class));
        assertFails(() -> target.getReference(outOfBoundsIndex, String.class), IncompatibleClassChangeError.class);
        assertFails(() -> target.getReference(1, String.class), IncompatibleClassChangeError.class);
        assertFails(() -> target.getReference(0, String.class), IncompatibleClassChangeError.class);
        assertFails(() -> target.getReference(2, Object.class), IncompatibleClassChangeError.class);
        assertFails(() -> target.getReference(2, Integer.class), IncompatibleClassChangeError.class);

        assertFails(() -> target.getReference(-1, String.class), ArrayIndexOutOfBoundsException.class);
        assertFails(() -> target.getReference(1, null), NullPointerException.class);

    }

    @Test
    public void testSubUpdater() {
        StateField all = stateField.subUpdater(0, 2);
        StateField first = all.subUpdater(0, 1);
        StateField second = all.subUpdater(1, 1);

        TestNode node = new TestNode();
        all.set(node, 0b11);

        assertEquals(0b1, first.get(node));
        assertEquals(0b1, second.get(node));

        all.set(node, 0b00);
        first.set(node, 0b1);
        second.set(node, 0b1);

        assertEquals(0b11, all.get(node));

        // only if assertions are enabled
        assertFails(() -> all.set(node, 0b111), IllegalArgumentException.class, (e) -> {
            assertEquals("Bits lost in masked state updater set. Provided bits: 0x7 Written bits: 0x3. This could indicate a bug in subUpdater indices in the node object inlining logic.",
                            e.getMessage());
        });

        assertSame(all, all.subUpdater(0, 2));
        assertSame(first, first.subUpdater(0, 1));

        assertFails(() -> all.subUpdater(-1, 2), IllegalArgumentException.class);
        assertFails(() -> all.subUpdater(0, 3), IllegalArgumentException.class);
        assertFails(() -> all.subUpdater(1, 2), IllegalArgumentException.class);
        assertFails(() -> all.subUpdater(2, 1), IllegalArgumentException.class);
        assertFails(() -> all.subUpdater(3, 0), IllegalArgumentException.class);
        assertFails(() -> all.subUpdater(2, 0), IllegalArgumentException.class);
        assertFails(() -> all.subUpdater(1, 0), IllegalArgumentException.class);
        assertFails(() -> all.subUpdater(0, 0), IllegalArgumentException.class);

    }

    @Test
    public void testValidate() {
        stateField.validate(new TestNode());
        charField.validate(new TestNode());
        referenceStringField.validate(new TestNode());

        assertFails(() -> stateField.validate(null), NullPointerException.class);
        assertFails(() -> charField.validate(null), NullPointerException.class);
        assertFails(() -> referenceStringField.validate(null), NullPointerException.class);

        Node node = new Node() {
        };
        assertFails(() -> stateField.validate(node), ClassCastException.class);
        assertFails(() -> charField.validate(node), ClassCastException.class);
        assertFails(() -> referenceStringField.validate(node), ClassCastException.class);
    }

    static class ParentUpdaterNode extends Node implements SpecializationDataNode {
    }

    static class ParentUpdaterNoSpecializationDataNode extends Node {
    }

    @Test
    public void testSpecializationDataNodeValidation() {
        TestNode node = new TestNode();
        // parent lookup is fine for this one
        ParentUpdaterNode specializationData = node.insert(new ParentUpdaterNode());

        ParentUpdaterNoSpecializationDataNode noSpecializationDataBadNode = node.insert(new ParentUpdaterNoSpecializationDataNode());
        // not fine for this one as the parent does not implement SpecializationDataNode
        ParentUpdaterNode noSpecializationDataAsParent = noSpecializationDataBadNode.insert(new ParentUpdaterNode());

        stateField.validate(node); // fine
        stateField.validate(specializationData); // also fine uses parent lookup
        stateField.validate(noSpecializationDataBadNode);

        assertFails(() -> stateField.validate(noSpecializationDataAsParent), ClassCastException.class, (e) -> {
            assertEquals(e.getMessage(),
                            "Invalid inline context node passed to an inlined field. A receiver of type 'InlineSupportTest.TestNode' was expected but is 'InlineSupportTest.ParentUpdaterNode'. " +
                                            "Did you pass the wrong node to an execute method of an inlined cached node?");
        });
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testParentUpdater() {
        TestNode node = new TestNode();
        ParentUpdaterNode updater = node.insert(new ParentUpdaterNode());
        Node otherNode = new Node() {
        };

        StateField parentState = stateField.createParentAccessor(ParentUpdaterNode.class);
        assertEquals(42, parentState.get(updater));
        parentState.set(updater, 43);
        assertEquals(43, parentState.get(updater));

        parentState.set(node, 44);
        assertEquals(44, parentState.get(node));

        assertFails(() -> parentState.get(null), NullPointerException.class);
        assertFails(() -> parentState.get(otherNode), ClassCastException.class);
        assertFails(() -> parentState.set(null, 42), NullPointerException.class);
        assertFails(() -> parentState.set(otherNode, 42), ClassCastException.class);
        assertEquals(44, parentState.get(updater));

        IntField parentInt = intField.createParentAccessor(ParentUpdaterNode.class);
        // parent updater has no more effect.
        assertSame(parentInt, intField);

        ReferenceField<String> parentString = referenceStringField.createParentAccessor(ParentUpdaterNode.class);
        assertSame(parentString, referenceStringField);

    }

}
