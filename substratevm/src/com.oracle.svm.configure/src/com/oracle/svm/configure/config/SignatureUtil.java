/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.util.ModuleSupport;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;

public class SignatureUtil {
    public static String[] toParameterTypes(String signature) {
        List<String> list = new ArrayList<>();
        int position = 1;
        int arrayDimensions = 0;
        while (signature.charAt(position) != ')') {
            String type = null;
            if (signature.charAt(position) == '[') {
                arrayDimensions++;
            } else if (signature.charAt(position) == 'L') {
                int end = signature.indexOf(';', position + 1);
                type = MetaUtil.internalNameToJava(signature.substring(position, end + 1), true, false);
                position = end;
            } else {
                type = JavaKind.fromPrimitiveOrVoidTypeChar(signature.charAt(position)).toString();
            }
            position++;
            if (type != null) {
                String s = type;
                for (; arrayDimensions > 0; arrayDimensions--) {
                    s += "[]";
                }
                list.add(s);
            }
        }
        // ignore return type
        if (arrayDimensions > 0) {
            throw new IllegalArgumentException("Invalid array in signature: " + signature);
        }
        return list.toArray(new String[0]);
    }

    public static String toInternalSignature(List<?> parameterTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (Object type : parameterTypes) {
            sb.append(MetaUtil.toInternalName(type.toString()));
        }
        return sb.append(')').toString();
    }

    public static String toInternalClassName(String qualifiedForNameString) {
        assert qualifiedForNameString.indexOf('/') == -1 : "Requires qualified Java name, not internal representation";
        assert !qualifiedForNameString.endsWith("[]") : "Requires Class.forName syntax, for example '[Ljava.lang.String;'";
        String s = qualifiedForNameString;
        int n = 0;
        while (n < s.length() && s.charAt(n) == '[') {
            n++;
        }
        if (n > 0) { // transform to Java source syntax
            StringBuilder sb = new StringBuilder(s.length() + n);
            if (s.charAt(n) == 'L' && s.charAt(s.length() - 1) == ';') {
                sb.append(s, n + 1, s.length() - 1); // cut off leading '[' and 'L' and trailing ';'
            } else if (n == s.length() - 1) {
                sb.append(JavaKind.fromPrimitiveOrVoidTypeChar(s.charAt(n)).getJavaName());
            } else {
                throw new IllegalArgumentException();
            }
            for (int i = 0; i < n; i++) {
                sb.append("[]");
            }
            s = sb.toString();
        }
        return s;
    }
}

@AutomaticFeature
class SignatureUtilFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!access.getApplicationClassPath().isEmpty()) {
            ModuleSupport.exportAndOpenPackageToClass("jdk.internal.vm.ci", "jdk.vm.ci.meta", false, SignatureUtil.class);
        }
    }
}
