/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.dsl.processor.compiler;

import java.lang.reflect.*;

public abstract class AbstractCompiler implements Compiler {

    protected static Object method(Object o, String methodName) throws Exception {
        Method method = o.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(o);
    }

    protected static Object method(Object o, String methodName, Class[] paramTypes, Object... values) throws Exception {
        Method method = o.getClass().getMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(o, values);
    }

    protected static Object field(Object o, String fieldName) throws Exception {
        if (o == null) {
            return null;
        }
        Field field = o.getClass().getField(fieldName);
        field.setAccessible(true);
        return field.get(o);
    }

    protected static String parseHeader(String content) {
        int index = content.indexOf("/*");
        if (index == -1) {
            return null;
        }
        if (!content.substring(0, index).trim().equals("")) {
            // just whitespace before
            return null;
        }

        int endIndex = content.indexOf("*/", index);
        if (endIndex == -1) {
            return null;
        }
        return content.substring(index, endIndex + 2);
    }

}
