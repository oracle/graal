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
package com.oracle.svm.core.c.locale;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.jdk.UserSystemProperty;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.PauseNode;
import jdk.graal.compiler.word.Word;

/**
 * The locale is a process-wide setting. This class uses {@link LocaleCHelper C code} to initialize
 * the system-specific locale at run-time. For this, the C code needs to change the locale multiple
 * times, which is a dangerous operation as it is not necessarily multi-threading safe. Therefore,
 * the locale initialization needs to be executed once per process during early startup.
 * <p>
 * In some cases, such as libgraal, this C code must not be executed as it may interfere with other
 * threads that are already running in the same process. If the option
 * {@link SubstrateOptions#UseSystemLocale} is disabled, the locale 'en-US' is used instead of the
 * system-specific locale.
 * <p>
 * Note that the JavaDoc of {@link java.util.Locale} explains commonly used terms such as script,
 * display, format, variant, and extensions.
 */
@AutomaticallyRegisteredImageSingleton
public class LocaleSupport {
    private static final CGlobalData<Pointer> STATE = CGlobalDataFactory.createWord(State.UNINITIALIZED);

    private LocaleData locale;

    @Fold
    public static LocaleSupport singleton() {
        return ImageSingletons.lookup(LocaleSupport.class);
    }

    @Fold
    static boolean isSystemSpecificLocaleSupported() {
        return LibC.isSupported() && SubstrateOptions.UseSystemLocale.getValue();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void initialize() {
        if (!isSystemSpecificLocaleSupported()) {
            return;
        }

        Pointer statePtr = STATE.get();
        UnsignedWord value = statePtr.compareAndSwapWord(0, State.UNINITIALIZED, State.INITIALIZING, LocationIdentity.ANY_LOCATION);
        if (value == State.UNINITIALIZED) {
            int result = LocaleCHelper.initializeLocale();
            if (result == LocaleCHelper.SVM_LOCALE_INITIALIZATION_SUCCEEDED()) {
                statePtr.writeWordVolatile(0, State.SUCCESS);
            } else if (result == LocaleCHelper.SVM_LOCALE_INITIALIZATION_OUT_OF_MEMORY()) {
                statePtr.writeWordVolatile(0, State.OUT_OF_MEMORY);
            } else {
                throw VMError.shouldNotReachHere("LocaleCHelper.initializeLocale() returned an unexpected result.");
            }
        } else {
            while (value == State.INITIALIZING) {
                PauseNode.pause();
                value = statePtr.readWordVolatile(0, LocationIdentity.ANY_LOCATION);
            }
        }
    }

    public static void checkForError() {
        if (!isSystemSpecificLocaleSupported()) {
            return;
        }

        UnsignedWord state = STATE.get().readWord(0);
        if (state != State.SUCCESS) {
            if (state == State.OUT_OF_MEMORY) {
                throw new OutOfMemoryError("Not enough native memory to initialize the locale support.");
            }
            throw VMError.shouldNotReachHere("Locale support had an unexpected state after initialization.");
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+5/src/java.base/share/classes/jdk/internal/util/SystemProps.java#L131-L141")
    public synchronized LocaleData getLocale() {
        /* This method is only called a few times, so need to optimize the locking. */
        if (locale != null) {
            return locale;
        }

        if (!isSystemSpecificLocaleSupported()) {
            String country = "US";
            String language = "en";
            locale = new LocaleData(country, null, null, language, null, null, null, null, null, null, null, null);
            return locale;
        }

        assert STATE.get().readWord(0) == State.SUCCESS;

        /* Convert all C values to Java strings. */
        LocaleCHelper.LocaleProps props = LocaleCHelper.getLocale();
        String defaultCountry = CTypeConversion.toJavaString(props.displayCountry());
        String defaultCountryDisplay = CTypeConversion.toJavaString(props.displayCountry());
        String defaultCountryFormat = CTypeConversion.toJavaString(props.formatCountry());
        String defaultLanguage = CTypeConversion.toJavaString(props.displayLanguage());
        String defaultLanguageDisplay = CTypeConversion.toJavaString(props.displayLanguage());
        String defaultLanguageFormat = CTypeConversion.toJavaString(props.formatLanguage());
        String defaultScript = CTypeConversion.toJavaString(props.displayScript());
        String defaultScriptDisplay = CTypeConversion.toJavaString(props.displayScript());
        String defaultScriptFormat = CTypeConversion.toJavaString(props.formatScript());
        String defaultVariant = CTypeConversion.toJavaString(props.displayVariant());
        String defaultVariantDisplay = CTypeConversion.toJavaString(props.displayVariant());
        String defaultVariantFormat = CTypeConversion.toJavaString(props.formatVariant());

        /* The code below is similar to the JDK class SystemProps. */
        LocaleAspect country = getLocaleAspect(UserSystemProperty.COUNTRY, UserSystemProperty.COUNTRY_DISPLAY, UserSystemProperty.COUNTRY_FORMAT,
                        defaultCountry, defaultCountryDisplay, defaultCountryFormat);
        LocaleAspect language = getLocaleAspect(UserSystemProperty.LANGUAGE, UserSystemProperty.LANGUAGE_DISPLAY, UserSystemProperty.LANGUAGE_FORMAT,
                        defaultLanguage, defaultLanguageDisplay, defaultLanguageFormat);
        LocaleAspect script = getLocaleAspect(UserSystemProperty.SCRIPT, UserSystemProperty.SCRIPT_DISPLAY, UserSystemProperty.SCRIPT_FORMAT,
                        defaultScript, defaultScriptDisplay, defaultScriptFormat);
        LocaleAspect variant = getLocaleAspect(UserSystemProperty.VARIANT, UserSystemProperty.VARIANT_DISPLAY, UserSystemProperty.VARIANT_FORMAT,
                        defaultVariant, defaultVariantDisplay, defaultVariantFormat);

        locale = new LocaleData(country.base, country.display, country.format, language.base, language.display, language.format, script.base, script.display, script.format, variant.base,
                        variant.display, variant.format);
        return locale;
    }

    /**
     * If a locale system property is set at image build-time or as a command-line option at
     * run-time (-D...), then we don't want to use the default value that the C code determined.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+5/src/java.base/share/classes/jdk/internal/util/SystemProps.java#L178-L209")
    private static LocaleAspect getLocaleAspect(String baseKey, String displayKey, String formatKey,
                    String defaultBase, String defaultDisplay, String defaultFormat) {
        /*
         * This method is only called once, when the first locale-specific lazy system property is
         * initialized. All locale system properties are initialized lazily, so we can be sure that
         * the accessed system properties below only have an initial value if they were set at image
         * build-time or from a command-line option during early VM startup.
         */
        String base = SystemPropertiesSupport.singleton().getInitialProperty(baseKey, false);
        String display = SystemPropertiesSupport.singleton().getInitialProperty(displayKey, false);
        String format = SystemPropertiesSupport.singleton().getInitialProperty(formatKey, false);
        if (base == null) {
            base = defaultBase;
            if (display == null && defaultDisplay != null && !defaultDisplay.equals(base)) {
                display = defaultDisplay;
            }
            if (format == null && defaultFormat != null && !defaultFormat.equals(base)) {
                format = defaultFormat;
            }
        }
        return new LocaleAspect(base, display, format);
    }

    private record LocaleAspect(String base, String display, String format) {
    }

    private static final class State {
        static final UnsignedWord UNINITIALIZED = Word.unsigned(0);
        static final UnsignedWord INITIALIZING = Word.unsigned(1);
        static final UnsignedWord SUCCESS = Word.unsigned(2);
        static final UnsignedWord OUT_OF_MEMORY = Word.unsigned(3);
    }
}
