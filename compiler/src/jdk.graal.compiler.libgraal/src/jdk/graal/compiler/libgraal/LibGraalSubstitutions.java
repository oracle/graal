/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.libgraal;

import java.io.IOException;
import java.text.DateFormatSymbols;
import java.time.temporal.TemporalAccessor;
import java.util.Formatter;
import java.util.Locale;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.PathUtilities;

class LibGraalSubstitutions {

    @TargetClass(className = "jdk.vm.ci.services.Services", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_vm_ci_services_Services {
        /**
         * Static final boolean field {@code Services.IS_IN_NATIVE_IMAGE} is used in many places in
         * the JVMCI codebase to switch between the different implementations needed for regular use
         * (a built-in module {@code jdk.graal.compiler} in the JVM) or as part of libgraal.
         */
        // Checkstyle: stop
        @Alias //
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
        public static boolean IS_IN_NATIVE_IMAGE = true;
        // Checkstyle: resume
    }

    @TargetClass(className = "jdk.vm.ci.hotspot.Cleaner", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_vm_ci_hotspot_Cleaner {

        /**
         * Make package-private {@code clean()} accessible so that it can be called from
         * {@link LibGraalSupportImpl#doReferenceHandling()}.
         */
        @Alias
        public static native void clean();
    }

    /**
     * There are no String-based class-lookups happening at libgraal runtime. Thus, we can safely
     * prune all classloading-logic out of the image.
     */
    @TargetClass(value = java.lang.Class.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_lang_Class {
        @Substitute
        public static Class<?> forName(String name, boolean initialize, ClassLoader loader)
                        throws ClassNotFoundException {
            throw new ClassNotFoundException(name + " (class loading not supported in libgraal)");
        }

        @Substitute
        private static Class<?> forName(String className, Class<?> caller)
                        throws ClassNotFoundException {
            throw new ClassNotFoundException(className + " (class loading not supported in libgraal)");
        }

        @Substitute
        public static Class<?> forName(Module module, String name) {
            return null;
        }
    }

    @TargetClass(value = java.lang.ClassLoader.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_lang_ClassLoader {
        @Substitute
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name + " (class loading not supported in libgraal)");
        }

        @Substitute
        static Class<?> findBootstrapClassOrNull(String name) {
            return null;
        }
    }

    @TargetClass(className = "java.util.Formatter$FormatSpecifier", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_util_Formatter_FormatSpecifier {

        /**
         * Custom version of
         * {@code java.util.Formatter.FormatSpecifier#localizedMagnitude(java.util.Formatter, java.lang.StringBuilder, java.lang.CharSequence, int, int, int, java.util.Locale)}
         * where the given locale is unconditionally replaced with {@code null}). Since the original
         * method was already able to accept `null` as locale, the substitution is straightforward.
         * The substitution does not contain any code path that requires dynamic class or resource
         * lookup.
         */
        @Substitute
        StringBuilder localizedMagnitude(Formatter fmt, StringBuilder sb,
                        CharSequence value, final int offset, int f, int width,
                        Locale unused) {
            if (sb == null) {
                sb = new StringBuilder();
            }
            int begin = sb.length();

            char zero = '0'; // getZero(l);

            // determine localized grouping separator and size
            char grpSep = '\0';
            int grpSize = -1;
            char decSep = '\0';

            int len = value.length();
            int dot = len;
            for (int j = offset; j < len; j++) {
                if (value.charAt(j) == '.') {
                    dot = j;
                    break;
                }
            }

            if (dot < len) {
                decSep = '.'; // getDecimalSeparator(l);
            }

            if (Target_java_util_Formatter_Flags.contains(f, Target_java_util_Formatter_Flags.GROUP)) {
                grpSep = ','; // getGroupingSeparator(l);

                Locale l = null;
                if (l == null || l.equals(Locale.US)) {
                    grpSize = 3;
                } else {
                    throw GraalError.shouldNotReachHere("localizedMagnitude with l != null");
                }
            }

            // localize the digits inserting group separators as necessary
            for (int j = offset; j < len; j++) {
                if (j == dot) {
                    sb.append(decSep);
                    // no more group separators after the decimal separator
                    grpSep = '\0';
                    continue;
                }

                char c = value.charAt(j);
                sb.append((char) ((c - '0') + zero));
                if (grpSep != '\0' && j != dot - 1 && ((dot - j) % grpSize == 1)) {
                    sb.append(grpSep);
                }
            }

            // apply zero padding
            if (width > sb.length() && Target_java_util_Formatter_Flags.contains(f, Target_java_util_Formatter_Flags.ZERO_PAD)) {
                String zeros = String.valueOf(zero).repeat(width - sb.length());
                sb.insert(begin, zeros);
            }

            return sb;
        }

        /**
         * Custom version of
         * {@code java.util.Formatter.FormatSpecifier#print(java.util.Formatter, java.time.temporal.TemporalAccessor, char, java.util.Locale)}
         * where the given locale is unconditionally replaced with {@code null}). Since the original
         * method was already able to accept `null` as locale, the substitution is straightforward.
         * The substitution does not contain any code path that requires dynamic class or resource
         * lookup.
         */
        @Substitute
        void print(Target_java_util_Formatter fmt, TemporalAccessor t, char c, Locale unused) throws IOException {
            StringBuilder sb = new StringBuilder();
            print(fmt, sb, t, c, null);
            // justify based on width
            if (Target_java_util_Formatter_Flags.contains(flags, Target_java_util_Formatter_Flags.UPPERCASE)) {
                appendJustified(fmt.a, sb.toString().toUpperCase(Locale.ROOT));
            } else {
                appendJustified(fmt.a, sb);
            }
        }

        @Alias
        native Appendable print(Target_java_util_Formatter fmt, StringBuilder sb, TemporalAccessor t, char c,
                        Locale l) throws IOException;

        @Alias
        native void appendJustified(Appendable a, CharSequence cs) throws IOException;

        @Alias //
        int flags;
    }

    @TargetClass(className = "java.util.Formatter", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_util_Formatter {
        @Alias //
        Appendable a;
    }

    @TargetClass(className = "java.util.Formatter$Flags", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_util_Formatter_Flags {
        // Checkstyle: stop
        @Alias //
        static int ZERO_PAD;
        @Alias //
        static int GROUP;
        @Alias //
        static int UPPERCASE;
        // Checkstyle: resume

        @Alias
        static native boolean contains(int flags, int f);
    }

    @TargetClass(value = java.text.DateFormatSymbols.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_text_DateFormatSymbols {
        /**
         * {@link DateFormatSymbols#getInstance(Locale)} relies on String-based class-lookup (to
         * find resource bundle {@code sun.text.resources.cldr.FormatData}) which we do not want to
         * rely on at libgraal runtime because it increases image size too much. Instead, we return
         * the DateFormatSymbols instance that we already have in the image heap.
         */
        @Substitute
        public static DateFormatSymbols getInstance(Locale unused) {
            return PathUtilities.getSharedDateFormatSymbols();
        }
    }
}
