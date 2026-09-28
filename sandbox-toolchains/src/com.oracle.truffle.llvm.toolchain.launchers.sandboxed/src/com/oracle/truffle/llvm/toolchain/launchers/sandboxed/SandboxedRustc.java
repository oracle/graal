/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SandboxedRustc extends SandboxedDriver {

    private static final String CLANG_TARGET_TRIPLE_PROPERTY = "org.graalos.targetTriple";
    private static final String RUST_TARGET_TRIPLE_PROPERTY = "org.graalos.rustTargetTriple";
    private static final String CLANG_TARGET = getRequiredProperty(CLANG_TARGET_TRIPLE_PROPERTY);
    private static final String RUST_TARGET = getRequiredProperty(RUST_TARGET_TRIPLE_PROPERTY);

    protected SandboxedRustc() {
        super("rustc");
    }

    protected List<String> getExtendedArgs(String[] args) {
        String target = analyzeTarget(args);
        ArrayList<String> extendedArgList = new ArrayList<>(args.length + 1);
        if (isToolchainTarget(target)) {
            extendedArgList.add("-Clinker=" + getToolchainRoot().resolve("bin").resolve("clang++"));
        } else {
            extendedArgList.add("-Clinker=" + getLLVMRoot().resolve("bin").resolve("clang++"));
        }
        extendedArgList.addAll(List.of(args));
        return extendedArgList;
    }

    private static String getRequiredProperty(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new RuntimeException("missing required property: " + property);
        }
        return value;
    }

    private static boolean isToolchainTarget(String target) {
        return CLANG_TARGET.equals(target) || RUST_TARGET.equals(target);
    }

    private static String analyzeTarget(String[] args) {
        String target = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--target=")) {
                target = arg.substring("--target=".length());
            } else if (arg.equals("--target") && i + 1 < args.length) {
                target = args[i + 1];
            }
        }
        return target;
    }

    public static Path getWrapperPath() {
        return getToolchainRoot().resolve("bin").resolve("rustc");
    }

    protected void run(String[] args) {
        run(getExtendedArgs(args));
    }

    public static void main(String[] args) {
        new SandboxedRustc().run(args);
    }
}
