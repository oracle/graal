/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Duplicated utilities from {@link Objects} and {@link Arrays} to remove the dependencies on the
 * standard library e.g. avoid a breakpoint on {@link Objects#requireNonNull(Object)} to the affect
 * the debugger/interpreter implementation.
 */
public final class MetadataUtil {

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    public static <T> T requireNonNull(T obj) {
        obj.getClass(); // trigger implicit NPE
        return obj;
    }

    public static int hashCode(Object o) {
        return o != null ? o.hashCode() : 0;
    }

    public static int arrayHashCode(Object[] a) {
        if (a == null) {
            return 0;
        }

        int result = 1;

        for (Object element : a) {
            result = 31 * result + (element == null ? 0 : element.hashCode());
        }

        return result;
    }

    public static boolean arrayEquals(Object[] a, Object[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }

        int length = a.length;
        if (a2.length != length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (!MetadataUtil.equals(a[i], a2[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Cheap alternative to {@link String#format(String, Object...)} that only provides simple
     * modifiers.
     */
    // GR-55171: Consolidate into LogUtils
    public static String fmt(String simpleFormat, Object... args) throws IllegalArgumentException {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        int argIndex = 0;
        while (index < simpleFormat.length()) {
            char ch = simpleFormat.charAt(index++);
            if (ch == '%') {
                if (index >= simpleFormat.length()) {
                    throw new IllegalArgumentException("An unquoted '%' character cannot terminate a format specification");
                }
                char specifier = simpleFormat.charAt(index++);
                switch (specifier) {
                    case 's' -> {
                        if (argIndex >= args.length) {
                            throw new IllegalArgumentException("Too many format specifiers or not enough arguments");
                        }
                        sb.append(args[argIndex++]);
                    }
                    case '%' -> sb.append('%');
                    case 'n' -> sb.append(System.lineSeparator());
                    default -> throw new IllegalArgumentException("Illegal format specifier: " + specifier);
                }
            } else {
                sb.append(ch);
            }
        }
        if (argIndex < args.length) {
            throw new IllegalArgumentException("Not enough format specifiers or too many arguments");
        }
        return sb.toString();
    }

    public static String toUniqueString(Signature signature) {
        return signature.toMethodDescriptor();
    }

    public static String toUniqueString(JavaMethod method) {
        return fmt("%s.%s/%s",
                        toUniqueString(method.getDeclaringClass()),
                        method.getName(),
                        toUniqueString(method.getSignature()));
    }

    public static String toUniqueString(JavaType type) {
        return type.getName();
    }

    public static String toUniqueString(JavaField field) {
        return fmt("%s.%s/%s",
                        toUniqueString(field.getDeclaringClass()),
                        field.getName(),
                        toUniqueString(field.getType()));
    }

    private static final String METADATA_SUFFIX = ".metadata";

    public static String metadataFileName(String binaryFileName) {
        assert !binaryFileName.isEmpty();
        assert !binaryFileName.endsWith(File.pathSeparator);
        return binaryFileName + METADATA_SUFFIX;
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "path.getParent() is never null")
    public static Path metadataFilePath(Path binaryFilePath) {
        String binaryFileName = binaryFilePath.getFileName().toString();
        String metadataFileName = metadataFileName(binaryFileName);
        return binaryFilePath.resolveSibling(metadataFileName);
    }
}
