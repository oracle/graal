/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.libespresso;

import static com.oracle.truffle.espresso.libespresso.Arguments.abort;

import java.util.EnumSet;

import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionType;

class Native {
    private String argPrefix;

    private OptionDescriptors compilerOptionDescriptors;
    private OptionDescriptors vmOptionDescriptors;

    private OptionDescriptors getCompilerOptions() {
        if (compilerOptionDescriptors == null) {
            compilerOptionDescriptors = RuntimeOptions.getOptions(EnumSet.of(RuntimeOptions.OptionClass.Compiler));
        }
        return compilerOptionDescriptors;
    }

    private OptionDescriptors getVMOptions() {
        if (vmOptionDescriptors == null) {
            vmOptionDescriptors = RuntimeOptions.getOptions(EnumSet.of(RuntimeOptions.OptionClass.VM));
        }
        return vmOptionDescriptors;
    }

    void init(boolean fromXXHandling) {
        argPrefix = fromXXHandling ? "-" : "--vm.";
    }

    private String formatArg(String arg) {
        return argPrefix + arg;
    }

    void setNativeOption(String arg) {
        if (arg.startsWith("Dgraal.")) {
            setGraalStyleRuntimeOption(arg.substring("Dgraal.".length()));
        } else if (arg.startsWith("D")) {
            setSystemProperty(arg.substring("D".length()));
        } else if (arg.startsWith("XX:")) {
            setRuntimeOption(arg.substring("XX:".length()));
        } else if (arg.startsWith("X")) {
            if (isXOption(arg)) {
                setXOption(arg.substring("X".length()));
            } else {
                throw abort("Unrecognized option: " + formatArg(arg) + "'. Some VM options may be only supported in --jvm mode.");
            }
        } else {
            throw abort("Unrecognized option: " + formatArg(arg) + "'.");
        }
    }

    private void setGraalStyleRuntimeOption(String arg) {
        if (arg.startsWith("+") || arg.startsWith("-")) {
            throw abort("Dgraal option must use <name>=<value> format, not +/- prefix");
        }
        int eqIdx = arg.indexOf('=');
        String key;
        String value;
        if (eqIdx < 0) {
            key = arg;
            value = "";
        } else {
            key = arg.substring(0, eqIdx);
            value = arg.substring(eqIdx + 1);
        }
        OptionDescriptor descriptor = getCompilerOptions().get(key);
        if (descriptor == null) {
            throw unknownOption(key);
        }
        try {
            RuntimeOptions.set(key, descriptor.getKey().getType().convert(value));
        } catch (IllegalArgumentException iae) {
            throw abort("Invalid argument: " + formatArg(arg) + "': " + iae.getMessage());
        }
    }

    public void setSystemProperty(String arg) {
        int eqIdx = arg.indexOf('=');
        String key;
        String value;
        if (eqIdx < 0) {
            key = arg;
            value = "";
        } else {
            key = arg.substring(0, eqIdx);
            value = arg.substring(eqIdx + 1);
        }
        System.setProperty(key, value);
    }

    public void setRuntimeOption(String arg) {
        int eqIdx = arg.indexOf('=');
        String key;
        Object value;
        if (arg.startsWith("+") || arg.startsWith("-")) {
            key = arg.substring(1);
            if (eqIdx >= 0) {
                throw abort("Invalid argument: " + formatArg(arg) + "': Use either +/- or =, but not both");
            }
            OptionDescriptor descriptor = getVMOptionDescriptor(key);
            if (!isBooleanOption(descriptor)) {
                throw abort("Invalid argument: " + key + " is not a boolean option, set it with " + argPrefix + "XX:" + key + "=<value>.");
            }
            value = arg.startsWith("+");
        } else if (eqIdx > 0) {
            key = arg.substring(0, eqIdx);
            OptionDescriptor descriptor = getVMOptionDescriptor(key);
            if (isBooleanOption(descriptor)) {
                throw abort("Boolean option '" + key + "' must be set with +/- prefix, not <name>=<value> format.");
            }
            try {
                value = descriptor.getKey().getType().convert(arg.substring(eqIdx + 1));
            } catch (IllegalArgumentException iae) {
                throw abort("Invalid argument: " + formatArg(arg) + "': " + iae.getMessage());
            }
        } else {
            throw abort("Invalid argument: " + formatArg(arg) + "'. Prefix boolean options with + or -, suffix other options with <name>=<value>");
        }
        RuntimeOptions.set(key, value);
    }

    private OptionDescriptor getVMOptionDescriptor(String key) {
        OptionDescriptor descriptor = getVMOptions().get(key);
        if (descriptor == null) {
            throw unknownOption(key);
        }
        return descriptor;
    }

    /* Is an option that starts with an 'X' one of the recognized X options? */
    private static boolean isXOption(String arg) {
        return (arg.startsWith("Xmn") || arg.startsWith("Xms") || arg.startsWith("Xmx") || arg.startsWith("Xss"));
    }

    /* Set a `-X` option, given something like "mx2g". */
    private void setXOption(String arg) {
        try {
            RuntimeOptions.set(arg, null);
        } catch (RuntimeException re) {
            throw abort("Invalid argument: '" + argPrefix + "X" + arg + "' does not specify a valid number.");
        }
    }

    private static boolean isBooleanOption(OptionDescriptor descriptor) {
        return descriptor.getKey().getType().equals(OptionType.defaultType(Boolean.class));
    }

    private static Arguments.ArgumentException unknownOption(String key) {
        throw abort("Unknown native option: " + key + "."
        /* + "Use --help:vm to list available options." */);
    }
}
