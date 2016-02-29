/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Modifier;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

/**
 * {@link ReadFieldBaseNode} is a generated class that uses {@link Specialization}.
 */
@AcceptMessage(value = "READ", receiverType = JavaObject.class, language = JavaInteropLanguage.class)
final class ReadFieldNode extends ReadFieldBaseNode {
    @Override
    public Object access(VirtualFrame frame, JavaObject object, int index) {
        Object obj = object.obj;
        Object val = Array.get(obj, index);

        if (ToJavaNode.isPrimitive(val)) {
            return val;
        }
        return JavaInterop.asTruffleObject(val);
    }

    @Override
    public Object access(VirtualFrame frame, JavaObject object, String name) {
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
