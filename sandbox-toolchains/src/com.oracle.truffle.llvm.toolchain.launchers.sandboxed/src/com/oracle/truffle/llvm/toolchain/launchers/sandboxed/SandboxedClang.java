/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.llvm.toolchain.launchers.sandboxed;

import java.util.ArrayList;
import java.util.List;

public class SandboxedClang extends SandboxedDriver {

    private static final String TARGET_TRIPLE_PROPERTY = "org.graalos.targetTriple";
    private static final String DEFAULT_TARGET;

    static {
        String targetTriple = System.getProperty(TARGET_TRIPLE_PROPERTY);
        if (targetTriple == null || targetTriple.isBlank()) {
            throw new RuntimeException("missing required property: " + TARGET_TRIPLE_PROPERTY);
        }
        DEFAULT_TARGET = targetTriple;
    }

    protected SandboxedClang(String exe) {
        super(exe);
    }

    protected List<String> getExtendedArgs(String[] args) {
        String target = getTarget(args);
        ArrayList<String> extendedArgList = new ArrayList<>(args.length + 4);

        /*
         * Just give clang the paths to our sysroot, and tell it to target musl. It will figure out
         * all the include and library paths on its own.
         */
        if (target == null) {
            target = DEFAULT_TARGET;
            extendedArgList.add("--target=" + target);
        }
        extendedArgList.add("-B" + getToolchainBin());
        extendedArgList.add("--sysroot=" + getSysroot(target));

        // original arguments from user
        extendedArgList.addAll(List.of(args));

        return extendedArgList;
    }

    protected static String getDefaultTargetTriple() {
        return DEFAULT_TARGET;
    }

    private static String getTarget(String[] args) {
        String target = null;
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.startsWith("--target=")) {
                target = arg.substring("--target=".length());
            } else if (arg.equals("-target") && i < args.length) {
                target = args[i++];
            }
        }
        return target;
    }

    protected final void run(String[] args) {
        run(getExtendedArgs(args));
    }

    public static void main(String[] args) {
        new SandboxedClang("clang").run(args);
    }

    public static void mainxx(String[] args) {
        new SandboxedClang("clang++").run(args);
    }
}
