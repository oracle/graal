/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.java.LambdaUtils;

import com.oracle.svm.common.type.TypeResult;

public class ClassUtils {
    public static String hashClassData(byte[] classData, int offset, int length) {
        try { // Only for lookups, cryptographic properties are irrelevant
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(classData, offset, length);
            return LambdaUtils.toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    public static TypeResult<Class<?>> getPrimitiveTypeByName(String className) {
        switch (className) {
            case "boolean":
                return TypeResult.forClass(boolean.class);
            case "char":
                return TypeResult.forClass(char.class);
            case "float":
                return TypeResult.forClass(float.class);
            case "double":
                return TypeResult.forClass(double.class);
            case "byte":
                return TypeResult.forClass(byte.class);
            case "short":
                return TypeResult.forClass(short.class);
            case "int":
                return TypeResult.forClass(int.class);
            case "long":
                return TypeResult.forClass(long.class);
            case "void":
                return TypeResult.forClass(void.class);
        }
        return null;
    }
}
