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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.java.JavaFunctionMessageResolution.ExecuteNode.DoExecuteNode;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = JavaObject.class, language = JavaInteropLanguage.class)
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

        public Object access(VirtualFrame frame, JavaObject object, String name, Object[] args) {
            for (Method m : object.clazz.getMethods()) {
                if (m.getName().equals(name)) {
                    if (m.getParameterTypes().length == args.length || m.isVarArgs()) {
                        if (doExecute == null || args.length != doExecute.numberOfArguments()) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            doExecute = insert(new DoExecuteNode(args.length));
                        }
                        return doExecute.execute(frame, m, object.obj, args);
                    }
                }
            }
            throw UnknownIdentifierException.raise(name);
        }

    }

    @Resolve(message = "NEW")
    abstract static class NewNode extends Node {

        public Object access(JavaObject object, Object[] args) {
            return execute(object, args);
        }

        private static Object execute(JavaObject receiver, Object[] args) {
            if (receiver.obj != null) {
                throw new IllegalStateException("Can only work on classes: " + receiver.obj);
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof JavaObject) {
                    args[i] = ((JavaObject) args[i]).obj;
                }
            }
            IllegalStateException ex = new IllegalStateException("No suitable constructor found for " + receiver.clazz);
            for (Constructor<?> constructor : receiver.clazz.getConstructors()) {
                try {
                    Object ret = constructor.newInstance(args);
                    if (ToJavaNode.isPrimitive(ret)) {
                        return ret;
                    }
                    return JavaInterop.asTruffleObject(ret);
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException instEx) {
                    ex = new IllegalStateException(instEx);
                }
            }
            throw ex;
        }

    }

    @Resolve(message = "IS_NULL")
    abstract static class NullCheckNode extends Node {

        public Object access(JavaObject object) {
            return object == JavaObject.NULL;
        }

    }

    @Resolve(message = "READ")
    abstract static class ReadFieldNode extends Node {

        @Child private ArrayReadNode read = ArrayReadNodeGen.create();

        public Object access(VirtualFrame frame, JavaObject object, Number index) {
            return read.executeWithTarget(frame, object, index);
        }

        public Object access(JavaObject object, String name) {
            try {
                Object obj = object.obj;
                final boolean onlyStatic = obj == null;
                Object val;
                try {
                    final Field field = object.clazz.getField(name);
                    final boolean isStatic = (field.getModifiers() & Modifier.STATIC) != 0;
                    if (onlyStatic != isStatic) {
                        throw new NoSuchFieldException();
                    }
                    val = field.get(obj);
                } catch (NoSuchFieldException ex) {
                    for (Method m : object.clazz.getMethods()) {
                        final boolean isStatic = (m.getModifiers() & Modifier.STATIC) != 0;
                        if (onlyStatic != isStatic) {
                            continue;
                        }
                        if (m.getName().equals(name)) {
                            return new JavaFunctionObject(m, obj);
                        }
                    }
                    throw (NoSuchFieldError) new NoSuchFieldError(ex.getMessage()).initCause(ex);
                }
                if (ToJavaNode.isPrimitive(val)) {
                    return val;
                }
                return JavaInterop.asTruffleObject(val);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

    }

    @Resolve(message = "WRITE")
    abstract static class WriteFieldNode extends Node {

        @Child private ToJavaNode toJava = new ToJavaNode();

        public Object access(VirtualFrame frame, JavaObject receiver, String name, Object value) {
            try {
                Object obj = receiver.obj;
                try {
                    Class<?> fieldType = receiver.clazz.getField(name).getType();
                    Object convertedValue = toJava.convert(frame, value, fieldType);
                    receiver.clazz.getField(name).set(obj, convertedValue);
                    return JavaObject.NULL;
                } catch (NoSuchFieldException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Child private ArrayWriteNode write = ArrayWriteNodeGen.create();

        public Object access(VirtualFrame frame, JavaObject receiver, Number index, Object value) {
            return write.executeWithTarget(frame, receiver, index, value);
        }

    }

}
