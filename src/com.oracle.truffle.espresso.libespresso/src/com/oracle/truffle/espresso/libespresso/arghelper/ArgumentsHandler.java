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

import java.io.PrintStream;

import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.Context;
import org.graalvm.word.Pointer;

import com.oracle.truffle.espresso.libespresso.Arguments;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMOption;

public class ArgumentsHandler {
    /**
     * Default option description indentation.
     */
    public static final int LAUNCHER_OPTIONS_INDENT = 45;

    private PrintStream out = System.out;
    private PrintStream err = System.err;

    private final Context.Builder builder;

    private final Native nativeAccess;
    private final PolyglotArgs polyglotAccess;
    private final ModulePropertyCounter modulePropertyCounter;

    private final StringBuilder helpMsg = new StringBuilder();

    private final boolean experimental;

    private boolean helpVM = false;

    public ArgumentsHandler(Context.Builder builder, JNIJavaVMInitArgs args) {
        this.builder = builder;
        this.nativeAccess = new Native(this);
        this.modulePropertyCounter = new ModulePropertyCounter(builder);
        this.polyglotAccess = new PolyglotArgs(builder);
        this.experimental = checkExperimental(args);
    }

    private static boolean checkExperimental(JNIJavaVMInitArgs args) {
        Pointer p = (Pointer) args.getOptions();
        for (int i = 0; i < args.getNOptions(); i++) {
            JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
            CCharPointer str = option.getOptionString();
            if (str.isNonNull()) {
                String optionString = CTypeConversion.toJavaString(option.getOptionString());
                switch (optionString) {
                    case "--experimental-options":
                    case "--experimental-options=true":
                        return true;
                    case "--experimental-options=false":
                        return false;
                    default:
                }
            }
        }
        return false;
    }

    public boolean isModulesOption(String key) {
        return modulePropertyCounter.isModulesOption(key);
    }

    public void addModules(String option) {
        modulePropertyCounter.addModules(option);
    }

    public void addExports(String option) {
        modulePropertyCounter.addExports(option);
    }

    public void addOpens(String option) {
        modulePropertyCounter.addOpens(option);
    }

    public void addReads(String option) {
        modulePropertyCounter.addReads(option);
    }

    public void handleXXArg(String optionString) {
        String toPolyglot = optionString.substring("-XX:".length());
        if (toPolyglot.length() >= 1 && (toPolyglot.charAt(0) == '+' || toPolyglot.charAt(0) == '-')) {
            String value = Boolean.toString(toPolyglot.charAt(0) == '+');
            toPolyglot = "--java." + toPolyglot.substring(1) + "=" + value;
        } else {
            toPolyglot = "--java." + toPolyglot;
        }
        try {
            parsePolyglotOption(toPolyglot);
            return;
        } catch (Arguments.ArgumentException e) {
            if (e.isExperimental()) {
                throw abort(e.getMessage().replace(toPolyglot, optionString));
            }
            /* Ignore, and try to pass it as a vm arg */
        }
        // Pass as host vm arg
        nativeAccess.init(true);
        nativeAccess.setNativeOption(optionString.substring(1));
    }

    public void handleVMOption(String optionString) {
        nativeAccess.init(false);
        nativeAccess.setNativeOption(optionString.substring("--vm.".length()));
    }

    public void parsePolyglotOption(String optionString) {
        polyglotAccess.parsePolyglotOption(optionString, experimental);
    }

    public void argumentProcessingDone() {
        printHelp();
        polyglotAccess.argumentProcessingDone();
    }

    public void helpVM() {
        helpVM = true;
    }

    void launcherOption(String option, String description) {
        out.println(getHelpLine(option, description));
    }

    static boolean isBooleanOption(OptionDescriptor descriptor) {
        return descriptor.getKey().getType().equals(OptionType.defaultType(Boolean.class));
    }

    private void printHelp() {
        if (helpVM) {
            nativeAccess.printNativeHelp();
        }
    }

    private static String getHelpLine(String option, String description) {
        return getHelpLine(option, description, 2, LAUNCHER_OPTIONS_INDENT);
    }

    private static String getHelpLine(String option, String description, int indentStart, int optionWidth) {
        String indent = spaces(indentStart);
        String desc = wrap(description != null ? description : "");
        String nl = System.lineSeparator();
        String[] descLines = desc.split(nl);
        StringBuilder toPrint = new StringBuilder();
        if (option.length() >= optionWidth && description != null) {
            toPrint.append(indent).append(option).append(nl).append(indent).append(spaces(optionWidth)).append(descLines[0]);
        } else {
            toPrint.append(indent).append(option).append(spaces(optionWidth - option.length())).append(descLines[0]);
        }
        for (int i = 1; i < descLines.length; i++) {
            toPrint.append('\n').append(indent).append(spaces(optionWidth)).append(descLines[i]);
        }
        return toPrint.toString();
    }

    private static String spaces(int length) {
        return new String(new char[length]).replace('\0', ' ');
    }

    private static String wrap(String s) {
        final int width = 120;
        StringBuilder sb = new StringBuilder(s);
        int cursor = 0;
        while (cursor + width < sb.length()) {
            int i = sb.lastIndexOf(" ", cursor + width);
            if (i == -1 || i < cursor) {
                i = sb.indexOf(" ", cursor + width);
            }
            if (i != -1) {
                sb.replace(i, i + 1, System.lineSeparator());
                cursor = i;
            } else {
                break;
            }
        }
        return sb.toString();
    }
}
