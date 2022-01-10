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
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.VM;
import com.oracle.svm.core.config.ConfigurationValues;

/**
 * This class maintains the system properties at run time.
 *
 * Some of the standard system properties can just be taken from the image generator:
 * {@link #HOSTED_PROPERTIES}. Other important system properties need to be computed at run time.
 * However, we want to do the computation lazily to reduce the startup cost. For example, getting
 * the current working directory is quite expensive. We initialize such a property either when it is
 * explicitly accessed, or when all properties are accessed.
 */
public abstract class SystemPropertiesSupport {

    /** System properties that are taken from the VM hosting the image generator. */
    private static final String[] HOSTED_PROPERTIES = {
                    "java.version",
                    ImageInfo.PROPERTY_IMAGE_KIND_KEY,
                    /*
                     * We do not support cross-compilation for now. Separator might also be cached
                     * in other classes, so changing them would be tricky.
                     */
                    "line.separator", "path.separator", "file.separator",
                    /* For our convenience for now. */
                    "file.encoding", "sun.jnu.encoding",
                    "java.class.version",
                    "java.specification.name",
                    "java.specification.vendor",
                    "java.specification.version",
                    "java.vm.specification.name",
                    "java.vm.specification.vendor",
                    "java.vm.specification.version"
    };

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

        for (String key : HOSTED_PROPERTIES) {
            String value = System.getProperty(key);
            properties.put(key, value);
            savedProperties.put(key, value);
        }

        initializeProperty("java.vm.name", "Substrate VM");
        initializeProperty("java.vm.vendor", "Oracle Corporation");
        initializeProperty("java.vendor", "Oracle Corporation");
        initializeProperty("java.vendor.url", "https://www.graalvm.org/");

        initializeProperty("java.class.path", "");
        initializeProperty("java.endorsed.dirs", "");
        initializeProperty("java.ext.dirs", "");
        initializeProperty("java.library.path", "");
        initializeProperty("sun.arch.data.model", Integer.toString(ConfigurationValues.getTarget().wordJavaKind.getBitCount()));

        String targetName = System.getProperty("svm.targetName");
        String targetArch = System.getProperty("svm.targetArch");
        initializeProperty("os.name", targetName != null ? targetName : System.getProperty("os.name"));
        initializeProperty("os.arch", targetArch != null ? targetArch : System.getProperty("os.arch"));

        initializeProperty(ImageInfo.PROPERTY_IMAGE_CODE_KEY, ImageInfo.PROPERTY_IMAGE_CODE_VALUE_RUNTIME);

        lazyRuntimeValues = new HashMap<>();
        lazyRuntimeValues.put("user.name", this::userName);
        lazyRuntimeValues.put("user.home", this::userHome);
        lazyRuntimeValues.put("user.dir", this::userDir);
        lazyRuntimeValues.put("java.io.tmpdir", this::tmpdirValue);
        lazyRuntimeValues.put("os.version", this::osVersionValue);
        lazyRuntimeValues.put("java.vm.version", VM::getVersion);
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
