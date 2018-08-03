/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

final class JavaInteropAccessor extends Accessor {

    EngineSupport engine() {
        return engineSupport();
    }

    static final JavaInteropAccessor ACCESSOR = new JavaInteropAccessor();

    static boolean isGuestPrimitive(Object obj) {
        return (obj instanceof Boolean ||
                        obj instanceof Byte ||
                        obj instanceof Short ||
                        obj instanceof Integer ||
                        obj instanceof Long ||
                        obj instanceof Float ||
                        obj instanceof Double ||
                        obj instanceof Character ||
                        obj instanceof String);
    }

    @Override
    protected JavaInteropSupport javaInteropSupport() {
        return new JavaInteropSupport() {
            @Override
            public Node createToJavaNode() {
                return ToJavaNode.create();
            }

            @Override
            public Object toJava(Node javaNode, Class<?> rawType, Type genericType, Object value, Object polyglotContext) {
                ToJavaNode toJavaNode = (ToJavaNode) javaNode;
                return toJavaNode.execute(value, rawType, genericType, polyglotContext);
            }

            @Override
            public boolean isHostObject(Object object) {
                return object instanceof JavaObject;
            }

            @Override
            public Object asHostObject(Object obj) {
                assert isHostObject(obj);
                JavaObject javaObject = (JavaObject) obj;
                return javaObject.obj;
            }

            @Override
            public Object toGuestObject(Object obj, Object languageContext) {
                return JavaInterop.asTruffleObject(obj, languageContext);
            }

            @Override
            public Object asBoxedGuestValue(Object hostObject, Object languageContext) {
                if (isGuestPrimitive(hostObject)) {
                    return JavaObject.forObject(hostObject, languageContext);
                } else if (hostObject instanceof TruffleObject) {
                    return hostObject;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalArgumentException("Provided value not an interop value.");
                }
            }

            @Override
            public boolean isHostFunction(Object object) {
                if (TruffleOptions.AOT) {
                    return false;
                }
                return object instanceof JavaFunctionObject;
            }

            @Override
            public String javaGuestFunctionToString(Object object) {
                if (TruffleOptions.AOT) {
                    return "";
                }
                return ((JavaFunctionObject) object).getDescription();
            }

            @Override
            public Object asStaticClassObject(Class<?> clazz, Object hostLanguageContext) {
                return JavaObject.forStaticClass(clazz, hostLanguageContext);
            }

            @Override
            public boolean isHostSymbol(Object object) {
                return object instanceof JavaObject && ((JavaObject) object).isStaticClass();
            }
        };
    }

}
