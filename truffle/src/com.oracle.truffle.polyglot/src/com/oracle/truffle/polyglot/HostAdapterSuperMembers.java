/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Objects;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * An adapter that provides access to a host adapter's super methods.
 */
@ExportLibrary(InteropLibrary.class)
final class HostAdapterSuperMembers implements TruffleObject {
    final HostObject adapter;

    HostAdapterSuperMembers(HostObject adapter) {
        this.adapter = Objects.requireNonNull(adapter);
    }

    public Object getAdapter() {
        return adapter;
    }

    static final class NameCache {
        @CompilationFinal private Pair<String, String> cachedNameToSuper;
        private static final NameCache UNCACHED = new NameCache(true);

        NameCache() {
        }

        NameCache(boolean uncached) {
            if (uncached) {
                this.cachedNameToSuper = Pair.empty();
            }
        }

        String getSuperMethodName(String name) {
            if (cachedNameToSuper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedNameToSuper = Pair.create(name, HostAdapterFactory.getSuperMethodName(name));
            }
            String cachedName = cachedNameToSuper.getLeft();
            if (cachedName != null) {
                if (cachedName.equals(name)) {
                    return cachedNameToSuper.getRight();
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedNameToSuper = Pair.empty();
                    return HostAdapterFactory.getSuperMethodName(name);
                }
            } else {
                return HostAdapterFactory.getSuperMethodName(name);
            }
        }

        static NameCache create() {
            return new NameCache();
        }

        static NameCache getUncached() {
            return UNCACHED;
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object readMember(String name,
                    @Shared("cache") @Cached NameCache cache,
                    @CachedLibrary("this.adapter") InteropLibrary interop) throws UnsupportedMessageException, UnknownIdentifierException {
        String superMethodName = cache.getSuperMethodName(name);
        return interop.readMember(getAdapter(), superMethodName);
    }

    @ExportMessage
    Object invokeMember(String name, Object[] args,
                    @Shared("cache") @Cached NameCache cache,
                    @CachedLibrary("this.adapter") InteropLibrary interop) throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        String superMethodName = cache.getSuperMethodName(name);
        return interop.invokeMember(getAdapter(), superMethodName, args);
    }

    @ExportMessage
    boolean isMemberReadable(String name,
                    @Shared("cache") @Cached NameCache cache,
                    @CachedLibrary("this.adapter") InteropLibrary interop) {
        String superMethodName = cache.getSuperMethodName(name);
        return interop.isMemberReadable(getAdapter(), superMethodName);
    }

    @ExportMessage
    boolean isMemberInvocable(String name,
                    @Shared("cache") @Cached NameCache cache,
                    @CachedLibrary("this.adapter") InteropLibrary interop) {
        String superMethodName = cache.getSuperMethodName(name);
        return interop.isMemberInvocable(getAdapter(), superMethodName);
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(boolean includeInternal) {
        return new HostObject.KeysArray(includeInternal ? collectSuperMembers() : new String[0]);
    }

    @TruffleBoundary
    private String[] collectSuperMembers() {
        HostClassDesc classDesc = HostClassDesc.forClass(this.adapter.getEngine(), this.adapter.getLookupClass());
        EconomicSet<String> names = EconomicSet.create();
        Collection<String> methodNames = classDesc.getMethodNames(false, true);
        for (String name : methodNames) {
            if (name.startsWith(HostAdapterBytecodeGenerator.SUPER_PREFIX)) {
                names.add(name.substring(HostAdapterBytecodeGenerator.SUPER_PREFIX.length()));
            }
        }
        return names.toArray(new String[names.size()]);
    }
}
