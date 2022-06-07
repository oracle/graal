/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.util.Collection;
import java.util.Objects;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.host.HostMethodDesc.SuperMethod;

/**
 * An adapter that provides access to a host adapter's super methods.
 */
@ExportLibrary(InteropLibrary.class)
final class HostAdapterSuperMembers implements TruffleObject {

    static final int LIMIT = 3;

    final HostObject adapter;

    HostAdapterSuperMembers(HostObject adapter) {
        this.adapter = Objects.requireNonNull(adapter);
    }

    public HostObject getAdapter() {
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

        @NeverDefault
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
    static class ReadMember {

        @Specialization(guards = {"memberLibrary.isString(memberString)"})
        static Object read(HostAdapterSuperMembers receiver,
                        Object memberString,
                        @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary,
                        @Shared("cache") @Cached NameCache cache,
                        @CachedLibrary("receiver.adapter") InteropLibrary interop,
                        @Bind("$node") Node node,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, UnknownMemberException {
            String name;
            try {
                name = memberLibrary.asString(memberString);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            String superMethodName = cache.getSuperMethodName(name);
            try {
                return interop.readMember(receiver.getAdapter(), (Object) superMethodName);
            } catch (UnknownMemberException ex) {
                error.enter(node);
                throw UnknownMemberException.create(memberString);
            }
        }

        @Specialization
        static Object read(HostAdapterSuperMembers receiver,
                        HostMethodDesc method,
                        @CachedLibrary("receiver.adapter") InteropLibrary interop,
                        @Bind("$node") Node node,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, UnknownMemberException {
            if (!(method instanceof SuperMethod)) {
                error.enter(node);
                throw UnknownMemberException.create(method);
            }
            try {
                return interop.readMember(receiver.getAdapter(), ((SuperMethod) method).getDelegateMethod());
            } catch (UnknownMemberException ex) {
                error.enter(node);
                throw UnknownMemberException.create(method);
            }
        }

        @Fallback()
        static Object readOther(@SuppressWarnings("unused") HostAdapterSuperMembers receiver,
                        Object member) throws UnknownMemberException {
            throw UnknownMemberException.create(member);
        }
    }

    @ExportMessage
    static class InvokeMember {

        @Specialization(guards = {"memberLibrary.isString(memberString)"})
        static Object invoke(HostAdapterSuperMembers receiver,
                        Object memberString,
                        Object[] args,
                        @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary,
                        @Shared("cache") @Cached NameCache cache,
                        @CachedLibrary("receiver.adapter") InteropLibrary interop,
                        @Bind("$node") Node node,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, UnknownMemberException, ArityException, UnsupportedTypeException {
            String name;
            try {
                name = memberLibrary.asString(memberString);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            String superMethodName = cache.getSuperMethodName(name);
            try {
                return interop.invokeMember(receiver.getAdapter(), (Object) superMethodName, args);
            } catch (UnknownMemberException ex) {
                error.enter(node);
                throw UnknownMemberException.create(memberString);
            }
        }

        @Specialization
        static Object invoke(HostAdapterSuperMembers receiver,
                        HostMethodDesc method,
                        Object[] args,
                        @CachedLibrary("receiver.adapter") InteropLibrary interop,
                        @Bind("$node") Node node,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, UnknownMemberException, ArityException, UnsupportedTypeException {
            if (!(method instanceof SuperMethod)) {
                error.enter(node);
                throw UnknownMemberException.create(method);
            }
            try {
                return interop.invokeMember(receiver.getAdapter(), ((SuperMethod) method).getDelegateMethod(), args);
            } catch (UnknownMemberException ex) {
                error.enter(node);
                throw UnknownMemberException.create(method);
            }
        }

        @Fallback()
        @SuppressWarnings("unused")
        static Object invokeOther(HostAdapterSuperMembers receiver,
                        Object member, Object[] args) throws UnknownMemberException {
            throw UnknownMemberException.create(member);
        }
    }

    @ExportMessage
    static class IsMemberReadable {

        @Specialization(guards = {"receiver.getAdapter().getLookupClass() == cachedClazz", "compareNode.execute(node, member, cachedMember)"}, limit = "LIMIT")
        @SuppressWarnings("unused")
        static boolean doCached(HostAdapterSuperMembers receiver, Object member,
                        @Shared("cache") @Cached NameCache cache,
                        @Bind("$node") Node node,
                        @Cached("receiver.getAdapter().getLookupClass()") Class<?> cachedClazz,
                        @Cached("member") Object cachedMember,
                        @Shared("compareNode") @Cached HostObject.CompareMemberNode compareNode,
                        @Cached("doGeneric(receiver, member, cache)") boolean cachedInvokable) {
            assert cachedInvokable == doGeneric(receiver, member, cache);
            return cachedInvokable;
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(HostAdapterSuperMembers receiver, Object member,
                        @Shared("cache") @Cached NameCache cache,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver.adapter") InteropLibrary interop,
                        @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary,
                        @Shared("seenMethod") @Cached InlinedBranchProfile seenMethod,
                        @Shared("seenString") @Cached InlinedBranchProfile seenString,
                        @Shared("seenUnknown") @Cached InlinedBranchProfile seenUnknown) {
            if (member instanceof SuperMethod) {
                seenMethod.enter(node);
                return interop.isMemberReadable(receiver.getAdapter(), ((SuperMethod) member).getDelegateMethod());
            } else if (memberLibrary.isString(member)) {
                seenString.enter(node);
                String name;
                try {
                    name = memberLibrary.asString(member);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                String superMethodName = cache.getSuperMethodName(name);
                return interop.isMemberReadable(receiver.getAdapter(), (Object) superMethodName);
            } else {
                seenUnknown.enter(node);
                return false;
            }
        }

        static boolean doGeneric(HostAdapterSuperMembers receiver, Object member, NameCache cache) {
            InlinedBranchProfile profile = InlinedBranchProfile.getUncached();
            return doGeneric(receiver, member, cache, //
                            null, InteropLibrary.getUncached(), InteropLibrary.getUncached(), profile, profile, profile);
        }
    }

    @ExportMessage
    static class IsMemberInvocable {

        @Specialization(guards = {"receiver.getAdapter().getLookupClass() == cachedClazz", "compareNode.execute(node, member, cachedMember)"}, limit = "LIMIT")
        @SuppressWarnings("unused")
        static boolean doCached(HostAdapterSuperMembers receiver, Object member,
                        @Shared("cache") @Cached NameCache cache,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver.adapter") InteropLibrary interop,
                        @Cached("receiver.getAdapter().getLookupClass()") Class<?> cachedClazz,
                        @Cached("member") Object cachedMember,
                        @Shared("compareNode") @Cached HostObject.CompareMemberNode compareNode,
                        @Cached("doGeneric(receiver, member, cache)") boolean cachedInvokable) {
            assert cachedInvokable == doGeneric(receiver, member, cache);
            return cachedInvokable;
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(HostAdapterSuperMembers receiver, Object member,
                        @Shared("cache") @Cached NameCache cache,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver.adapter") InteropLibrary interop,
                        @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary,
                        @Shared("seenMethod") @Cached InlinedBranchProfile seenMethod,
                        @Shared("seenString") @Cached InlinedBranchProfile seenString,
                        @Shared("seenUnknown") @Cached InlinedBranchProfile seenUnknown) {
            if (member instanceof SuperMethod) {
                seenMethod.enter(node);
                return interop.isMemberInvocable(receiver.getAdapter(), ((SuperMethod) member).getDelegateMethod());
            } else if (memberLibrary.isString(member)) {
                seenString.enter(node);
                String name;
                try {
                    name = memberLibrary.asString(member);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                String superMethodName = cache.getSuperMethodName(name);
                return interop.isMemberInvocable(receiver.getAdapter(), (Object) superMethodName);
            } else {
                seenUnknown.enter(node);
                return false;
            }
        }

        static boolean doGeneric(HostAdapterSuperMembers receiver, Object member, NameCache cache) {
            InlinedBranchProfile profile = InlinedBranchProfile.getUncached();
            return doGeneric(receiver, member, cache, //
                            null, InteropLibrary.getUncached(), InteropLibrary.getUncached(), profile, profile, profile);
        }
    }

    @ExportMessage
    Object getMemberObjects() {
        return new HostObject.MembersArray(collectSuperMembers());
    }

    @TruffleBoundary
    private Object[] collectSuperMembers() {
        HostClassDesc classDesc = HostClassDesc.forClass(this.adapter.context, this.adapter.getLookupClass());
        EconomicSet<HostMethodDesc> superMethods = EconomicSet.create();
        Collection<HostMethodDesc> methods = classDesc.getMethodValues(false);
        for (HostMethodDesc method : methods) {
            if (method.hasOverloads()) {
                for (HostMethodDesc om : method.getOverloads()) {
                    collectSuperMethod(om, superMethods);
                }
            } else {
                collectSuperMethod(method, superMethods);
            }
        }
        return methods.toArray(new HostMethodDesc[methods.size()]);
    }

    private static void collectSuperMethod(HostMethodDesc method, EconomicSet<HostMethodDesc> superMethods) {
        if (method.getName().startsWith(HostAdapterBytecodeGenerator.SUPER_PREFIX)) {
            HostMethodDesc superMethod = new SuperMethod(method);
            superMethods.add(superMethod);
        }
    }
}
