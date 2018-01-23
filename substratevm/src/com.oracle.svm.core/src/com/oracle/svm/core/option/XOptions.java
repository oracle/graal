/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.util.Arrays;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.log.Log;

/** A parser for the HotSpot-like memory sizing options "-Xmn", "-Xms", "-Xmx", "-Xss". */
public class XOptions {

    /*
     * Access methods.
     */

    public static XOptions singleton() {
        return ImageSingletons.lookup(XOptions.class);
    }

    public static XFlag getXmn() {
        return XOptions.singleton().xmn;
    }

    public static XFlag getXms() {
        return XOptions.singleton().xms;
    }

    public static XFlag getXmx() {
        return XOptions.singleton().xmx;
    }

    public static XFlag getXss() {
        return XOptions.singleton().xss;
    }

    /** The flag instances. */
    private final XFlag xmn;
    private final XFlag xmx;
    private final XFlag xms;
    private final XFlag xss;

    /** For iterations over the flags. */
    private final XFlag[] xFlagArray;

    /** Private constructor during image building: clients use the image singleton. */
    @Platforms(Platform.HOSTED_ONLY.class)
    XOptions() {
        xmn = new XFlag("-Xmn");
        xmx = new XFlag("-Xmx");
        xms = new XFlag("-Xms");
        xss = new XFlag("-Xss");
        xFlagArray = new XFlag[]{xmn, xms, xmx, xss};
    }

    /** An X flag. */
    public static class XFlag {

        /* Fields. */
        /* . The string for the prefix of the flag. */
        private final String prefix;
        /* . The value of the flag. */
        private long value;
        /* . When was the value set, if ever. */
        private long epoch;

        /** Constructor. */
        @Platforms(Platform.HOSTED_ONLY.class)
        XFlag(String optionString) {
            prefix = optionString;
            value = 0L;
            epoch = 0L;
        }

        public String getPrefix() {
            return prefix;
        }

        public long getValue() {
            return value;
        }

        public long getEpoch() {
            return epoch;
        }

        void setValue(long valueArg) {
            value = valueArg;
            epoch += 1L;
        }
    }

    /** Parse the "-X" options out of a String[], returning the ones that are not "-X" options. */
    public String[] parse(String[] args) {
        int newIdx = 0;
        for (int oldIdx = 0; oldIdx < args.length; oldIdx += 1) {
            final String arg = args[oldIdx];
            boolean parsed = false;
            for (int flagIdx = 0; flagIdx < xFlagArray.length; flagIdx += 1) {
                final XFlag xFlag = xFlagArray[flagIdx];
                parsed |= parse(arg, xFlag);
            }
            if (!parsed) {
                assert newIdx <= oldIdx;
                args[newIdx] = arg;
                newIdx += 1;
            }
        }
        return (newIdx == args.length) ? args : Arrays.copyOf(args, newIdx);
    }

    /** Try to parse the arg as the given xFlag. Returns true if successful, false otherwise. */
    private static boolean parse(String arg, XFlag xFlag) {
        if (arg.startsWith(xFlag.getPrefix())) {
            final String valueString = arg.substring(xFlag.getPrefix().length());
            try {
                final long valueLong = SubstrateOptionsParser.parseLong(valueString);
                xFlag.setValue(valueLong);
                return true;
            } catch (NumberFormatException nfe) {
                Log.logStream().println("error: " + "Wrong value for option '" + xFlag.getPrefix() + "': '" + valueString + "' is not a valid number.");
                System.exit(1);
            }
        }
        return false;
    }
}

/** Set up the singleton instance. */
@AutomaticFeature
class XOptionAccessFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(XOptions.class, new XOptions());
    }
}
