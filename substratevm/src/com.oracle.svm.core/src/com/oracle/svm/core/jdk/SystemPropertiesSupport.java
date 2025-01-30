/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static java.util.Locale.Category.DISPLAY;
import static java.util.Locale.Category.FORMAT;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.RuntimeSystemPropertiesSupport;

import com.oracle.svm.core.LibCHelper;
import com.oracle.svm.core.VM;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.headers.LibCSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.GraalError;

/**
 * This class maintains the system properties at run time.
 *
 * Some of the standard system properties can just be taken from the image generator:
 * {@link #HOSTED_PROPERTIES}. Other important system properties need to be computed at run time.
 * However, we want to do the computation lazily to reduce the startup cost. For example, getting
 * the current working directory is quite expensive. We initialize such a property either when it is
 * explicitly accessed, or when all properties are accessed.
 */
public abstract class SystemPropertiesSupport implements RuntimeSystemPropertiesSupport {

    /** System properties that are taken from the VM hosting the image generator. */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final String[] HOSTED_PROPERTIES = {
                    "java.version",
                    "java.version.date",
                    ImageInfo.PROPERTY_IMAGE_KIND_KEY,
                    /*
                     * We do not support cross-compilation for now. Separator might also be cached
                     * in other classes, so changing them would be tricky.
                     */
                    "line.separator", "path.separator", "file.separator",
                    /* For our convenience for now. */
                    "file.encoding", "sun.jnu.encoding", "native.encoding", "stdout.encoding", "stderr.encoding",
                    "java.class.version",
                    "java.runtime.version",
                    "java.specification.name",
                    "java.specification.vendor",
                    "java.specification.version",
                    "java.vm.specification.name",
                    "java.vm.specification.vendor",
                    "java.vm.specification.version"
    };

    /* The list of field positions in locale_props_t (see locale_str.h). */
    private static final int LANGUAGE_POSITION = 0;
    private static final int SCRIPT_POSITION = LANGUAGE_POSITION + 1;
    private static final int COUNTRY_POSITION = SCRIPT_POSITION + 1;
    private static final int VARIANT_POSITION = COUNTRY_POSITION + 1;
    private static final int EXTENSION_POSITION = VARIANT_POSITION + 1;

    /** System properties that are lazily computed at run time on first access. */
    private final Map<String, Supplier<String>> lazyRuntimeValues;

    private Properties properties;

    /**
     * Initial value of the system properties after parsing command line options at run time.
     * Changes by the application using {@link System#setProperties} do not affect this map.
     */
    final Map<String, String> savedProperties;

    private final Map<String, String> readOnlySavedProperties;
    private final String hostOS = System.getProperty("os.name");
    // needed as fallback for platforms that don't implement osNameValue

    private volatile boolean fullyInitialized;

    @Fold
    public static SystemPropertiesSupport singleton() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("this-escape")
    protected SystemPropertiesSupport() {
        properties = new Properties();
        savedProperties = new HashMap<>();
        readOnlySavedProperties = Collections.unmodifiableMap(savedProperties);

        for (String key : HOSTED_PROPERTIES) {
            String value = System.getProperty(key);
            if (value != null) {
                properties.put(key, value);
                savedProperties.put(key, value);
            }
        }

        initializeProperty("java.runtime.name", "GraalVM Runtime Environment");

        VM vm = ImageSingletons.lookup(VM.class);
        initializeProperty("java.vendor", vm.vendor);
        initializeProperty("java.vendor.url", vm.vendorUrl);
        initializeProperty("java.vendor.version", vm.vendorVersion);
        assert vm.info.equals(vm.info.toLowerCase(Locale.ROOT)) : "java.vm.info should not contain uppercase characters";
        initializeProperty("java.vm.info", vm.info);
        initializeProperty("java.vm.name", "Substrate VM");
        initializeProperty("java.vm.vendor", vm.vendor);
        initializeProperty("java.vm.version", vm.version);

        initializeProperty("java.class.path", "");
        initializeProperty("java.endorsed.dirs", "");
        initializeProperty("java.ext.dirs", "");
        initializeProperty("sun.arch.data.model", Integer.toString(ConfigurationValues.getTarget().wordJavaKind.getBitCount()));

        initializeProperty(ImageInfo.PROPERTY_IMAGE_CODE_KEY, ImageInfo.PROPERTY_IMAGE_CODE_VALUE_RUNTIME);

        lazyRuntimeValues = new HashMap<>();
        lazyRuntimeValues.put("user.name", this::userName);
        lazyRuntimeValues.put("user.home", this::userHome);
        lazyRuntimeValues.put("user.dir", this::userDir);
        lazyRuntimeValues.put("java.io.tmpdir", this::javaIoTmpDir);
        lazyRuntimeValues.put("java.library.path", this::javaLibraryPath);
        lazyRuntimeValues.put("os.version", this::osVersionValue);
        lazyRuntimeValues.put(UserSystemProperty.USER_LANGUAGE, () -> postProcessLocale(UserSystemProperty.USER_LANGUAGE, parseLocale(DISPLAY).language(), null));
        lazyRuntimeValues.put(UserSystemProperty.USER_LANGUAGE_DISPLAY, () -> postProcessLocale(UserSystemProperty.USER_LANGUAGE, parseLocale(DISPLAY).language(), DISPLAY));
        lazyRuntimeValues.put(UserSystemProperty.USER_LANGUAGE_FORMAT, () -> postProcessLocale(UserSystemProperty.USER_LANGUAGE, parseLocale(FORMAT).language(), FORMAT));
        lazyRuntimeValues.put(UserSystemProperty.USER_SCRIPT, () -> postProcessLocale(UserSystemProperty.USER_SCRIPT, parseLocale(DISPLAY).script(), null));
        lazyRuntimeValues.put(UserSystemProperty.USER_SCRIPT_DISPLAY, () -> postProcessLocale(UserSystemProperty.USER_SCRIPT, parseLocale(DISPLAY).script(), DISPLAY));
        lazyRuntimeValues.put(UserSystemProperty.USER_SCRIPT_FORMAT, () -> postProcessLocale(UserSystemProperty.USER_SCRIPT, parseLocale(FORMAT).script(), FORMAT));
        lazyRuntimeValues.put(UserSystemProperty.USER_COUNTRY, () -> postProcessLocale(UserSystemProperty.USER_COUNTRY, parseLocale(DISPLAY).country(), null));
        lazyRuntimeValues.put(UserSystemProperty.USER_COUNTRY_DISPLAY, () -> postProcessLocale(UserSystemProperty.USER_COUNTRY, parseLocale(DISPLAY).country(), DISPLAY));
        lazyRuntimeValues.put(UserSystemProperty.USER_COUNTRY_FORMAT, () -> postProcessLocale(UserSystemProperty.USER_COUNTRY, parseLocale(FORMAT).country(), FORMAT));
        lazyRuntimeValues.put(UserSystemProperty.USER_VARIANT, () -> postProcessLocale(UserSystemProperty.USER_VARIANT, parseLocale(DISPLAY).variant(), null));
        lazyRuntimeValues.put(UserSystemProperty.USER_VARIANT_DISPLAY, () -> postProcessLocale(UserSystemProperty.USER_VARIANT, parseLocale(DISPLAY).variant(), DISPLAY));
        lazyRuntimeValues.put(UserSystemProperty.USER_VARIANT_FORMAT, () -> postProcessLocale(UserSystemProperty.USER_VARIANT, parseLocale(FORMAT).variant(), FORMAT));
        lazyRuntimeValues.put(UserSystemProperty.USER_EXTENSIONS, () -> postProcessLocale(UserSystemProperty.USER_EXTENSIONS, parseLocale(DISPLAY).extensions(), null));
        lazyRuntimeValues.put(UserSystemProperty.USER_EXTENSIONS_DISPLAY, () -> postProcessLocale(UserSystemProperty.USER_EXTENSIONS, parseLocale(DISPLAY).extensions(), DISPLAY));
        lazyRuntimeValues.put(UserSystemProperty.USER_EXTENSIONS_FORMAT, () -> postProcessLocale(UserSystemProperty.USER_EXTENSIONS, parseLocale(FORMAT).extensions(), FORMAT));

        String targetName = System.getProperty("svm.targetName");
        if (targetName != null) {
            initializeProperty("os.name", targetName);
        } else {
            lazyRuntimeValues.put("os.name", this::osNameValue);
        }

        String targetArch = System.getProperty("svm.targetArch");
        if (targetArch != null) {
            initializeProperty("os.arch", targetArch);
        } else {
            initializeProperty("os.arch", ImageSingletons.lookup(Platform.class).getArchitecture());
        }
    }

    private void ensureFullyInitialized() {
        if (!fullyInitialized) {
            for (String key : lazyRuntimeValues.keySet()) {
                initializeLazyValue(key);
            }
            fullyInitialized = true;
        }
    }

    public Map<String, String> getSavedProperties() {
        ensureFullyInitialized();
        return readOnlySavedProperties;
    }

    public Properties getProperties() {
        /*
         * We do not know what the user is going to do with the returned Properties object, so we
         * need to do a full initialization.
         */
        ensureFullyInitialized();
        return properties;
    }

    protected String getProperty(String key) {
        initializeLazyValue(key);
        return properties.getProperty(key);
    }

    protected String getSavedProperty(String key, String defaultValue) {
        initializeLazyValue(key);
        String value = savedProperties.get(key);
        return value != null ? value : defaultValue;
    }

    public void setProperties(Properties props) {
        // Flush lazy values into savedProperties
        ensureFullyInitialized();
        if (props == null) {
            Properties newProps = new Properties();
            for (Map.Entry<String, String> e : savedProperties.entrySet()) {
                newProps.setProperty(e.getKey(), e.getValue());
            }
            properties = newProps;
        } else {
            properties = props;
        }
    }

    /**
     * Initializes a property at startup from external input (e.g., command line arguments). This
     * must only be called while the runtime is single threaded.
     */
    @Override
    public void initializeProperty(String key, String value) {
        initializeProperty(key, value, true);
    }

    public void initializeProperty(String key, String value, boolean strict) {
        String prevValue = savedProperties.put(key, value);
        if (strict && prevValue != null && !prevValue.equals(value)) {
            VMError.shouldNotReachHere("System property " + key + " is initialized to " + value + " but was previously initialized to " + prevValue + ".");
        }
        properties.setProperty(key, value);
    }

    public String setProperty(String key, String value) {
        /*
         * The return value of setProperty is the previous value of the key, so we need to ensure
         * that a lazy value for that property was computed.
         */
        initializeLazyValue(key);
        return (String) properties.setProperty(key, value);
    }

    public String clearProperty(String key) {
        initializeLazyValue(key);
        return (String) properties.remove(key);
    }

    private void initializeLazyValue(String key) {
        if (!fullyInitialized && lazyRuntimeValues.containsKey(key) && properties.get(key) == null) {
            /*
             * Hashtable.putIfAbsent has the correct synchronization to guard against concurrent
             * manual updates of the same property key.
             */
            String value = lazyRuntimeValues.get(key).get();
            setRawProperty(key, value);
        }
    }

    private void setRawProperty(String key, String value) {
        if (value != null && properties.putIfAbsent(key, value) == null) {
            synchronized (savedProperties) {
                savedProperties.put(key, value);
            }
        }
    }

    private String cachedUserName;

    String userName() {
        if (cachedUserName == null) {
            cachedUserName = userNameValue();
        }
        return cachedUserName;
    }

    private String cachedUserHome;

    String userHome() {
        if (cachedUserHome == null) {
            cachedUserHome = userHomeValue();
        }
        return cachedUserHome;
    }

    private String cachedUserDir;

    String userDir() {
        if (cachedUserDir == null) {
            cachedUserDir = userDirValue();
        }
        return cachedUserDir;
    }

    private String cachedJavaIoTmpdir;

    String javaIoTmpDir() {
        if (cachedJavaIoTmpdir == null) {
            cachedJavaIoTmpdir = javaIoTmpdirValue();
        }
        return cachedJavaIoTmpdir;
    }

    private String cachedJavaLibraryPath;

    String javaLibraryPath() {
        if (cachedJavaLibraryPath == null) {
            cachedJavaLibraryPath = javaLibraryPathValue();
        }
        return cachedJavaLibraryPath;
    }

    // Platform-specific subclasses compute the actual system property values lazily at run time.

    protected abstract String userNameValue();

    protected abstract String userHomeValue();

    protected abstract String userDirValue();

    protected String javaIoTmpdirValue() {
        return tmpdirValue();
    }

    protected String tmpdirValue() {
        throw VMError.intentionallyUnimplemented();
    }

    protected String javaLibraryPathValue() {
        /* Default implementation. */
        return "";
    }

    protected String osNameValue() {
        /*
         * Fallback for systems that don't implement osNameValue in their SystemPropertiesSupport
         * implementation.
         */
        return hostOS;
    }

    protected abstract String osVersionValue();

    public record LocaleEncoding(String language, String script, String country, String variant, String extensions) {
        private LocaleEncoding(CCharPointerPointer properties) {
            this(fromCStringArray(properties, LANGUAGE_POSITION),
                            fromCStringArray(properties, SCRIPT_POSITION),
                            fromCStringArray(properties, COUNTRY_POSITION),
                            fromCStringArray(properties, VARIANT_POSITION),
                            fromCStringArray(properties, EXTENSION_POSITION));
        }

        private static String fromCStringArray(CCharPointerPointer cString, int index) {
            if (cString.isNull()) {
                return null;
            }
            return CTypeConversion.toJavaString(cString.read(index));
        }
    }

    private LocaleEncoding displayLocale;

    private LocaleEncoding formatLocale;

    protected LocaleEncoding parseLocale(Locale.Category category) {
        if (!ImageSingletons.contains(LibCSupport.class)) {
            /* If native calls are not supported, just return fixed values. */
            return new LocaleEncoding("en", "", "US", "", "");
        }
        switch (category) {
            case DISPLAY -> {
                if (displayLocale == null) {
                    displayLocale = new LocaleEncoding(LibCHelper.Locale.parseDisplayLocale());
                }
                return displayLocale;
            }
            case FORMAT -> {
                if (formatLocale == null) {
                    formatLocale = new LocaleEncoding(LibCHelper.Locale.parseFormatLocale());
                }
                return formatLocale;
            }
            default -> throw new GraalError("Unknown locale category: " + category + ".");
        }
    }

    private String postProcessLocale(String base, String value, Locale.Category category) {
        if (category == null) {
            /* user.xxx property */
            String baseValue = null;
            if (value != null) {
                setRawProperty(base, value);
                baseValue = value;
            }
            return baseValue;
        }
        switch (category) {
            case DISPLAY, FORMAT -> {
                /* user.xxx.(display|format) property */
                String baseValue = getProperty(base);
                if (baseValue == null && value != null) {
                    setRawProperty(base + '.' + category.name().toLowerCase(Locale.ROOT), value);
                    return value;
                }
                return null;
            }
            default -> throw new GraalError("Unknown locale category: " + category + ".");
        }
    }

    public static class UserSystemProperty {
        public static final String USER_LANGUAGE = "user.language";
        public static final String USER_LANGUAGE_DISPLAY = USER_LANGUAGE + ".display";
        public static final String USER_LANGUAGE_FORMAT = USER_LANGUAGE + ".format";
        public static final String USER_SCRIPT = "user.script";
        public static final String USER_SCRIPT_DISPLAY = USER_SCRIPT + ".display";
        public static final String USER_SCRIPT_FORMAT = USER_SCRIPT + ".format";
        public static final String USER_COUNTRY = "user.country";
        public static final String USER_COUNTRY_DISPLAY = USER_COUNTRY + ".display";
        public static final String USER_COUNTRY_FORMAT = USER_COUNTRY + ".format";
        public static final String USER_VARIANT = "user.variant";
        public static final String USER_VARIANT_DISPLAY = USER_VARIANT + ".display";
        public static final String USER_VARIANT_FORMAT = USER_VARIANT + ".format";
        public static final String USER_EXTENSIONS = "user.extensions";
        public static final String USER_EXTENSIONS_DISPLAY = USER_EXTENSIONS + ".display";
        public static final String USER_EXTENSIONS_FORMAT = USER_EXTENSIONS + ".format";
        public static final String USER_REGION = "user.region";
    }
}
