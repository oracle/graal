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
package com.oracle.truffle.api.interop.java;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.JavaFunctionMessageResolution.ExecuteNode.DoExecuteNode;
import com.oracle.truffle.api.nodes.Node;
import java.util.Map;
import java.util.Objects;

@MessageResolution(receiverType = JavaObject.class)
class JavaObjectMessageResolution {

    /**
     * The generated class uses {@link Specialization}.
     */
    @Resolve(message = "GET_SIZE")
    abstract static class ArrayGetSizeNode extends Node {

        public Object access(JavaObject receiver) {
            Object obj = receiver.obj;
            if (obj == null) {
                return 0;
            }
            return Array.getLength(obj);
        }

    }

    @Resolve(message = "HAS_SIZE")
    abstract static class ArrayHasSizeNode extends Node {

        public Object access(JavaObject receiver) {
            Object obj = receiver.obj;
            if (obj == null) {
                return false;
            }
            try {
                return obj instanceof Object[] || Array.getLength(obj) >= 0;
            } catch (IllegalArgumentException ex) {
                return Boolean.FALSE;
            }
        }

    }

    @Resolve(message = "INVOKE")
    abstract static class InvokeNode extends Node {

        @Child private DoExecuteNode doExecute;

        public Object access(JavaObject object, String name, Object[] args) {
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(Message.createInvoke(args.length));
            }

            Method foundMethod = JavaInteropReflect.findMethod(object, name, args);

            if (foundMethod != null) {
                if (doExecute == null || args.length != doExecute.numberOfArguments()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    doExecute = insert(new DoExecuteNode(args.length));
                }
                return doExecute.execute(foundMethod, object.obj, args);
            }

            throw UnknownIdentifierException.raise(name);
        }
    }

    @Resolve(message = "NEW")
    abstract static class NewNode extends Node {

        public Object access(JavaObject object, Object[] args) {
            return execute(object, args);
        }

        @TruffleBoundary
        private static Object execute(JavaObject receiver, Object[] args) {
            if (receiver.obj != null) {
                throw new IllegalStateException("Can only work on classes: " + receiver.obj);
            }
            if (TruffleOptions.AOT) {
                throw new IllegalStateException();
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof JavaObject) {
                    args[i] = ((JavaObject) args[i]).obj;
                }
            }
            return JavaInteropReflect.newConstructor(receiver.clazz, args);
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
            return primitive.isPrimitive(object.obj);
        }

    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNode extends Node {
        @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

        public Object access(JavaObject object) {
            Object result = primitive.toPrimitive(object.obj, null);
            return result == null ? JavaObject.NULL : result;
        }

    }

    @Resolve(message = "READ")
    abstract static class ReadFieldNode extends Node {

        @Child private ArrayReadNode read = ArrayReadNodeGen.create();

        public Object access(VirtualFrame frame, JavaObject object, Number index) {
            return read.executeWithTarget(frame, object, index);
        }

        @TruffleBoundary
        public Object access(JavaObject object, String name) {
            try {
                if (object.obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) object.obj;
                    return JavaInterop.asTruffleValue(map.get(name));
                }
                if (TruffleOptions.AOT) {
                    return JavaObject.NULL;
                }
                return JavaInteropReflect.readField(object, name);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

    }

    @Resolve(message = "WRITE")
    abstract static class WriteFieldNode extends Node {

        @Child private ToJavaNode toJava = ToJavaNodeGen.create();

        public Object access(JavaObject receiver, String name, Object value) {
            Object obj = receiver.obj;
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> map = (Map<Object, Object>) obj;
                Object convertedValue = toJava.execute(value, TypeAndClass.ANY);
                return map.put(name, convertedValue);
            }
            if (TruffleOptions.AOT) {
                throw UnsupportedMessageException.raise(Message.WRITE);
            }
            Field f = JavaInteropReflect.findField(receiver, name);
            Object convertedValue = toJava.execute(value, new TypeAndClass<>(f.getGenericType(), f.getType()));
            JavaInteropReflect.setField(obj, f, convertedValue);
            return JavaObject.NULL;
        }

        @Child private ArrayWriteNode write = ArrayWriteNodeGen.create();

        public Object access(VirtualFrame frame, JavaObject receiver, Number index, Object value) {
            return write.executeWithTarget(frame, receiver, index, value);
        }

    }

    @Resolve(message = "KEYS")
    abstract static class PropertiesNode extends Node {
        @TruffleBoundary
        public Object access(JavaObject receiver) {
            String[] fields;
            if (receiver.obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) receiver.obj;
                fields = new String[map.size()];
                int i = 0;
                for (Object key : map.keySet()) {
                    fields[i++] = Objects.toString(key, null);
                }
            } else {
                fields = TruffleOptions.AOT ? new String[0] : JavaInteropReflect.findUniquePublicMemberNames(receiver.clazz, receiver.obj != null);
            }
            return JavaInterop.asTruffleObject(fields);
        }

    }

    @Resolve(message = "KEY_INFO")
    abstract static class PropertyInfoNode extends Node {

        @TruffleBoundary
        public Object access(JavaObject receiver, String name) {
            if (receiver.obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) receiver.obj;
                if (map.containsKey(name)) {
                    return 0b111;
                } else {
                    return 0;
                }
            }
            if (TruffleOptions.AOT) {
                return 0;
            }
            if (JavaInteropReflect.isField(receiver, name)) {
                return 0b111;
            }
            if (JavaInteropReflect.isMethod(receiver, name)) {
                return 0b1111;
            }
            return 0;
        }
    }
}
