/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.properties;

import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.jdk.SystemPropertiesSupport;

public final class RuntimePropertyParser {

    private static final String PROPERTY_PREFIX = "-D";

    /**
     * Parses all arguments to find those that set Java system properties (those that start with
     * "-D"). Any such matches are removed from the argument list and the corresponding system
     * property is set. The returned array of arguments are those that should be passed through to
     * the application.
     */
    public static String[] parse(String[] args) {
        int newIdx = 0;
        for (int oldIdx = 0; oldIdx < args.length; oldIdx++) {
            String arg = args[oldIdx];
            if (arg.startsWith(PROPERTY_PREFIX) && parseProperty(arg.substring(PROPERTY_PREFIX.length()))) {
                // Option consumed
            } else {
                assert newIdx <= oldIdx;
                args[newIdx] = arg;
                newIdx++;
            }
        }
        if (newIdx == args.length) {
            /* We can be allocation free and just return the original arguments. */
            return args;
        } else {
            return Arrays.copyOf(args, newIdx);
        }
    }

    private static boolean parseProperty(String property) {
        int splitIndex = property.indexOf('=');
        if (splitIndex == -1) {
            return false;
        }

        String key = property.substring(0, splitIndex);
        String value = property.substring(splitIndex + 1);

        ImageSingletons.lookup(SystemPropertiesSupport.class).initializeProperty(key, value);

        return true;
    }
}
