/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs;

import java.util.zip.Inflater;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.jni.StrongHandles;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;

public class LibsState {
    private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, LibsState.class);

    private final StrongHandles<Inflater> handle2Inflater = new StrongHandles<>();

    public long handlifyInflater(Inflater i) {
        return handle2Inflater.handlify(i);
    }

    public void cleanInflater(long handle) {
        handle2Inflater.freeHandle(handle);
    }

    public Inflater getInflater(long handle) {
        Inflater inflater = handle2Inflater.getObject(handle);
        if (inflater == null) {
            throw throwInternalError();
        }
        return inflater;
    }

    @TruffleBoundary
    private static EspressoException throwInternalError() {
        Meta meta = EspressoContext.get(null).getMeta();
        return meta.throwExceptionWithMessage(meta.java_lang_InternalError, "the provided handle doesn't correspond to an Inflater");
    }

    public static TruffleLogger getLogger() {
        return logger;
    }

}
