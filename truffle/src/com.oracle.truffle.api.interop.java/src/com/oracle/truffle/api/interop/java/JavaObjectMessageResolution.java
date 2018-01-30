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

        @Child private LookupMethodNode lookupMethod;
        @Child private IsApplicableByArityNode isApplicableByArityNode;
        @Child private ExecuteMethodNode executeMethod;
        @Child private LookupFieldNode lookupField;
        @Child private ReadFieldNode readField;
        @Child private Node sendIsExecutableNode;
        @Child private Node sendExecuteNode;

        public Object access(JavaObject object, String name, Object[] args) {
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(Message.createInvoke(args.length));
            }

            // (1) look for a method; if found, invoke it on obj.
            JavaMethodDesc foundMethod = lookupMethod(object, name);
            if (foundMethod != null) {
                if (isApplicableByArity(foundMethod, args.length)) {
                    return executeMethod(foundMethod, object, args);
                }
            }

            // (2) look for a field; if found, read its value and if that IsExecutable, Execute it.
            JavaFieldDesc foundField = lookupField(object, name);
            if (foundField != null) {
                Object fieldValue = readField(foundField, object);
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
                            sendExecuteNode = insert(Message.createExecute(args.length).createNode());
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

        private JavaMethodDesc lookupMethod(JavaObject object, String name) {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupMethodNode.create());
            }
            return lookupMethod.execute(object, name);
        }

        private Object executeMethod(JavaMethodDesc foundMethod, JavaObject object, Object[] args) {
            if (executeMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeMethod = insert(ExecuteMethodNode.create());
            }
            return executeMethod.execute(foundMethod, object.obj, args, object.languageContext);
        }

        private boolean isApplicableByArity(JavaMethodDesc foundMethod, int argsLength) {
            if (isApplicableByArityNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isApplicableByArityNode = insert(IsApplicableByArityNode.create());
            }
            return isApplicableByArityNode.execute(foundMethod, argsLength);
        }

        private JavaFieldDesc lookupField(JavaObject object, String name) {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNode.create());
            }
            return lookupField.execute(object, name);
        }

        private Object readField(JavaFieldDesc field, JavaObject object) {
            if (readField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readField = insert(ReadFieldNode.create());
            }
            return readField.execute(field, object);
        }
    }

    @Resolve(message = "IS_INSTANTIABLE")
    abstract static class IsInstantiableObjectNode extends Node {

        public Object access(JavaObject receiver) {
            if (TruffleOptions.AOT) {
                return false;
            }
            return receiver.isClass() && JavaClassDesc.forClass(receiver.clazz).lookupConstructor() != null;
        }
    }

    @Resolve(message = "NEW")
    abstract static class NewNode extends Node {
        @Child private ExecuteMethodNode doExecute;
        @Child private ToJavaNode toJava;

        public Object access(JavaObject receiver, Object[] args) {
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(Message.createNew(args.length));
            }

            if (receiver.isClass()) {
                if (receiver.clazz.isArray()) {
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
                    return JavaInterop.asTruffleObject(Array.newInstance(receiver.clazz.getComponentType(), length), receiver.languageContext);
                }

                JavaClassDesc classDesc = JavaClassDesc.forClass(receiver.clazz);
                JavaMethodDesc method = classDesc.lookupConstructor();
                if (method != null) {
                    if (doExecute == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        doExecute = insert(ExecuteMethodNode.create());
                    }
                    return doExecute.execute(method, null, args, receiver.languageContext);
                }
            }
            throw UnsupportedTypeException.raise(new Object[]{receiver});
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class NullCheckNode extends Node {

        public Object access(JavaObject object) {
            return object == JavaObject.NULL;
        }

    }

    @Resolve(message = "IS_BOXED")
    abstract static class BoxedCheckNode extends Node {
        @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

        public Object access(JavaObject object) {
            return JavaInterop.isPrimitive(object.obj);
        }

    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNode extends Node {
        @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

        public Object access(JavaObject object) {
            if (JavaInterop.isPrimitive(object.obj)) {
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
            if (TruffleOptions.AOT || object == JavaObject.NULL) {
                throw UnsupportedMessageException.raise(Message.READ);
            }
            JavaFieldDesc foundField = lookupField(object, name);
            if (foundField != null) {
                return readField(foundField, object);
            }
            JavaMethodDesc foundMethod = lookupMethod(object, name);
            if (foundMethod != null) {
                return new JavaFunctionObject(foundMethod, object.obj, object.languageContext);
            }
            Class<?> innerclass = lookupInnerClass(object, name);
            if (innerclass != null) {
                return JavaObject.forClass(innerclass, object.languageContext);
            }
            throw UnknownIdentifierException.raise(name);
        }

        private Object readField(JavaFieldDesc field, JavaObject object) {
            if (readField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readField = insert(ReadFieldNode.create());
            }
            return readField.execute(field, object);
        }

        private JavaFieldDesc lookupField(JavaObject object, String name) {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNode.create());
            }
            return lookupField.execute(object, name);
        }

        private JavaMethodDesc lookupMethod(JavaObject object, String name) {
            if (lookupMethod == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupMethod = insert(LookupMethodNode.create());
            }
            return lookupMethod.execute(object, name);
        }

        private Class<?> lookupInnerClass(JavaObject object, String name) {
            if (lookupInnerClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupInnerClass = insert(LookupInnerClassNode.create());
            }
            return lookupInnerClass.execute(object.clazz, name);
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
            if (TruffleOptions.AOT || receiver == JavaObject.NULL) {
                throw UnsupportedMessageException.raise(Message.WRITE);
            }
            JavaFieldDesc f = lookupField(receiver, name);
            if (f == null) {
                throw UnknownIdentifierException.raise(name);
            }
            try {
                writeField(f, receiver, value);
            } catch (ClassCastException | NullPointerException e) {
                // conversion failed by ToJavaNode
                throw UnsupportedTypeException.raise(e, new Object[]{value});
            }
            return JavaObject.NULL;
        }

        private JavaFieldDesc lookupField(JavaObject object, String name) {
            if (lookupField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupField = insert(LookupFieldNode.create());
            }
            return lookupField.execute(object, name);
        }

        private void writeField(JavaFieldDesc field, JavaObject object, Object value) {
            if (writeField == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeField = insert(WriteFieldNode.create());
            }
            writeField.execute(field, object, value);
        }
    }

    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {

        public Object access(JavaObject receiver) {
            return receiver != JavaObject.NULL;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class PropertiesNode extends Node {
        @TruffleBoundary
        public Object access(JavaObject receiver, boolean includeInternal) {
            String[] fields = TruffleOptions.AOT ? new String[0] : JavaInteropReflect.findUniquePublicMemberNames(receiver.clazz, !receiver.isClass(), includeInternal);
            return JavaInterop.asTruffleObject(fields);
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class PropertyInfoNode extends Node {

        private static final int READABLE = KeyInfo.newBuilder().setReadable(true).build();
        private static final int READABLE_WRITABLE = KeyInfo.newBuilder().setReadable(true).setWritable(true).build();
        private static final int READABLE_WRITABLE_INVOCABLE = KeyInfo.newBuilder().setReadable(true).setWritable(true).setInvocable(true).build();
        private static final int READABLE_WRITABLE_INVOCABLE_INTERNAL = KeyInfo.newBuilder().setReadable(true).setWritable(true).setInvocable(true).setInternal(true).build();

        @TruffleBoundary
        public int access(JavaObject receiver, Number index) {
            int i = index.intValue();
            if (i != index.doubleValue()) {
                // No non-integer indexes
                return 0;
            }
            if (i < 0) {
                return 0;
            }
            if (receiver.isArray()) {
                int length = Array.getLength(receiver.obj);
                if (i < length) {
                    return READABLE_WRITABLE;
                }
            } else if (receiver.obj instanceof List) {
                int length = ((List<?>) receiver.obj).size();
                if (i < length) {
                    return READABLE_WRITABLE;
                }
            }
            return 0;
        }

        @TruffleBoundary
        public int access(JavaObject receiver, String name) {
            if (TruffleOptions.AOT) {
                return 0;
            }
            if (JavaInteropReflect.isField(receiver, name)) {
                return READABLE_WRITABLE;
            }
            if (JavaInteropReflect.isMethod(receiver, name)) {
                if (JavaInteropReflect.isInternalMethod(receiver, name)) {
                    return READABLE_WRITABLE_INVOCABLE_INTERNAL;
                } else {
                    return READABLE_WRITABLE_INVOCABLE;
                }
            }
            if (JavaInteropReflect.isMemberType(receiver, name)) {
                return READABLE;
            }
            return 0;
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class IsExecutableObjectNode extends Node {

        public Object access(JavaObject receiver) {
            if (TruffleOptions.AOT) {
                return false;
            }
            return receiver.obj != null && JavaClassDesc.forClass(receiver.clazz).implementsFunctionalInterface();
        }
    }

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteObjectNode extends Node {
        @Child private ExecuteMethodNode doExecute;

        public Object access(JavaObject receiver, Object[] args) {
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(Message.createExecute(args.length));
            }
            if (receiver.obj != null) {
                assert !receiver.isClass();
                JavaMethodDesc method = JavaClassDesc.forClass(receiver.clazz).getFunctionalMethod();
                if (method != null) {
                    if (doExecute == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        doExecute = insert(ExecuteMethodNode.create());
                    }
                    return doExecute.execute(method, receiver.obj, args, receiver.languageContext);
                }
            }
            throw UnsupportedMessageException.raise(Message.createExecute(args.length));
        }
    }
}
