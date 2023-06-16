/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot;

final class JVMCIOpenSupport {

    private static final Module truffleRuntimeModule;
    private static final Module jvmciModule;
    private static final boolean truffleAttachLibraryAvailable;
    static {
        truffleRuntimeModule = JVMCIOpenSupport.class.getModule();
        jvmciModule = truffleRuntimeModule.getLayer().findModule("jdk.internal.vm.ci").orElse(null);
        truffleAttachLibraryAvailable = jvmciModule != null && loadTruffleAttachLibrary();
    }

    private JVMCIOpenSupport() {
    }

    static boolean hasOpenJVMCI() {
        return jvmciModule != null && isJVMCIExportedOrOpenedToTruffleRuntime();
    }

    static boolean openJVMCI() {
        if (jvmciModule == null) {
            return false;
        }
        if (isJVMCIExportedOrOpenedToTruffleRuntime()) {
            return true;
        }
        if (truffleAttachLibraryAvailable) {
            truffleRuntimeModule.addReads(jvmciModule);
            openJVMCITo0(truffleRuntimeModule);
            return true;
        }
        return false;
    }

    private static boolean isJVMCIExportedOrOpenedToTruffleRuntime() {
        return jvmciModule.isExported("jdk.vm.ci.hotspot", truffleRuntimeModule);
    }

    private static boolean loadTruffleAttachLibrary() {
        /*
         * Support for unittests. Unittests are passing path to the truffleattach library using
         * truffle.attach.library system property.
         */
        String attachLib = System.getProperty("truffle.attach.library");
        try {
            if (attachLib == null) {
                try {
                    System.loadLibrary("truffleattach");
                } catch (UnsatisfiedLinkError invalidLibrary) {
                    return false;
                }
            } else {
                System.load(attachLib);
            }
            return true;
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    private static native void openJVMCITo0(Module toModule);
}
