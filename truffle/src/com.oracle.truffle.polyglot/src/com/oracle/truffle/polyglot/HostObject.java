/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyglot.HostObjectMRFactory.ArrayReadNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.ArrayReadNodeGen.ArrayGetNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.ArrayRemoveNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.ArrayWriteNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.ArrayWriteNodeGen.ArraySetNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.KeyInfoCacheNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.LookupConstructorNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.LookupFieldNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.LookupFunctionalMethodNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.LookupInnerClassNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.LookupMethodNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.MapRemoveNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.ReadFieldNodeGen;
import com.oracle.truffle.polyglot.HostObjectMRFactory.WriteFieldNodeGen;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;

final class HostObject implements TruffleObject {

    static final HostObject NULL = new HostObject(null, null, false);

    final Object obj;
    final PolyglotLanguageContext languageContext;
    private final boolean staticClass;

    private HostObject(Object obj, PolyglotLanguageContext languageContext, boolean staticClass) {
        this.obj = obj;
        this.languageContext = languageContext;
        this.staticClass = staticClass;
    }

    static HostObject forClass(Class<?> clazz, PolyglotLanguageContext languageContext) {
        assert clazz != null;
        return new HostObject(clazz, languageContext, false);
    }

    static HostObject forStaticClass(Class<?> clazz, PolyglotLanguageContext languageContext) {
        assert clazz != null;
        return new HostObject(clazz, languageContext, true);
    }

    static HostObject forObject(Object object, PolyglotLanguageContext languageContext) {
        assert object != null && !(object instanceof Class<?>);
        return new HostObject(object, languageContext, false);
    }

    static boolean isInstance(Object obj) {
        return obj instanceof HostObject;
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof HostObject;
    }

    static boolean isStaticClass(Object object) {
        return object instanceof HostObject && ((HostObject) object).isStaticClass();
    }

    static boolean isJavaInstance(Class<?> targetType, Object javaObject) {
        if (javaObject instanceof HostObject) {
            final Object value = valueOf((HostObject) javaObject);
            return targetType.isInstance(value);
        } else {
            return false;
        }
    }

    boolean isPrimitive() {
        return PolyglotImpl.isGuestPrimitive(obj);
    }

    static Object valueOf(TruffleObject value) {
        final HostObject obj = (HostObject) value;
        return obj.obj;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return HostObjectMRForeign.ACCESS;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(obj);
    }

    boolean isClass() {
        return obj instanceof Class<?>;
    }

    boolean isArray() {
        return obj != null && obj.getClass().isArray();
    }

    boolean isNull() {
        return obj == null;
    }

    boolean isStaticClass() {
        return staticClass;
    }

    Class<?> getObjectClass() {
        return obj == null ? null : obj.getClass();
    }

    Class<?> asStaticClass() {
        assert isStaticClass();
        return (Class<?>) obj;
    }

    Class<?> asClass() {
        assert isClass();
        return (Class<?>) obj;
    }

    /**
     * Gets the {@link Class} for member lookups.
     */
    Class<?> getLookupClass() {
        if (obj == null) {
            return null;
        } else if (isStaticClass()) {
            return asStaticClass();
        } else {
            return obj.getClass();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HostObject) {
            HostObject other = (HostObject) o;
            return this.obj == other.obj && this.languageContext == other.languageContext;
        }
        return false;
    }

    @Override
    public String toString() {
        if (obj == null) {
            return "null";
        }
        if (isClass()) {
            return "JavaClass[" + asClass().getTypeName() + "]";
        }
        return "JavaObject[" + obj + " (" + getObjectClass().getTypeName() + ")" + "]";
    }

}

@MessageResolution(receiverType = HostObject.class)
class HostObjectMR {

    @Resolve(message = "GET_SIZE")
    abstract static class ArrayGetSizeNode extends Node {

        public Object access(HostObject receiver) {
            Object obj = receiver.obj;
            if (obj != null) {
                if (obj.getClass().isArray()) {
                    return Array.getLength(obj);
                } else if (obj instanceof List<?>) {
                    return ((List<?>) obj).size();
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.raise(Message.GET_SIZE);
        }

    }

    @Resolve(message = "HAS_SIZE")
    abstract static class ArrayHasSizeNode extends Node {

        public Object access(HostObject receiver) {
            Object obj = receiver.obj;
            if (obj == null) {
                return false;
            }
            return obj.getClass().isArray() || obj instanceof List<?>;
        }

    }

    @Resolve(message = "INVOKE")
    abstract static class InvokeNode extends Node {
        private static final Message INVOKE = Message.INVOKE;
        @Child private LookupMethodNode lookupMethod;
        @Child private HostExecuteNode executeMethod;
        @Child private LookupFieldNode lookupField;
        @Child private ReadFieldNode readField;
        @Child private Node sendIsExecutableNode;
        @Child private Node sendExecuteNode;

        public Object access(HostObject object, String name, Object[] args) {
            if (TruffleOptions.AOT || object.isNull()) {
                throw UnsupportedMessageException.raise(INVOKE);
            }

            boolean isStatic = object.isStaticClass();
            Class<?> lookupClass = object.getLookupClass();

            // (1) look for a method; if found, invoke it on obj.
            HostMethodDesc foundMethod = lookupMethod().execute(lookupClass, name, isStatic);
            if (foundMethod != null) {
                return executeMethod().execute(foundMethod, object.obj, args, object.languageContext);
            }

            // (2) look for a field; if found, read its value and if that IsExecutable, Execute it.
            HostFieldDesc foundField = lookupField().execute(lookupClass, name, isStatic);
            if (foundField != null) {
                Object fieldValue = readField().execute(foundField, object);
                if (fieldValue instanceof TruffleObject) {
                    TruffleObject fieldObject = (TruffleObject) fieldValue;
                    if (sendIsExecutableNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        sendIsExecutableNode = insert(Message.IS_EXECUTABLE.createNode());
                    }
                    boolean isExecutable = ForeignAccess.sendIsExecutable(sendIsExecutableNode, fieldObject);
                    if (isExecutable) {
                        if (sendExecuteNode == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            sendExecuteNode = insert(Message.EXECUTE.createNode());
                        }
                        try {
                            return ForeignAccess.sendExecute(sendExecuteNode, fieldObject, args);
                        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                            throw e.raise();
                        }
                    }
                }
            }

            throw UnknownIdentifierException.raise(name);
        }

        private LookupMethodNode lookupMethod() {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupMethodNodeGen.create());
            }
            return lookupMethod;
        }

        private HostExecuteNode executeMethod() {
            if (executeMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeMethod = insert(HostExecuteNode.create());
            }
            return executeMethod;
        }

        private LookupFieldNode lookupField() {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNodeGen.create());
            }
            return lookupField;
        }

        private ReadFieldNode readField() {
            if (readField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readField = insert(ReadFieldNodeGen.create());
            }
            return readField;
        }
    }

    @Resolve(message = "IS_INSTANTIABLE")
    abstract static class IsInstantiableObjectNode extends Node {
        @Child private LookupConstructorNode lookupConstructor;

        public Object access(HostObject receiver) {
            if (TruffleOptions.AOT) {
                return false;
            }
            return receiver.isClass() && lookupConstructor().execute(receiver.asClass()) != null;
        }

        private LookupConstructorNode lookupConstructor() {
            if (lookupConstructor == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupConstructor = insert(LookupConstructorNodeGen.create());
            }
            return lookupConstructor;
        }
    }

    @Resolve(message = "NEW")
    abstract static class NewNode extends Node {
        private static final Message NEW = Message.NEW;
        @Child private LookupConstructorNode lookupConstructor;
        @Child private HostExecuteNode executeMethod;
        @Child private ToHostNode toJava;

        public Object access(HostObject receiver, Object[] args) {
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(NEW);
            }

            if (receiver.isClass()) {
                Class<?> javaClass = receiver.asClass();
                if (javaClass.isArray()) {
                    return newArray(receiver, args);
                }

                HostMethodDesc constructor = lookupConstructor().execute(javaClass);
                if (constructor != null) {
                    return executeMethod().execute(constructor, null, args, receiver.languageContext);
                }
            }
            throw UnsupportedMessageException.raise(NEW);
        }

        private Object newArray(HostObject receiver, Object[] args) {
            if (args.length != 1) {
                throw ArityException.raise(1, args.length);
            }
            if (toJava == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJava = insert(ToHostNode.create());
            }
            int length;
            try {
                length = (int) toJava.execute(args[0], int.class, null, receiver.languageContext);
            } catch (ClassCastException | NullPointerException e) {
                // conversion failed by ToJavaNode
                throw UnsupportedTypeException.raise(e, args);
            }
            Object array = Array.newInstance(receiver.asClass().getComponentType(), length);
            return HostObject.forObject(array, receiver.languageContext);
        }

        private LookupConstructorNode lookupConstructor() {
            if (lookupConstructor == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupConstructor = insert(LookupConstructorNodeGen.create());
            }
            return lookupConstructor;
        }

        private HostExecuteNode executeMethod() {
            if (executeMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeMethod = insert(HostExecuteNode.create());
            }
            return executeMethod;
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class NullCheckNode extends Node {

        public Object access(HostObject object) {
            return object.isNull();
        }

    }

    @Resolve(message = "IS_BOXED")
    abstract static class BoxedCheckNode extends Node {
        @Child private ToHostPrimitiveNode primitive = ToHostPrimitiveNode.create();

        public Object access(HostObject object) {
            return object.isPrimitive();
        }

    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNode extends Node {
        @Child private ToHostPrimitiveNode primitive = ToHostPrimitiveNode.create();

        public Object access(HostObject object) {
            if (object.isPrimitive()) {
                return object.obj;
            } else {
                return UnsupportedMessageException.raise(Message.UNBOX);
            }
        }

    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ArrayReadNode arrayRead;
        @Child private LookupFieldNode lookupField;
        @Child private ReadFieldNode readField;
        @Child private LookupMethodNode lookupMethod;
        @Child private LookupInnerClassNode lookupInnerClass;

        public Object access(HostObject object, Number index) {
            if (arrayRead == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                arrayRead = insert(ArrayReadNodeGen.create());
            }
            return arrayRead.executeWithTarget(object, index);
        }

        public Object access(HostObject object, String name) {
            if (TruffleOptions.AOT || object.isNull()) {
                throw UnsupportedMessageException.raise(Message.READ);
            }
            boolean isStatic = object.isStaticClass();
            Class<?> lookupClass = object.getLookupClass();
            HostFieldDesc foundField = lookupField().execute(lookupClass, name, isStatic);
            if (foundField != null) {
                return readField().execute(foundField, object);
            }
            HostMethodDesc foundMethod = lookupMethod().execute(lookupClass, name, isStatic);
            if (foundMethod != null) {
                return new HostFunction(foundMethod, object.obj, object.languageContext);
            }
            if (isStatic) {
                LookupInnerClassNode lookupInnerClassNode = lookupInnerClass();
                if ("class".equals(name)) {
                    return HostObject.forClass(lookupClass, object.languageContext);
                }
                Class<?> innerclass = lookupInnerClassNode.execute(lookupClass, name);
                if (innerclass != null) {
                    return HostObject.forStaticClass(innerclass, object.languageContext);
                }
            }
            throw UnknownIdentifierException.raise(name);
        }

        private ReadFieldNode readField() {
            if (readField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readField = insert(ReadFieldNodeGen.create());
            }
            return readField;
        }

        private LookupFieldNode lookupField() {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNodeGen.create());
            }
            return lookupField;
        }

        private LookupMethodNode lookupMethod() {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupMethodNodeGen.create());
            }
            return lookupMethod;
        }

        private LookupInnerClassNode lookupInnerClass() {
            if (lookupInnerClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupInnerClass = insert(LookupInnerClassNodeGen.create());
            }
            return lookupInnerClass;
        }
    }

    abstract static class ArrayReadNode extends Node {

        abstract static class ArrayGet extends Node {

            protected abstract Object execute(Object array, int index);

            @Specialization
            boolean doBoolean(boolean[] array, int index) {
                return array[index];
            }

            @Specialization
            byte doByte(byte[] array, int index) {
                return array[index];
            }

            @Specialization
            short doShort(short[] array, int index) {
                return array[index];
            }

            @Specialization
            char doChar(char[] array, int index) {
                return array[index];
            }

            @Specialization
            int doInt(int[] array, int index) {
                return array[index];
            }

            @Specialization
            long doLong(long[] array, int index) {
                return array[index];
            }

            @Specialization
            float doFloat(float[] array, int index) {
                return array[index];
            }

            @Specialization
            double doDouble(double[] array, int index) {
                return array[index];
            }

            @Specialization
            Object doObject(Object[] array, int index) {
                return array[index];
            }
        }

        @Child private ArrayGet arrayGet = ArrayGetNodeGen.create();
        private final ToGuestValueNode toGuest = ToGuestValueNode.create();

        protected abstract Object executeWithTarget(HostObject receiver, Object index);

        @Specialization(guards = {"receiver.isArray()"})
        protected Object doArrayIntIndex(HostObject receiver, int index) {
            return doArrayAccess(receiver, index);
        }

        @Specialization(guards = {"receiver.isArray()", "index.getClass() == clazz"}, replaces = "doArrayIntIndex")
        protected Object doArrayCached(HostObject receiver, Number index,
                        @Cached("index.getClass()") Class<? extends Number> clazz) {
            return doArrayAccess(receiver, clazz.cast(index).intValue());
        }

        @Specialization(guards = {"receiver.isArray()"}, replaces = "doArrayCached")
        protected Object doArrayGeneric(HostObject receiver, Number index) {
            return doArrayAccess(receiver, index.intValue());
        }

        @TruffleBoundary
        @Specialization(guards = {"isList(receiver)"})
        protected Object doListIntIndex(HostObject receiver, int index) {
            try {
                return toGuest.apply(receiver.languageContext, ((List<?>) receiver.obj).get(index));
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(index));
            }
        }

        @TruffleBoundary
        @Specialization(guards = {"isList(receiver)"}, replaces = "doListIntIndex")
        protected Object doListGeneric(HostObject receiver, Number index) {
            return doListIntIndex(receiver, index.intValue());
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = {"!receiver.isArray()", "!isList(receiver)"})
        protected static Object notArray(HostObject receiver, Number index) {
            throw UnsupportedMessageException.raise(Message.READ);
        }

        private Object doArrayAccess(HostObject object, int index) {
            Object obj = object.obj;
            assert object.isArray();
            Object val = null;
            try {
                val = arrayGet.execute(obj, index);
            } catch (ArrayIndexOutOfBoundsException outOfBounds) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(index));
            }

            return toGuest.apply(object.languageContext, val);
        }

        static boolean isList(HostObject receiver) {
            return receiver.obj instanceof List;
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private ArrayWriteNode arrayWrite;
        @Child private LookupFieldNode lookupField;
        @Child private WriteFieldNode writeField;

        public Object access(HostObject receiver, Number index, Object value) {
            if (arrayWrite == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                arrayWrite = insert(ArrayWriteNode.create());
            }
            try {
                return arrayWrite.executeWithTarget(receiver, index, value);
            } catch (ClassCastException | NullPointerException e) {
                // conversion failed by ToJavaNode
                throw UnsupportedTypeException.raise(e, new Object[]{value});
            }
        }

        public Object access(HostObject receiver, String name, Object value) {
            if (TruffleOptions.AOT || receiver.isNull()) {
                throw UnsupportedMessageException.raise(Message.WRITE);
            }
            HostFieldDesc f = lookupField().execute(receiver.getLookupClass(), name, receiver.isStaticClass());
            if (f == null) {
                throw UnknownIdentifierException.raise(name);
            }
            try {
                writeField().execute(f, receiver, value);
            } catch (ClassCastException | NullPointerException e) {
                // conversion failed by ToJavaNode
                throw UnsupportedTypeException.raise(e, new Object[]{value});
            }
            return HostObject.NULL;
        }

        private LookupFieldNode lookupField() {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNodeGen.create());
            }
            return lookupField;
        }

        private WriteFieldNode writeField() {
            if (writeField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeField = insert(WriteFieldNodeGen.create());
            }
            return writeField;
        }
    }

    abstract static class ArrayWriteNode extends Node {

        abstract static class ArraySet extends Node {

            protected abstract void execute(Object array, int index, Object value);

            @Specialization
            void doBoolean(boolean[] array, int index, boolean value) {
                array[index] = value;
            }

            @Specialization
            void doByte(byte[] array, int index, byte value) {
                array[index] = value;
            }

            @Specialization
            void doShort(short[] array, int index, short value) {
                array[index] = value;
            }

            @Specialization
            void doChar(char[] array, int index, char value) {
                array[index] = value;
            }

            @Specialization
            void doInt(int[] array, int index, int value) {
                array[index] = value;
            }

            @Specialization
            void doLong(long[] array, int index, long value) {
                array[index] = value;
            }

            @Specialization
            void doFloat(float[] array, int index, float value) {
                array[index] = value;
            }

            @Specialization
            void doDouble(double[] array, int index, double value) {
                array[index] = value;
            }

            @Specialization
            void doObject(Object[] array, int index, Object value) {
                array[index] = value;
            }
        }

        @Child private ToHostNode toJavaNode = ToHostNode.create();
        @Child private ArraySet arraySet = ArraySetNodeGen.create();

        protected abstract Object executeWithTarget(HostObject receiver, Object index, Object value);

        @Specialization(guards = {"receiver.isArray()"})
        protected final Object doArrayIntIndex(HostObject receiver, int index, Object value) {
            return doArrayAccess(receiver, index, value);
        }

        @Specialization(guards = {"receiver.isArray()", "index.getClass() == clazz"})
        protected final Object doArrayCached(HostObject receiver, Number index, Object value,
                        @Cached("index.getClass()") Class<? extends Number> clazz) {
            return doArrayAccess(receiver, clazz.cast(index).intValue(), value);
        }

        @Specialization(guards = {"receiver.isArray()"}, replaces = "doArrayCached")
        protected final Object doArrayGeneric(HostObject receiver, Number index, Object value) {
            return doArrayAccess(receiver, index.intValue(), value);
        }

        @SuppressWarnings("unchecked")
        @TruffleBoundary
        @Specialization(guards = {"isList(receiver)"})
        protected Object doListIntIndex(HostObject receiver, int index, Object value) {
            final Object javaValue = toJavaNode.execute(value, Object.class, null, receiver.languageContext);
            try {
                List<Object> list = ((List<Object>) receiver.obj);
                if (index == list.size()) {
                    list.add(javaValue);
                } else {
                    list.set(index, javaValue);
                }
                return value;
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(index));
            }
        }

        @TruffleBoundary
        @Specialization(guards = {"isList(receiver)"}, replaces = "doListIntIndex")
        protected Object doListGeneric(HostObject receiver, Number index, Object value) {
            return doListIntIndex(receiver, index.intValue(), value);
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = {"!receiver.isArray()", "!isList(receiver)"})
        protected static Object notArray(HostObject receiver, Number index, Object value) {
            throw UnsupportedMessageException.raise(Message.WRITE);
        }

        private Object doArrayAccess(HostObject receiver, int index, Object value) {
            Object obj = receiver.obj;
            assert receiver.isArray();
            final Object javaValue = toJavaNode.execute(value, obj.getClass().getComponentType(), null, receiver.languageContext);
            try {
                arraySet.execute(obj, index, javaValue);
            } catch (ArrayIndexOutOfBoundsException outOfBounds) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(index));
            }
            return HostObject.NULL;
        }

        static boolean isList(HostObject receiver) {
            return receiver.obj instanceof List;
        }

        static ArrayWriteNode create() {
            return ArrayWriteNodeGen.create();
        }
    }

    abstract static class MapRemoveNode extends Node {

        protected abstract Object executeWithTarget(HostObject receiver, String name);

        @SuppressWarnings("unchecked")
        @TruffleBoundary
        @Specialization(guards = {"isMap(receiver)"})
        protected Object doMapGeneric(HostObject receiver, String name) {
            Map<String, Object> map = (Map<String, Object>) receiver.obj;
            if (!map.containsKey(name)) {
                throw UnknownIdentifierException.raise(name);
            }
            map.remove(name);
            return true;
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = {"!isMap(receiver)"})
        protected static Object notMap(HostObject receiver, String name) {
            throw UnsupportedMessageException.raise(Message.REMOVE);
        }

        static boolean isMap(HostObject receiver) {
            return receiver.obj instanceof Map;
        }

    }

    @Resolve(message = "REMOVE")
    abstract static class RemoveNode extends Node {
        @Child private ArrayRemoveNode arrayRemove;
        @Child private MapRemoveNode mapRemove;

        public Object access(HostObject receiver, Number index) {
            if (arrayRemove == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                arrayRemove = insert(ArrayRemoveNodeGen.create());
            }
            return arrayRemove.executeWithTarget(receiver, index);
        }

        public Object access(HostObject receiver, String name) {
            if (mapRemove == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                mapRemove = insert(MapRemoveNodeGen.create());
            }
            return mapRemove.executeWithTarget(receiver, name);
        }
    }

    abstract static class ArrayRemoveNode extends Node {

        protected abstract boolean executeWithTarget(HostObject receiver, Object index);

        @SuppressWarnings("unchecked")
        @TruffleBoundary
        @Specialization(guards = {"isList(receiver)"})
        protected boolean doListIntIndex(HostObject receiver, int index) {
            try {
                ((List<Object>) receiver.obj).remove(index);
            } catch (IndexOutOfBoundsException outOfBounds) {
                throw UnknownIdentifierException.raise(String.valueOf(index));
            }
            return true;
        }

        @TruffleBoundary
        @Specialization(guards = {"isList(receiver)"}, replaces = "doListIntIndex")
        protected boolean doListGeneric(HostObject receiver, Number index) {
            return doListIntIndex(receiver, index.intValue());
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = {"!isList(receiver)"})
        protected static boolean notArray(HostObject receiver, Number index) {
            throw UnsupportedMessageException.raise(Message.REMOVE);
        }

        static boolean isList(HostObject receiver) {
            return receiver.obj instanceof List;
        }

    }

    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {

        public Object access(HostObject receiver) {
            return !receiver.isNull();
        }
    }

    @Resolve(message = "KEYS")
    abstract static class KeysNode extends Node {
        @TruffleBoundary
        public Object access(HostObject receiver, boolean includeInternal) {
            if (receiver.isNull()) {
                throw UnsupportedMessageException.raise(Message.KEYS);
            }
            String[] fields = TruffleOptions.AOT ? new String[0] : HostInteropReflect.findUniquePublicMemberNames(receiver.getLookupClass(), receiver.isStaticClass(), includeInternal);
            return HostObject.forObject(fields, receiver.languageContext);
        }
    }

    abstract static class KeyInfoCacheNode extends Node {
        static final int LIMIT = 3;

        KeyInfoCacheNode() {
        }

        public abstract int execute(Class<?> clazz, String name, boolean onlyStatic);

        @SuppressWarnings("unused")
        @Specialization(guards = {"onlyStatic == cachedStatic", "clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static int doCached(Class<?> clazz, String name, boolean onlyStatic,
                        @Cached("onlyStatic") boolean cachedStatic,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(clazz, name, onlyStatic)") int cachedKeyInfo) {
            assert cachedKeyInfo == doUncached(clazz, name, onlyStatic);
            return cachedKeyInfo;
        }

        @Specialization(replaces = "doCached")
        static int doUncached(Class<?> clazz, String name, boolean onlyStatic) {
            return HostInteropReflect.findKeyInfo(clazz, name, onlyStatic);
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {

        @Child private KeyInfoCacheNode keyInfoCache;

        public int access(HostObject receiver, int index) {
            if (index < 0) {
                return 0;
            }
            if (receiver.isArray()) {
                int length = Array.getLength(receiver.obj);
                if (index < length) {
                    return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
                }
            } else if (receiver.obj instanceof List) {
                int length = listSize((List<?>) receiver.obj);
                if (index < length) {
                    return KeyInfo.READABLE | KeyInfo.MODIFIABLE | KeyInfo.REMOVABLE;
                } else if (index == length) {
                    return KeyInfo.INSERTABLE;
                }
            }
            return KeyInfo.NONE;
        }

        @TruffleBoundary
        public int access(HostObject receiver, Number index) {
            int i = index.intValue();
            if (i != index.doubleValue()) {
                // No non-integer indexes
                return 0;
            }
            return access(receiver, i);
        }

        @TruffleBoundary
        private static int listSize(List<?> list) {
            return list.size();
        }

        public int access(HostObject receiver, String name) {
            if (receiver.isNull()) {
                throw UnsupportedMessageException.raise(Message.KEY_INFO);
            }
            if (TruffleOptions.AOT) {
                return 0;
            }
            return keyInfoCache().execute(receiver.getLookupClass(), name, receiver.isStaticClass());
        }

        private KeyInfoCacheNode keyInfoCache() {
            if (keyInfoCache == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                keyInfoCache = insert(KeyInfoCacheNodeGen.create());
            }
            return keyInfoCache;
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class IsExecutableObjectNode extends Node {
        @Child private LookupFunctionalMethodNode lookupMethod;

        public Object access(HostObject receiver) {
            if (TruffleOptions.AOT) {
                return false;
            }
            return receiver.obj != null && !receiver.isClass() && lookupFunctionalInterfaceMethod(receiver) != null;
        }

        private HostMethodDesc lookupFunctionalInterfaceMethod(HostObject receiver) {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupFunctionalMethodNodeGen.create());
            }
            return lookupMethod.execute(receiver.getLookupClass());
        }
    }

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteObjectNode extends Node {
        private static final Message EXECUTE = Message.EXECUTE;
        @Child private LookupFunctionalMethodNode lookupMethod;
        @Child private HostExecuteNode doExecute;

        public Object access(HostObject receiver, Object[] args) {
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(EXECUTE);
            }
            if (receiver.obj != null && !receiver.isClass()) {
                HostMethodDesc method = lookupFunctionalInterfaceMethod(receiver);
                if (method != null) {
                    if (doExecute == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        doExecute = insert(HostExecuteNode.create());
                    }
                    return doExecute.execute(method, receiver.obj, args, receiver.languageContext);
                }
            }
            throw UnsupportedMessageException.raise(EXECUTE);
        }

        private HostMethodDesc lookupFunctionalInterfaceMethod(HostObject receiver) {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupFunctionalMethodNodeGen.create());
            }
            return lookupMethod.execute(receiver.getLookupClass());
        }
    }

    abstract static class LookupConstructorNode extends Node {
        static final int LIMIT = 3;

        LookupConstructorNode() {
        }

        public abstract HostMethodDesc execute(Class<?> clazz);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz"}, limit = "LIMIT")
        static HostMethodDesc doCached(Class<?> clazz,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("doUncached(clazz)") HostMethodDesc cachedMethod) {
            assert cachedMethod == doUncached(clazz);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        static HostMethodDesc doUncached(Class<?> clazz) {
            return HostClassDesc.forClass(clazz).lookupConstructor();
        }
    }

    abstract static class LookupFieldNode extends Node {
        static final int LIMIT = 3;

        LookupFieldNode() {
        }

        public abstract HostFieldDesc execute(Class<?> clazz, String name, boolean onlyStatic);

        @SuppressWarnings("unused")
        @Specialization(guards = {"onlyStatic == cachedStatic", "clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static HostFieldDesc doCached(Class<?> clazz, String name, boolean onlyStatic,
                        @Cached("onlyStatic") boolean cachedStatic,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(clazz, name, onlyStatic)") HostFieldDesc cachedField) {
            assert cachedField == HostInteropReflect.findField(clazz, name, onlyStatic);
            return cachedField;
        }

        @Specialization(replaces = "doCached")
        static HostFieldDesc doUncached(Class<?> clazz, String name, boolean onlyStatic) {
            return HostInteropReflect.findField(clazz, name, onlyStatic);
        }
    }

    abstract static class LookupFunctionalMethodNode extends Node {
        static final int LIMIT = 3;

        LookupFunctionalMethodNode() {
        }

        public abstract HostMethodDesc execute(Class<?> clazz);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz"}, limit = "LIMIT")
        static HostMethodDesc doCached(Class<?> clazz,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("doUncached(clazz)") HostMethodDesc cachedMethod) {
            assert cachedMethod == doUncached(clazz);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        static HostMethodDesc doUncached(Class<?> clazz) {
            return HostClassDesc.forClass(clazz).getFunctionalMethod();
        }
    }

    abstract static class LookupInnerClassNode extends Node {
        static final int LIMIT = 3;

        LookupInnerClassNode() {
        }

        public abstract Class<?> execute(Class<?> outerclass, String name);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static Class<?> doCached(Class<?> clazz, String name,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(clazz, name)") Class<?> cachedInnerClass) {
            assert cachedInnerClass == HostInteropReflect.findInnerClass(clazz, name);
            return cachedInnerClass;
        }

        @Specialization(replaces = "doCached")
        static Class<?> doUncached(Class<?> clazz, String name) {
            return HostInteropReflect.findInnerClass(clazz, name);
        }
    }

    abstract static class LookupMethodNode extends Node {
        static final int LIMIT = 3;

        LookupMethodNode() {
        }

        public abstract HostMethodDesc execute(Class<?> clazz, String name, boolean onlyStatic);

        @SuppressWarnings("unused")
        @Specialization(guards = {"onlyStatic == cachedStatic", "clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static HostMethodDesc doCached(Class<?> clazz, String name, boolean onlyStatic,
                        @Cached("onlyStatic") boolean cachedStatic,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(clazz, name, onlyStatic)") HostMethodDesc cachedMethod) {
            assert cachedMethod == HostInteropReflect.findMethod(clazz, name, onlyStatic);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        static HostMethodDesc doUncached(Class<?> clazz, String name, boolean onlyStatic) {
            return HostInteropReflect.findMethod(clazz, name, onlyStatic);
        }
    }

    abstract static class ReadFieldNode extends Node {
        static final int LIMIT = 3;

        ReadFieldNode() {
        }

        public abstract Object execute(HostFieldDesc field, HostObject object);

        @SuppressWarnings("unused")
        @Specialization(guards = {"field == cachedField"}, limit = "LIMIT")
        static Object doCached(HostFieldDesc field, HostObject object,
                        @Cached("field") HostFieldDesc cachedField,
                        @Cached("create()") ToGuestValueNode toGuest) {
            Object val = cachedField.get(object.obj);
            return toGuest.apply(object.languageContext, val);
        }

        @Specialization(replaces = "doCached")
        static Object doUncached(HostFieldDesc field, HostObject object,
                        @Cached("create()") ToGuestValueNode toGuest) {
            Object val = field.get(object.obj);
            return toGuest.apply(object.languageContext, val);
        }
    }

    abstract static class WriteFieldNode extends Node {
        static final int LIMIT = 3;

        @Child private ToHostNode toHost = ToHostNode.create();

        WriteFieldNode() {
        }

        public abstract void execute(HostFieldDesc field, HostObject object, Object value);

        @SuppressWarnings("unused")
        @Specialization(guards = {"field == cachedField"}, limit = "LIMIT")
        void doCached(HostFieldDesc field, HostObject object, Object rawValue,
                        @Cached("field") HostFieldDesc cachedField) {
            Object val = toHost.execute(rawValue, cachedField.getType(), cachedField.getGenericType(), object.languageContext);
            cachedField.set(object.obj, val);
        }

        @Specialization(replaces = "doCached")
        void doUncached(HostFieldDesc field, HostObject object, Object rawValue) {
            Object val = toHost.execute(rawValue, field.getType(), field.getGenericType(), object.languageContext);
            field.set(object.obj, val);
        }
    }

}
