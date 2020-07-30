/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.IntValueProfile;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
final class PolyglotLanguageBindings implements TruffleObject {

    final Object[] scopes;

    private PolyglotLanguageBindings(Object[] scopes) {
        this.scopes = scopes;
    }

    static Object create(Iterable<Scope> scopes) {
        Iterator<Scope> scope = scopes.iterator();
        Object firstScope = null;
        List<Object> otherScopes = null;
        while (scope.hasNext()) {
            Object variables = scope.next().getVariables();
            assert InteropLibrary.getFactory().getUncached().hasMembers(variables) : "Variables object must return true for isObject().";
            if (firstScope == null) {
                firstScope = variables;
            } else {
                if (otherScopes == null) {
                    otherScopes = new ArrayList<>(4);
                    otherScopes.add(firstScope);
                }
                otherScopes.add(variables);
            }
        }
        if (otherScopes == null) {
            return firstScope;
        } else {
            return new PolyglotLanguageBindings(otherScopes.toArray());
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        assert scopes.length != 1 : "should be handled by create()";
        // unfortunately we cannot do much butter as scopes might have
        // overlapping keys. So we need to make the set unique.
        Set<String> keySet = new HashSet<>();
        InteropLibrary interopDispatch = InteropLibrary.getFactory().getUncached();
        for (Object scope : scopes) {
            Object members = interopDispatch.getMembers(scope, includeInternal);
            InteropLibrary membersLibrary = InteropLibrary.getFactory().getUncached(members);
            long size = membersLibrary.getArraySize(members);
            for (long i = 0; i < size; i++) {
                try {
                    keySet.add(interopDispatch.asString(membersLibrary.readArrayElement(members, i)));
                } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
                }
            }
        }
        return new DefaultScope.VariableNamesObject(keySet);
    }

    @ExportMessage
    boolean isMemberReadable(String member,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
        int length = lengthProfile.profile(scopes.length);
        for (int i = 0; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberReadable(scope, member)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    Object readMember(String member,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) throws UnknownIdentifierException {
        int length = lengthProfile.profile(scopes.length);
        for (int i = 0; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberReadable(scope, member)) {
                try {
                    return interop.readMember(scope, member);
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    // next scope
                }
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean isMemberModifiable(String member,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
        int length = lengthProfile.profile(scopes.length);
        for (int i = 0; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberModifiable(scope, member)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    boolean isMemberInsertable(String member,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
        int length = lengthProfile.profile(scopes.length);
        boolean wasInsertable = false;
        for (int i = 0; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberExisting(scope, member)) {
                return false;
            }
            if (interop.isMemberInsertable(scope, member)) {
                wasInsertable = true;
            }
        }
        return wasInsertable;
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile)
                    throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {

        int length = lengthProfile.profile(scopes.length);
        Object firstInsertableScope = null;
        for (int i = 0; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberExisting(scope, member)) {
                // existed therefore it cannot be insertable any more
                if (interop.isMemberModifiable(scope, member)) {
                    interop.writeMember(scope, member, value);
                    return;
                } else {
                    // we cannot modify nor insert
                    throw UnsupportedMessageException.create();
                }
            }
            if (interop.isMemberInsertable(scope, member) && firstInsertableScope == null) {
                firstInsertableScope = scope;
            }
        }

        if (firstInsertableScope != null) {
            interop.writeMember(firstInsertableScope, member, value);
            return;
        }

        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    void removeMember(String member,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) throws UnsupportedMessageException, UnknownIdentifierException {
        int length = lengthProfile.profile(scopes.length);
        for (int i = 0; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberRemovable(scope, member)) {
                interop.removeMember(scope, member);
                return;
            } else if (interop.isMemberExisting(scope, member)) {
                throw UnsupportedMessageException.create();
            }
        }
    }

    @ExportMessage
    boolean isMemberRemovable(String member,
                    @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
        int length = lengthProfile.profile(scopes.length);
        for (int i = 0; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberRemovable(scope, member)) {
                return true;
            } else if (interop.isMemberExisting(scope, member)) {
                return false;
            }
        }
        return false;
    }

}
