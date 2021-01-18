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

package com.oracle.truffle.espresso.libespresso.arghelper;

import static com.oracle.truffle.espresso.libespresso.Arguments.abort;
import static com.oracle.truffle.espresso.libespresso.Arguments.abortExperimental;
import static com.oracle.truffle.espresso.libespresso.arghelper.ArgumentsHandler.isBooleanOption;

import java.util.logging.Level;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

class PolyglotArgs {
    private final Context.Builder builder;

    private Engine tempEngine;

    PolyglotArgs(Context.Builder builder) {
        this.builder = builder;
    }

    private Engine getTempEngine() {
        if (tempEngine == null) {
            tempEngine = Engine.newBuilder().useSystemProperties(false).build();
        }
        return tempEngine;
    }

    void argumentProcessingDone() {
        if (tempEngine != null) {
            tempEngine.close();
            tempEngine = null;
        }
    }

    void parsePolyglotOption(String arg, boolean experimentalOptions) {
        if (arg.length() <= 2 || !arg.startsWith("--")) {
            throw abort(String.format("Unrecognized option: %s%n", arg));
        }
        int eqIdx = arg.indexOf('=');
        String key;
        String value;
        if (eqIdx < 0) {
            key = arg.substring(2);
            value = null;
        } else {
            key = arg.substring(2, eqIdx);
            value = arg.substring(eqIdx + 1);
        }

        int index = key.indexOf('.');
        String group = key;
        if (index >= 0) {
            group = group.substring(0, index);
        }
        if ("log".equals(group)) {
            if (key.endsWith(".level")) {
                try {
                    if (value == null) {
                        value = "";
                    }
                    Level.parse(value);
                    builder.option(key, value);
                } catch (IllegalArgumentException e) {
                    throw abort(String.format("Invalid log level %s specified. %s'", arg, e.getMessage()));
                }
                return;
            } else if (key.equals("log.file")) {
                throw abort("Unsupported log.file option");
            }
        }
        OptionDescriptor descriptor = findOptionDescriptor(group, key);
        if (descriptor == null) {
            descriptor = findOptionDescriptor("java", "java" + "." + key);
            if (descriptor == null) {
                throw abort(String.format("Unrecognized option: %s%n", arg));
            }
        }
        if (value == null) {
            if (isBooleanOption(descriptor)) {
                value = "true";
            } else {
                value = "";
            }
        }
        try {
            descriptor.getKey().getType().convert(value);
        } catch (IllegalArgumentException e) {
            throw abort(String.format("Invalid argument %s specified. %s'", arg, e.getMessage()));
        }
        if (!experimentalOptions && descriptor.getStability() == OptionStability.EXPERIMENTAL) {
            throw abortExperimental(String.format("Option '%s' is experimental and must be enabled via '--experimental-options'%n" +
                            "Do not use experimental options in production environments.", arg));
        }
        // use the full name of the found descriptor
        builder.option(descriptor.getName(), value);
    }

    private OptionDescriptor findOptionDescriptor(String group, String key) {
        OptionDescriptors descriptors = null;
        switch (group) {
            case "engine":
                descriptors = getTempEngine().getOptions();
                break;
            default:
                Engine engine = getTempEngine();
                if (engine.getLanguages().containsKey(group)) {
                    descriptors = engine.getLanguages().get(group).getOptions();
                } else if (engine.getInstruments().containsKey(group)) {
                    descriptors = engine.getInstruments().get(group).getOptions();
                }
                break;
        }
        if (descriptors == null) {
            return null;
        }
        return descriptors.get(key);
    }

}
