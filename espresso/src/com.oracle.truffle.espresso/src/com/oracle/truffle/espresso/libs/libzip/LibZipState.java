/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libzip;

import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jni.StrongHandles;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;

/**
 * Class for maintaining state when using pure java LibZip.
 */
public class LibZipState {
    // Checkstyle: stop field name check
    public final ObjectKlass java_util_zip_Inflater;
    public final Field java_util_zip_Inflater_inputConsumed;
    public final Field java_util_zip_Inflater_outputConsumed;
    public final ObjectKlass java_util_zip_DataFormatException;
    // Checkstyle: resume field name check

    private final StrongHandles<Inflater> handle2Inflater = new StrongHandles<>();
    private final StrongHandles<Deflater> handle2Deflater = new StrongHandles<>();
    private final Meta meta;

    public LibZipState(Meta meta) {
        this.meta = meta;
        java_util_zip_Inflater = meta.knownKlass(EspressoSymbols.Types.java_util_zip_Inflater);
        java_util_zip_DataFormatException = meta.knownKlass(EspressoSymbols.Types.java_util_zip_DataFormatException);
        java_util_zip_Inflater_inputConsumed = java_util_zip_Inflater.requireDeclaredField(EspressoSymbols.Names.inputConsumed, EspressoSymbols.Types._int);
        java_util_zip_Inflater_outputConsumed = java_util_zip_Inflater.requireDeclaredField(EspressoSymbols.Names.outputConsumed, EspressoSymbols.Types._int);
    }

    public long handlifyInflater(Inflater i) {
        return handle2Inflater.handlify(i);
    }

    public void cleanInflater(long handle) {
        handle2Inflater.freeHandle(handle);
    }

    public Inflater getInflater(long handle) {
        Inflater inflater = handle2Inflater.getObject(handle);
        if (inflater == null) {
            throw throwInternalError("the provided handle doesn't correspond to an Inflater");
        }
        return inflater;
    }

    public long handlifyDeflater(Deflater i) {
        return handle2Deflater.handlify(i);
    }

    public void cleanDeflater(long handle) {
        handle2Deflater.freeHandle(handle);
    }

    public Deflater getDeflater(long handle) {
        Deflater deflater = handle2Deflater.getObject(handle);
        if (deflater == null) {
            throw throwInternalError("the provided handle doesn't correspond to an Deflater");
        }
        return deflater;
    }

    @TruffleBoundary
    private EspressoException throwInternalError(String msg) {
        return meta.throwExceptionWithMessage(meta.java_lang_InternalError, msg);
    }

}
