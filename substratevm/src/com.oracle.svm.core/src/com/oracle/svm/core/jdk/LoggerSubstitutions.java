/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

@TargetClass(java.util.logging.Logger.class)
@SuppressWarnings({"unused"})
final class Target_java_util_logging_Logger {
    static {
        /* Initialize the logging system by creating a logger. */
        Logger dummy = Logger.getLogger("svm_initialization_dummy");
        /* The default output handler is added lazily, so query it here to ensure it is created. */
        dummy.getParent().getHandlers();
    }

    @Alias private ResourceBundle catalog;
    @Alias private String catalogName;
    @Alias private Locale catalogLocale;

    @Substitute
    private ResourceBundle findResourceBundle(String name, boolean useCallersClassLoader) {
        /* TODO: the method was originally synchronized. */
        if (name == null) {
            return null;
        }

        /* Same caching as in original implementation. */
        Locale currentLocale = Locale.getDefault();
        if (catalog != null && currentLocale.equals(catalogLocale) && name.equals(catalogName)) {
            return catalog;
        }

        /*
         * Call our (also substituted) resource bundle lookup, without any references to
         * ClassLoader.
         */
        catalog = ResourceBundle.getBundle(name, currentLocale);
        catalogName = name;
        catalogLocale = currentLocale;
        return catalog;
    }

    @Substitute
    public static Logger getLogger(String name, String resourceBundleName) {
        /* We cannot access our caller class, so just pass null for it. */
        return demandLogger(name, resourceBundleName, null);
    }

    @Substitute
    public static Logger getLogger(String name) {
        /* We cannot access our caller class, so just pass null for it. */
        return demandLogger(name, null, null);
    }

    @Alias
    private static native Logger demandLogger(String name, String resourceBundleName, Class<?> caller);

}

@TargetClass(java.util.logging.LogManager.class)
@SuppressWarnings({"unused"})
final class Target_java_util_logging_LogManager {
    @Alias private boolean readPrimordialConfiguration;

    @Substitute
    private void readPrimordialConfiguration() {
        assert readPrimordialConfiguration : "logging infrastructure must be correctly initialized during native image generation";
    }

    @Substitute
    private void loadLoggerHandlers(final Logger logger, final String name, final String handlersPropertyName) {
        /* Do nothing. Logger handlers are instantiated via reflection, which we don't support. */
    }

    @Substitute
    private Filter getFilterProperty(String name, Filter defaultValue) {
        String val = getProperty(name);
        if (val != null) {
            throw VMError.unsupportedFeature("Cannot instantiate logging filter class " + val);
        }
        return defaultValue;
    }

    @Substitute
    private Formatter getFormatterProperty(String name, Formatter defaultValue) {
        String val = getProperty(name);
        if (SimpleFormatter.class.getName().equals(val)) {
            return new SimpleFormatter();
        } else if (val != null) {
            throw VMError.unsupportedFeature("Cannot instantiate logging formatter class " + val);
        }
        return defaultValue;
    }

    @Alias
    private native String getProperty(String name);

}

@TargetClass(java.util.logging.LogRecord.class)
final class Target_java_util_logging_LogRecord {
    @Substitute
    private void inferCaller() {
        /*
         * The original implementation performs a stack walk here. We cannot do that. But luckily
         * this is a best-effort operation anyway, so doing nothing is a suitable substitution.
         */
    }
}

/** Dummy class to have a class with the file's name. */
public final class LoggerSubstitutions {
}
