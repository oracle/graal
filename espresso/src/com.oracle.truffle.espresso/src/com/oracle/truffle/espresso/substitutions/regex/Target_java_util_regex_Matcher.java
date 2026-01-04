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
package com.oracle.truffle.espresso.substitutions.regex;

import static com.oracle.truffle.espresso.substitutions.regex.Target_java_util_regex_Matcher.GetMatcherTruffleStringNode.getText;
import static com.oracle.truffle.espresso.substitutions.regex.Target_java_util_regex_Pattern.convertFlags;
import static java.lang.Math.max;

import java.util.regex.Matcher;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.espresso.classfile.JavaVersion;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoInlineNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeInterface;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.substitutions.regex.Target_java_util_regex_Pattern.TRegexEnsureCompiledNode;

/**
 * If {@link com.oracle.truffle.espresso.EspressoOptions#UseTRegex} is enabled and the pattern is
 * {@link TRegexStatus#isSupported(Meta, StaticObject) supported}, we intercept calls to
 * {@code Matcher#match(int, int)} and {@code Matcher#search(int)} in order to run {@code TRegex}
 * instead of the guest {@code regex}.
 * <p>
 * Both of these operations follow the same procedure:
 * <p>
 * <ul>
 * <li>Ensure the pattern is {@code TRegex}-compiled with the corresponding {@link TRegexStatus
 * action} (ie: {@link TRegexStatus#Match},{@link TRegexStatus#FullMatch}, or
 * {@link TRegexStatus#Search}).</li>
 * <li>Use the {@code TRegex}-compiled object to execute the matching/searching.</li>
 * <li>Fill up the guest {@link Matcher} from data we extract from the result of {@code TRegex}
 * execution.</li>
 * </ul>
 */
@EspressoSubstitutions
public final class Target_java_util_regex_Matcher {
    static final int NOANCHOR = 0;
    static final int ENDANCHOR = 1;

    private Target_java_util_regex_Matcher() {
    }

    @ImportStatic(Target_java_util_regex_Matcher.class)
    @Substitution(hasReceiver = true, languageFilter = UseTRegexFilter.class)
    abstract static class Match extends SubstitutionNode {
        abstract boolean execute(@JavaType(Matcher.class) StaticObject self, int from, int anchor);

        @Specialization(guards = {"!isUnsupportedMatcher(self, meta)", "anchor == ENDANCHOR"})
        static boolean doFullMatch(StaticObject self, int from, int anchor,
                        @Bind("getMeta()") Meta meta,
                        @Shared("exec") @Cached TRegexExecNode tRegexExecNode,
                        @Shared("compile") @Cached TRegexEnsureCompiledNode tRegexCompileNode,
                        @Bind Node node) {
            assert meta.getLanguage().useTRegex();
            assert !isUnsupportedMatcher(self, meta) && anchor == ENDANCHOR;

            TRegexStatus action = TRegexStatus.FullMatch;
            Field destination = meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_tregexFullmatch;

            return runTRegexMatch(meta, self, from, node, tRegexCompileNode, tRegexExecNode, action, destination);
        }

        @Specialization(guards = {"!isUnsupportedMatcher(self, meta)", "anchor == NOANCHOR"})
        static boolean doMatch(StaticObject self, int from, int anchor,
                        @Bind("getMeta()") Meta meta,
                        @Shared("exec") @Cached TRegexExecNode tRegexExecNode,
                        @Shared("compile") @Cached TRegexEnsureCompiledNode tRegexCompileNode,
                        @Bind Node node) {
            assert meta.getLanguage().useTRegex();
            assert !isUnsupportedMatcher(self, meta) && anchor == NOANCHOR;

            TRegexStatus action = TRegexStatus.Match;
            Field destination = meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_tregexMatch;

            return runTRegexMatch(meta, self, from, node, tRegexCompileNode, tRegexExecNode, action, destination);
        }

        @Specialization(guards = "isUnsupportedMatcher(self, meta)")
        static boolean doFallback(StaticObject self, int from, int anchor,
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.tRegexSupport.java_util_regex_Matcher_match.getCallTargetNoSubstitution())") DirectCallNode original) {
            assert meta.getLanguage().useTRegex();
            assert isUnsupportedMatcher(self, meta);

            meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, StaticObject.NULL);
            compileFallBackIfRequired(self, meta);
            return (Boolean) original.call(self, from, anchor);
        }

        private static boolean runTRegexMatch(Meta meta,
                        StaticObject guestMatcher, int from,
                        Node node,
                        TRegexEnsureCompiledNode tRegexCompileNode,
                        TRegexExecNode tRegexExecNode,
                        TRegexStatus action, Field destination) {
            CompilerAsserts.partialEvaluationConstant(action);

            meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_searchFromBackup.setInt(guestMatcher, from);
            meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(guestMatcher, action);

            StaticObject guestPattern = meta.tRegexSupport.java_util_regex_Matcher_parentPattern.getObject(guestMatcher);
            Object tRegexObject = tRegexCompileNode.execute(node, meta, guestPattern, action, destination);
            assert tRegexObject != null;

            saveBackup(guestMatcher, meta);
            if (tRegexExecNode.execute(node, tRegexObject, guestMatcher, from, meta)) {
                if (action == TRegexStatus.FullMatch) {
                    int last = meta.tRegexSupport.java_util_regex_Matcher_last.getInt(guestMatcher);
                    int to = meta.tRegexSupport.java_util_regex_Matcher_to.getInt(guestMatcher);
                    return last == to;
                } else {
                    assert action == TRegexStatus.Match;
                    return true;
                }
            }
            return false;
        }
    }

    @ImportStatic(Target_java_util_regex_Matcher.class)
    @Substitution(hasReceiver = true, languageFilter = UseTRegexFilter.class)
    abstract static class Search extends SubstitutionNode {
        abstract boolean execute(@JavaType(Matcher.class) StaticObject self, int from);

        @Specialization(guards = {"!isUnsupportedMatcher(self, meta)"})
        static boolean doSearch(StaticObject self, int from,
                        @Bind("getMeta()") Meta meta,
                        @Cached TRegexExecNode tRegexExecNode,
                        @Cached TRegexEnsureCompiledNode tRegexCompileNode,
                        @Bind Node node) {
            TRegexStatus action = TRegexStatus.Search;
            Field destination = meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_tregexSearch;

            assert meta.getLanguage().useTRegex();
            meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_searchFromBackup.setInt(self, from);
            meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, TRegexStatus.Search);

            StaticObject pattern = meta.tRegexSupport.java_util_regex_Matcher_parentPattern.getObject(self);
            Object regexObject = tRegexCompileNode.execute(node, meta, pattern, action, destination);

            saveBackup(self, meta);
            return tRegexExecNode.execute(node, regexObject, self, from, meta);
        }

        @Specialization(guards = "isUnsupportedMatcher(self, meta)")
        static boolean doFallback(StaticObject self, int from,
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.tRegexSupport.java_util_regex_Matcher_search.getCallTargetNoSubstitution())") DirectCallNode original) {
            assert meta.getLanguage().useTRegex();
            meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, StaticObject.NULL);
            compileFallBackIfRequired(self, meta);
            return (Boolean) original.call(self, from);
        }
    }

    @Substitution(hasReceiver = true, languageFilter = UseTRegexFilter.class)
    public static boolean hitEnd(StaticObject self, @Inject Meta meta) {
        executeLastWithFallback(self, meta);
        return meta.tRegexSupport.java_util_regex_Matcher_hitEnd.getBoolean(self);
    }

    @Substitution(hasReceiver = true, languageFilter = UseTRegexFilter.class)
    public static boolean requireEnd(StaticObject self, @Inject Meta meta) {
        executeLastWithFallback(self, meta);
        return meta.tRegexSupport.java_util_regex_Matcher_requireEnd.getBoolean(self);
    }

    @Substitution(hasReceiver = true, languageFilter = UseTRegexFilter.class)
    abstract static class GroupCount extends SubstitutionNode {
        abstract int execute(@JavaType(Matcher.class) StaticObject self);

        @Specialization
        static int doDefault(StaticObject self,
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.tRegexSupport.java_util_regex_Matcher_groupCount.getCallTargetNoSubstitution())") DirectCallNode original) {
            StaticObject pattern = meta.tRegexSupport.java_util_regex_Matcher_parentPattern.getObject(self);
            if (!TRegexStatus.isGroupDataValid(meta, pattern)) {
                compileFallBackIfRequired(self, meta);
            }
            return (Integer) original.call(self);
        }
    }

    @GenerateInline
    public abstract static class TRegexExecNode extends EspressoInlineNode {
        public abstract boolean execute(Node node, Object regexObject, StaticObject guestMatcher, int from, Meta meta);

        @Specialization
        static boolean doDefault(Node node, Object regexObject, StaticObject guestMatcher, int from, Meta meta,
                        @Cached GetMatcherTruffleStringNode getTruffleString,
                        @CachedLibrary(limit = "3") InteropLibrary regexObjectInterop,
                        @CachedLibrary(limit = "3") InteropLibrary integerInterop,
                        @CachedLibrary(limit = "3") InteropLibrary booleanInterop,
                        @CachedLibrary(limit = "3") InteropLibrary execResInterop,
                        @Cached InlinedBranchProfile ioobeProfile) {
            Object regexApplyResult;
            try {
                StaticObject patternObject = meta.tRegexSupport.java_util_regex_Matcher_parentPattern.getObject(guestMatcher);
                assert TRegexStatus.isTRegexCompiled(meta, patternObject);

                TruffleString truffleString = getTruffleString.execute(node, guestMatcher, getText(guestMatcher, meta), meta);

                int fromClipped = max(from, 0);
                meta.tRegexSupport.java_util_regex_Matcher_first.setInt(guestMatcher, fromClipped);

                // bounds of the region we feed to TRegex
                int regionFrom = meta.tRegexSupport.java_util_regex_Matcher_from.getInt(guestMatcher);
                int regionTo = meta.tRegexSupport.java_util_regex_Matcher_to.getInt(guestMatcher);

                int truffleStringLength = truffleString.byteLength(TruffleString.Encoding.UTF_16) >> 1;
                if (regionFrom < 0 || regionTo < regionFrom || regionTo > truffleStringLength) {
                    ioobeProfile.enter(node);
                    meta.throwException(meta.java_lang_IndexOutOfBoundsException);
                }

                // The result of invoking 'exec' is a truffle object of the form:
                /*-
                 * {
                 *      Readable Members:
                 *          boolean isMatch;
                 *      Invocable Members:
                 *          int getStart(int group);
                 *          int getEnd(int group);
                 * }
                 */
                regexApplyResult = regexObjectInterop.invokeMember(regexObject, "exec", truffleString, fromClipped, regionTo, regionFrom, regionTo);
                boolean isMatch = booleanInterop.asBoolean(execResInterop.readMember(regexApplyResult, "isMatch"));

                int modCount = meta.tRegexSupport.java_util_regex_Matcher_modCount.getInt(guestMatcher);
                meta.tRegexSupport.java_util_regex_Matcher_modCount.setInt(guestMatcher, modCount + 1);

                if (isMatch) {
                    // Make sure the matcher's groups array corresponds to the current pattern.
                    synchronizeWithParentPattern(guestMatcher, patternObject, meta);

                    int first = integerInterop.asInt(execResInterop.invokeMember(regexApplyResult, "getStart", 0));
                    meta.tRegexSupport.java_util_regex_Matcher_first.setInt(guestMatcher, first);

                    int last = integerInterop.asInt(execResInterop.invokeMember(regexApplyResult, "getEnd", 0));
                    meta.tRegexSupport.java_util_regex_Matcher_last.setInt(guestMatcher, last);

                    StaticObject matcherGroupsArray = meta.tRegexSupport.java_util_regex_Matcher_groups.getObject(guestMatcher);
                    int groupCount = meta.tRegexSupport.java_util_regex_Pattern_capturingGroupCount.getInt(patternObject);

                    assert groupCount == Target_java_util_regex_Pattern.getGroupCount(regexObject, regexObjectInterop,
                                    integerInterop) : "Different TRegex actions for the same pattern produce different group counts.";

                    // Fill the matcher's data as guest expects it.
                    int[] unwrapped = matcherGroupsArray.unwrap(meta.getLanguage());
                    for (int i = 0; i < groupCount; i++) {
                        int start = integerInterop.asInt(execResInterop.invokeMember(regexApplyResult, "getStart", i));
                        int end = integerInterop.asInt(execResInterop.invokeMember(regexApplyResult, "getEnd", i));
                        unwrapped[i * 2] = start;
                        unwrapped[i * 2 + 1] = end;
                    }
                } else {
                    // Fail state w.r.t. guest.
                    meta.tRegexSupport.java_util_regex_Matcher_first.setInt(guestMatcher, -1);
                }
                meta.tRegexSupport.java_util_regex_Matcher_oldLast.setInt(guestMatcher, meta.tRegexSupport.java_util_regex_Matcher_last.getInt(guestMatcher));
                return isMatch;
            } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateInline
    public abstract static class GetMatcherTruffleStringNode extends EspressoInlineNode {
        public abstract TruffleString execute(Node node, StaticObject matcher, StaticObject text, Meta meta);

        @Specialization(guards = "isSynced(matcher, text, meta)")
        static TruffleString doSynced(StaticObject matcher, StaticObject text, Meta meta) {
            assert isSynced(matcher, text, meta);
            return (TruffleString) meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_tstring.getHiddenObject(matcher);
        }

        @Specialization(guards = {"!isSynced(matcher, text, meta)", "isString(text, meta)"})
        static TruffleString doUnsyncedString(StaticObject matcher, StaticObject text, Meta meta,
                        @Shared("fromJava") @Cached TruffleString.FromJavaStringNode fromJavaString) {
            return saveTruffleString(matcher, text, text, fromJavaString, meta);
        }

        @Specialization(guards = {"!isSynced(matcher, text, meta)", "!isString(text, meta)"})
        static TruffleString doUnsyncedCharSeq(StaticObject matcher, StaticObject text, Meta meta,
                        @Shared("fromJava") @Cached TruffleString.FromJavaStringNode fromJavaString,
                        @Cached("create(meta.java_lang_CharSequence_toString)") InvokeInterface toStringCall) {
            StaticObject textStr = (StaticObject) toStringCall.execute(new Object[]{text});
            return saveTruffleString(matcher, textStr, text, fromJavaString, meta);
        }

        static boolean isString(StaticObject obj, Meta meta) {
            return obj.getKlass() == meta.java_lang_String;
        }

        static boolean isSynced(StaticObject matcher, StaticObject text, Meta meta) {
            Object syncedText = meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_textSync.getHiddenObject(matcher);
            return syncedText == text;
        }

        static StaticObject getText(StaticObject matcher, Meta meta) {
            return meta.tRegexSupport.java_util_regex_Matcher_text.getObject(matcher);
        }
    }

    @TruffleBoundary
    static Source getSource(TRegexStatus action, String pattern, int flags, JavaVersion javaVersion) {
        String combined = "Encoding=UTF-16,Flavor=JavaUtilPattern," + action.actionString() + ",JavaJDKVersion=" + javaVersion;
        String sourceStr = combined + '/' + pattern + '/' + convertFlags(flags);
        return Source.newBuilder("regex", sourceStr, "patternExpr").build();
    }

    @TruffleBoundary
    private static void executeSearch(StaticObject self, int from, Meta meta) {
        meta.tRegexSupport.java_util_regex_Matcher_search.getCallTargetNoSubstitution().call(self, from);
    }

    @TruffleBoundary
    private static void executeMatch(StaticObject self, int from, int anchor, Meta meta) {
        meta.tRegexSupport.java_util_regex_Matcher_match.getCallTargetNoSubstitution().call(self, from, anchor);
    }

    private static void synchronizeWithParentPattern(StaticObject self, StaticObject parentPattern, Meta meta) {
        assert TRegexStatus.isGroupDataValid(meta, parentPattern);
        Object currentSync = meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_patternSync.getHiddenObject(self);
        if (currentSync != null && currentSync == parentPattern) {
            // Already sync-ed
            return;
        }
        int parentGroupCount = meta.tRegexSupport.java_util_regex_Pattern_capturingGroupCount.getInt(parentPattern);
        if (meta.getJavaVersion().java21OrEarlier()) {
            parentGroupCount = max(parentGroupCount, 10);
        }
        StaticObject groups = meta.tRegexSupport.java_util_regex_Matcher_groups.getObject(self);
        if (groups.length(meta.getLanguage()) != parentGroupCount * 2) {
            StaticObject newGroups = meta._int.allocatePrimitiveArray(parentGroupCount * 2);
            meta.tRegexSupport.java_util_regex_Matcher_groups.setObject(self, newGroups);
        }
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_patternSync.setHiddenObject(self, parentPattern);
    }

    static void compileFallBackIfRequired(StaticObject self, Meta meta) {
        StaticObject parentPattern = meta.tRegexSupport.java_util_regex_Matcher_parentPattern.getObject(self);
        if (StaticObject.isNull(meta.tRegexSupport.java_util_regex_Pattern_root.getObject(parentPattern))) {
            meta.tRegexSupport.java_util_regex_Pattern_capturingGroupCount.setInt(parentPattern, 1);
            meta.tRegexSupport.java_util_regex_Pattern_compile.invokeDirectSpecial(parentPattern);
            TRegexStatus.setGuestCompiled(meta, parentPattern);
        } else if (!TRegexStatus.isGuestCompiled(meta, parentPattern)) {
            // Deserialized pattern.
            assert !TRegexStatus.isInitialized(meta, parentPattern);
            TRegexStatus.init(meta, parentPattern);
            TRegexStatus.setGuestCompiled(meta, parentPattern);
        }
        assert TRegexStatus.isGuestCompiled(meta, parentPattern);
        synchronizeWithParentPattern(self, parentPattern, meta);

        int localCount = meta.tRegexSupport.java_util_regex_Pattern_localCount.getInt(parentPattern);
        int localsTCNCount = meta.tRegexSupport.java_util_regex_Pattern_localTCNCount.getInt(parentPattern);
        StaticObject locals = meta._int.allocatePrimitiveArray(localCount);
        StaticObject localsPos = meta.tRegexSupport.java_util_regex_IntHashSet.allocateReferenceArray(localsTCNCount);
        meta.tRegexSupport.java_util_regex_Matcher_locals.setObject(self, locals);
        meta.tRegexSupport.java_util_regex_Matcher_localsPos.setObject(self, localsPos);
    }

    private static void executeLastWithFallback(StaticObject self, Meta meta) {
        if (meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_matchingModeBackup.getHiddenObject(self) != StaticObject.NULL) {
            int from = meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_searchFromBackup.getInt(self);
            TRegexStatus action = (TRegexStatus) meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_matchingModeBackup.getHiddenObject(self);
            assert action != TRegexStatus.Validate;
            compileFallBackIfRequired(self, meta);
            applyBackup(self, meta);
            meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_matchingModeBackup.setHiddenObject(self, StaticObject.NULL);
            switch (action) {
                case Match -> executeMatch(self, from, NOANCHOR, meta);
                case FullMatch -> executeMatch(self, from, ENDANCHOR, meta);
                case Search -> executeSearch(self, from, meta);
            }
        }
    }

    private static void saveBackup(StaticObject self, Meta meta) {
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_oldLastBackup.setInt(self, meta.tRegexSupport.java_util_regex_Matcher_oldLast.getInt(self));
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_modCountBackup.setInt(self, meta.tRegexSupport.java_util_regex_Matcher_modCount.getInt(self));
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_transparentBoundsBackup.setBoolean(self, meta.tRegexSupport.java_util_regex_Matcher_transparentBounds.getBoolean(self));
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_anchoringBoundsBackup.setBoolean(self, meta.tRegexSupport.java_util_regex_Matcher_anchoringBounds.getBoolean(self));
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_fromBackup.setInt(self, meta.tRegexSupport.java_util_regex_Matcher_from.getInt(self));
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_toBackup.setInt(self, meta.tRegexSupport.java_util_regex_Matcher_to.getInt(self));
    }

    private static void applyBackup(StaticObject self, Meta meta) {
        meta.tRegexSupport.java_util_regex_Matcher_oldLast.setInt(self, meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_oldLastBackup.getInt(self));
        meta.tRegexSupport.java_util_regex_Matcher_modCount.setInt(self, meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_modCountBackup.getInt(self));
        meta.tRegexSupport.java_util_regex_Matcher_transparentBounds.setBoolean(self, meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_transparentBoundsBackup.getBoolean(self));
        meta.tRegexSupport.java_util_regex_Matcher_anchoringBounds.setBoolean(self, meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_anchoringBoundsBackup.getBoolean(self));
        meta.tRegexSupport.java_util_regex_Matcher_from.setInt(self, meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_fromBackup.getInt(self));
        meta.tRegexSupport.java_util_regex_Matcher_to.setInt(self, meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_toBackup.getInt(self));
    }

    static boolean isUnsupportedMatcher(StaticObject self, Meta meta) {
        assert meta.getLanguage().useTRegex();
        assert meta.tRegexSupport.java_util_regex_Matcher_parentPattern != null;

        StaticObject parentPattern = meta.tRegexSupport.java_util_regex_Matcher_parentPattern.getObject(self);
        if (Target_java_util_regex_Pattern.isUnsupportedPattern(parentPattern, meta)) {
            return true;
        }

        return !meta.tRegexSupport.java_util_regex_Matcher_anchoringBounds.getBoolean(self) || meta.tRegexSupport.java_util_regex_Matcher_transparentBounds.getBoolean(self);
    }

    static boolean isString(StaticObject obj, Meta meta) {
        return obj.getKlass() == meta.java_lang_String;
    }

    private static TruffleString saveTruffleString(
                    StaticObject matcher,
                    StaticObject espressoString,
                    StaticObject sync,
                    TruffleString.FromJavaStringNode fromJavaStringNode,
                    Meta meta) {
        TruffleString truffleString = fromJavaStringNode.execute(meta.toHostString(espressoString), TruffleString.Encoding.UTF_16);
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_tstring.setHiddenObject(matcher, truffleString);
        meta.tRegexSupport.java_util_regex_Matcher_HIDDEN_textSync.setHiddenObject(matcher, sync);
        return truffleString;
    }
}
