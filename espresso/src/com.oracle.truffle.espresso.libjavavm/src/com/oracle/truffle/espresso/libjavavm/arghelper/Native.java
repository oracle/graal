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

package com.oracle.truffle.espresso.libjavavm.arghelper;

import static com.oracle.truffle.espresso.libjavavm.Arguments.abort;
import static com.oracle.truffle.espresso.libjavavm.arghelper.ArgumentsHandler.isBooleanOption;

import java.util.EnumSet;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.espresso.libjavavm.Arguments;

/**
 * Handles communication with the host VM for passing arguments from the command line.
 */
class Native {

    Native(ArgumentsHandler handler) {
        this.handler = handler;
    }

    private final ArgumentsHandler handler;

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
                throw abort("Unrecognized option: " + formatArg(arg) + "'.");
            }
        } else {
            throw abort("Unrecognized option: " + formatArg(arg) + "'.");
        }
    }

    void printNativeHelp() {
        handler.printRaw("Native VM options:");
        SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>();
        for (OptionDescriptor descriptor : getVMOptions()) {
            if (!descriptor.isDeprecated()) {
                sortedOptions.put(descriptor.getName(), descriptor);
            }
        }
        for (Map.Entry<String, OptionDescriptor> entry : sortedOptions.entrySet()) {
            OptionDescriptor descriptor = entry.getValue();
            String helpMsg = descriptor.getHelp();
            if (isBooleanOption(descriptor)) {
                Boolean val = (Boolean) descriptor.getKey().getDefaultValue();
                if (helpMsg.length() != 0) {
                    helpMsg += ' ';
                }
                if (val == null || !val) {
                    helpMsg += "Default: - (disabled).";
                } else {
                    helpMsg += "Default: + (enabled).";
                }
                launcherOption("--vm.XX:\u00b1" + entry.getKey(), helpMsg);
            } else {
                Object def = descriptor.getKey().getDefaultValue();
                if (def instanceof String) {
                    def = "\"" + def + "\"";
                }
                launcherOption("--vm.XX:" + entry.getKey() + "=" + def, helpMsg);
            }
        }
        printCompilerOptions();
        printBasicNativeHelp();
    }

    private void launcherOption(String s, String helpMsg) {
        handler.printLauncherOption(s, helpMsg);
    }

    private String formatArg(String arg) {
        return argPrefix + arg;
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

    private static void setSystemProperty(String arg) {
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

    private void setRuntimeOption(String arg) {
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

    private void printCompilerOptions() {
        handler.printRaw("Compiler options:");
        SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>();
        for (OptionDescriptor descriptor : getCompilerOptions()) {
            if (!descriptor.isDeprecated()) {
                sortedOptions.put(descriptor.getName(), descriptor);
            }
        }
        for (Map.Entry<String, OptionDescriptor> entry : sortedOptions.entrySet()) {
            OptionDescriptor descriptor = entry.getValue();
            String helpMsg = descriptor.getHelp();
            Object def = descriptor.getKey().getDefaultValue();
            if (def instanceof String) {
                def = '"' + (String) def + '"';
            }
            launcherOption("--vm.Dgraal." + entry.getKey() + "=" + def, helpMsg);
        }
    }

    private void printBasicNativeHelp() {
        launcherOption("--vm.D<property>=<value>", "Sets a system property in the host VM.");
        /* The default values are *copied* from com.oracle.svm.core.genscavenge.HeapPolicy */
        launcherOption("--vm.Xmn<value>", "Sets the maximum size of the young generation, in bytes. Default: 256MB.");
        launcherOption("--vm.Xmx<value>", "Sets the maximum size of the heap, in bytes. Default: MaximumHeapSizePercent * physical memory.");
        launcherOption("--vm.Xms<value>", "Sets the minimum size of the heap, in bytes. Default: 2 * maximum young generation size.");
        launcherOption("--vm.Xss<value>", "Sets the size of each thread stack, in bytes. Default: OS-dependent.");
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

    private static Arguments.ArgumentException unknownOption(String key) {
        throw abort("Unknown native option: " + key + "." + "Use --help:vm to list available options.");
    }
}
