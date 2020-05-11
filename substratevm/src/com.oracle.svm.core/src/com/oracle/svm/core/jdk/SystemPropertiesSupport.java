/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.oracle.svm.core.option.RuntimeOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.VM;
import com.oracle.svm.core.config.ConfigurationValues;

/**
 * This class maintains the system properties at run time.
 *
 * Some of the standard system properties can just be taken from the image generator:
 * Other important system properties need to be computed at run time.
 * However, we want to do the computation lazily to reduce the startup cost. For example, getting
 * the current working directory is quite expensive. We initialize such a property either when it is
 * explicitly accessed, or when all properties are accessed.
 */
public abstract class SystemPropertiesSupport {

    public static class SystemPropertyFeatureOptions{
        @Option(help = "Report the usage of undefined property as exception when it is not explicitly set)")//
        public static final RuntimeOptionKey<Boolean> ReportUndefinedSystemPropertyError = new RuntimeOptionKey<>(false);
    }

    private enum InitKind {AsHosted, Runtime, BuildTime, Undefined}

    private static class PropertyInfo {
        private String name;
        private BooleanSupplier onlyWith;
        private InitKind initKind;
        private Supplier<String> initValue;

        public PropertyInfo(String name, BooleanSupplier onlyWith, InitKind InitKind, Supplier<String> initValue) {
            if (name == null) {
                throw new NullPointerException("Property name should never be null!");
            }
            this.name = name;
            this.onlyWith = onlyWith;
            this.initKind = InitKind;
            this.initValue = initValue;
        }

    }

    private static final Map<String, PropertyInfo> SYSTEM_PROPERTIES= new ConcurrentHashMap<>();

    private static final JDK8OrEarlier jdk8OrEarlier = new JDK8OrEarlier();
    private static final JDK11OrLater jdk11OrLater = new JDK11OrLater();
    private static final BooleanSupplier always = ()-> true;
    private static final Supplier<String> STRING_SUPPLIER_SUPPLIER = () -> "";

    static {
        //Set properties same as hosted
        addAsHostedSystemProperty("file.encoding", always);
        // https://github.com/openjdk/jdk/commit/d4941f14af150fb81db7cff5394192637be701dd
        addAsHostedSystemProperty("file.encoding.pkg", jdk8OrEarlier);
        addAsHostedSystemProperty("file.separator", always);
        addAsHostedSystemProperty("java.class.version", always);
        addAsHostedSystemProperty("java.runtime.version", always);
        addAsHostedSystemProperty("java.specification.name", always);
        addAsHostedSystemProperty("java.specification.vendor", always);
        addAsHostedSystemProperty("java.specification.version", always);
        addAsHostedSystemProperty("java.version", always);
        // https://openjdk.java.net/jeps/322
       addAsHostedSystemProperty("java.version.date", jdk11OrLater);
       addAsHostedSystemProperty("java.vendor.version", jdk11OrLater);
       addAsHostedSystemProperty("java.vm.specification.name", always);
       addAsHostedSystemProperty("java.vm.specification.vendor", always);
       addAsHostedSystemProperty("java.vm.specification.version", always);
       addAsHostedSystemProperty("line.separator", always);
       addAsHostedSystemProperty("path.separator", always);
       addAsHostedSystemProperty("sun.cpu.endian", always);
       addAsHostedSystemProperty("sun.cpu.isalist", always);
       addAsHostedSystemProperty("sun.jnu.encoding", always);
       addAsHostedSystemProperty("sun.io.unicode.encoding", always);
       addAsHostedSystemProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY, always);

        //Undefined properties
        addUndefinedSystemProperty("awt.toolkit", always);
        addUndefinedSystemProperty("java.awt.graphicsenv", always);
        addUndefinedSystemProperty("java.awt.printerjob", always);
        addUndefinedSystemProperty("java.home", always);
        addUndefinedSystemProperty("sun.java.command", always);
        addUndefinedSystemProperty("sun.java.launcher", always);
        addUndefinedSystemProperty("sun.os.patch.level", always);
        addUndefinedSystemProperty("user.country", always);
        addUndefinedSystemProperty("user.language", always);
        addUndefinedSystemProperty("user.timezone", always);
        // http://hg.openjdk.java.net/jdk9/jdk9/hotspot/rev/3b241fb72b89
        addUndefinedSystemProperty("java.vm.compressedOopsMode", jdk11OrLater);
        // http://hg.openjdk.java.net/jdk9/jdk9/hotspot/rev/39c579b50006
        addUndefinedSystemProperty("jdk.debug", jdk11OrLater);

        // Build time initialized properties
        addSystemProperty("java.class.path", always, InitKind.BuildTime, STRING_SUPPLIER_SUPPLIER);
        // http://hg.openjdk.java.net/jdk9/jdk9/jdk/rev/e336cbd8b15e
        addSystemProperty("java.endorsed.dirs", jdk8OrEarlier, InitKind.BuildTime, STRING_SUPPLIER_SUPPLIER);
        addSystemProperty("java.ext.dirs", jdk8OrEarlier, InitKind.BuildTime, STRING_SUPPLIER_SUPPLIER);
        addSystemProperty("java.runtime.name", always, InitKind.BuildTime, ()->"Substrate VM Runtime Environment");
        addSystemProperty("java.vendor", always, InitKind.BuildTime, ()->"Oracle Corporation");
        addSystemProperty("java.vendor.url.bug", always, InitKind.BuildTime, ()->"https://github.com/oracle/graal/issues");
        addSystemProperty("java.vm.info", always, InitKind.BuildTime, ()->"static mode");
        addSystemProperty("java.vm.name", always, InitKind.BuildTime, () ->"Substrate VM");
        addSystemProperty("java.vm.vendor", always, InitKind.BuildTime, ()->"Oracle Corporation");
        addSystemProperty("java.vendor.url", always, InitKind.BuildTime, ()->"https://www.graalvm.org/");
        String targetArch = System.getProperty("svm.targetArch");
        addSystemProperty("os.arch", always, InitKind.BuildTime, ()->targetArch != null ? targetArch : System.getProperty("os.arch"));
        String targetName = System.getProperty("svm.targetName");
        addSystemProperty("os.name", always, InitKind.BuildTime, ()->targetName != null ? targetName : System.getProperty("os.name"));
        addSystemProperty("sun.arch.data.model", always, InitKind.BuildTime, ()->Integer.toString(ConfigurationValues.getTarget().wordJavaKind.getBitCount()));
        // http://openjdk.java.net/jeps/261 http://hg.openjdk.java.net/jdk9/jdk9/hotspot/rev/c558850fac57
        addSystemProperty("sun.boot.class.path", jdk8OrEarlier, InitKind.BuildTime, STRING_SUPPLIER_SUPPLIER);
        addSystemProperty("sun.management.compiler", always, InitKind.BuildTime,()-> "GraalVM Compiler");
        addSystemProperty(ImageInfo.PROPERTY_IMAGE_CODE_KEY, always, InitKind.BuildTime, ()->ImageInfo.PROPERTY_IMAGE_CODE_VALUE_RUNTIME);
    }

    /** System properties that are lazily computed at run time on first access. */
    private final Map<String, Supplier<String>> lazyRuntimeValues;

    private Properties properties;
    private final Map<String, String> savedProperties;
    private final Map<String, String> readOnlySavedProperties;

    private volatile boolean fullyInitialized;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected SystemPropertiesSupport() {
        properties = new Properties();
        savedProperties = new HashMap<>();
        readOnlySavedProperties = Collections.unmodifiableMap(savedProperties);

        // Runtime initialized properties
       addSystemProperty("user.name", always, InitKind.Runtime, this::userName);
       addSystemProperty("user.home", always, InitKind.Runtime, this::userHome);
       addSystemProperty("user.dir", always, InitKind.Runtime, this::userDir);
       addSystemProperty("java.io.tmpdir", always, InitKind.Runtime, this::tmpdirValue);
       addSystemProperty("os.version", always, InitKind.Runtime, this::osVersionValue);
       addSystemProperty("java.vm.version", always, InitKind.Runtime, VM::getVersion);

        lazyRuntimeValues = new HashMap<>();
        SYSTEM_PROPERTIES.forEach((key, propertyInfo) -> {
            if (propertyInfo.onlyWith.getAsBoolean()) {
                switch (propertyInfo.initKind){
                    case AsHosted:
                        initializeProperty(key, System.getProperty(key));
                        break;
                   case BuildTime:
                       initializeProperty(key, propertyInfo.initValue.get());
                       break;
                    case Undefined:
                        break;
                    case Runtime:
                        lazyRuntimeValues.put(key, propertyInfo.initValue);
                }
            }
        });
    }

    private static void addSystemProperty(String name, BooleanSupplier onlyWith, InitKind InitKind, Supplier<String> initValue){
        SYSTEM_PROPERTIES.put(name,new PropertyInfo(name, onlyWith, InitKind, initValue));
    }

    private static void addUndefinedSystemProperty(String name, BooleanSupplier onlyWith) {
        SYSTEM_PROPERTIES.put(name,new PropertyInfo(name, onlyWith, InitKind.Undefined, null));
    }

    private static void addAsHostedSystemProperty(String name, BooleanSupplier onlyWith){
        SYSTEM_PROPERTIES.put(name,new PropertyInfo(name, onlyWith, InitKind.AsHosted, null));
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
        String ret = properties.getProperty(key);
        if (ret == null && isUndefined(key) && SystemPropertyFeatureOptions.ReportUndefinedSystemPropertyError.getValue()) {
            throw new UndefinedSystemPropertyException("Java system property " + key + " is undefined in native image. Please explicitly assign the expected value via -D" + key + "=, or avoid using this property.");
        }
        return ret;
    }

    public static boolean isUndefined(String key) {
        PropertyInfo propertyInfo = SYSTEM_PROPERTIES.get(key);
        return propertyInfo != null && propertyInfo.initKind == InitKind.Undefined;
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
    public void initializeProperty(String key, String value) {
        savedProperties.put(key, value);
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
            if (properties.putIfAbsent(key, value) == null) {
                // Checkstyle: stop
                synchronized (savedProperties) {
                    savedProperties.put(key, value);
                }
                // Checkstyle: resume
            }
        }
    }

    private String cachedUserName;

    public String userName() {
        if (cachedUserName == null) {
            cachedUserName = userNameValue();
        }
        return cachedUserName;
    }

    private String cachedUserHome;

    public String userHome() {
        if (cachedUserHome == null) {
            cachedUserHome = userHomeValue();
        }
        return cachedUserHome;
    }

    private String cachedUserDir;

    public String userDir() {
        if (cachedUserDir == null) {
            cachedUserDir = userDirValue();
        }
        return cachedUserDir;
    }

    // Platform-specific subclasses compute the actual system property values lazily at run time.

    protected abstract String userNameValue();

    protected abstract String userHomeValue();

    protected abstract String userDirValue();

    protected abstract String tmpdirValue();

    protected abstract String osVersionValue();
}
