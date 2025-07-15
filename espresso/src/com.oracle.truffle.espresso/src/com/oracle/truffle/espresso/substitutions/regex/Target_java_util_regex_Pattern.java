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

import static com.oracle.truffle.espresso.substitutions.regex.Target_java_util_regex_Matcher.getSource;

import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoInlineNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

/**
 * When {@link com.oracle.truffle.espresso.EspressoOptions#UseTRegex} is enabled, the substitutions
 * in this package get enabled, which replaces guest {@code regex} manipulations with
 * {@code TRegex}.
 * <p>
 * On creating a pattern, we intercept the guest constructor to {@link TRegexStatus#Validate
 * validate} the pattern string.
 * <ul>
 * <li>If validation succeeds, we mark the guest pattern object as
 * {@link TRegexStatus#isSupported(Meta, StaticObject) supported}, and summarily fill the guest
 * object.</li>
 * <li>If validation fails, we simply fallback to the original guest implementation, and will not
 * further involve {@code TRegex} for that pattern.</li>
 * </ul>
 * <p>
 * Note that we do not yet compile the {@code TRegex} pattern yet, as compiling requires knowing
 * what action it needs to perform (ie: {@link TRegexStatus#Match},{@link TRegexStatus#FullMatch},
 * or {@link TRegexStatus#Search}).
 * <p>
 * Such compilation is performed if needed by {@link TRegexEnsureCompiledNode}, that is invoked
 * during relevant guest {@link java.util.regex.Matcher} method calls:
 * {@code Matcher#match(int, int)} and {@code Matcher#search(int)}.
 * <p>
 * Note: Since guest serialization does not retain hidden fields, a guest-deserialized pattern is
 * considered unsupported for now.
 */
@EspressoSubstitutions
public final class Target_java_util_regex_Pattern {
    static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, "Regex");
    private static final int ALL_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE |
                    Pattern.DOTALL | Pattern.UNICODE_CASE | Pattern.CANON_EQ | Pattern.UNIX_LINES | Pattern.LITERAL |
                    Pattern.UNICODE_CHARACTER_CLASS | Pattern.COMMENTS;

    private Target_java_util_regex_Pattern() {
    }

    static boolean isUnsupportedPattern(StaticObject parentPattern, Meta meta) {
        return !TRegexStatus.isSupported(meta, parentPattern);
    }

    @Substitution(hasReceiver = true, methodName = "<init>", languageFilter = UseTRegexFilter.class)
    abstract static class Init extends SubstitutionNode {

        abstract void execute(@JavaType(Pattern.class) StaticObject self, @JavaType(String.class) StaticObject p, int f);

        @Specialization
        void doDefault(StaticObject self, StaticObject p, int f,
                        @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "3") InteropLibrary regexInterop,
                        @Cached("create(getMeta().tRegexSupport.java_util_regex_Pattern_init.getCallTargetNoSubstitution())") DirectCallNode original,
                        @Cached InlinedBranchProfile parseErrorProfile, @Cached InlinedBranchProfile argErrorProfile) {
            assert getLanguage().useTRegex();

            Meta meta = context.getMeta();
            if (StaticObject.isNull(p)) {
                argErrorProfile.enter(this);
                throw meta.throwNullPointerException();
            }
            if ((f & ~ALL_FLAGS) != 0) {
                argErrorProfile.enter(this);
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, unknownFlagMessage(f));
            }
            String pattern = meta.toHostString(p);
            Source src = getSource(TRegexStatus.Validate, pattern, f, meta.getJavaVersion());

            TRegexStatus.init(meta, self);
            try {
                context.getEnv().parseInternal(src).call();
            } catch (Exception e) {
                parseErrorProfile.enter(this);
                try {
                    if (regexInterop.isException(e) && regexInterop.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        // this can either RegexSyntaxException or UnsupportedRegexException (no
                        // good way to distinguish)
                        LOGGER.log(Level.FINE, e, () -> "Unsupported Pattern: " + pattern);
                        original.call(self, p, f);
                        TRegexStatus.setGuestCompiled(meta, self);
                        return;
                    } else {
                        throw e;
                    }
                } catch (UnsupportedMessageException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere(ex);
                }
            }

            meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_tregexSearch.setHiddenObject(self, null);
            meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_tregexMatch.setHiddenObject(self, null);
            meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_tregexFullmatch.setHiddenObject(self, null);

            meta.tRegexSupport.java_util_regex_Pattern_capturingGroupCount.setInt(self, 0);
            meta.tRegexSupport.java_util_regex_Pattern_pattern.setObject(self, p);
            meta.tRegexSupport.java_util_regex_Pattern_flags.setInt(self, f);
            meta.tRegexSupport.java_util_regex_Pattern_flags0.setInt(self, f);
            meta.tRegexSupport.java_util_regex_Pattern_compiled.setBoolean(self, true);

            TRegexStatus.setSupported(meta, self);
        }
    }

    @Substitution(hasReceiver = true, methodName = "namedGroups", languageFilter = UseTRegexFilter.class)
    abstract static class NamedGroups extends SubstitutionNode {

        abstract @JavaType(Map.class) StaticObject execute(@JavaType(Pattern.class) StaticObject self);

        @Specialization
        @JavaType(Map.class)
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(getMeta().tRegexSupport.java_util_regex_Pattern_namedGroups.getCallTargetNoSubstitution())") DirectCallNode original) {
            assert context.getLanguage().useTRegex();
            Meta meta = context.getMeta();
            if (!TRegexStatus.isGroupDataValid(meta, self)) {
                Target_java_util_regex_Matcher.compileFallBackIfRequired(self, meta);
            }
            return (StaticObject) original.call(self);
        }
    }

    @TruffleBoundary
    public static String convertFlags(int flags) {
        StringBuilder sb = new StringBuilder(8);
        if (isSet(flags, Pattern.CANON_EQ)) {
            sb.append('c');
        }
        if (isSet(flags, Pattern.UNICODE_CHARACTER_CLASS)) {
            sb.append('U');
        }
        if (isSet(flags, Pattern.UNIX_LINES)) {
            sb.append('d');
        }
        if (isSet(flags, Pattern.CASE_INSENSITIVE)) {
            sb.append('i');
        }
        if (isSet(flags, Pattern.MULTILINE)) {
            sb.append('m');
        }
        if (isSet(flags, Pattern.DOTALL)) {
            sb.append('s');
        }
        if (isSet(flags, Pattern.UNICODE_CASE)) {
            sb.append('u');
        }
        if (isSet(flags, Pattern.COMMENTS)) {
            sb.append('x');
        }
        if (isSet(flags, Pattern.LITERAL)) {
            sb.append('l');
        }
        return sb.toString();
    }

    @TruffleBoundary
    private static String unknownFlagMessage(int f) {
        return "Unknown flag 0x" + Integer.toHexString(f);
    }

    private static boolean isSet(int flags, int flag) {
        return (flags & flag) != 0;
    }

    @GenerateInline
    @GenerateUncached
    @ImportStatic(TRegexStatus.class)
    abstract static class TRegexEnsureCompiledNode extends EspressoInlineNode {
        public abstract Object execute(Node node, Meta meta, StaticObject self, TRegexStatus action, Field destination);

        @Specialization(guards = "isTRegexCompiled(meta, pattern, action)")
        static Object doAlreadyCompiled(Meta meta, StaticObject pattern, TRegexStatus action, Field destination) {
            assert TRegexStatus.isTRegexCompiled(meta, pattern, action);
            return destination.getHiddenObject(pattern);
        }

        @Specialization(guards = "!isTRegexCompiled(meta, pattern, action)")
        static Object doCompile(Meta meta, StaticObject pattern, TRegexStatus action, Field destination,
                        @CachedLibrary(limit = "3") InteropLibrary regexObjectInterop,
                        @CachedLibrary(limit = "3") InteropLibrary integerInterop,
                        @CachedLibrary(limit = "3") InteropLibrary mapInterop,
                        @CachedLibrary(limit = "3") InteropLibrary stringInterop,
                        @CachedLibrary(limit = "3") InteropLibrary arrayInterop) {
            String patternString = meta.toHostString(meta.tRegexSupport.java_util_regex_Pattern_pattern.getObject(pattern));

            // The result is a truffle object of the form:
            /*-
             * {
             *      Readable Members:
             *          int groupCount;
             *          Map<TruffleString, Integer> groups;
             *      Invocable Members:
             *          Object exec(String text, int from, int to, int regionFrom, int regionTo);
             * }
             */
            // Note that the 'groups' map is not an interop map, but rather a truffle object whose
            // member names are the keys, and whose member values are the value.
            Source src = getSource(action, patternString, meta.tRegexSupport.java_util_regex_Pattern_flags.getInt(pattern), meta.getJavaVersion());
            Object regexObject = meta.getContext().getEnv().parseInternal(src).call();
            LOGGER.log(Level.FINEST, () -> "Compiled Pattern: " + patternString);
            destination.setHiddenObject(pattern, regexObject);

            int groupCount = getGroupCount(regexObject, regexObjectInterop, integerInterop);
            meta.tRegexSupport.java_util_regex_Pattern_capturingGroupCount.setInt(pattern, groupCount);

            StaticObject existingNamedGroups = meta.tRegexSupport.java_util_regex_Pattern_namedGroups_field.getObject(pattern);
            if (StaticObject.isNull(existingNamedGroups)) {
                // 'map' is a regular truffle object of the form:
                /*-
                 * {
                 *      Readable Members:
                 *          int groupName1;
                 *          int groupName2;
                 *          ...
                 *          int groupNameN;
                 * }
                 */
                Object map = getGroups(regexObject, regexObjectInterop);

                Object groupNames = getMembers(map, mapInterop);
                long namedGroupsCount = getArraySize(groupNames, arrayInterop);

                StaticObject guestMap = meta.java_util_HashMap.allocateInstance();
                meta.java_util_HashMap_init.invokeDirectSpecial(guestMap, (int) namedGroupsCount);

                for (long i = 0; i < namedGroupsCount; i++) {
                    String groupName = getKey(groupNames, i, stringInterop, arrayInterop);
                    StaticObject guestGroupName = meta.toGuestString(groupName);

                    int groupID = (int) getValue(map, groupName, mapInterop);
                    StaticObject guestGroupID = meta.boxInteger(groupID);

                    // no need for virtual dispatch, we know the receiver type
                    meta.java_util_HashMap_put.invokeDirect(guestMap, guestGroupName, guestGroupID);
                }
                meta.tRegexSupport.java_util_regex_Pattern_namedGroups_field.setObject(pattern, guestMap);
            } else {
                assert checkNamedGroups(regexObject, existingNamedGroups, meta);
            }
            // Make it known that this pattern has the corresponding action TRegex-compiled.
            TRegexStatus.setTRegexStatus(meta, pattern, action);
            return regexObject;
        }

        private static boolean checkNamedGroups(Object regexObject, StaticObject existingNamedGroups, Meta meta) {
            InteropLibrary library = InteropLibrary.getUncached();
            Object map = getGroups(regexObject, library);
            Object keys = getMembers(map, library);
            long size = getArraySize(keys, library);

            Method getMethod = ((ObjectKlass) existingNamedGroups.getKlass()).itableLookup(meta.java_util_Map, meta.java_util_Map_get.getITableIndex());
            int existingSize = (int) meta.java_util_Map_size.invokeDirectInterface(existingNamedGroups);
            assert existingSize == size : "Expected groups of size " + size + " but found " + existingSize;
            for (long i = 0; i < size; i++) {
                String key = getKey(keys, i, library, library);
                int index = (int) getValue(map, key, library);
                StaticObject exitingValue = (StaticObject) getMethod.invokeDirect(map, meta.toGuestString(key));
                assert StaticObject.notNull(exitingValue) : "Expected index for group " + key + " to be " + index + " but it was not found";
                int existingIntValue = meta.unboxInteger(exitingValue);
                assert existingIntValue == index : "Expected index for group " + key + " to be " + index + " but found " + existingIntValue;
            }
            return true;
        }
    }

    private static Object getValue(Object map, String key, InteropLibrary mapInterop) {
        try {
            return mapInterop.readMember(map, key);
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static String getKey(Object keys, long i, InteropLibrary stringInterop, InteropLibrary arrayInterop) {
        try {
            return stringInterop.asString(arrayInterop.readArrayElement(keys, i));
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static long getArraySize(Object keys, InteropLibrary arrayInterop) {
        try {
            return arrayInterop.getArraySize(keys);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static Object getMembers(Object map, InteropLibrary mapInterop) {
        try {
            return mapInterop.getMembers(map);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static Object getGroups(Object regexObject, InteropLibrary regexObjectInterop) {
        try {
            return regexObjectInterop.readMember(regexObject, "groups");
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static int getGroupCount(Object regexObject, InteropLibrary regexObjectInterop, InteropLibrary integerInterop) {
        try {
            return integerInterop.asInt(regexObjectInterop.readMember(regexObject, "groupCount"));
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
