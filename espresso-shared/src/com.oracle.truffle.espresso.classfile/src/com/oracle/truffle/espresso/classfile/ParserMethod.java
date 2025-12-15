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
package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_NATIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_VARARGS;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.attributes.AttributedElement;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;

/**
 * Immutable raw representation of methods in Espresso, this is the output of the parser.
 */
public final class ParserMethod implements AttributedElement {
    public static final ParserMethod[] EMPTY_ARRAY = new ParserMethod[0];

    private final int flags;
    private final Symbol<Name> name;
    private final Symbol<Signature> signature;

    public int getFlags() {
        return flags;
    }

    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public static ParserMethod create(int flags, Symbol<Name> name, Symbol<Signature> signature, Attribute[] attributes) {
        return new ParserMethod(flags, name, signature, attributes);
    }

    public Symbol<Name> getName() {
        return name;
    }

    public Symbol<Signature> getSignature() {
        return signature;
    }

    @Override
    public Attribute[] getAttributes() {
        return attributes;
    }

    ParserMethod(int flags, Symbol<Name> name, Symbol<Signature> signature, Attribute[] attributes) {
        this.flags = flags;
        this.name = name;
        this.signature = signature;
        this.attributes = attributes;
    }

    /**
     * Checks whether the declared method denoted by those arguments is a signature polymorphic
     * method according to JVMS-2.9.3.
     */
    public static boolean isDeclaredSignaturePolymorphic(Symbol<Type> declaringType, Symbol<Signature> symbolicSignature, int modifiers, JavaVersion javaVersion) {
        // JVMS 2.9.3 Special Methods:
        // A method is signature polymorphic if and only if all of the following conditions hold :
        // * It is declared in the java.lang.invoke.MethodHandle or java.lang.invoke.VarHandle
        // class.
        // * It has a single formal parameter of type Object[].
        // * It has the ACC_VARARGS and ACC_NATIVE flags set.
        // * ONLY JAVA <= 8: It has a return type of Object.
        if (!ParserKlass.isSignaturePolymorphicHolderType(declaringType)) {
            return false;
        }
        int required = ACC_NATIVE | ACC_VARARGS;
        if ((modifiers & required) != required) {
            return false;
        }
        if (javaVersion.java8OrEarlier()) {
            return symbolicSignature == ParserSymbols.ParserSignatures.Object_Object_array;
        } else {
            int paramsEnd = symbolicSignature.indexOf((byte) ')');
            return symbolicSignature.subSequence(1, paramsEnd).contentEquals(ParserSymbols.ParserTypes.java_lang_Object_array);
        }
    }
}
