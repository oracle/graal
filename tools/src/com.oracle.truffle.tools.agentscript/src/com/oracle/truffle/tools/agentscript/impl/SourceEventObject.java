/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;

@SuppressWarnings("unused")
@ExportLibrary(InteropLibrary.class)
final class SourceEventObject implements TruffleObject {
    private final Source source;

    SourceEventObject(Source source) {
        this.source = source;
    }

    @ExportMessage
    static boolean hasMembers(SourceEventObject obj) {
        return true;
    }

    enum Members {
        characters,
        name,
        language,
        mimeType,
        uri;
    }

    @CompilerDirectives.TruffleBoundary
    @ExportMessage
    static Object getMembers(SourceEventObject obj, boolean includeInternal) {
        return ArrayObject.wrap(Members.values());
    }

    @CompilerDirectives.TruffleBoundary
    @ExportMessage
    static boolean isMemberReadable(SourceEventObject obj, String member) {
        try {
            return Members.valueOf(member) != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @CompilerDirectives.TruffleBoundary
    @ExportMessage
    static Object readMember(SourceEventObject obj, String member) throws UnknownIdentifierException {
        final Members existingMember;
        try {
            existingMember = Members.valueOf(member);
        } catch (IllegalArgumentException ex) {
            throw UnknownIdentifierException.create(member);
        }
        switch (existingMember) {
            case characters:
                return obj.source.getCharacters().toString();
            case name:
                return obj.source.getName();
            case language:
                return obj.source.getLanguage();
            case mimeType:
                return NullObject.nullCheck(obj.source.getMimeType());
            case uri:
                return obj.source.getURI().toASCIIString();
        }
        throw new IllegalArgumentException(member);
    }
}
