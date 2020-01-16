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

import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.log.Log;

/**
 * A parser for the HotSpot-like memory sizing options "-Xmn", "-Xms", "-Xmx", "-Xss". Every option
 * has a corresponding {@link RuntimeOptionKey} in {@link SubstrateOptions}. So, the resulting
 * behavior is pretty much as if an {@link APIOption} was specified for a {@link RuntimeOptionKey}.
 */
public class XOptions {
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
        xmn = new XFlag("-X", "mn", "The maximum size of the young generation at run-time, in bytes.");
        xmx = new XFlag("-X", "mx", "The maximum heap size at run-time, in bytes.");
        xms = new XFlag("-X", "ms", "The minimum heap size at run-time, in bytes.");
        xss = new XFlag("-X", "ss", "The size of each thread stack at run-time, in bytes.");
        xFlagArray = new XFlag[]{xmn, xms, xmx, xss};
    }

    /** An X flag. */
    public static class XFlag {
        private final String prefix;
        private final String name;
        private final String description;
        private long value;
        private long epoch;

        /** The concatenation of the prefix and the name. */
        private final String prefixAndName;

        /** Constructor. */
        @Platforms(Platform.HOSTED_ONLY.class)
        XFlag(String prefix, String name, String description) {
            this.prefix = prefix;
            this.name = name;
            this.description = description;
            this.prefixAndName = prefix.concat(name);
            this.value = 0L;
            this.epoch = 0L;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getName() {
            return name;
        }

        public String getPrefixAndName() {
            return prefixAndName;
        }

        public String getDescription() {
            return description;
        }

        public long getValue() {
            return value;
        }

        public long getEpoch() {
            return epoch;
        }

        public void setValue(long valueArg) {
            value = valueArg;
            epoch += 1L;
        }
    }

    /* Return the list of all the XFlags. */
    public XFlag[] getXFlags() {
        /* Danger: Array results are not immutable. */
        return xFlagArray;
    }

    /** Parse the "-X" options out of a String[], returning the ones that are not "-X" options. */
    public String[] parse(String[] args, boolean exitOnError) {
        int newIdx = 0;
        for (int oldIdx = 0; oldIdx < args.length; oldIdx += 1) {
            final String arg = args[oldIdx];
            boolean parsed = false;
            for (XFlag xFlag : xFlagArray) {
                try {
                    parsed |= parseWithNameAndPrefix(xFlag, arg);
                } catch (NumberFormatException nfe) {
                    if (exitOnError) {
                        Log.logStream().println("error: Wrong value for option '" + arg + "' is not a valid number.");
                        System.exit(1);
                    } else {
                        throw new IllegalArgumentException("Illegal value for option '" + arg + "'", nfe);
                    }
                }
            }
            if (!parsed) {
                assert newIdx <= oldIdx;
                args[newIdx] = arg;
                newIdx += 1;
            }
        }
        return (newIdx == args.length) ? args : Arrays.copyOf(args, newIdx);
    }

    /**
     * Try to parse the arg as the given xFlag. Returns true if successful, false otherwise. Throws
     * NumberFormatException if the option was recognized, but the value was not a number.
     */
    private boolean parseWithNameAndPrefix(XFlag xFlag, String arg) throws NumberFormatException {
        if (arg.startsWith(xFlag.getPrefixAndName())) {
            final String valueString = arg.substring(xFlag.getPrefixAndName().length());
            parseFromValueString(xFlag, valueString);
            return true;
        }
        return false;
    }

    /* Set from a String value, e.g., "2g". Returns true if successful, false otherwise. */
    public void parseFromValueString(XFlag xFlag, String value) throws NumberFormatException {
        final long valueLong = SubstrateOptionsParser.parseLong(value);
        xFlag.setValue(valueLong);
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
