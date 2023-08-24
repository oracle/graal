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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.tregex.parser.flavors.java.JavaFlags;
import com.oracle.truffle.regex.util.TruffleNull;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * These substitutions are just for performance. Directly uses the optimized host intrinsics
 * avoiding expensive guest native calls.
 */
@EspressoSubstitutions
public final class Target_java_util_regex_Pattern {
    @Substitution(hasReceiver = true, methodName = "<init>")
    abstract static class Init extends SubstitutionNode {

        abstract void execute(@JavaType(Pattern.class) StaticObject self, @JavaType(String.class) StaticObject p, int f);

        @Specialization
        void doDefault(
                @JavaType(Pattern.class) StaticObject self, @JavaType(String.class) StaticObject p, int f,
                @Bind("getContext()") EspressoContext context,
                @CachedLibrary(limit = "3") InteropLibrary regexInterop,
                @Cached("create(context.getMeta().java_util_regex_Pattern_init.getCallTargetNoSubstitution())") DirectCallNode original) {
            Meta meta = context.getMeta();
            String pattern = meta.toHostString(p);

            String combined = "RegressionTestMode=true,Encoding=UTF-16,Flavor=JavaUtilPattern,JavaMatch=true";
            String sourceStr = combined + '/' + pattern + '/' + new JavaFlags(f);
            Source src = Source.newBuilder("regex", sourceStr, "patternExpr").build();

            Object target = null;
            try {
                target = context.getEnv().parseInternal(src).call();
            } catch (RegexSyntaxException e) {
                StaticObject guestException = meta.java_util_regex_PatternSyntaxException.allocateInstance(context);
                CallTarget exceptionInit = meta.java_util_regex_PatternSyntaxException_init.getCallTarget(); // TODO is this ok?
                exceptionInit.call(guestException, meta.toGuestString(e.getMessage()), p, e.getPosition());

                meta.throwException(guestException);
            }

            // always null because we do not compile in search mode until it is required
            meta.java_util_regex_Pattern_HIDDEN_tregexSearch.setHiddenObject(self, StaticObject.NULL);

            // fallback to original implementation if feature is not supported
            if (target == TruffleNull.INSTANCE) {
                original.call(self, p, f);
                meta.java_util_regex_Pattern_HIDDEN_tregex.setHiddenObject(self, StaticObject.NULL);
            } else {
                try {
                    int groupCount = (Integer) regexInterop.readMember(target, "groupCount");
                    meta.java_util_regex_Pattern_capturingGroupCount.setInt(self, groupCount);
                    meta.java_util_regex_Pattern_HIDDEN_tregex.setHiddenObject(self, target);
                    meta.java_util_regex_Pattern_pattern.setObject(self, p);
                    meta.java_util_regex_Pattern_flags0.setInt(self, f);
                    meta.java_util_regex_Pattern_compiled.setBoolean(self, true);
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Substitution(hasReceiver = true, methodName = "namedGroups")
    abstract static class NamedGroups extends SubstitutionNode {

        abstract @JavaType(Map.class) StaticObject execute(@JavaType(Pattern.class) StaticObject self);

        // helper method as workaround for StaticObject.NULL producing an error in @Specialization(...)
        public static Object getNull() {
            return StaticObject.NULL;
        }

        @Specialization(guards = "context.getMeta().java_util_regex_Pattern_HIDDEN_tregex.getHiddenObject(self) != getNull()") // TODO change to StaticObject.NULL
        @JavaType(Map.class) StaticObject doDefault(
                @JavaType(Pattern.class) StaticObject self,
                @Bind("getContext()") EspressoContext context,
                @CachedLibrary(limit = "3") InteropLibrary regexInterop) {
            Object regexObject = context.getMeta().java_util_regex_Pattern_HIDDEN_tregex.getHiddenObject(self);
            try {
                Object map = regexInterop.readMember(regexObject, "groups");
                Object keys = regexInterop.getMembers(map);
                long size = regexInterop.getArraySize(keys);

                StaticObject guestMap = getMeta().java_util_HashMap.allocateInstance();
                getMeta().java_util_HashMap_init.getCallTarget().call(guestMap, (int) size);

                for (long i = 0; i < size; i++) {
                    String key = (String) regexInterop.readArrayElement(keys, i);
                    Object value = regexInterop.readMember(map, key);
                    StaticObject guestKey = getMeta().toGuestString(key);

                    Object integerValue = getMeta().java_lang_Integer_valueOf.getCallTarget().call(value);
                    getMeta().java_util_HashMap_put.getCallTarget().call(guestMap, guestKey, integerValue);
                }

                return (StaticObject) getMeta().java_util_Map_copy_of.getCallTarget().call(guestMap);
            } catch (UnsupportedMessageException | UnknownIdentifierException | InvalidArrayIndexException e) {
                throw new RuntimeException(e);
            }
        }

        @Specialization(guards = "context.getMeta().java_util_regex_Pattern_HIDDEN_tregex.getHiddenObject(self) == getNull()") // TODO change to StaticObject.NULL
        @JavaType(Map.class) StaticObject doFallback(
                @JavaType(Pattern.class) StaticObject self,
                @Bind("getContext()") EspressoContext context,
                @Cached("create(getContext().getMeta().java_util_regex_Pattern_namedGroups.getCallTargetNoSubstitution())") DirectCallNode original) {
            return (StaticObject) original.call(self);
        }
    }
}
