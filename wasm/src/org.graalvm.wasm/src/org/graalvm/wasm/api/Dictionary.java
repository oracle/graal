/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.api;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

@ExportLibrary(InteropLibrary.class)
public class Dictionary implements TruffleObject {
    private final LinkedHashMap<String, Object> members;

    public Dictionary() {
        this.members = new LinkedHashMap<>();
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @TruffleBoundary
    protected static UnknownIdentifierException unknown(String identifier) {
        return UnknownIdentifierException.create(identifier);
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    public Object readMember(String member) throws UnknownIdentifierException {
        final Object x = members.get(member);
        if (x != null) {
            return x;
        } else {
            throw unknown(member);
        }
    }

    public void addMembers(Object[] nameValuePairs) {
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            String name = (String) nameValuePairs[i];
            Object value = nameValuePairs[i + 1];
            addMember(name, value);
        }
    }

    public void addMember(String name, Object value) {
        members.put(name, value);
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    @TruffleBoundary
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        final List<String> keys = Arrays.asList(members.keySet().toArray(new String[0]));
        return new Sequence<>(keys);
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    public boolean isMemberReadable(String member) {
        return members.containsKey(member);
    }

    public static Dictionary create(Object[] nameValuePairs) {
        final Dictionary dictionary = new Dictionary();
        dictionary.addMembers(nameValuePairs);
        return dictionary;
    }

    @SuppressWarnings("unused")
    @ExportMessage(name = "toDisplayString")
    public Object toDisplayString(boolean allowSideEffects) {
        return this.getClass().getName() + "[" + members + "]";
    }
}
