/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.LogManager;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.logger.SurrogateLogger;

class FormatAccessors {

    // format string for printing the log record
    private static String getLoggingProperty(String name) {
        return LogManager.getLogManager().getProperty(name);
    }

    private static String format = null;

    @SuppressWarnings("unused")
    public static String getFormat(java.util.logging.SimpleFormatter parent) {
        if (format == null) {
            /*
             * If multiple threads are doing the initialization at the same time it is not a problem
             * because they will all get to the same result in the end.
             */
            format = SurrogateLogger.getSimpleFormat(FormatAccessors::getLoggingProperty);

        }
        return format;
    }

    @SuppressWarnings("unused")
    public static void setFormat(java.util.logging.SimpleFormatter parent, String f) {
        format = f;
    }
}

@TargetClass(value = java.util.logging.SimpleFormatter.class)
public final class Target_java_util_logging_SimpleFormatter {

    @Alias @InjectAccessors(FormatAccessors.class)//
    private String format;
}
