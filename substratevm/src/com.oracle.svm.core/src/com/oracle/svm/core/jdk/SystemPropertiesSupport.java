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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

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
                    /* We do not support cross-compilation for now. */
                    "os.arch", "os.name",
                    /* For our convenience for now. */
                    "file.encoding", "sun.jnu.encoding",
    };

    /** System properties that are lazily computed at run time on first access. */
    private final Map<String, Supplier<String>> lazyRuntimeValues;

    private final Properties properties;

    private volatile boolean fullyInitialized;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected SystemPropertiesSupport() {
        properties = new Properties();

        for (String key : HOSTED_PROPERTIES) {
            properties.put(key, System.getProperty(key));
        }

        lazyRuntimeValues = new HashMap<>();
        lazyRuntimeValues.put("java.vm.name", () -> "Substrate VM");
        lazyRuntimeValues.put("java.vendor", () -> "Oracle Corporation");
        lazyRuntimeValues.put("java.vendor.url", () -> "https://www.graalvm.org/");
        lazyRuntimeValues.put("user.name", this::userNameValue);
        lazyRuntimeValues.put("user.home", this::userHomeValue);
        lazyRuntimeValues.put("user.dir", this::userDirValue);
        lazyRuntimeValues.put("java.io.tmpdir", this::tmpdirValue);
        lazyRuntimeValues.put("os.version", this::osVersionValue);

        lazyRuntimeValues.put(ImageInfo.PROPERTY_IMAGE_CODE_KEY, () -> ImageInfo.PROPERTY_IMAGE_CODE_VALUE_RUNTIME);
    }

    public Properties getProperties() {
        /*
         * We do not know what the user is going to do with the returned Properties object, so we
         * need to do a full initialization.
         */
        if (!fullyInitialized) {
            for (String key : lazyRuntimeValues.keySet()) {
                initializeLazyValue(key);
            }
            fullyInitialized = true;
        }

        return properties;
    }

    protected String getProperty(String key) {
        initializeLazyValue(key);
        return properties.getProperty(key);
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
             * putIfAbsent has the correct synchronization to guard against concurrent manual
             * updates of the same property key.
             */
            properties.putIfAbsent(key, lazyRuntimeValues.get(key).get());
        }
    }

    // Platform-specific subclasses compute the actual system property values lazily at run time.

    protected abstract String userNameValue();

    protected abstract String userHomeValue();

    protected abstract String userDirValue();

    protected abstract String tmpdirValue();

    protected abstract String osVersionValue();
}
