/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.lang.reflect.Array;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
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

@MessageResolution(receiverType = JavaObject.class)
class JavaObjectMessageResolution {

    @Resolve(message = "GET_SIZE")
    abstract static class ArrayGetSizeNode extends Node {

        public Object access(JavaObject receiver) {
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

        public Object access(JavaObject receiver) {
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
        @Child private ExecuteMethodNode executeMethod;
        @Child private LookupFieldNode lookupField;
        @Child private ReadFieldNode readField;
        @Child private Node sendIsExecutableNode;
        @Child private Node sendExecuteNode;

        public Object access(JavaObject object, String name, Object[] args) {
            if (TruffleOptions.AOT || object.isNull()) {
                throw UnsupportedMessageException.raise(INVOKE);
            }

            boolean isStatic = object.isStaticClass();
            Class<?> lookupClass = object.getLookupClass();

            // (1) look for a method; if found, invoke it on obj.
            JavaMethodDesc foundMethod = lookupMethod().execute(lookupClass, name, isStatic);
            if (foundMethod != null) {
                return executeMethod().execute(foundMethod, object.obj, args, object.languageContext);
            }

            // (2) look for a field; if found, read its value and if that IsExecutable, Execute it.
            JavaFieldDesc foundField = lookupField().execute(lookupClass, name, isStatic);
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
                lookupMethod = insert(LookupMethodNode.create());
            }
            return lookupMethod;
        }

        private ExecuteMethodNode executeMethod() {
            if (executeMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeMethod = insert(ExecuteMethodNode.create());
            }
            return executeMethod;
        }

        private LookupFieldNode lookupField() {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNode.create());
            }
            return lookupField;
        }

        private ReadFieldNode readField() {
            if (readField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readField = insert(ReadFieldNode.create());
            }
            return readField;
        }
    }

    @Resolve(message = "IS_INSTANTIABLE")
    abstract static class IsInstantiableObjectNode extends Node {
        @Child private LookupConstructorNode lookupConstructor;

        public Object access(JavaObject receiver) {
            if (TruffleOptions.AOT) {
                return false;
            }
            return receiver.isClass() && lookupConstructor().execute(receiver.asClass()) != null;
        }

        private LookupConstructorNode lookupConstructor() {
            if (lookupConstructor == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupConstructor = insert(LookupConstructorNode.create());
            }
            return lookupConstructor;
        }
    }

    @Resolve(message = "NEW")
    abstract static class NewNode extends Node {
        private static final Message NEW = Message.NEW;
        @Child private LookupConstructorNode lookupConstructor;
        @Child private ExecuteMethodNode executeMethod;
        @Child private ToJavaNode toJava;

        public Object access(JavaObject receiver, Object[] args) {
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(NEW);
            }

            if (receiver.isClass()) {
                Class<?> javaClass = receiver.asClass();
                if (javaClass.isArray()) {
                    return newArray(receiver, args);
                }

                JavaMethodDesc constructor = lookupConstructor().execute(javaClass);
                if (constructor != null) {
                    return executeMethod().execute(constructor, null, args, receiver.languageContext);
                }
            }
            throw UnsupportedMessageException.raise(NEW);
        }

        private Object newArray(JavaObject receiver, Object[] args) {
            if (args.length != 1) {
                throw ArityException.raise(1, args.length);
            }
            if (toJava == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJava = insert(ToJavaNode.create());
            }
            int length;
            try {
                length = (int) toJava.execute(args[0], int.class, null, receiver.languageContext);
            } catch (ClassCastException | NullPointerException e) {
                // conversion failed by ToJavaNode
                throw UnsupportedTypeException.raise(e, args);
            }
            Object array = Array.newInstance(receiver.asClass().getComponentType(), length);
            return JavaObject.forObject(array, receiver.languageContext);
        }

        private LookupConstructorNode lookupConstructor() {
            if (lookupConstructor == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupConstructor = insert(LookupConstructorNode.create());
            }
            return lookupConstructor;
        }

        private ExecuteMethodNode executeMethod() {
            if (executeMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeMethod = insert(ExecuteMethodNode.create());
            }
            return executeMethod;
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class NullCheckNode extends Node {

        public Object access(JavaObject object) {
            return object.isNull();
        }

    }

    @Resolve(message = "IS_BOXED")
    abstract static class BoxedCheckNode extends Node {
        @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

        public Object access(JavaObject object) {
            return JavaInteropAccessor.isGuestPrimitive(object.obj);
        }

    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNode extends Node {
        @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

        public Object access(JavaObject object) {
            if (JavaInteropAccessor.isGuestPrimitive(object.obj)) {
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

        public Object access(JavaObject object, Number index) {
            if (arrayRead == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                arrayRead = insert(ArrayReadNode.create());
            }
            return arrayRead.executeWithTarget(object, index);
        }

        public Object access(JavaObject object, String name) {
            if (TruffleOptions.AOT || object.isNull()) {
                throw UnsupportedMessageException.raise(Message.READ);
            }
            boolean isStatic = object.isStaticClass();
            Class<?> lookupClass = object.getLookupClass();
            JavaFieldDesc foundField = lookupField().execute(lookupClass, name, isStatic);
            if (foundField != null) {
                return readField().execute(foundField, object);
            }
            JavaMethodDesc foundMethod = lookupMethod().execute(lookupClass, name, isStatic);
            if (foundMethod != null) {
                return new JavaFunctionObject(foundMethod, object.obj, object.languageContext);
            }
            if (isStatic) {
                LookupInnerClassNode lookupInnerClassNode = lookupInnerClass();
                if ("class".equals(name)) {
                    return JavaObject.forClass(lookupClass, object.languageContext);
                }
                Class<?> innerclass = lookupInnerClassNode.execute(lookupClass, name);
                if (innerclass != null) {
                    return JavaObject.forStaticClass(innerclass, object.languageContext);
                }
            }
            throw UnknownIdentifierException.raise(name);
        }

        private ReadFieldNode readField() {
            if (readField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readField = insert(ReadFieldNode.create());
            }
            return readField;
        }

        private LookupFieldNode lookupField() {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNode.create());
            }
            return lookupField;
        }

        private LookupMethodNode lookupMethod() {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupMethodNode.create());
            }
            return lookupMethod;
        }

        private LookupInnerClassNode lookupInnerClass() {
            if (lookupInnerClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupInnerClass = insert(LookupInnerClassNode.create());
            }
            return lookupInnerClass;
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private ArrayWriteNode arrayWrite;
        @Child private LookupFieldNode lookupField;
        @Child private WriteFieldNode writeField;

        public Object access(JavaObject receiver, Number index, Object value) {
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

        public Object access(JavaObject receiver, String name, Object value) {
            if (TruffleOptions.AOT || receiver.isNull()) {
                throw UnsupportedMessageException.raise(Message.WRITE);
            }
            JavaFieldDesc f = lookupField().execute(receiver.getLookupClass(), name, receiver.isStaticClass());
            if (f == null) {
                throw UnknownIdentifierException.raise(name);
            }
            try {
                writeField().execute(f, receiver, value);
            } catch (ClassCastException | NullPointerException e) {
                // conversion failed by ToJavaNode
                throw UnsupportedTypeException.raise(e, new Object[]{value});
            }
            return JavaObject.NULL;
        }

        private LookupFieldNode lookupField() {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNode.create());
            }
            return lookupField;
        }

        private WriteFieldNode writeField() {
            if (writeField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeField = insert(WriteFieldNode.create());
            }
            return writeField;
        }
    }

    @Resolve(message = "REMOVE")
    abstract static class RemoveNode extends Node {
        @Child private ArrayRemoveNode arrayRemove;
        @Child private MapRemoveNode mapRemove;

        public Object access(JavaObject receiver, Number index) {
            if (arrayRemove == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                arrayRemove = insert(ArrayRemoveNode.create());
            }
            return arrayRemove.executeWithTarget(receiver, index);
        }

        public Object access(JavaObject receiver, String name) {
            if (mapRemove == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                mapRemove = insert(MapRemoveNode.create());
            }
            return mapRemove.executeWithTarget(receiver, name);
        }
    }

    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {

        public Object access(JavaObject receiver) {
            return !receiver.isNull();
        }
    }

    @Resolve(message = "KEYS")
    abstract static class KeysNode extends Node {
        @TruffleBoundary
        public Object access(JavaObject receiver, boolean includeInternal) {
            if (receiver.isNull()) {
                throw UnsupportedMessageException.raise(Message.KEYS);
            }
            String[] fields = TruffleOptions.AOT ? new String[0] : JavaInteropReflect.findUniquePublicMemberNames(receiver.getLookupClass(), receiver.isStaticClass(), includeInternal);
            return JavaObject.forObject(fields, receiver.languageContext);
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {

        @Child private KeyInfoCacheNode keyInfoCache;

        public int access(JavaObject receiver, int index) {
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
        public int access(JavaObject receiver, Number index) {
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

        public int access(JavaObject receiver, String name) {
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
                keyInfoCache = insert(KeyInfoCacheNode.create());
            }
            return keyInfoCache;
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class IsExecutableObjectNode extends Node {
        @Child private LookupFunctionalMethodNode lookupMethod;

        public Object access(JavaObject receiver) {
            if (TruffleOptions.AOT) {
                return false;
            }
            return receiver.obj != null && !receiver.isClass() && lookupFunctionalInterfaceMethod(receiver) != null;
        }

        private JavaMethodDesc lookupFunctionalInterfaceMethod(JavaObject receiver) {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupFunctionalMethodNode.create());
            }
            return lookupMethod.execute(receiver.getLookupClass());
        }
    }

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteObjectNode extends Node {
        private static final Message EXECUTE = Message.EXECUTE;
        @Child private LookupFunctionalMethodNode lookupMethod;
        @Child private ExecuteMethodNode doExecute;

        public Object access(JavaObject receiver, Object[] args) {
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(EXECUTE);
            }
            if (receiver.obj != null && !receiver.isClass()) {
                JavaMethodDesc method = lookupFunctionalInterfaceMethod(receiver);
                if (method != null) {
                    if (doExecute == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        doExecute = insert(ExecuteMethodNode.create());
                    }
                    return doExecute.execute(method, receiver.obj, args, receiver.languageContext);
                }
            }
            throw UnsupportedMessageException.raise(EXECUTE);
        }

        private JavaMethodDesc lookupFunctionalInterfaceMethod(JavaObject receiver) {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupFunctionalMethodNode.create());
            }
            return lookupMethod.execute(receiver.getLookupClass());
        }
    }
}
