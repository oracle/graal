/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.runtime.EspressoContext;

@ExportLibrary(InteropLibrary.class)
public class EspressoTopScope implements TruffleObject {
    public static final String JNI = "<JNI>";
    public static final String JAVA_VM = "<JavaVM>";

    private final EspressoContext context;

    public EspressoTopScope(EspressoContext context) {
        this.context = context;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return EspressoLanguage.class;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        if (JNI.equals(member)) {
            // TODO
            return true;
        }
        if (JAVA_VM.equals(member)) {
            return context.getVM().getJavaVM();
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        if (JNI.equals(member)) {
            return true;
        }
        if (JAVA_VM.equals(member)) {
            return true;
        }
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        EconomicSet<String> members = EconomicSet.create();
        members.add(JNI);
        members.add(JAVA_VM);
        return new KeysArray(members.toArray(new String[members.size()]));
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage final Object toDisplayString(@SuppressWarnings("unused")boolean allowSideEffects) {
        return "JavaTopScope";
    }
}
