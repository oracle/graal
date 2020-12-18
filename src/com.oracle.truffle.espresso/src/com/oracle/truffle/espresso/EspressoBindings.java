/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.SourceVersion;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Wrapper to expose guest class loading as an non-enumerable interop map.
 *
 * <p>
 * Classes cannot be enumerated; and in this implementation, not even the ones already loaded. e.g.
 * {@link InteropLibrary#getMembers(Object) Peeking all memebers} will return an empty interop
 * collection. <br>
 * {@link InteropLibrary#readMember(Object, String) Reading a member} will trigger class loading; it
 * is equivalent to calling {@link ClassLoader#loadClass(String)} on the provided guest class
 * loader. <br>
 * {@link ClassNotFoundException} is translated into interop's {@link UnknownIdentifierException
 * member not found}, all other guest exceptions thrown during class loading will be propagated.
 */
@ExportLibrary(InteropLibrary.class)
public final class EspressoBindings implements TruffleObject {

    final StaticObject loader;

    public EspressoBindings(@Host(ClassLoader.class) StaticObject loader) {
        assert StaticObject.notNull(loader) : "boot classloader (null) not supported";
        this.loader = loader;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return EmptyKeysArray.INSTANCE;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @ExportMessage(name = "hasMemberReadSideEffects")
    @SuppressWarnings("static-method")
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        return SourceVersion.isName(member);
    }

    @ExportMessage
    Object readMember(String member,
                    @CachedLibrary("this.loader") InteropLibrary interop,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) throws UnsupportedMessageException, UnknownIdentifierException {
        if (!isMemberReadable(member)) {
            error.enter();
            throw UnknownIdentifierException.create(member);
        }
        try {
            StaticObject clazz = (StaticObject) interop.invokeMember(loader, "loadClass/(Ljava/lang/String;)Ljava/lang/Class;", member);
            return clazz.getMirrorKlass();
        } catch (EspressoException e) {
            error.enter();
            Meta meta = context.getMeta();
            if (InterpreterToVM.instanceOf(e.getExceptionObject(), meta.java_lang_ClassNotFoundException)) {
                throw UnknownIdentifierException.create(member, e);
            }
            throw e; // exception during class loading
        } catch (ArityException | UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere(e);
        }
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
    @SuppressWarnings("static-method")
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "espresso-system-classloader";
    }
}

@ExportLibrary(InteropLibrary.class)
final class EmptyKeysArray implements TruffleObject {

    static final TruffleObject INSTANCE = new EmptyKeysArray();

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    long getArraySize() {
        return 0;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isArrayElementReadable(@SuppressWarnings("unused") long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        throw InvalidArrayIndexException.create(index);
    }
}
