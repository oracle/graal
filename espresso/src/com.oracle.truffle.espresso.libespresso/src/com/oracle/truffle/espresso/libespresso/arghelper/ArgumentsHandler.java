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
import java.util.function.Consumer;

import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.Context;
import org.graalvm.word.Pointer;

import com.oracle.truffle.espresso.libespresso.Arguments;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMOption;

/**
 * A helper class that centralizes different ways of handling option parsing for the java -truffle
 * launcher.
 */
public class ArgumentsHandler {
    /**
     * Default option description indentation.
     */
    public static final int LAUNCHER_OPTIONS_INDENT = 45;

    private static final PrintStream out = System.out;

    private final Native nativeAccess;
    private final PolyglotArgs polyglotAccess;
    private final ModulePropertyCounter modulePropertyCounter;

    private final boolean experimental;

    private boolean helpVM = false;
    private boolean helpTools = false;
    private boolean helpEngine = false;
    private boolean helpLanguages = false;

    private boolean helpExpert = false;
    private boolean helpInternal = false;

    public ArgumentsHandler(Context.Builder builder, JNIJavaVMInitArgs args) {
        this.nativeAccess = new Native(this);
        this.modulePropertyCounter = new ModulePropertyCounter(builder);
        this.polyglotAccess = new PolyglotArgs(builder, this);
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

    /**
     * <code>-XX:</code> arguments are handled specially: If there is an espresso option with the
     * same name as the option passed, it will set it for the guest VM. Otherwise, it is forwarded
     * to the host vm.
     *
     * As such,
     * <ul>
     * <li>Specifying <code>-XX:+InlineFieldAccessors</code> will match with the InlineFieldAccessor
     * option in EspressoOptions, and set it to true.</li>
     * <li>Specifying <code>--vm.XX:MaxDirectMemorySize=1024k</code> will pass the
     * <code>-XX:MaxDirectMemorySize</code> option to the host VM, and bypass the matching option in
     * espresso</li>
     * <li>Specifying <code>-XX:MaxDirectMemorySize=1024k</code> will match with the
     * MaxDirectMemorySize option in EspressoOptions, and set it accordingly, but will NOT set this
     * flag for the host VM.</li>
     * <li>Specifying <code>-XX:+PrintGC</code> will pass it directly to the host VM, as it does not
     * match with any of the Espresso options.</li>
     * </ul>
     */
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

    public void parsePolyglotOption(String optionString) {
        polyglotAccess.parsePolyglotOption(optionString, experimental);
    }

    public void handleVMOption(String optionString) {
        nativeAccess.init(false);
        nativeAccess.setNativeOption(optionString.substring("--vm.".length()));
    }

    public void argumentProcessingDone() {
        printHelp();
        polyglotAccess.argumentProcessingDone();
    }

    public void help(String arg) {
        switch (arg) {
            case "--help:vm":
                helpVM = true;
                break;
            case "--help:tools":
                helpTools = true;
                break;
            case "--help:engine":
                helpEngine = true;
                break;
            case "--help:languages":
                helpLanguages = true;
                break;
            case "--help:internal":
                helpInternal = true;
                break;
            case "--help:expert":
                helpExpert = true;
                break;
            default:
                abort("Unrecognized option: '" + arg + "'.");
        }
    }

    void printRaw(String message) {
        out.println(message);
    }

    void printLauncherOption(String option, String description) {
        out.println(getHelpLine(option, description));
    }

    void printLauncherOption(String option, String description, int indentation) {
        out.println(getHelpLine(option, description, indentation, LAUNCHER_OPTIONS_INDENT));
    }

    static boolean isBooleanOption(OptionDescriptor descriptor) {
        return descriptor.getKey().getType().equals(OptionType.defaultType(Boolean.class));
    }

    private void printHelp() {
        boolean help = false;
        if (helpVM) {
            nativeAccess.printNativeHelp();
            help = true;
        }
        if (helpTools) {
            printHelp(polyglotAccess::printToolsHelp);
            help = true;
        }
        if (helpEngine) {
            printHelp(polyglotAccess::printEngineHelp);
            help = true;
        }
        if (helpLanguages) {
            printHelp(polyglotAccess::printLanguageHelp);
            help = true;
        }
        if ((helpExpert || helpInternal) && !help) {
            // an expert or internal help was requested, but no category was specified. Default to
            // engine help.
            printHelp(polyglotAccess::printEngineHelp);
            help = true;
        }
        if (help) {
            System.exit(0);
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

    private void printHelp(Consumer<OptionCategory> printer) {
        boolean user = true;
        if (helpInternal) {
            printer.accept(OptionCategory.INTERNAL);
            user = false;
        }
        if (helpExpert) {
            printer.accept(OptionCategory.EXPERT);
            user = false;
        }
        if (user) {
            printer.accept(OptionCategory.USER);
        }
    }
}
