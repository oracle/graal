/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.processor;

public final class ProcessorUtils {
    /** Appends "import " and prepend ";\n" to the given string. */
    public static String imports(String str) {
        return "import " + str + ";\n";
    }

    /** Appends and prepends a quotation mark around the given string. */
    public static String stringify(String str) {
        return '\"' + str + '\"';
    }

    /** "[modifiers] [returnType] methodName(comma-separated arguments)". */
    public static String methodDeclaration(String modifiers, String returnType, String methodName, String[] arguments) {
        StringBuilder str = new StringBuilder();
        if (modifiers != null) {
            str.append(modifiers).append(" ");
        }
        if (returnType != null) {
            str.append(returnType).append(" ");
        }
        str.append(methodName).append("(");
        str.append(listToString(arguments, ", "));
        str.append(")");
        return str.toString();
    }

    /** "[modifiers] type fieldName[ = defaultValue];". */
    public static String fieldDeclaration(String modifiers, String type, String fieldName, String defaultValue) {
        return (modifiers == null ? "" : (modifiers + " ")) + type + " " + fieldName + ((defaultValue == null) ? "" : (" = " + defaultValue)) + ";";
    }

    /** "className argName". */
    public static String argument(String className, String argName) {
        return className + " " + argName;
    }

    /** "varName = value;". */
    public static String assignment(String varName, String value) {
        return varName + " = " + value + ";";
    }

    /** "[receiver.]methodName(comma-separated args)". */
    public static String call(String receiver, String methodName, String[] args) {
        StringBuilder str = new StringBuilder();
        if (receiver != null) {
            str.append(receiver).append(".");
        }
        str.append(methodName);
        str.append("(");
        str.append(listToString(args, ", "));
        str.append(")");
        return str.toString();
    }

    /** Returns the given string whose first letter is upper case. */
    public static String capitalize(String str) {
        if (str.length() > 0) {
            char[] c = str.toCharArray();
            c[0] = Character.toUpperCase(c[0]);
            return new String(c);
        }
        return "";
    }

    /** Returns the given string whose first letter is lower case. */
    public static String decapitalize(String str) {
        if (str.length() > 0) {
            char[] c = str.toCharArray();
            c[0] = Character.toLowerCase(c[0]);
            return new String(c);
        }
        return "";
    }

    /**
     * Transforms a Java-like class name declaration to a member name for it. Acts the same as
     * {@link #decapitalize(String)} for strings starting with a single capital letter, but for
     * strings with multiple upper case characters at the beginning, it lower-cases the start until
     * the last upper-case.
     * <p>
     * For example:
     * <ul>
     * <li>"JVMSomeThing" -> "jvmSomeThing"</li>
     * <li>"AClass" -> "aClass"</li>
     * <li>"ClassName" -> "className"</li>
     * </ul>
     */
    public static String toMemberName(String str) {
        if (str.length() >= 2) {
            if (Character.isUpperCase(str.charAt(0)) && (Character.isUpperCase(str.charAt(1)))) {
                // the first two characters are upper-case: need some special handling.
                char[] c = str.toCharArray();
                int i = 0;
                while (i < c.length && Character.isUpperCase(c[i])) {
                    // Lower case everything until we see a lower case.
                    c[i] = Character.toLowerCase(c[i]);
                    i++;
                }
                if (i != c.length) {
                    // If we are not at the end, we went too far by one. Re-upper case the lastly
                    // modified character.
                    c[i - 1] = Character.toUpperCase(c[i - 1]);
                }
                return new String(c);
            }
        }
        // Handles empty string
        return decapitalize(str);
    }

    /**
     * Converts a C-like name convention to a java like class name convention. In practice, this
     * removes underscores, and converts the character following it to upper case, along with
     * converting the first letter to upper case.
     * <p>
     * For example:
     * <ul>
     * <li>"native_member" -> "NativeMember"</li>
     * <li>"_struct_type" -> "StructType"</li>
     * </ul>
     */
    public static String removeUnderscores(String strName) {
        StringBuilder builder = new StringBuilder();
        int current = 0;
        int index;
        while ((current < strName.length()) && ((index = strName.indexOf("_", current)) >= 0)) {
            if (index > current) {
                String substring = strName.substring(current, index);
                builder.append(capitalize(substring));
            }
            current = index + 1;
        }
        String substring = strName.substring(current);
        builder.append(capitalize(substring));
        return builder.toString();
    }

    private static String listToString(String[] strs, String separator) {
        StringBuilder str = new StringBuilder();
        boolean first = true;
        for (String arg : strs) {
            if (!first) {
                str.append(separator);
            }
            str.append(arg);
            first = false;
        }
        return str.toString();
    }
}
