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
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.ByteBuffer;
import java.text.DateFormatSymbols;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

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

    /// Removes string-based class lookup from the libgraal runtime image. All compiler classes and
    /// providers needed by libgraal are discovered while building the image, and libgraal does not
    /// define or load new Java classes at runtime. Keeping these methods live would make the JDK
    /// class-loading stack reachable, including resource lookup through class loaders, even though
    /// that machinery cannot produce useful results for libgraal.
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

    /// Cuts off runtime class loading and class-loader resource lookup. Libgraal runs from a closed
    /// image heap with its providers and resources selected during image building, so runtime
    /// classpath probing is dead code. Returning empty results here prevents the normal JDK class
    /// loader implementation from making `URLClassPath`, `FileURLMapper`, jar handling, and jar
    /// verification reachable through resource lookup paths.
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

        @Substitute
        public URL getResource(@SuppressWarnings("unused") String name) {
            return null;
        }

        @Substitute
        public Enumeration<URL> getResources(@SuppressWarnings("unused") String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Substitute
        public Stream<URL> resources(@SuppressWarnings("unused") String name) {
            return Stream.empty();
        }

        @Substitute
        public InputStream getResourceAsStream(@SuppressWarnings("unused") String name) {
            return null;
        }

        @Substitute
        protected URL findResource(@SuppressWarnings("unused") String name) {
            return null;
        }

        @Substitute
        protected URL findResource(@SuppressWarnings("unused") String moduleName, @SuppressWarnings("unused") String name) throws IOException {
            return null;
        }

        @Substitute
        protected Enumeration<URL> findResources(@SuppressWarnings("unused") String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Substitute
        public static URL getSystemResource(@SuppressWarnings("unused") String name) {
            return null;
        }

        @Substitute
        public static Enumeration<URL> getSystemResources(@SuppressWarnings("unused") String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Substitute
        public static InputStream getSystemResourceAsStream(@SuppressWarnings("unused") String name) {
            return null;
        }
    }

    /// Removes boot-loader resource and package lookup from the libgraal runtime image. The hosted
    /// libgraal setup resolves any boot-layer services and resources it needs before image
    /// generation; runtime code must not search the boot class path or module path. Empty results are
    /// therefore the correct libgraal behavior and avoid dragging in the boot loader's module,
    /// package, URL, jar, and security-verification support.
    @TargetClass(className = "jdk.internal.loader.BootLoader", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_internal_loader_BootLoader {
        @Substitute
        public static URL findResource(@SuppressWarnings("unused") String name) {
            return null;
        }

        @Substitute
        public static Enumeration<URL> findResources(@SuppressWarnings("unused") String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Substitute
        public static URL findResource(@SuppressWarnings("unused") String moduleName, @SuppressWarnings("unused") String name) throws IOException {
            return null;
        }

        @Substitute
        public static InputStream findResourceAsStream(@SuppressWarnings("unused") String moduleName, @SuppressWarnings("unused") String name) throws IOException {
            return null;
        }

        @Substitute
        public static Package definePackage(@SuppressWarnings("unused") Class<?> c) {
            return null;
        }

        @Substitute
        public static Package getDefinedPackage(@SuppressWarnings("unused") String packageName) {
            return null;
        }

        @Substitute
        public static Stream<Package> packages() {
            return Stream.empty();
        }
    }

    /// Removes application/platform class-loader resource lookup from the libgraal runtime image.
    /// These loaders normally delegate to JDK class-path and module-path machinery that can open jar
    /// files, map file URLs, and verify signed jar contents. Libgraal has no runtime class path, so
    /// these lookups are intentionally empty and the associated `URLClassPath`, jar, and
    /// `sun.security.*` verification paths are not needed.
    @TargetClass(className = "jdk.internal.loader.BuiltinClassLoader", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_internal_loader_BuiltinClassLoader {
        @Substitute
        public URL findResource(@SuppressWarnings("unused") String name) {
            return null;
        }

        @Substitute
        public Enumeration<URL> findResources(@SuppressWarnings("unused") String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Substitute
        public URL findResource(@SuppressWarnings("unused") String moduleName, @SuppressWarnings("unused") String name) throws IOException {
            return null;
        }

        @Substitute
        public InputStream findResourceAsStream(@SuppressWarnings("unused") String moduleName, @SuppressWarnings("unused") String name) throws IOException {
            return null;
        }
    }

    /// Prevents runtime iteration over JDK {@link java.util.ServiceLoader} results. Libgraal copies
    /// the relevant service-provider objects into its own image-heap data structures during image
    /// building; runtime code must consume those materialized providers instead of asking the JDK to
    /// rediscover services. An empty iterator keeps `ServiceLoader$LazyClassPathLookupIterator`,
    /// `META-INF/services` resource scanning, and the classpath URL/jar stack unreachable.
    @TargetClass(value = java.util.ServiceLoader.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_util_ServiceLoader {
        @Substitute
        public Iterator<?> iterator() {
            return Collections.emptyIterator();
        }
    }

    /// Disables URL stream-handler provider lookup through {@link java.util.ServiceLoader}. Libgraal
    /// does not install URL protocol handlers dynamically at runtime, and any URL values that remain
    /// in the image are data objects rather than a reason to search provider configuration files.
    /// Returning `null` preserves the JDK fallback behavior while avoiding reachability of service
    /// provider iteration, classpath resources, and jar/security support through URL initialization.
    @TargetClass(value = java.net.URL.class, onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_java_net_URL {
        @Substitute
        private static URLStreamHandler lookupViaProviders(@SuppressWarnings("unused") String protocol) {
            return null;
        }
    }

    /// Replaces runtime module-reference opening with an empty reader. Module descriptors and
    /// resources that matter to libgraal are handled while building the image; at runtime libgraal
    /// should not open modules from the file system or from jar files. This prevents module-reader
    /// implementations such as jar-backed readers from becoming reachable and pulling in jar input,
    /// URL/file mapping, and signed-jar verification code.
    @TargetClass(className = "jdk.internal.module.ModuleReferenceImpl", onlyWith = LibGraalFeature.IsEnabled.class)
    static final class Target_jdk_internal_module_ModuleReferenceImpl {
        @Substitute
        public ModuleReader open() {
            return EmptyModuleReader.SINGLETON;
        }
    }

    /// Singleton empty {@link ModuleReader} used by the libgraal-only module-reference substitution.
    /// It makes every runtime module-resource query behave as "not found", which matches the
    /// no-runtime-module-IO assumption for libgraal and avoids constructing any of the JDK's concrete
    /// file-system or jar-backed module readers.
    private static final class EmptyModuleReader implements ModuleReader {
        private static final EmptyModuleReader SINGLETON = new EmptyModuleReader();

        @Override
        public Optional<URI> find(@SuppressWarnings("unused") String name) {
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> open(@SuppressWarnings("unused") String name) {
            return Optional.empty();
        }

        @Override
        public Optional<ByteBuffer> read(@SuppressWarnings("unused") String name) {
            return Optional.empty();
        }

        @Override
        public Stream<String> list() {
            return Stream.empty();
        }

        @Override
        public void close() {
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
