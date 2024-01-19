/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.oracle.truffle.espresso.substitutions.Target_java_util_regex_Pattern.convertFlags;

@EspressoSubstitutions
public final class Target_java_util_regex_Matcher {
    @Substitution(hasReceiver = true, methodName = "<init>")
    abstract static class Init extends SubstitutionNode {
        public static boolean isUnsupported(EspressoContext context, StaticObject parentPattern) {
            Object unsupported = context.getMeta().java_util_regex_Pattern_HIDDEN_unsupported.getHiddenObject(parentPattern);
            return unsupported == null || (boolean) unsupported;
        }

        abstract void execute(@JavaType(Matcher.class) StaticObject self, @JavaType(Pattern.class) StaticObject parent, @JavaType(CharSequence.class) StaticObject text);

        @Specialization(guards = "isUnsupported(getContext(), parent)")
        void doFallback(
                        @JavaType(Matcher.class) StaticObject self, @JavaType(Pattern.class) StaticObject parent, @JavaType(CharSequence.class) StaticObject text,
                        @Cached("create(getContext().getMeta().java_util_regex_Matcher_init.getCallTargetNoSubstitution())") DirectCallNode original) {
            original.call(self, parent, text);
        }

        @Specialization
        void doTruffleStringConversion(
                        @JavaType(Matcher.class) StaticObject self, @JavaType(Pattern.class) StaticObject parent, @JavaType(CharSequence.class) StaticObject text,
                        @Bind("getContext()") EspressoContext context,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached("create(context.getMeta().java_util_regex_Matcher_init.getCallTargetNoSubstitution())") DirectCallNode original) {
            saveTruffleString(self, context, fromJavaStringNode, text);
            original.call(self, parent, text);
        }
    }

    @Substitution(hasReceiver = true, methodName = "reset")
    abstract static class Reset extends SubstitutionNode {
        public static boolean isUnsupported(EspressoContext context, StaticObject self) {
            return Target_java_util_regex_Matcher.isUnsupported(context, self);
        }

        abstract @JavaType(Matcher.class) StaticObject execute(@JavaType(Matcher.class) StaticObject self, @JavaType(CharSequence.class) StaticObject text);

        @Specialization(guards = "isUnsupported(getContext(), self)")
        @JavaType(Matcher.class)
        StaticObject doFallback(
                        @JavaType(Matcher.class) StaticObject self, @JavaType(CharSequence.class) StaticObject text,
                        @Cached("create(getContext().getMeta().java_util_regex_Matcher_reset.getCallTargetNoSubstitution())") DirectCallNode original) {
            return (StaticObject) original.call(self, text);
        }

        @Specialization
        @JavaType(Matcher.class)
        StaticObject doTruffleStringConversion(
                        @JavaType(Matcher.class) StaticObject self, @JavaType(CharSequence.class) StaticObject text,
                        @Bind("getContext()") EspressoContext context,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached("create(context.getMeta().java_util_regex_Matcher_reset.getCallTargetNoSubstitution())") DirectCallNode original) {
            saveTruffleString(self, context, fromJavaStringNode, text);
            return (StaticObject) original.call(self, text);
        }
    }

    @Substitution(hasReceiver = true, methodName = "match")
    abstract static class Match extends SubstitutionNode {
        abstract boolean execute(@JavaType(Matcher.class) StaticObject self, int from, int anchor);

        // helper method as workaround for StaticObject.NULL producing an error in
        // @Specialization(...)
        public static Object getNull() {
            return StaticObject.NULL;
        }

        private static final int ENDANCHOR = 1;

        public static boolean isUnsupported(EspressoContext context, StaticObject self) {
            return Target_java_util_regex_Matcher.isUnsupported(context, self);
        }

        public static Object getRegexObject(EspressoContext context, StaticObject self, int anchor) {
            StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
            if (anchor == ENDANCHOR) {
                return context.getMeta().java_util_regex_Pattern_HIDDEN_tregexFullmatch.getHiddenObject(parentPattern);
            } else {
                return context.getMeta().java_util_regex_Pattern_HIDDEN_tregexMatch.getHiddenObject(parentPattern);
            }
        }

        private static boolean checkResult(StaticObject self, int from, int anchor, EspressoContext context, InteropLibrary regexInterop, Object regexObject, JavaRegexExecNode javaRegexExecNode,
                        Node node) {
            saveBackup(context, self);
            boolean isMatch = javaRegexExecNode.execute(node, context, regexObject, self, from);

            if (isMatch) {
                if (anchor == ENDANCHOR) {
                    boolean result;
                    try {
                        int last = regexInterop.asInt(context.getMeta().java_util_regex_Matcher_last.get(self));
                        result = last == regexInterop.asInt(context.getMeta().java_util_regex_Matcher_to.get(self));
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }

                    if (result) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
            context.getMeta().java_util_regex_Matcher_first.setInt(self, -1);
            return false;
        }

        @Specialization(guards = {"getRegexObject(context, self, anchor) != getNull()", "!isUnsupported(getContext(), self)"})
        boolean doLazy(@JavaType(Matcher.class) StaticObject self, int from, int anchor,
                        @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "3") InteropLibrary regexInterop,
                        @Cached JavaRegexExecNode javaRegexExecNode,
                        @Bind("this") Node node) {
            getMeta().java_util_regex_Matcher_HIDDEN_searchFromBackup.setHiddenObject(self, from);
            getMeta().java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, ENDANCHOR);

            Object regexObject = getRegexObject(context, self, anchor);
            return checkResult(self, from, anchor, context, regexInterop, regexObject, javaRegexExecNode, node);
        }

        @Specialization(guards = {"getRegexObject(context, self, anchor) == getNull()", "!isUnsupported(getContext(), self)"})
        boolean doCompile(@JavaType(Matcher.class) StaticObject self, int from, int anchor,
                        @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "3") InteropLibrary regexInterop,
                        @Cached JavaRegexExecNode javaRegexExecNode,
                        @Cached JavaRegexCompileNode javaRegexCompileNode,
                        @Bind("this") Node node) {
            getMeta().java_util_regex_Matcher_HIDDEN_searchFromBackup.setHiddenObject(self, from);
            getMeta().java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, ENDANCHOR);
            String method;
            Field destination;
            if (anchor == ENDANCHOR) {
                method = "fullmatch";
                destination = getMeta().java_util_regex_Pattern_HIDDEN_tregexFullmatch;
            } else {
                method = "match";
                destination = getMeta().java_util_regex_Pattern_HIDDEN_tregexMatch;
            }
            Object regexObject = javaRegexCompileNode.execute(node, context, self, method, destination);
            return checkResult(self, from, anchor, context, regexInterop, regexObject, javaRegexExecNode, node);
        }

        @Specialization(guards = "isUnsupported(getContext(), self)")
        boolean doFallback(@JavaType(Matcher.class) StaticObject self, int from, int anchor,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().java_util_regex_Matcher_match.getCallTargetNoSubstitution())") DirectCallNode original) {
            getMeta().java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, StaticObject.NULL);
            compileFallBackIfRequired(context, self);
            return (Boolean) original.call(self, from, anchor);
        }
    }

    @Substitution(hasReceiver = true, methodName = "search")
    abstract static class Search extends SubstitutionNode {
        abstract boolean execute(@JavaType(Matcher.class) StaticObject self, int from);

        // helper method as workaround for StaticObject.NULL producing an error in
        // @Specialization(...)
        public static Object getNull() {
            return StaticObject.NULL;
        }

        public static Object getRegexSearchObject(EspressoContext context, StaticObject self) {
            StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
            return context.getMeta().java_util_regex_Pattern_HIDDEN_tregexSearch.getHiddenObject(parentPattern);
        }

        public static boolean isUnsupported(EspressoContext context, StaticObject self) {
            return Target_java_util_regex_Matcher.isUnsupported(context, self);
        }

        @Specialization(guards = {"getRegexSearchObject(context, self) != getNull()", "!isUnsupported(getContext(), self)"})
        boolean doLazy(@JavaType(Matcher.class) StaticObject self, int from,
                        @Bind("getContext()") EspressoContext context,
                        // @CachedLibrary(limit = "3") InteropLibrary regexInterop,
                        @Cached JavaRegexExecNode javaRegexExecNode,
                        @Bind("this") Node node) {
            getMeta().java_util_regex_Matcher_HIDDEN_searchFromBackup.setHiddenObject(self, from);
            getMeta().java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, 2);

            Object regexObject = getRegexSearchObject(context, self);
            saveBackup(context, self);
            boolean isMatch = javaRegexExecNode.execute(node, context, regexObject, self, from);

            if (!isMatch) {
                context.getMeta().java_util_regex_Matcher_first.setInt(self, -1);
            }
            return isMatch;
        }

        @Specialization(guards = {"getRegexSearchObject(context, self) == getNull()", "!isUnsupported(getContext(), self)"})
        boolean doCompile(@JavaType(Matcher.class) StaticObject self, int from,
                        @Bind("getContext()") EspressoContext context,
                        @Cached JavaRegexExecNode javaRegexExecNode,
                        @Cached JavaRegexCompileNode javaRegexCompileNode,
                        @Bind("this") Node node) {
            getMeta().java_util_regex_Matcher_HIDDEN_searchFromBackup.setHiddenObject(self, from);
            getMeta().java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, 2);

            Object regexObject = javaRegexCompileNode.execute(node, context, self, "search", getMeta().java_util_regex_Pattern_HIDDEN_tregexSearch);
            saveBackup(context, self);
            boolean isMatch = javaRegexExecNode.execute(node, context, regexObject, self, from);

            if (!isMatch) {
                context.getMeta().java_util_regex_Matcher_first.setInt(self, -1);
            }
            return isMatch;
        }

        @Specialization(guards = "isUnsupported(getContext(), self)")
        boolean doFallback(@JavaType(Matcher.class) StaticObject self, int from,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().java_util_regex_Matcher_search.getCallTargetNoSubstitution())") DirectCallNode original) {
            getMeta().java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, StaticObject.NULL);

            compileFallBackIfRequired(context, self);
            return (Boolean) original.call(self, from);
        }
    }

    @Substitution(hasReceiver = true, methodName = "hitEnd")
    abstract static class HitEnd extends SubstitutionNode {
        abstract boolean execute(@JavaType(Matcher.class) StaticObject self);

        @Specialization
        boolean doDefault(@JavaType(Matcher.class) StaticObject self,
                        @Bind("getContext()") EspressoContext context) {
            // if action field is null, then the last action was already executed with fallback
            executeLastWithFallback(context, self);
            return getMeta().java_util_regex_Matcher_hitEnd.getBoolean(self);
        }
    }

    @Substitution(hasReceiver = true, methodName = "requireEnd")
    abstract static class RequireEnd extends SubstitutionNode {
        abstract boolean execute(@JavaType(Matcher.class) StaticObject self);

        @Specialization
        boolean doDefault(@JavaType(Matcher.class) StaticObject self,
                        @Bind("getContext()") EspressoContext context) {
            executeLastWithFallback(context, self);
            return getMeta().java_util_regex_Matcher_requireEnd.getBoolean(self);
        }
    }

    @Substitution(hasReceiver = true, methodName = "groupCount")
    abstract static class GroupCount extends SubstitutionNode {
        abstract int execute(@JavaType(Matcher.class) StaticObject self);

        @Specialization
        int doDefault(@JavaType(Matcher.class) StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().java_util_regex_Matcher_groupCount.getCallTargetNoSubstitution())") DirectCallNode original) {

            StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
            if (context.getMeta().java_util_regex_Pattern_capturingGroupCount.getInt(parentPattern) == -1) {
                context.getMeta().java_util_regex_Pattern_capturingGroupCount.setInt(parentPattern, 1);
                compileFallBackIfRequired(context, self);
            }

            return (Integer) original.call(self);
        }
    }

    @GenerateInline
    @GenerateUncached
    public abstract static class JavaRegexCompileNode extends Node {
        public abstract Object execute(Node node, EspressoContext context, StaticObject self, String method, Field destination);

        @Specialization
        static Object doDefault(EspressoContext context, StaticObject self, String method, Field destination,
                        @CachedLibrary(limit = "3") InteropLibrary regexObjectInterop,
                        @CachedLibrary(limit = "3") InteropLibrary integerInterop,
                        @CachedLibrary(limit = "3") InteropLibrary mapInterop,
                        @CachedLibrary(limit = "3") InteropLibrary stringInterop,
                        @CachedLibrary(limit = "3") InteropLibrary arrayInterop) {
            StaticObject patternObject = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
            String pattern = context.getMeta().toHostString(context.getMeta().java_util_regex_Pattern_pattern.getObject(patternObject));

            Source src = getSource(context, method, pattern, patternObject);

            Object regexObject = context.getEnv().parseInternal(src).call();
            destination.setHiddenObject(patternObject, regexObject);

            try {
                int groupCount = integerInterop.asInt(regexObjectInterop.readMember(regexObject, "groupCount"));
                StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
                context.getMeta().java_util_regex_Pattern_capturingGroupCount.setInt(parentPattern, groupCount);
                reallocateGroupsArrayIfNecessary(context, self, parentPattern);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            if (context.getMeta().java_util_regex_Pattern_namedGroups_field.getObject(patternObject) == StaticObject.NULL) {
                try {
                    Object map = regexObjectInterop.readMember(regexObject, "groups");
                    Object keys = mapInterop.getMembers(map);
                    long size = arrayInterop.getArraySize(keys);

                    StaticObject guestMap = context.getMeta().java_util_HashMap.allocateInstance();
                    context.getMeta().java_util_HashMap_init.getCallTarget().call(guestMap, (int) size);

                    for (long i = 0; i < size; i++) {
                        String key = stringInterop.asString(arrayInterop.readArrayElement(keys, i));
                        Object value = mapInterop.readMember(map, key);
                        StaticObject guestKey = context.getMeta().toGuestString(key);

                        Object integerValue = context.getMeta().java_lang_Integer_valueOf.getCallTarget().call(value);
                        context.getMeta().java_util_HashMap_put.getCallTarget().call(guestMap, guestKey, integerValue);
                    }
                    context.getMeta().java_util_regex_Pattern_namedGroups_field.setObject(patternObject, guestMap);
                } catch (UnsupportedMessageException | UnknownIdentifierException | InvalidArrayIndexException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return regexObject;
        }
    }

    @GenerateInline
    @GenerateUncached
    public abstract static class JavaRegexExecNode extends Node {
        public abstract boolean execute(Node node, EspressoContext context, Object regexObject, StaticObject self, int from);

        @Specialization
        static boolean doDefault(EspressoContext context, Object regexObject, StaticObject self, int from,
                        @Cached TruffleString.SubstringByteIndexNode substringNode,
                        @CachedLibrary(limit = "3") InteropLibrary regexObjectInterop,
                        @CachedLibrary(limit = "3") InteropLibrary integerInterop,
                        @CachedLibrary(limit = "3") InteropLibrary booleanInterop,
                        @CachedLibrary(limit = "3") InteropLibrary execResInterop) {
            Object execRes;
            try {
                TruffleString truffleString = (TruffleString) context.getMeta().java_util_regex_Matcher_HIDDEN_tstring.getHiddenObject(self);

                int fromClipped = from < 0 ? 0 : from;
                context.getMeta().java_util_regex_Matcher_first.setInt(self, fromClipped);

                // bounds of the region we feed to TRegex
                int regionFrom = context.getMeta().java_util_regex_Matcher_from.getInt(self);
                int regionTo = context.getMeta().java_util_regex_Matcher_to.getInt(self);

                TruffleString matchTruffleString = truffleString;
                if (regionFrom != 0 || regionTo != truffleString.byteLength(TruffleString.Encoding.UTF_16) / 2) {
                    try {
                        matchTruffleString = substringNode.execute(truffleString, regionFrom * 2, (regionTo - regionFrom) * 2, TruffleString.Encoding.UTF_16, true);
                    } catch (IndexOutOfBoundsException e) {
                        context.getMeta().throwException(context.getMeta().java_lang_IndexOutOfBoundsException);
                    }
                }

                execRes = regexObjectInterop.invokeMember(regexObject, "exec", matchTruffleString, fromClipped - regionFrom);
                boolean isMatch = booleanInterop.asBoolean(execResInterop.readMember(execRes, "isMatch"));
                int modCount = context.getMeta().java_util_regex_Matcher_modCount.getInt(self);
                context.getMeta().java_util_regex_Matcher_modCount.setInt(self, modCount + 1);

                if (isMatch) {
                    int first = regionFrom + integerInterop.asInt(execResInterop.invokeMember(execRes, "getStart", 0));
                    int last = regionFrom + integerInterop.asInt(execResInterop.invokeMember(execRes, "getEnd", 0));

                    context.getMeta().java_util_regex_Matcher_first.setInt(self, first);
                    context.getMeta().java_util_regex_Matcher_last.setInt(self, last);

                    int groupCount = integerInterop.asInt(regexObjectInterop.readMember(regexObject, "groupCount"));
                    for (int i = 0; i < groupCount; i++) {
                        int start = regionFrom + integerInterop.asInt(execResInterop.invokeMember(execRes, "getStart", i));
                        int end = regionFrom + integerInterop.asInt(execResInterop.invokeMember(execRes, "getEnd", i));

                        StaticObject array = context.getMeta().java_util_regex_Matcher_groups.getObject(self);
                        array.<int[]> unwrap(context.getLanguage())[i * 2] = start;
                        array.<int[]> unwrap(context.getLanguage())[i * 2 + 1] = end;
                    }
                }

                return isMatch;
            } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @TruffleBoundary
    private static Source getSource(EspressoContext context, String method, String pattern, StaticObject patternObject) {
        String combined = "RegressionTestMode=true,Encoding=UTF-16,Flavor=JavaUtilPattern,PythonMethod=" + method;
        String sourceStr = combined + '/' + pattern + '/' + convertFlags(context.getMeta().java_util_regex_Pattern_flags0.getInt(patternObject));
        return Source.newBuilder("regex", sourceStr, "patternExpr").build();
    }

    @TruffleBoundary
    private static void executeSearch(EspressoContext context, StaticObject self, int from) {
        context.getMeta().java_util_regex_Matcher_search.getCallTargetNoSubstitution().call(self, from);
    }

    @TruffleBoundary
    private static void executeMatch(EspressoContext context, StaticObject self, int from, int action) {
        context.getMeta().java_util_regex_Matcher_match.getCallTargetNoSubstitution().call(self, from, action);
    }

    private static void reallocateGroupsArrayIfNecessary(EspressoContext context, StaticObject self, StaticObject parentPattern) {
        int parentGroupCount = Math.max(context.getMeta().java_util_regex_Pattern_capturingGroupCount.getInt(parentPattern), 10);
        StaticObject groups = context.getMeta().java_util_regex_Matcher_groups.getObject(self);
        if (groups.length(context.getLanguage()) != parentGroupCount * 2) {
            StaticObject newGroups = context.getMeta()._int.allocatePrimitiveArray(parentGroupCount * 2);
            context.getMeta().java_util_regex_Matcher_groups.setObject(self, newGroups);
        }
    }

    private static void compileFallBackIfRequired(EspressoContext context, StaticObject self) {
        StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
        if (context.getMeta().java_util_regex_Pattern_root.getObject(parentPattern) == StaticObject.NULL) {
            context.getMeta().java_util_regex_Pattern_compile.invokeDirect(parentPattern);
            reallocateGroupsArrayIfNecessary(context, self, parentPattern);

            int localCount = context.getMeta().java_util_regex_Pattern_localCount.getInt(parentPattern);
            int localsTCNCount = context.getMeta().java_util_regex_Pattern_localTCNCount.getInt(parentPattern);
            StaticObject locals = context.getMeta()._int.allocatePrimitiveArray(localCount);
            StaticObject localsPos = context.getMeta().java_util_regex_IntHashSet.allocateReferenceArray(localsTCNCount);
            context.getMeta().java_util_regex_Matcher_locals.setObject(self, locals);
            context.getMeta().java_util_regex_Matcher_localsPos.setObject(self, localsPos);
        }
    }

    private static void executeLastWithFallback(EspressoContext context, StaticObject self) {
        if (context.getMeta().java_util_regex_Matcher_HIDDEN_matchingModeBackup.getHiddenObject(self) != StaticObject.NULL) {
            int from = (int) context.getMeta().java_util_regex_Matcher_HIDDEN_searchFromBackup.getHiddenObject(self);
            int action = (int) context.getMeta().java_util_regex_Matcher_HIDDEN_matchingModeBackup.getHiddenObject(self);

            compileFallBackIfRequired(context, self);

            applyBackup(context, self);

            if (action <= 1) {
                executeMatch(context, self, from, action);
            } else {
                executeSearch(context, self, from);
            }
        }
    }

    private static void saveBackup(EspressoContext context, StaticObject self) {
        context.getMeta().java_util_regex_Matcher_HIDDEN_oldLastBackup.setHiddenObject(self, context.getMeta().java_util_regex_Matcher_oldLast.getValue(self));
        context.getMeta().java_util_regex_Matcher_HIDDEN_modCountBackup.setHiddenObject(self, context.getMeta().java_util_regex_Matcher_modCount.getValue(self));
        context.getMeta().java_util_regex_Matcher_HIDDEN_transparentBoundsBackup.setHiddenObject(self, context.getMeta().java_util_regex_Matcher_transparentBounds.getValue(self));
        context.getMeta().java_util_regex_Matcher_HIDDEN_anchoringBoundsBackup.setHiddenObject(self, context.getMeta().java_util_regex_Matcher_anchoringBounds.getValue(self));
        context.getMeta().java_util_regex_Matcher_HIDDEN_fromBackup.setHiddenObject(self, context.getMeta().java_util_regex_Matcher_from.getValue(self));
        context.getMeta().java_util_regex_Matcher_HIDDEN_toBackup.setHiddenObject(self, context.getMeta().java_util_regex_Matcher_to.getValue(self));
    }

    private static void applyBackup(EspressoContext context, StaticObject self) {
        context.getMeta().java_util_regex_Matcher_oldLast.setValue(self, context.getMeta().java_util_regex_Matcher_HIDDEN_oldLastBackup.getHiddenObject(self));
        context.getMeta().java_util_regex_Matcher_modCount.setValue(self, context.getMeta().java_util_regex_Matcher_HIDDEN_modCountBackup.getHiddenObject(self));
        context.getMeta().java_util_regex_Matcher_transparentBounds.setValue(self, context.getMeta().java_util_regex_Matcher_HIDDEN_transparentBoundsBackup.getHiddenObject(self));
        context.getMeta().java_util_regex_Matcher_anchoringBounds.setValue(self, context.getMeta().java_util_regex_Matcher_HIDDEN_anchoringBoundsBackup.getHiddenObject(self));
        context.getMeta().java_util_regex_Matcher_from.setValue(self, context.getMeta().java_util_regex_Matcher_HIDDEN_fromBackup.getHiddenObject(self));
        context.getMeta().java_util_regex_Matcher_to.setValue(self, context.getMeta().java_util_regex_Matcher_HIDDEN_toBackup.getHiddenObject(self));
    }

    private static boolean isUnsupported(EspressoContext context, StaticObject self) {
        StaticObject parentPattern = context.getMeta().java_util_regex_Matcher_parentPattern.getObject(self);
        Object unsupported = context.getMeta().java_util_regex_Pattern_HIDDEN_unsupported.getHiddenObject(parentPattern);
        boolean anchoringBounds = context.getMeta().java_util_regex_Matcher_anchoringBounds.getBoolean(self);
        boolean transparentBounds = context.getMeta().java_util_regex_Matcher_transparentBounds.getBoolean(self);
        return unsupported == null || (boolean) unsupported || !anchoringBounds || transparentBounds;
    }

    private static void saveTruffleString(
                    StaticObject self,
                    EspressoContext context,
                    TruffleString.FromJavaStringNode fromJavaStringNode,
                    StaticObject originalCharSeq) {
        StaticObject originalString = originalCharSeq;
        if (originalCharSeq.getKlass() != context.getMeta().java_lang_String) {
            if (originalCharSeq.getKlass() == null) {
                context.getMeta().throwNullPointerException();
            }
            Method handleMethodToString = ((ObjectKlass) originalCharSeq.getKlass()).itableLookup(context.getMeta().java_lang_CharSequence,
                            context.getMeta().java_lang_CharSequence_toString.getITableIndex());
            originalString = (StaticObject) handleMethodToString.invokeDirect(originalCharSeq);
        }

        TruffleString truffleString = fromJavaStringNode.execute(context.getMeta().toHostString(originalString), TruffleString.Encoding.UTF_16);
        context.getMeta().java_util_regex_Matcher_HIDDEN_tstring.setHiddenObject(self, truffleString);
    }
}
