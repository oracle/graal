/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.Method;

@ExportLibrary(InteropLibrary.class)
public final class ForeignStackTraceElementObject implements TruffleObject {

    private final Method method;
    private final SourceSection sourceSection;

    public ForeignStackTraceElementObject(Method method, SourceSection sourceSection) {
        this.method = method;
        this.sourceSection = sourceSection;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return true;
    }

    @ExportMessage
    Object getExecutableName() {
        return method.getNameAsString();
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return sourceSection != null;
    }

    @ExportMessage
    SourceSection getSourceLocation() throws UnsupportedMessageException {
        if (sourceSection == null) {
            throw UnsupportedMessageException.create();
        } else {
            return sourceSection;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasDeclaringMetaObject() {
        return true;
    }

    @ExportMessage
    Object getDeclaringMetaObject() {
        return method.getDeclaringKlass().mirror();
    }
}
