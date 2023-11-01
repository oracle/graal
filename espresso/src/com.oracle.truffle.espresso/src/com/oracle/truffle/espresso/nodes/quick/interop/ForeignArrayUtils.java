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

package com.oracle.truffle.espresso.nodes.quick.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class ForeignArrayUtils {
    private ForeignArrayUtils() {
        throw EspressoError.shouldNotReachHere("Must not instantiate the utils class");
    }

    public static Object readForeignArrayElement(StaticObject array, int index, EspressoLanguage language, Meta meta, InteropLibrary interop, BranchProfile exceptionProfile) {
        assert array.isForeignObject();
        assert interop.hasArrayElements(array.rawForeignObject(language));
        try {
            return interop.readArrayElement(array.rawForeignObject(language), index);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("readArrayElement on a non-array foreign object", e);
        } catch (InvalidArrayIndexException e) {
            exceptionProfile.enter();
            throw meta.throwExceptionWithMessage(meta.getMeta().java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public static void writeForeignArrayElement(StaticObject array, int index, Object value, EspressoLanguage language, Meta meta, InteropLibrary interop, BranchProfile exceptionProfile) {
        assert array.isForeignObject();
        assert interop.hasArrayElements(array.rawForeignObject(language));
        try {
            interop.writeArrayElement(array.rawForeignObject(language), index, value);
        } catch (UnsupportedMessageException e) {
            // Read-only interop array.
            exceptionProfile.enter();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArrayStoreException, e.getMessage());
        } catch (UnsupportedTypeException e) {
            exceptionProfile.enter();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, createTypeErrorMessage(value));
        } catch (InvalidArrayIndexException e) {
            exceptionProfile.enter();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    @TruffleBoundary
    private static String createTypeErrorMessage(Object value) {
        return "Could not cast the value " + value + " to the type of the foreign array elements";
    }

}
