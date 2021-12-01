/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;

/**
 * A parser for the HotSpot-like memory sizing options "-Xmn", "-Xms", "-Xmx", "-Xss". Every option
 * has a corresponding {@link RuntimeOptionKey} in {@link SubstrateOptions}.
 */
public final class XOptions {
    private static final XFlag[] XOPTIONS = {
                    new XFlag("ms", SubstrateGCOptions.MinHeapSize),
                    new XFlag("mx", SubstrateGCOptions.MaxHeapSize),
                    new XFlag("mn", SubstrateGCOptions.MaxNewSize),
                    new XFlag("ss", SubstrateOptions.StackSize)
    };

    /**
     * Parses an XOption from a name and a value (e.g., from "mx2g") and adds it to the given map.
     */
    public static boolean parse(String keyAndValue, EconomicMap<OptionKey<?>, Object> values, boolean exitOnError) {
        XFlag xFlag = findXFlag(keyAndValue);
        if (xFlag != null) {
            long value = parse(xFlag, keyAndValue, exitOnError);
            xFlag.optionKey.update(values, value);
            return true;
        }
        return false;
    }

    /**
     * Sets an XOption from a name and a value (e.g., from "mx2g"). Returns true if successful,
     * false otherwise. Throws an exception if the option was recognized, but the value was not a
     * number.
     */
    public static boolean setOption(String keyAndValue) {
        XFlag xFlag = findXFlag(keyAndValue);
        if (xFlag != null) {
            long value = parse(xFlag, keyAndValue, false);
            xFlag.optionKey.update(value);
            return true;
        }
        return false;
    }

    private static XFlag findXFlag(String keyAndValue) {
        for (XFlag xFlag : XOPTIONS) {
            if (keyAndValue.startsWith(xFlag.name)) {
                return xFlag;
            }
        }
        return null;
    }

    private static long parse(XFlag xFlag, String keyAndValue, boolean exitOnError) {
        final String valueString = keyAndValue.substring(xFlag.name.length());
        try {
            return SubstrateOptionsParser.parseLong(valueString);
        } catch (NumberFormatException nfe) {
            if (exitOnError) {
                Log.logStream().println("error: Wrong value for option '" + RuntimeOptionParser.X_OPTION_PREFIX + keyAndValue + "' is not a valid number.");
                System.exit(1);
                throw VMError.shouldNotReachHere();
            } else {
                throw new IllegalArgumentException("Invalid option '" + RuntimeOptionParser.X_OPTION_PREFIX + keyAndValue + "' does not specify a valid number.");
            }
        }
    }

    private static class XFlag {
        final String name;
        final RuntimeOptionKey<Long> optionKey;

        @Platforms(Platform.HOSTED_ONLY.class)
        XFlag(String name, RuntimeOptionKey<Long> optionKey) {
            this.name = name;
            this.optionKey = optionKey;
        }
    }
}
