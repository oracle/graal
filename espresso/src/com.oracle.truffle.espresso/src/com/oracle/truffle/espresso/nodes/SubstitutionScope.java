/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.MethodParametersAttribute;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.impl.Method;

@ExportLibrary(InteropLibrary.class)
final class SubstitutionScope implements TruffleObject {
    @CompilationFinal(dimensions = 1) private final Object[] args;
    private final Method method;
    private String[] paramNames;

    SubstitutionScope(Object[] arguments, Method.MethodVersion method) {
        this.args = arguments;
        this.method = method.getMethod();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isScope() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return EspressoLanguage.class;
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        try {
            int index = Integer.parseInt(member);
            if (index < 0 || index >= args.length) {
                throw UnknownIdentifierException.create(member);
            }
            return args[index];
        } catch (NumberFormatException e) {
            // OK, see if we have parameter names as a fallback.
            // The main use case which is JDWP doesn't use anything but slots,
            // so this branch should rarely be taken.
            String[] names = paramNames;
            if (names == null) {
                names = paramNames = fetchNames();
            }
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(member)) {
                    return args[i];
                }
            }
            throw UnknownIdentifierException.create(member);
        }
    }

    private String[] fetchNames() {
        MethodParametersAttribute methodParameters = (MethodParametersAttribute) method.getAttribute(Names.MethodParameters);

        if (methodParameters == null) {
            return new String[0];
        }
        // verify parameter attribute first
        MethodParametersAttribute.Entry[] entries = methodParameters.getEntries();
        int cpLength = method.getConstantPool().length();
        for (MethodParametersAttribute.Entry entry : entries) {
            int nameIndex = entry.getNameIndex();
            if (nameIndex < 0 || nameIndex >= cpLength) {
                return new String[0];
            }
            if (nameIndex != 0 && method.getConstantPool().tagAt(nameIndex) != ConstantPool.Tag.UTF8) {
                return new String[0];
            }
        }
        String[] result = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            MethodParametersAttribute.Entry entry = entries[i];
            // For a 0 index, give an empty name.
            String name;
            if (entry.getNameIndex() != 0) {
                name = method.getConstantPool().utf8At(entry.getNameIndex(), "parameter name").toString();
            } else {
                name = "";
            }
            result[i] = name;
        }
        return result;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        try {
            int index = Integer.parseInt(member);
            return 0 <= index && index < args.length;
        } catch (NumberFormatException e) {
            // OK, see if we have parameter names as a fallback.
            // The main use case which is JDWP doesn't use anything but slots,
            // so this branch should rarely be taken.
            String[] names = paramNames;
            if (names == null) {
                names = paramNames = fetchNames();
            }
            for (String name : names) {
                if (name.equals(member)) {
                    return true;
                }
            }
            return false;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return method.getNameAsString();
    }

}
