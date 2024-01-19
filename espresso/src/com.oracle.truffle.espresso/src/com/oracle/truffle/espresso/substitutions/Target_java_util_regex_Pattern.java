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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.util.Map;
import java.util.regex.Pattern;

@EspressoSubstitutions
public final class Target_java_util_regex_Pattern {
    private static final int ALL_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE |
                    Pattern.DOTALL | Pattern.UNICODE_CASE | Pattern.CANON_EQ | Pattern.UNIX_LINES | Pattern.LITERAL |
                    Pattern.UNICODE_CHARACTER_CLASS | Pattern.COMMENTS;

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

            String combined = "RegressionTestMode=true,Encoding=UTF-16,Flavor=JavaUtilPattern,Validate=true";

            if ((f & ~ALL_FLAGS) != 0) {
                StaticObject guestException = meta.java_lang_IllegalArgumentException.allocateInstance(context);
                CallTarget exceptionInit = meta.java_lang_IllegalArgumentException_init.getCallTarget();
                exceptionInit.call(guestException, meta.toGuestString(unknownFlagMessage(f)));
                meta.throwException(guestException);
            }

            // this is to reproduce the java.util.regex behavior, where isEmpty is called on the
            // pattern
            if (p == StaticObject.NULL) {
                throw meta.throwNullPointerException();
            }

            String sourceStr = getString(f, combined, pattern);
            Source src = getSource(sourceStr);
            try {
                context.getEnv().parseInternal(src).call();
            } catch (Exception e) {
                CompilerDirectives.transferToInterpreter();
                try {
                    if (regexInterop.isException(e) && regexInterop.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        // this can either RegexSyntaxException or UnsupportedRegexException (no
                        // good way to distinguish)
                        meta.java_util_regex_Pattern_HIDDEN_unsupported.setHiddenObject(self, true);
                        original.call(self, p, f);
                        return;
                    } else {
                        throw e;
                    }
                } catch (UnsupportedMessageException ex) {
                    CompilerDirectives.shouldNotReachHere(e);
                }
            }

            meta.java_util_regex_Pattern_HIDDEN_tregexSearch.setHiddenObject(self, StaticObject.NULL);
            meta.java_util_regex_Pattern_HIDDEN_tregexMatch.setHiddenObject(self, StaticObject.NULL);
            meta.java_util_regex_Pattern_HIDDEN_tregexFullmatch.setHiddenObject(self, StaticObject.NULL);
            meta.java_util_regex_Pattern_HIDDEN_unsupported.setHiddenObject(self, false);

            // indicates that the group count is invalid
            meta.java_util_regex_Pattern_capturingGroupCount.setInt(self, -1);
            meta.java_util_regex_Pattern_pattern.setObject(self, p);
            meta.java_util_regex_Pattern_flags.setInt(self, f);
            meta.java_util_regex_Pattern_flags0.setInt(self, f);
            meta.java_util_regex_Pattern_compiled.setBoolean(self, true);
        }

        @TruffleBoundary
        private static Source getSource(String sourceStr) {
            Source src = Source.newBuilder("regex", sourceStr, "patternExpr").build();
            return src;
        }
    }

    @Substitution(hasReceiver = true, methodName = "namedGroups")
    abstract static class NamedGroups extends SubstitutionNode {

        abstract @JavaType(Map.class) StaticObject execute(@JavaType(Pattern.class) StaticObject self);

        @Specialization
        @JavaType(Map.class)
        StaticObject doFallback(
                        @JavaType(Pattern.class) StaticObject self,
                        @Cached("create(getContext().getMeta().java_util_regex_Pattern_namedGroups.getCallTargetNoSubstitution())") DirectCallNode original,
                        @Cached("create(getContext().getMeta().java_util_regex_Pattern_init.getCallTargetNoSubstitution())") DirectCallNode initOriginal) {
            if (getContext().getMeta().java_util_regex_Pattern_namedGroups_field.getObject(self) == StaticObject.NULL) {
                StaticObject pattern = getContext().getMeta().java_util_regex_Pattern_pattern.getObject(self);
                Object flags = getContext().getMeta().java_util_regex_Pattern_flags.getValue(self);
                initOriginal.call(self, pattern, flags);
            }
            return (StaticObject) original.call(self);
        }
    }

    @TruffleBoundary
    private static String getString(int f, String combined, String pattern) {
        return combined + '/' + pattern + '/' + convertFlags(f);
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
