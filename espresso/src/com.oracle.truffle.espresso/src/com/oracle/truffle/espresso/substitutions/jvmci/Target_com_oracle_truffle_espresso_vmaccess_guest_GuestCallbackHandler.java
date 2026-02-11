/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.jvmci;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_vmaccess_guest_GuestCallbackHandler {
    @Substitution
    @GenerateInline(false) // not available for Substitutions
    abstract static class IdentityHashCode extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract int execute(@JavaType(Object.class) StaticObject o);

        @Specialization
        static int doCached(
                        @JavaType(Object.class) StaticObject o,
                        @Bind Node node,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile notForeignProfile,
                        @Cached InlinedBranchProfile unsupportedProfile) {
            if (!o.isForeignObject()) {
                notForeignProfile.enter(node);
                Meta meta = EspressoContext.get(node).getMeta();
                throw meta.throwIllegalArgumentExceptionBoundary("Expected a foreign object");
            }
            EspressoLanguage language = EspressoLanguage.get(node);
            try {
                return interop.identityHashCode(o.rawForeignObject(language));
            } catch (UnsupportedMessageException e) {
                unsupportedProfile.enter(node);
                Meta meta = EspressoContext.get(node).getMeta();
                throw meta.throwIllegalArgumentExceptionBoundary(e.getMessage());
            }
        }
    }

    @Substitution
    @GenerateInline(false) // not available for Substitutions
    abstract static class Identical extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject o1, @JavaType(Object.class) StaticObject o2);

        @Specialization
        static boolean doCached(
                        @JavaType(Object.class) StaticObject o1,
                        @JavaType(Object.class) StaticObject o2,
                        @Bind Node node,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary otherInterop,
                        @Cached InlinedBranchProfile notForeignProfile) {
            if (!o1.isForeignObject() || !o2.isForeignObject()) {
                notForeignProfile.enter(node);
                Meta meta = EspressoContext.get(node).getMeta();
                throw meta.throwIllegalArgumentExceptionBoundary("Expected 2 foreign objects");
            }
            EspressoLanguage language = EspressoLanguage.get(node);
            return interop.isIdentical(o1.rawForeignObject(language), o2.rawForeignObject(language), otherInterop);
        }
    }

    @Substitution
    @GenerateInline(false) // not available for Substitutions
    abstract static class GetClassName extends SubstitutionNode {
        static final int LIMIT = 2;

        @JavaType(String.class)
        abstract StaticObject execute(@JavaType(Object.class) StaticObject o);

        @Specialization
        @JavaType(Object.class)
        static StaticObject doCached(
                        @JavaType(Object.class) StaticObject o,
                        @Bind Node node,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary metaObjectInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary nameInterop,
                        @Cached InlinedBranchProfile notForeignProfile,
                        @Cached InlinedBranchProfile unsupportedProfile) {
            Meta meta = EspressoContext.get(node).getMeta();
            if (!o.isForeignObject()) {
                notForeignProfile.enter(node);
                throw meta.throwIllegalArgumentExceptionBoundary("Expected a foreign object");
            }
            EspressoLanguage language = EspressoLanguage.get(node);
            try {
                Object foreignObject = o.rawForeignObject(language);
                Object metaObject = interop.getMetaObject(foreignObject);
                Object name = metaObjectInterop.getMetaQualifiedName(metaObject);
                return meta.toGuestString(nameInterop.asString(name));
            } catch (UnsupportedMessageException e) {
                unsupportedProfile.enter(node);
                throw meta.throwIllegalArgumentExceptionBoundary(e.getMessage());
            }
        }
    }

    @Substitution
    @GenerateInline(false) // not available for Substitutions
    abstract static class ReadMember extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        @JavaType(Object.class)
        static StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @Bind Node node,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberValueInterop,
                        @Cached InlinedBranchProfile notForeignProfile) {
            EspressoContext context = EspressoContext.get(node);
            Meta meta = context.getMeta();
            String hostMember = meta.toHostString(member);
            if (!receiver.isForeignObject()) {
                notForeignProfile.enter(node);
                throw meta.throwIllegalArgumentExceptionBoundary("Expected a foreign object");
            }
            EspressoLanguage language = EspressoLanguage.get(node);
            Object rawForeignObject = receiver.rawForeignObject(language);
            if (!interop.isMemberReadable(rawForeignObject, hostMember)) {
                return StaticObject.NULL;
            }
            try {
                Object memberValue = interop.readMember(rawForeignObject, hostMember);
                assert !(memberValue instanceof StaticObject);
                return StaticObject.createForeign(language, meta.java_lang_Object, memberValue, memberValueInterop);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    @Substitution
    @GenerateInline(false) // not available for Substitutions
    abstract static class Execute extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Object.class) StaticObject executable,
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object[].class) StaticObject arguments);

        @Specialization
        @JavaType(Object.class)
        static StaticObject doCached(
                        @JavaType(Object.class) StaticObject executable,
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object[].class) StaticObject arguments,
                        @Bind Node node,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary resultInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary exceptionInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary messageInterop,
                        @Cached InlinedBranchProfile notForeignProfile,
                        @Cached InlinedBranchProfile interopExceptionProfile,
                        @Cached InlinedBranchProfile hostExceptionProfile) {
            EspressoLanguage language = EspressoLanguage.get(node);
            Meta meta = EspressoContext.get(node).getMeta();
            if (!receiver.isForeignObject() || !executable.isForeignObject()) {
                notForeignProfile.enter(node);
                throw meta.throwIllegalArgumentExceptionBoundary("Expected a foreign executable & receiver object");
            }
            Object[] hostArguments;
            if (StaticObject.notNull(arguments)) {
                Object[] rawArgs = arguments.unwrap(language);
                hostArguments = new Object[rawArgs.length + 1];
                hostArguments[0] = receiver.rawForeignObject(language);
                for (int i = 0; i < rawArgs.length; i++) {
                    StaticObject arg = (StaticObject) rawArgs[i];
                    Object hostArgument;
                    if (arg.isForeignObject()) {
                        hostArgument = arg.rawForeignObject(language);
                    } else {
                        hostArgument = arg;
                    }
                    hostArguments[i + 1] = hostArgument;
                }
            } else {
                hostArguments = new Object[1];
                hostArguments[0] = receiver.rawForeignObject(language);
            }
            try {
                Object result = interop.execute(executable.rawForeignObject(language), hostArguments);
                if (result instanceof StaticObject staticObjectResult) {
                    return staticObjectResult;
                } else if (resultInterop.isNull(result)) {
                    return StaticObject.NULL;
                } else {
                    return meta.boxPrimitive(result);
                }
            } catch (InteropException e) {
                interopExceptionProfile.enter(node);
                throw meta.throwIllegalArgumentExceptionBoundary(e.getMessage());
            } catch (EspressoException e) {
                throw e;
            } catch (AbstractTruffleException ex) {
                hostExceptionProfile.enter(node);
                StaticObject exceptionWrapper = meta.com_oracle_truffle_espresso_vmaccess_guest_EspressoCallbackException.allocateInstance(meta.getContext());
                StaticObject foreignWrapper = StaticObject.createForeign(language, meta.java_lang_Object, ex, exceptionInterop);
                String message = null;
                try {
                    Object exceptionMessage = exceptionInterop.getExceptionMessage(ex);
                    if (!messageInterop.isNull(exceptionMessage)) {
                        message = messageInterop.asString(exceptionMessage);
                    }
                } catch (UnsupportedMessageException e) {
                    // keep message null
                }
                meta.com_oracle_truffle_espresso_vmaccess_guest_EspressoCallbackException_init.invokeDirectSpecial(exceptionWrapper, foreignWrapper, meta.toGuestString(message));
                throw meta.throwException(exceptionWrapper);
            }
        }
    }
}
