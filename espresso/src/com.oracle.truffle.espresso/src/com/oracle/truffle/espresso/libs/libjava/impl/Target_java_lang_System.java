/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.io.InputStream;
import java.io.PrintStream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(value = java.lang.System.class, group = LibJava.class)
public final class Target_java_lang_System {

    @Substitution
    public static void registerNatives() {
    }

    @Substitution
    public static @JavaType(Target_java_lang_String.class) StaticObject mapLibraryName(@JavaType(Target_java_lang_String.class) StaticObject libname, @Inject Meta meta) {
        return meta.toGuestString(meta.getNativeAccess().mapLibraryName(meta.toHostString(libname)));
    }

    @Substitution
    @TruffleBoundary
    public static void setIn0(@JavaType(InputStream.class) StaticObject stream, @Inject EspressoContext ctx) {
        ctx.getMeta().java_lang_System_in.setObject(ctx.getMeta().java_lang_System.tryInitializeAndGetStatics(), stream);
    }

    @Substitution
    @TruffleBoundary
    public static void setOut0(@JavaType(PrintStream.class) StaticObject stream, @Inject EspressoContext ctx) {
        ctx.getMeta().java_lang_System_out.setObject(ctx.getMeta().java_lang_System.tryInitializeAndGetStatics(), stream);
    }

    @Substitution
    @TruffleBoundary
    public static void setErr0(@JavaType(PrintStream.class) StaticObject stream, @Inject EspressoContext ctx) {
        ctx.getMeta().java_lang_System_err.setObject(ctx.getMeta().java_lang_System.tryInitializeAndGetStatics(), stream);
    }

    @EspressoSubstitutions(type = "Ljdk/internal/util/SystemProps$Raw;", group = LibJava.class)
    public static final class Raw {
        @Substitution
        public static @JavaType(String[].class) StaticObject vmProperties(@Inject EspressoContext ctx, @Inject EspressoLanguage lang) {
            return ctx.getVM().JVM_GetProperties(lang);
        }

        @Substitution
        @TruffleBoundary
        public static @JavaType(String[].class) StaticObject platformProperties(@Inject EspressoContext ctx, @Inject TruffleIO io) {
            // Import properties from host.
            Props props = new Props(ctx);
            String[] known = new String[props.fixedLength];
            known[props.userHomeNdx] = java.lang.System.getProperty("user.home");
            known[props.userDirNdx] = java.lang.System.getProperty("user.dir");
            known[props.userNameNdx] = java.lang.System.getProperty("user.name");

            known[props.sunJnuEncodingNdx] = java.lang.System.getProperty("sun.jnu.encoding");
            if (ctx.getJavaVersion().java21OrEarlier()) {
                known[props.fileEncodingNdx] = java.lang.System.getProperty("file.encoding");
            }
            if (ctx.getJavaVersion().java25OrLater()) {
                known[props.nativeEncodingNDX] = java.lang.System.getProperty("native.encoding");
            }
            known[props.stdoutEncodingNdx] = java.lang.System.getProperty("stdout.encoding");
            known[props.stderrEncodingNdx] = java.lang.System.getProperty("stderr.encoding");

            known[props.osNameNdx] = java.lang.System.getProperty("os.name");
            known[props.osArchNdx] = java.lang.System.getProperty("os.arch");
            known[props.osVersionNdx] = java.lang.System.getProperty("os.version");
            known[props.lineSeparatorNdx] = java.lang.System.getProperty("line.separator");
            known[props.fileSeparatorNdx] = String.valueOf(io.getFileSeparator());
            known[props.pathSeparatorNdx] = String.valueOf(io.getPathSeparator());

            known[props.javaIoTmpdirNdx] = java.lang.System.getProperty("java.io.tmpdir");
            known[props.httpProxyHostNdx] = java.lang.System.getProperty("http.proxyHost");
            known[props.httpProxyPortNdx] = java.lang.System.getProperty("http.proxyPort");
            known[props.httpsProxyHostNdx] = java.lang.System.getProperty("https.proxyHost");
            known[props.httpsProxyPortNdx] = java.lang.System.getProperty("https.proxyPort");
            known[props.ftpProxyHostNdx] = java.lang.System.getProperty("ftp.proxyHost");
            known[props.ftpProxyPortNdx] = java.lang.System.getProperty("ftp.proxyPort");
            known[props.socksProxyHostNdx] = java.lang.System.getProperty("socksProxyHost");
            known[props.socksProxyPortNdx] = java.lang.System.getProperty("socksProxyPort");
            known[props.httpNonProxyHostsNdx] = java.lang.System.getProperty("http.nonProxyHosts");
            known[props.ftpNonProxyHostsNdx] = java.lang.System.getProperty("ftp.nonProxyHosts");
            known[props.socksNonProxyHostsNdx] = java.lang.System.getProperty("socksNonProxyHosts");
            known[props.sunArchAbiNdx] = java.lang.System.getProperty("sun.arch.abi");
            known[props.sunArchDataModelNdx] = java.lang.System.getProperty("sun.arch.data.model");
            known[props.sunOsPatchLevelNdx] = java.lang.System.getProperty("sun.os.patch.level");
            known[props.sunIoUnicodeEncodingNdx] = java.lang.System.getProperty("sun.io.unicode.encoding");
            known[props.sunCpuIsalistNdx] = java.lang.System.getProperty("sun.cpu.isalist");
            known[props.sunCpuEndianNdx] = java.lang.System.getProperty("sun.cpu.endian");

            known[props.displayLanguageNdx] = java.lang.System.getProperty("user.language.display");
            known[props.formatCountryNdx] = java.lang.System.getProperty("user.language.format");

            known[props.displayScriptNdx] = java.lang.System.getProperty("user.script.display");
            known[props.formatLanguageNdx] = java.lang.System.getProperty("user.script.format");

            known[props.displayCountryNdx] = java.lang.System.getProperty("user.country.display");
            known[props.formatScriptNdx] = java.lang.System.getProperty("user.country.format");

            known[props.displayVariantNdx] = java.lang.System.getProperty("user.variant.display");
            known[props.formatVariantNdx] = java.lang.System.getProperty("user.variant.format");

            Meta meta = ctx.getMeta();
            StaticObject[] guestProps = new StaticObject[props.fixedLength];
            for (int i = 0; i < props.fixedLength; i++) {
                guestProps[i] = meta.toGuestString(known[i]);
            }

            return ctx.getAllocator().wrapArrayAs(ctx.getMeta().java_lang_String.array(), guestProps);
        }

        private static final class Props {
            private final int displayCountryNdx;
            private final int displayLanguageNdx;
            private final int displayScriptNdx;
            private final int displayVariantNdx;
            // only in 21-
            private final int fileEncodingNdx;
            private final int fileSeparatorNdx;
            private final int formatCountryNdx;
            private final int formatLanguageNdx;
            private final int formatScriptNdx;
            private final int formatVariantNdx;
            private final int ftpNonProxyHostsNdx;
            private final int ftpProxyHostNdx;
            private final int ftpProxyPortNdx;
            private final int httpNonProxyHostsNdx;
            private final int httpProxyHostNdx;
            private final int httpProxyPortNdx;
            private final int httpsProxyHostNdx;
            private final int httpsProxyPortNdx;
            private final int javaIoTmpdirNdx;
            private final int lineSeparatorNdx;
            private final int osArchNdx;
            private final int osNameNdx;
            private final int osVersionNdx;
            private final int pathSeparatorNdx;
            // only in 25+
            private final int nativeEncodingNDX;
            private final int socksNonProxyHostsNdx;
            private final int socksProxyHostNdx;
            private final int socksProxyPortNdx;
            private final int stderrEncodingNdx;
            private final int stdoutEncodingNdx;
            private final int sunArchAbiNdx;
            private final int sunArchDataModelNdx;
            private final int sunCpuEndianNdx;
            private final int sunCpuIsalistNdx;
            private final int sunIoUnicodeEncodingNdx;
            private final int sunJnuEncodingNdx;
            private final int sunOsPatchLevelNdx;
            private final int userDirNdx;
            private final int userHomeNdx;
            private final int userNameNdx;
            private final int fixedLength;

            private Props(EspressoContext ctx) {
                ObjectKlass guestRaw = ctx.getMeta().jdk_internal_util_SystemProps_Raw;
                displayCountryNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_display_country_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                displayLanguageNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_display_language_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                displayScriptNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_display_script_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                displayVariantNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_display_variant_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                if (ctx.getJavaVersion().java21OrEarlier()) {
                    fileEncodingNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_file_encoding_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                } else {
                    fileEncodingNdx = -1;
                }
                if (ctx.getJavaVersion().java25OrLater()) {
                    nativeEncodingNDX = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_native_encoding_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                } else {
                    nativeEncodingNDX = -1;
                }
                fileSeparatorNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_file_separator_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                formatCountryNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_format_country_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                formatLanguageNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_format_language_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                formatScriptNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_format_script_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                formatVariantNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_format_variant_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                ftpNonProxyHostsNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_ftp_nonProxyHosts_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                ftpProxyHostNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_ftp_proxyHost_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                ftpProxyPortNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_ftp_proxyPort_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                httpNonProxyHostsNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_http_nonProxyHosts_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                httpProxyHostNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_http_proxyHost_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                httpProxyPortNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_http_proxyPort_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                httpsProxyHostNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_https_proxyHost_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                httpsProxyPortNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_https_proxyPort_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                javaIoTmpdirNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_java_io_tmpdir_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                lineSeparatorNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_line_separator_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                osArchNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_os_arch_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                osNameNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_os_name_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                osVersionNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_os_version_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                pathSeparatorNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_path_separator_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                socksNonProxyHostsNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_socksNonProxyHosts_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                socksProxyHostNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_socksProxyHost_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                socksProxyPortNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_socksProxyPort_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                stderrEncodingNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_stderr_encoding_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                stdoutEncodingNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_stdout_encoding_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                sunArchAbiNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_sun_arch_abi_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                sunArchDataModelNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_sun_arch_data_model_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                sunCpuEndianNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_sun_cpu_endian_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                sunCpuIsalistNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_sun_cpu_isalist_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                sunIoUnicodeEncodingNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_sun_io_unicode_encoding_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                sunJnuEncodingNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_sun_jnu_encoding_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                sunOsPatchLevelNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_sun_os_patch_level_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                userDirNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_user_dir_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                userHomeNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_user_home_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                userNameNdx = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("_user_name_NDX"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
                fixedLength = guestRaw.lookupDeclaredField(ctx.getNames().getOrCreate("FIXED_LENGTH"), Types._int).getInt(guestRaw.tryInitializeAndGetStatics());
            }
        }
    }

}
