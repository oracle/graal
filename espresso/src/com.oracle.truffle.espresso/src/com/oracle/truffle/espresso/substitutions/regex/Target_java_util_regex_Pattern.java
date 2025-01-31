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
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.substitutions.regex.Target_java_util_regex_Matcher.RegexAction;

@EspressoSubstitutions
public final class Target_java_util_regex_Pattern {
    static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, "Regex");
    private static final int ALL_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE |
                    Pattern.DOTALL | Pattern.UNICODE_CASE | Pattern.CANON_EQ | Pattern.UNIX_LINES | Pattern.LITERAL |
                    Pattern.UNICODE_CHARACTER_CLASS | Pattern.COMMENTS;

    private Target_java_util_regex_Pattern() {
    }

    static boolean isUnsupportedPattern(StaticObject parentPattern, Meta meta) {
        if (meta.java_util_regex_Pattern_HIDDEN_unsupported == null) {
            return true;
        }
        Object unsupported = meta.java_util_regex_Pattern_HIDDEN_unsupported.getHiddenObject(parentPattern);
        return (!(unsupported instanceof Boolean)) || (boolean) unsupported;
    }

    @Substitution(hasReceiver = true, methodName = "<init>")
    abstract static class Init extends SubstitutionNode {

        abstract void execute(@JavaType(Pattern.class) StaticObject self, @JavaType(String.class) StaticObject p, int f);

        @Specialization(guards = "context.regexSubstitutionsEnabled()")
        void doDefault(StaticObject self, StaticObject p, int f,
                        @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "3") InteropLibrary regexInterop,
                        @Shared("original") @Cached("create(getMeta().java_util_regex_Pattern_init.getCallTargetNoSubstitution())") DirectCallNode original,
                        @Cached InlinedBranchProfile parseErrorProfile, @Cached InlinedBranchProfile argErrorProfile) {
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
            Source src = getSource(RegexAction.Validate, pattern, f, meta.getJavaVersion());
            try {
                context.getEnv().parseInternal(src).call();
            } catch (Exception e) {
                parseErrorProfile.enter(this);
                try {
                    if (regexInterop.isException(e) && regexInterop.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        // this can either RegexSyntaxException or UnsupportedRegexException (no
                        // good way to distinguish)
                        LOGGER.log(Level.FINE, e, () -> "Unsupported Pattern: " + pattern);
                        meta.java_util_regex_Pattern_HIDDEN_unsupported.setHiddenObject(self, true);
                        original.call(self, p, f);
                        return;
                    } else {
                        throw e;
                    }
                } catch (UnsupportedMessageException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere(ex);
                }
            }

            meta.java_util_regex_Pattern_HIDDEN_tregexSearch.setHiddenObject(self, null);
            meta.java_util_regex_Pattern_HIDDEN_tregexMatch.setHiddenObject(self, null);
            meta.java_util_regex_Pattern_HIDDEN_tregexFullmatch.setHiddenObject(self, null);
            meta.java_util_regex_Pattern_HIDDEN_unsupported.setHiddenObject(self, false);

            // indicates that the group count is invalid
            meta.java_util_regex_Pattern_capturingGroupCount.setInt(self, -1);
            meta.java_util_regex_Pattern_pattern.setObject(self, p);
            meta.java_util_regex_Pattern_flags.setInt(self, f);
            meta.java_util_regex_Pattern_flags0.setInt(self, f);
            meta.java_util_regex_Pattern_compiled.setBoolean(self, true);
        }

        @Specialization(guards = "!context.regexSubstitutionsEnabled()")
        static void doDisabled(StaticObject self, StaticObject p, int f,
                        @Bind("getContext()") @SuppressWarnings("unused") EspressoContext context,
                        @Shared("original") @Cached("create(getMeta().java_util_regex_Pattern_init.getCallTargetNoSubstitution())") DirectCallNode original) {
            original.call(self, p, f);
        }
    }

    @Substitution(hasReceiver = true, methodName = "namedGroups")
    abstract static class NamedGroups extends SubstitutionNode {

        abstract @JavaType(Map.class) StaticObject execute(@JavaType(Pattern.class) StaticObject self);

        @Specialization(guards = "context.regexSubstitutionsEnabled()")
        @JavaType(Map.class)
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Shared("original") @Cached("create(getMeta().java_util_regex_Pattern_namedGroups.getCallTargetNoSubstitution())") DirectCallNode original,
                        @Cached("create(context.getMeta().java_util_regex_Pattern_init.getCallTargetNoSubstitution())") DirectCallNode initOriginal) {
            if (StaticObject.isNull(context.getMeta().java_util_regex_Pattern_namedGroups_field.getObject(self))) {
                StaticObject pattern = context.getMeta().java_util_regex_Pattern_pattern.getObject(self);
                Object flags = context.getMeta().java_util_regex_Pattern_flags.getValue(self);
                initOriginal.call(self, pattern, flags);
            }
            return (StaticObject) original.call(self);
        }

        @Specialization(guards = "!context.regexSubstitutionsEnabled()")
        static StaticObject doDisabled(StaticObject self,
                        @Bind("getContext()") @SuppressWarnings("unused") EspressoContext context,
                        @Shared("original") @Cached("create(getMeta().java_util_regex_Pattern_namedGroups.getCallTargetNoSubstitution())") DirectCallNode original) {
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
}
