/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import java.lang.invoke.MethodHandles;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Example code snippets for {@link DynamicObject}.
 */
@SuppressWarnings({"unused", "static-method", "truffle-inlining"})
class DynamicObjectSnippets implements TruffleObject {

    static final Object NULL_VALUE = null;

    static class MyContext {
    }

    static class Symbol {
    }

    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.MyObject"
    public class MyObject extends DynamicObject implements TruffleObject {
        MyObject(Shape shape) {
            super(shape);
        }

        static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    }

    public abstract class MyTruffleLanguage extends TruffleLanguage<MyContext> {
        final Shape initialShape = Shape.newBuilder().build();

        public MyObject newObject() {
            return new MyObject(initialShape);
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.MyObject"

    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.MyObjectWithFields"
    public class MyObjectWithFields extends DynamicObject implements TruffleObject {
        @DynamicField private Object extra1;
        @DynamicField private Object extra2;
        @DynamicField private long extraLong1;
        @DynamicField private long extraLong2;

        MyObjectWithFields(Shape shape) {
            super(shape);
        }

        static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    }

    Shape initialShape = Shape.newBuilder().layout(MyObject.class, MyObject.LOOKUP).build();

    MyObject obj = new MyObject(initialShape);
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.MyObjectWithFields"

    @GenerateCached(false)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.GetSimple"
    abstract static class GetSimpleNode extends Node {
        abstract Object execute(DynamicObject receiver, Object key);

        @Specialization
        static Object doCached(MyDynamicObjectSubclass receiver, Symbol key,
                        @Cached DynamicObject.GetNode getNode) {
            return getNode.execute(receiver, key, NULL_VALUE);
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.GetSimple"

    @GenerateCached(false)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.PutSimple"
    abstract static class SetSimpleNode extends Node {
        abstract Object execute(DynamicObject receiver, Object key, Object value);

        @Specialization
        static Object doCached(MyDynamicObjectSubclass receiver, Symbol key, Object value,
                        @Cached DynamicObject.PutNode putNode) {
            putNode.execute(receiver, key, value);
            return value;
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.PutSimple"

    @GenerateCached(false)
    @ImportStatic(TruffleString.Encoding.class)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.GetWithKeyEquals"
    abstract static class GetStringKeyNode extends Node {
        abstract Object execute(DynamicObject receiver, Object key);

        @Specialization(guards = "equalNode.execute(key, cachedKey, UTF_16)", limit = "3")
        static Object doCached(MyDynamicObjectSubclass receiver, TruffleString key,
                        @Cached("key") TruffleString cachedKey,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached DynamicObject.GetNode getNode) {
            return getNode.execute(receiver, cachedKey, NULL_VALUE);
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.GetWithKeyEquals"

    @GenerateCached(false)
    @ImportStatic(TruffleString.Encoding.class)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.SetWithKeyEquals"
    abstract static class SetStringKeyNode extends Node {
        abstract Object execute(DynamicObject receiver, Object key, Object value);

        @Specialization(guards = "equalNode.execute(key, cachedKey, UTF_16)", limit = "3")
        static Object doCached(MyDynamicObjectSubclass receiver, TruffleString key, Object value,
                        @Cached("key") TruffleString cachedKey,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached DynamicObject.PutNode putNode) {
            putNode.execute(receiver, cachedKey, value);
            return value;
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.SetWithKeyEquals"

    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.ReadMember"
    @ExportLibrary(InteropLibrary.class)
    static class MyDynamicObjectSubclass extends DynamicObject {
        MyDynamicObjectSubclass(Shape shape) {
            super(shape);
        }

        @ExportMessage
        Object readMember(String member,
                        @Cached DynamicObject.GetNode getNode) throws UnknownIdentifierException {
            Object result = getNode.execute(this, member, null);
            if (result == null) {
                throw UnknownIdentifierException.create(member);
            }
            return result;
        }
        // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.ReadMember"

        // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.ContainsKey"
        @ExportMessage(name = "isMemberReadable")
        @ExportMessage(name = "isMemberRemovable")
        boolean isMemberReadable(String member,
                        @Cached @Shared DynamicObject.ContainsKeyNode containsKeyNode) {
            return containsKeyNode.execute(this, member);
        }
        // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.ContainsKey"

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.GetMembers"
        @ExportMessage
        Object getMembers(boolean includeInternal,
                        @Cached DynamicObject.GetKeyArrayNode getKeyArrayNode) {
            return new Keys(getKeyArrayNode.execute(this));
        }

        @ExportLibrary(InteropLibrary.class)
        static final class Keys implements TruffleObject {

            @CompilationFinal(dimensions = 1) final Object[] keys;

            Keys(Object[] keys) {
                this.keys = keys;
            }

            @ExportMessage
            boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            Object readArrayElement(long index) throws InvalidArrayIndexException {
                if (!isArrayElementReadable(index)) {
                    throw InvalidArrayIndexException.create(index);
                }
                return keys[(int) index];
            }

            @ExportMessage
            long getArraySize() {
                return keys.length;
            }

            @ExportMessage
            boolean isArrayElementReadable(long index) {
                return index >= 0 && index < keys.length;
            }
        }
        // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.GetMembers"

        // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.WriteMember"
        static final int READ_ONLY = 1;
        static final int MISSING = -1;
        static final int FROZEN = 1;

        @ExportMessage
        void writeMember(String member, Object value,
                        @Cached @Shared DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
                        @Cached @Shared DynamicObject.GetPropertyFlagsNode getPropertyFlagsNode,
                        @Cached DynamicObject.PutNode putNode) throws UnknownIdentifierException, UnsupportedMessageException {
            if ((getShapeFlagsNode.execute(this) & FROZEN) == FROZEN) {
                throw UnsupportedMessageException.create();
            }
            int flags = getPropertyFlagsNode.execute(this, member, MISSING);
            if (flags == MISSING) {
                throw UnknownIdentifierException.create(member);
            } else if ((flags & READ_ONLY) == READ_ONLY) {
                throw UnsupportedMessageException.create();
            }
            putNode.execute(this, member, value);
        }

        @ExportMessage
        boolean isMemberModifiable(String member,
                        @Cached @Shared DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
                        @Cached @Shared DynamicObject.GetPropertyFlagsNode getPropertyFlagsNode) {
            if ((getShapeFlagsNode.execute(this) & FROZEN) == FROZEN) {
                return false;
            }
            int flags = getPropertyFlagsNode.execute(this, member, MISSING);
            return flags != MISSING && (flags & READ_ONLY) == 0;
        }

        @ExportMessage
        boolean isMemberInsertable(String member,
                        @Cached @Shared DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
                        @Cached @Shared DynamicObject.GetPropertyFlagsNode getPropertyFlagsNode) {
            if ((getShapeFlagsNode.execute(this) & FROZEN) == FROZEN) {
                return false;
            }
            return getPropertyFlagsNode.execute(this, member, MISSING) == MISSING;
        }
        // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.WriteMember"

        // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.RemoveKey"
        @ExportMessage
        void removeMember(String member,
                        @Cached @Shared DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
                        @Cached DynamicObject.RemoveKeyNode removeKeyNode) throws UnknownIdentifierException, UnsupportedMessageException {
            if ((getShapeFlagsNode.execute(this) & FROZEN) == FROZEN) {
                throw UnsupportedMessageException.create();
            }
            if (!removeKeyNode.execute(this, member)) {
                throw UnknownIdentifierException.create(member);
            }
        }
        // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.RemoveKey"
    }

    @GenerateCached(false)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.GetPropertyEquivalent"
    abstract static class GetPropertyEquivalentNode extends Node {
        abstract void execute(DynamicObject receiver, Object key);

        @Specialization
        int doGeneric(MyDynamicObjectSubclass receiver, Symbol key, int defaultValue,
                        @Cached DynamicObject.GetPropertyNode getPropertyNode) {
            Property property = getPropertyNode.execute(receiver, key);
            return property != null ? property.getFlags() : defaultValue;
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.GetPropertyEquivalent"

    @GenerateCached(false)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.PutConstant1"
    abstract static class DeclarePropertyNode extends Node {
        abstract void execute(DynamicObject receiver, Object key);

        @Specialization
        static void doCached(MyDynamicObjectSubclass receiver, Symbol key,
                        @Cached DynamicObject.PutConstantNode putConstantNode) {
            // declare property
            putConstantNode.execute(receiver, key, NULL_VALUE);
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.PutConstant1"

    @GenerateCached(false)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.PutConstant2"
    abstract static class InitializePropertyNode extends Node {
        abstract void execute(DynamicObject receiver, Object key, Object value);

        @Specialization
        static void doCached(MyDynamicObjectSubclass receiver, Symbol key, Object value,
                        @Cached DynamicObject.PutNode putNode) {
            // initialize property
            putNode.execute(receiver, key, value);
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.PutConstant2"

    @GenerateCached(false)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.SetShapeFlags"
    abstract static class FreezeObjectNode extends Node {
        static final int FROZEN = 1;

        abstract void execute(DynamicObject receiver);

        @Specialization
        static void freeze(DynamicObject receiver,
                        @Cached DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
                        @Cached DynamicObject.SetShapeFlagsNode setShapeFlagsNode) {
            int oldFlags = getShapeFlagsNode.execute(receiver);
            int newFlags = oldFlags | FROZEN;
            if (newFlags != oldFlags) {
                setShapeFlagsNode.execute(receiver, newFlags);
            }
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.SetShapeFlags"

    @GenerateCached(false)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.AddShapeFlags"
    abstract static class FreezeNode extends Node {
        static final int FROZEN = 1;

        abstract void execute(DynamicObject receiver);

        @Specialization
        static void freeze(DynamicObject receiver,
                        @Cached DynamicObject.SetShapeFlagsNode setShapeFlagsNode) {
            setShapeFlagsNode.executeAdd(receiver, FROZEN);
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.AddShapeFlags"

    @GenerateCached(false)
    abstract static class GetUnboxedNode extends Node {

        abstract int executeInt(DynamicObject receiver, Object key) throws UnexpectedResultException;

        abstract Object execute(DynamicObject receiver, Object key);

        @Specialization(rewriteOn = UnexpectedResultException.class)
        static int doInt(DynamicObject receiver, Symbol key,
                        @Shared @Cached DynamicObject.GetNode getNode) throws UnexpectedResultException {
            return getNode.executeInt(receiver, key, NULL_VALUE);
        }

        @Specialization(replaces = "doInt")
        static Object doGeneric(DynamicObject receiver, Symbol key,
                        @Shared @Cached DynamicObject.GetNode getNode) {
            return getNode.execute(receiver, key, NULL_VALUE);
        }
    }

    abstract static class ExprNode extends Node {
        abstract Object execute();
    }

    @GenerateCached(false)
    // @start region = "com.oracle.truffle.api.object.DynamicObjectSnippets.PutAll"
    abstract static class ObjectInitializerNode extends Node {
        @CompilationFinal private Shape initialShape;
        @CompilationFinal(dimensions = 1) private Object[] keys;
        @Children private ExprNode[] valueNodes;

        abstract void execute();

        @ExplodeLoop
        @Specialization
        void doDefault(@Cached DynamicObject.PutAllNode putAllNode) {
            var receiver = new MyDynamicObjectSubclass(initialShape);
            var values = new Object[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = valueNodes[i].execute();
            }
            putAllNode.execute(receiver, keys, values);
        }
    }
    // @end region = "com.oracle.truffle.api.object.DynamicObjectSnippets.PutAll"
}
