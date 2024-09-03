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
import static com.oracle.truffle.espresso.libjavavm.Arguments.warn;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.Context;
import org.graalvm.word.Pointer;

import com.oracle.truffle.espresso.libjavavm.Arguments;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIJavaVMOption;

/**
 * A helper class that centralizes different ways of handling option parsing for the java -truffle
 * launcher.
 */
public class ArgumentsHandler {
    /**
     * Default option description indentation.
     */
    public static final int LAUNCHER_OPTIONS_INDENT = 45;

    private static final String JDK_MODULES_PREFIX = "jdk.module.";

    private static final String ADD_MODULES = JDK_MODULES_PREFIX + "addmods";
    private static final String ADD_EXPORTS = JDK_MODULES_PREFIX + "addexports";
    private static final String ADD_OPENS = JDK_MODULES_PREFIX + "addopens";
    private static final String ADD_READS = JDK_MODULES_PREFIX + "addreads";
    private static final String ENABLE_NATIVE_ACCESS = JDK_MODULES_PREFIX + "enable.native.access";

    private static final String MODULE_PATH = JDK_MODULES_PREFIX + "path";
    private static final String UPGRADE_PATH = JDK_MODULES_PREFIX + "upgrade.path";
    private static final String LIMIT_MODS = JDK_MODULES_PREFIX + "limitmods";

    private static final String[] KNOWN_MODULE_OPTIONS = {
                    ADD_MODULES,
                    ADD_EXPORTS,
                    ADD_OPENS,
                    ADD_READS,
                    ENABLE_NATIVE_ACCESS,
                    MODULE_PATH,
                    UPGRADE_PATH,
                    LIMIT_MODS,
    };

    private static final PrintStream out = System.out;

    private final Native nativeAccess;
    private final PolyglotArgs polyglotAccess;

    private final Set<String> ignoredXXOptions;
    private final Map<String, String> mappedXXOptions;
    private final Context.Builder builder;

    private boolean experimental;

    private boolean helpVM = false;
    private boolean helpTools = false;
    private boolean helpEngine = false;
    private boolean helpLanguages = false;

    private boolean helpExpert = false;
    private boolean helpInternal = false;

    private boolean showIgnored;

    private List<String> addModules = new ArrayList<>();
    private List<String> addExports = new ArrayList<>();
    private List<String> addOpens = new ArrayList<>();
    private List<String> addReads = new ArrayList<>();
    private List<String> enableNativeAccess = new ArrayList<>();

    @SuppressWarnings("this-escape")
    public ArgumentsHandler(Context.Builder builder, Set<String> ignoredXXOptions, Map<String, String> mappedXXOptions, JNIJavaVMInitArgs args) {
        assert mappedXXOptions.values().stream().allMatch(s -> s.contains("."));
        this.ignoredXXOptions = ignoredXXOptions;
        this.mappedXXOptions = mappedXXOptions;
        this.nativeAccess = new Native(this);
        this.polyglotAccess = new PolyglotArgs(builder, this);
        this.builder = builder;
        parseEarlyArguments(args);
    }

    private void parseEarlyArguments(JNIJavaVMInitArgs args) {
        boolean foundFirstExperimental = false;
        Pointer p = (Pointer) args.getOptions();
        for (int i = 0; i < args.getNOptions(); i++) {
            JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
            CCharPointer str = option.getOptionString();
            if (str.isNonNull()) {
                String optionString = CTypeConversion.toJavaString(option.getOptionString());
                switch (optionString) {
                    case "--experimental-options":
                    case "--experimental-options=true":
                        if (!foundFirstExperimental) {
                            this.experimental = true;
                        }
                        foundFirstExperimental = true;
                        break;
                    case "--experimental-options=false":
                        if (!foundFirstExperimental) {
                            this.experimental = false;
                        }
                        foundFirstExperimental = true;
                        break;
                    case "--log.level=CONFIG":
                    case "--log.level=FINE":
                    case "--log.level=FINER":
                    case "--log.level=FINEST":
                    case "--log.level=ALL":
                        this.showIgnored = true;
                        break;
                    default:
                }
            }
        }
    }

    public boolean isModulesOption(String key) {
        if (key.startsWith(JDK_MODULES_PREFIX)) {
            for (String known : KNOWN_MODULE_OPTIONS) {
                if (key.equals(known)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addModules(String option) {
        addModules.add(option);
    }

    public void addExports(String option) {
        addExports.add(option);
    }

    public void addOpens(String option) {
        addOpens.add(option);
    }

    public void addReads(String option) {
        addReads.add(option);
    }

    public void enableNativeAccess(String option) {
        enableNativeAccess.add(option);
    }

    public void setExperimental(boolean experimental) {
        this.experimental = experimental;
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
        String arg = optionString.substring("-XX:".length());
        String group = "java";
        String name;
        String value;
        if (arg.length() >= 1 && (arg.charAt(0) == '+' || arg.charAt(0) == '-')) {
            name = arg.substring(1);
            value = Boolean.toString(arg.charAt(0) == '+');
        } else {
            int idx = arg.indexOf('=');
            if (idx < 0) {
                name = arg;
                value = "";
            } else {
                name = arg.substring(0, idx);
                value = arg.substring(idx + 1);
            }
        }
        if (ignoredXXOptions.contains(name)) {
            if (showIgnored) {
                warn("Ignoring option: " + optionString);
            }
            return;
        }
        String mapped = mappedXXOptions.get(name);
        if (mapped != null) {
            int idx = mapped.indexOf('.');
            assert idx > 0;
            group = mapped.substring(0, idx);
            name = mapped;
        }
        try {
            parsePolyglotOption(group, name, value, optionString);
            return;
        } catch (Arguments.ArgumentException e) {
            if (e.isExperimental()) {
                throw abort(e.getMessage().replace(arg, optionString));
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

    public void parsePolyglotOption(String group, String key, String value, String arg) {
        polyglotAccess.parsePolyglotOption(group, key, value, arg, experimental);
    }

    public void handleVMOption(String optionString) {
        nativeAccess.init(false);
        nativeAccess.setNativeOption(optionString.substring("--vm.".length()));
    }

    public void argumentProcessingDone() {
        if (!addModules.isEmpty()) {
            builder.option("java.AddModules", String.join(File.pathSeparator, addModules));
        }
        if (!addExports.isEmpty()) {
            builder.option("java.AddExports", String.join(File.pathSeparator, addExports));
        }
        if (!addOpens.isEmpty()) {
            builder.option("java.AddOpens", String.join(File.pathSeparator, addOpens));
        }
        if (!addReads.isEmpty()) {
            builder.option("java.AddReads", String.join(File.pathSeparator, addReads));
        }
        if (!enableNativeAccess.isEmpty()) {
            builder.option("java.EnableNativeAccess", String.join(File.pathSeparator, enableNativeAccess));
        }
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
