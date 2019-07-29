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
}
