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
package com.oracle.svm.hosted.webimage.name;

import java.util.Arrays;
import java.util.List;

import javax.lang.model.SourceVersion;

import com.oracle.svm.webimage.NamingConvention;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class NamingConventionUtil {
    /*
     * if a method has the same signature as a ctor and is called init we would get problems,
     * therefore we use a non unicode representation (some js parsers perform worse if idents use
     * unicode) and use a keyword that is reserved in java but not js
     */
    private static final String CONSTRUCTOR_SAFE_JS_NAME = "strictfp";
    private static final String CLASS_INITIALIZER = "volatile";

    public static String formatClassName(ResolvedJavaType type) {
        assert type != null;
        String className = type.getName();
        if (className.length() == 0) {
            // must check always, would destroy image execution on violation
            JVMCIError.shouldNotReachHere("Class names must have pos length -->" + className);
        }

        return cleanTypeName(className);
    }

    public static String formatVMFunctionName(ResolvedJavaMethod m, NamingConvention conv) {
        StringBuilder sb = new StringBuilder();
        Signature sig = m.getSignature();

        for (int i = 0; i < sig.getParameterCount(false); i++) {
            JavaType t = sig.getParameterType(i, null);

            if (t instanceof ResolvedJavaType) {
                sb.append(conv.identForType((ResolvedJavaType) t));
            } else {
                sb.append(cleanTypeName(t.getName()));
            }
        }

        JavaType t = sig.getReturnType(null);

        if (t instanceof ResolvedJavaType) {
            sb.append(conv.identForType((ResolvedJavaType) t));
        } else {
            sb.append(cleanTypeName(t.getName()));
        }

        // #5 ctors have "<" and ">" in md
        sb.insert(0, "__");
        String name = m.getName();
        /*
         * Sometimes Target_java_lang_Class.forName clashes with java.lang.Class.forName As a result
         * the HostedMethod adds a "*" which is not valid in JS.
         */
        name = name.replace("*", "_");
        switch (name) {
            case "<init>":
                name = CONSTRUCTOR_SAFE_JS_NAME;
                break;
            case "<clinit>":
                name = CLASS_INITIALIZER;
        }
        sb.insert(0, name);
        /**
         * The method name of {@link com.oracle.svm.hosted.reflect.ReflectionExpandSignatureMethod}
         * starts with digits, which is not a valid JS method name.
         */
        if (!SourceVersion.isName(name)) {
            sb.insert(0, "__");
        }

        return sb.toString();
    }

    public static String cleanTypeName(String name) {
        StringBuilder sb = new StringBuilder(name);

        int index;

        // #1 path separators
        while ((index = sb.indexOf("/")) != -1) {
            sb.replace(index, index + 1, "_");
        }
        // #2 object types - can stay
        // #3 array types
        while ((index = sb.indexOf("[")) != -1) {
            sb.replace(index, index + 1, "$");
        }

        // #4 replace "." in types
        while ((index = sb.indexOf(".")) != -1) {
            sb.replace(index, index + 1, "_");
        }

        // #6 replace ";" for object types
        while ((index = sb.indexOf(";")) != -1) {
            sb.replace(index, index + 1, "_");
        }

        return sb.toString();
    }

    public static List<String> splitClassDesc(String desc) {
        String ww = desc.replace(" ", "");
        ww = ww.replace(";", "");
        ww = ww.replace("L", "");

        if (desc.contains(".")) {
            JVMCIError.shouldNotReachHere("Unknown type name specifier");
        }

        String[] idents = ww.split("/");
        return Arrays.asList(idents);
    }
}
