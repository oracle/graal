/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.meta.MetaUtil;

/**
 * A utility for mangling Java method name and signatures into C function names. Support is also
 * provided for demangling.
 *
 * @see "http://java.sun.com/j2se/1.5.0/docs/guide/jni/spec/design.html#wp615"
 */
public final class Mangle {

    private Mangle() {
        /* no instances */
    }

    // Mangling

    private static String mangle(String name) {
        return mangle(name, false);
    }

    /**
     * Mangles a given string such that it can be represented as (part of) a valid C function name.
     */
    private static String mangle(String name, boolean isTruffleMangle) {
        final StringBuilder mangledName = new StringBuilder(100);
        final int length = name.length();
        for (int i = 0; i < length; i++) {
            final char ch = name.charAt(i);
            if (isAlphaNumeric(ch)) {
                mangledName.append(ch);
            } else if (ch == '_') {
                mangledName.append("_1");
            } else if (ch == '.') {
                mangledName.append("_");
            } else if (ch == ';') {
                mangledName.append("_2");
            } else if (ch == '[') {
                mangledName.append("_3");
            } else if (isTruffleMangle && ch == '$') {
                mangledName.append(ch);
            } else {
                mangledName.append(mangleChar(ch));
            }
        }

        return mangledName.toString();
    }

    /**
     * The delimiter in the string returned by mangleMethod(ByteString<Type>, String, ByteString
     * <Signature>, boolean) separating the short mangled form from the suffix to be added to obtain
     * the long mangled form.
     */
    public static final char LONG_NAME_DELIMITER = ' ';

    /**
     * Mangles a Java method to the symbol(s) to be used when binding it to a native function. If
     * {@code signature} is {@code null}, then a non-qualified symbol is returned. Otherwise, a
     * qualified symbol is returned. A qualified symbol has its non-qualified prefix separated from
     * its qualifying suffix by {@link #LONG_NAME_DELIMITER} if {@code splitSuffix} is {@code true}.
     *
     * @param declaringClass a fully qualified class descriptor
     * @param name a Java method name (not checked here for validity)
     * @param signature if non-null, a method signature to include in the mangled name
     * @param splitSuffix determines if {@link #LONG_NAME_DELIMITER} should be used as described
     *            above
     * @return the symbol for the C function as described above
     */
    public static String mangleMethod(Symbol<Type> declaringClass, String name, Symbol<Signature> signature, boolean splitSuffix) {
        final StringBuilder result = new StringBuilder(100);
        final String declaringClassName = MetaUtil.internalNameToJava(declaringClass.toString(), true, false);
        result.append("Java_").append(mangle(declaringClassName)).append('_').append(mangle(name));
        if (signature != null) {
            if (splitSuffix) {
                result.append(LONG_NAME_DELIMITER);
            }
            result.append("__");
            final String sig = signature.toString();
            final String parametersSignature = sig.substring(1, sig.lastIndexOf(')')).replace('/', '.').replace('$', '.');
            result.append(mangle(parametersSignature));
        }
        return result.toString();
    }

    /**
     * Mangle a method name and signature to the symbols to be used for a Truffle jni-named method
     * call. Signature must not be <code>null</code>. Truffle jni names are made up of the method
     * name, the return type and the parameter types. Note that the declaring class and the 'Java_'
     * marker is omitted from the result here.
     * 
     * @param methodName a Java method name (not checked here for validity)
     * @param signature if non-null, a method signature to include in the mangled name
     * @return a mangled jni-style string as described above
     */
    public static String truffleJniMethodName(String methodName, Symbol<Signature> signature) {
        assert signature != null;
        final StringBuilder result = new StringBuilder(100);
        result.append(mangle(methodName)).append("__");
        final String sig = signature.toString();
        final String returnType = sig.substring(sig.lastIndexOf(')') + 1).replace('/', '.');
        result.append(mangle(returnType, true));
        final String parametersSignature = sig.substring(1, sig.lastIndexOf(')')).replace('/', '.');
        result.append(mangle(parametersSignature, true));
        return result.toString();
    }

    private static String mangleChar(char ch) {
        final String s = Integer.toHexString(ch);
        assert s.length() <= 4;
        return "_0" + String.format("%4s", s).replace(' ', '0');
    }

    private static boolean isAlphaNumeric(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
    }
}
