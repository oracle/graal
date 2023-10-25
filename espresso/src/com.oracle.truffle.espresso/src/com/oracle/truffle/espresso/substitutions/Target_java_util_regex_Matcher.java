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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.util.regex.Matcher;

import static com.oracle.truffle.espresso.substitutions.Target_java_util_regex_Pattern.convertFlags;

/**
 * These substitutions are just for performance. Directly uses the optimized host intrinsics
 * avoiding expensive guest native calls.
 */
@EspressoSubstitutions
public final class Target_java_util_regex_Matcher {

    @Substitution(hasReceiver = true, methodName = "match")
    abstract static class Match extends SubstitutionNode {
        abstract boolean execute(@JavaType(Matcher.class) StaticObject self, int from, int anchor);

        // helper method as workaround for StaticObject.NULL producing an error in @Specialization(...)
        public static Object getNull() {
            return StaticObject.NULL;
        }
        private static final int ENDANCHOR = 1;

        public static Object getRegexObject(EspressoContext context, StaticObject self) {
            StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
            return context.getMeta().java_util_regex_Pattern_HIDDEN_tregex.getHiddenObject(parentPattern);
        }

        @Specialization(guards = "getRegexObject(context, self) != getNull()")
        boolean doDefault(@JavaType(Matcher.class) StaticObject self, int from, int anchor,
                          @Bind("getContext()") EspressoContext context,
                          @CachedLibrary(limit = "3") InteropLibrary regexInterop) {
            Object regexObject = getRegexObject(context, self);
            StaticObject text = context.getMeta().java_util_regex_Matcher_text.getObject(self);

            try {
                from = from < 0 ? 0 : from;
                context.getMeta().java_util_regex_Matcher_first.setInt(self, from);

                Object execRes = regexInterop.invokeMember(regexObject, "exec", text, from);
                boolean isMatch = regexInterop.asBoolean(regexInterop.readMember(execRes, "isMatch"));
                int modCount = context.getMeta().java_util_regex_Matcher_modCount.getInt(self);
                context.getMeta().java_util_regex_Matcher_modCount.setInt(self, modCount + 1);

                if (isMatch) {
                    int first = regexInterop.asInt(regexInterop.invokeMember(execRes, "getStart", 0));
                    int last = regexInterop.asInt(regexInterop.invokeMember(execRes, "getEnd", 0));

                    context.getMeta().java_util_regex_Matcher_first.setInt(self, first);
                    context.getMeta().java_util_regex_Matcher_last.setInt(self, last);

                    int groupCount = regexInterop.asInt(regexInterop.readMember(regexObject, "groupCount"));

                    for (int i = 0; i < groupCount; i++) {
                        int start = regexInterop.asInt(regexInterop.invokeMember(execRes, "getStart", i));
                        int end = regexInterop.asInt(regexInterop.invokeMember(execRes, "getEnd", i));

                        StaticObject array = context.getMeta().java_util_regex_Matcher_groups.getObject(self);
                        array.<int[]>unwrap(context.getLanguage())[i * 2] = start;
                        array.<int[]>unwrap(context.getLanguage())[i * 2 + 1] = end;
                    }

                    if (anchor == ENDANCHOR) {
                        boolean result = last == regexInterop.asInt(context.getMeta().java_util_regex_Matcher_to.get(self));

                        if (!result)
                            context.getMeta().java_util_regex_Matcher_first.setInt(self, -1);
                        return result;
                    }

                    return true;
                }

                return false;
            } catch (ArityException | UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                throw new RuntimeException(e);
            }
        }

        @Specialization(guards = "getRegexObject(getContext(), self) == getNull()")
        boolean doFallback(@JavaType(Matcher.class) StaticObject self, int from, int anchor,
                           @Bind("getContext()") EspressoContext context,
                           @Cached("create(context.getMeta().java_util_regex_Matcher_match.getCallTargetNoSubstitution())") DirectCallNode original) {
            return (Boolean) original.call(self, from, anchor);
        }
    }

    @Substitution(hasReceiver = true, methodName = "search")
    abstract static class Search extends SubstitutionNode {
        abstract boolean execute(@JavaType(Matcher.class) StaticObject self, int from);

        // helper method as workaround for StaticObject.NULL producing an error in @Specialization(...)
        public static Object getNull() {
            return StaticObject.NULL;
        }
        private static final int ENDANCHOR = 1;

        public static Object getRegexObject(EspressoContext context, StaticObject self) {
            StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
            return context.getMeta().java_util_regex_Pattern_HIDDEN_tregex.getHiddenObject(parentPattern);
        }

        public static Object getRegexSearchObject(EspressoContext context, StaticObject self) {
            StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
            return context.getMeta().java_util_regex_Pattern_HIDDEN_tregexSearch.getHiddenObject(parentPattern);
        }

        @Specialization(guards = "getRegexSearchObject(context, self) != getNull()")
        boolean doLazy(@JavaType(Matcher.class) StaticObject self, int from,
                          @Bind("getContext()") EspressoContext context,
                          @CachedLibrary(limit = "3") InteropLibrary regexInterop) {
            Object regexObject = getRegexSearchObject(context, self);
            StaticObject text = context.getMeta().java_util_regex_Matcher_text.getObject(self);

            try {
                from = from < 0 ? 0 : from;
                context.getMeta().java_util_regex_Matcher_first.setInt(self, from);

                Object execRes = regexInterop.invokeMember(regexObject, "exec", text, from);
                boolean isMatch = regexInterop.asBoolean(regexInterop.readMember(execRes, "isMatch"));
                int modCount = context.getMeta().java_util_regex_Matcher_modCount.getInt(self);
                context.getMeta().java_util_regex_Matcher_modCount.setInt(self, modCount + 1);

                if (isMatch) {
                    int first = regexInterop.asInt(regexInterop.invokeMember(execRes, "getStart", 0));
                    int last = regexInterop.asInt(regexInterop.invokeMember(execRes, "getEnd", 0));

                    context.getMeta().java_util_regex_Matcher_first.setInt(self, first);
                    context.getMeta().java_util_regex_Matcher_last.setInt(self, last);

                    int groupCount = regexInterop.asInt(regexInterop.readMember(regexObject, "groupCount"));

                    for (int i = 0; i < groupCount; i++) {
                        int start = regexInterop.asInt(regexInterop.invokeMember(execRes, "getStart", i));
                        int end = regexInterop.asInt(regexInterop.invokeMember(execRes, "getEnd", i));

                        StaticObject array = context.getMeta().java_util_regex_Matcher_groups.getObject(self);
                        array.<int[]>unwrap(context.getLanguage())[i * 2] = start;
                        array.<int[]>unwrap(context.getLanguage())[i * 2 + 1] = end;
                    }

                    return true;
                }
                context.getMeta().java_util_regex_Matcher_first.setInt(self, -1);
                return false;
            } catch (ArityException | UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                throw new RuntimeException(e);
            }
        }

        @Specialization(guards = {"getRegexObject(context, self) != getNull()", "getRegexSearchObject(context, self) == getNull()"})
        boolean doCompile(@JavaType(Matcher.class) StaticObject self, int from,
                          @Bind("getContext()") EspressoContext context,
                          @CachedLibrary(limit = "3") InteropLibrary regexInterop) {

            StaticObject patternObject = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
            String pattern = context.getMeta().toHostString(context.getMeta().java_util_regex_Pattern_pattern.getObject(patternObject));

            String combined = "RegressionTestMode=true,Encoding=UTF-16,Flavor=JavaUtilPattern,JavaMatch=false";
            String sourceStr = combined + '/' + pattern + '/' + convertFlags(context.getMeta().java_util_regex_Pattern_flags0.getInt(patternObject));
            Source src = Source.newBuilder("regex", sourceStr, "patternExpr").build();

            Object regexObject = context.getEnv().parseInternal(src).call();
            context.getMeta().java_util_regex_Pattern_HIDDEN_tregexSearch.setHiddenObject(patternObject, regexObject);

            StaticObject text = context.getMeta().java_util_regex_Matcher_text.getObject(self);

            try {
                from = from < 0 ? 0 : from;
                context.getMeta().java_util_regex_Matcher_first.setInt(self, from);

                Object execRes = regexInterop.invokeMember(regexObject, "exec", text, from);
                boolean isMatch = regexInterop.asBoolean(regexInterop.readMember(execRes, "isMatch"));
                int modCount = context.getMeta().java_util_regex_Matcher_modCount.getInt(self);
                context.getMeta().java_util_regex_Matcher_modCount.setInt(self, modCount + 1);

                if (isMatch) {
                    int first = regexInterop.asInt(regexInterop.invokeMember(execRes, "getStart", 0));
                    int last = regexInterop.asInt(regexInterop.invokeMember(execRes, "getEnd", 0));

                    context.getMeta().java_util_regex_Matcher_first.setInt(self, first);
                    context.getMeta().java_util_regex_Matcher_last.setInt(self, last);

                    int groupCount = regexInterop.asInt(regexInterop.readMember(regexObject, "groupCount"));

                    for (int i = 0; i < groupCount; i++) {
                        int start = regexInterop.asInt(regexInterop.invokeMember(execRes, "getStart", i));
                        int end = regexInterop.asInt(regexInterop.invokeMember(execRes, "getEnd", i));

                        StaticObject array = context.getMeta().java_util_regex_Matcher_groups.getObject(self);
                        array.<int[]>unwrap(context.getLanguage())[i * 2] = start;
                        array.<int[]>unwrap(context.getLanguage())[i * 2 + 1] = end;
                    }

                    return true;
                }

                context.getMeta().java_util_regex_Matcher_first.setInt(self, -1);
                return false;
            } catch (ArityException | UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                throw new RuntimeException(e);
            }
        }

        @Specialization(guards = "getRegexObject(getContext(), self) == getNull()")
        boolean doFallback(@JavaType(Matcher.class) StaticObject self, int from,
                           @Bind("getContext()") EspressoContext context,
                           @Cached("create(context.getMeta().java_util_regex_Matcher_search.getCallTargetNoSubstitution())") DirectCallNode original) {
            return (Boolean) original.call(self, from);
        }
    }
}