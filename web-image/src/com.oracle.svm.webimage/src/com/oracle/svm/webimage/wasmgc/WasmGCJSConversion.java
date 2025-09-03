/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.wasmgc;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.webimage.api.JSError;
import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.webimage.functionintrinsics.JSConversion;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.wasm.WasmForeignCallDescriptor;
import com.oracle.svm.webimage.wasmgc.annotation.WasmExport;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;

@AutomaticallyRegisteredImageSingleton(JSConversion.class)
@Platforms(WebImageWasmGCPlatform.class)
public class WasmGCJSConversion extends JSConversion {
    public static final WasmForeignCallDescriptor SET_JS_NATIVE = new WasmForeignCallDescriptor("setJSNative", void.class, new Class<?>[]{JSValue.class, WasmExtern.class});
    public static final WasmForeignCallDescriptor EXTRACT_JS_NATIVE = new WasmForeignCallDescriptor("extractJSNative", WasmExtern.class, new Class<?>[]{JSValue.class});

    @WasmExport(value = "object.getclass", comment = "Get Class from object")
    public static Class<?> getClass(Object o) {
        return o.getClass();
    }

    @WasmExport(value = "class.getname", comment = "Get fully qualified class name")
    public static String getClassName(Class<?> clazz) {
        return clazz.getName();
    }

    @WasmExport(value = "conversion.classfromencoding", comment = "Lookup class instance from metadata encoding")
    public static Class<?> classFromEncoding(String encoding) {
        return WasmGCMetadata.lookupClass(encoding);
    }

    @WasmExport("class.isprimitive")
    public static boolean isPrimitive(Class<?> clazz) {
        return clazz.isPrimitive();
    }

    @WasmExport(value = "object.isinstance", comment = "Checks whether the given object is a subtype of the given class")
    public static boolean isInstance(Object o, Class<?> clazz) {
        if (o == null) {
            return true;
        }
        return clazz.isAssignableFrom(o.getClass());
    }

    @WasmExport(value = "class.isjavalangobject", comment = "Checks whether the given class is Object.class")
    public static boolean isJavaLangObject(Class<?> clazz) {
        return clazz == Object.class;
    }

    @WasmExport(value = "class.isjavalangclass", comment = "Checks whether the given class is Class.class")
    public static boolean isJavaLangClass(Class<?> clazz) {
        return clazz == Class.class;
    }

    @WasmExport(value = "class.superclass", comment = "Gets superclass of given non-primitive non-object class")
    public static Class<?> getSuperClass(Class<?> clazz) {
        assert !clazz.isPrimitive() : "Cannot get superclass of primitive class: " + clazz;
        assert clazz != Object.class : "Cannot get superclass of java.lang.Object";
        return clazz.getSuperclass();
    }

    @WasmExport(value = "class.getboxedhub", comment = "Boxed class for given primitive class")
    public static Class<?> getBoxedHub(Class<?> clazz) {
        if (clazz == Boolean.TYPE) {
            return Boolean.class;
        }
        if (clazz == Byte.TYPE) {
            return Byte.class;
        }
        if (clazz == Short.TYPE) {
            return Short.class;
        }
        if (clazz == Character.TYPE) {
            return Character.class;
        }
        if (clazz == Integer.TYPE) {
            return Integer.class;
        }
        if (clazz == Long.TYPE) {
            return Long.class;
        }
        if (clazz == Float.TYPE) {
            return Float.class;
        }
        if (clazz == Double.TYPE) {
            return Double.class;
        }
        if (clazz == Void.TYPE) {
            return Void.class;
        }

        throw VMError.shouldNotReachHere("Tried getting boxed hub for non-primitive: " + clazz);
    }

    @WasmExport(value = "unbox.boolean")
    public static boolean unboxBoolean(Boolean x) {
        return x;
    }

    @WasmExport(value = "unbox.byte")
    public static byte unboxByte(Byte x) {
        return x;
    }

    @WasmExport(value = "unbox.short")
    public static short unboxShort(Short x) {
        return x;
    }

    @WasmExport(value = "unbox.char")
    public static char unboxChar(Character x) {
        return x;
    }

    @WasmExport(value = "unbox.int")
    public static int unboxInt(Integer x) {
        return x;
    }

    @WasmExport(value = "unbox.float")
    public static float unboxFloat(Float x) {
        return x;
    }

    @WasmExport(value = "unbox.long")
    public static long unboxLong(Long x) {
        return x;
    }

    @WasmExport("convert.isjavalangclass")
    public static boolean isJavaLangClass(Object o) {
        return o instanceof Class;
    }

    @WasmExport(value = "string.tochars", comment = "Create Java char array from Java string")
    public static char[] stringFromCharArray(String str) {
        return str.toCharArray();
    }

    @WasmExport(value = "convert.throwjserror", comment = "Throw JSError of given object")
    public static void throwExceptionFromJS(Object thrownObject) throws Throwable {
        if (thrownObject instanceof Throwable t) {
            throw t;
        }

        throw new JSError(thrownObject);
    }

    @Override
    public void setJavaScriptNativeImpl(JSValue self, Object jsNative) {
        if (jsNative.getClass().isArray()) {
            /*
             * TODO GR-60603 Deal with coercion rules for arrays. The JS backend and the existing
             * conversion code assumes that Java arrays are also JS objects, which they're not in
             * WasmGC.
             */
            throw VMError.unsupportedFeature("Cannot coerce arrays: " + jsNative.getClass().getTypeName());
        }

        assert jsNative instanceof WasmExtern : "Tried to store non-JS value in " + self.getClass() + ": " + jsNative.getClass();
        setJSNative0(SET_JS_NATIVE, self, (WasmExtern) jsNative);
    }

    @Override
    public Object extractJavaScriptNativeImpl(JSValue self) {
        return extractJSNative0(EXTRACT_JS_NATIVE, self);
    }

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native void setJSNative0(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, JSValue jsValue, WasmExtern extern);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native WasmExtern extractJSNative0(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, JSValue jsValue);

}
