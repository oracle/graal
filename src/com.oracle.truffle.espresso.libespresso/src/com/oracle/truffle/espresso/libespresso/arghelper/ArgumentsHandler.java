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

import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.polyglot.Context;
import org.graalvm.word.Pointer;

import com.oracle.truffle.espresso.libespresso.Arguments;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMOption;

public class ArgumentsHandler {
    private final Context.Builder builder;

    private final Native nativeAccess = new Native();
    private final PolyglotArgs polyglotAccess;
    private final ModulePropertyCounter modulePropertyCounter;

    private final boolean experimental;

    public ArgumentsHandler(Context.Builder builder, JNIJavaVMInitArgs args) {
        this.builder = builder;
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
        polyglotAccess.argumentProcessingDone();
    }
}
