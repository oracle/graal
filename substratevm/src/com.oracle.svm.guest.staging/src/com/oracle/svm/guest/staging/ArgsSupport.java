/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

/**
 * Support for platform-specific conversion of the command line to Java main arguments. This
 * singleton is also used to store the initial Java args that have been passed to create the current
 * VM.
 */
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class ArgsSupport {
    /**
     * Returns the registered argument support implementation for the current image.
     */
    public static ArgsSupport singleton() {
        return ImageSingletons.lookup(ArgsSupport.class);
    }

    /**
     * Original Java arguments supplied when the current VM was created. These are captured before the
     * runtime option parser consumes VM options, so callers can reconstruct input arguments that are
     * absent from the application main argument array.
     */
    private String[] initialArgs;

    /**
     * Records the original Java arguments used to create the current VM.
     */
    public void setInitialArgs(String[] initialArgs) {
        VMError.guarantee(this.initialArgs == null, "The initial Java args this VM was started with, can only be set once.");
        this.initialArgs = initialArgs;
    }

    /**
     * Returns the original Java arguments used to create the current VM, or {@code null} if they have
     * not been captured.
     */
    public String[] getInitialArgs() {
        return initialArgs;
    }

    /**
     * Convert C-style to Java-style command line arguments. The first C-style argument, which is
     * always the executable file name, is ignored.
     *
     * @param argc the number of arguments in the {@code argv} array.
     * @param argv a C {@code char**}.
     *
     * @return the command line argument strings in a Java string array.
     */
    public static String[] convertCToJavaArgs(int argc, CCharPointerPointer argv) {
        String[] args = new String[argc - 1];
        for (int i = 1; i < argc; ++i) {
            args[i - 1] = singleton().toJavaArg(argv.read(i));
        }
        return args;
    }

    /** Converts a single argv element to a Java string. */
    protected String toJavaArg(CCharPointer rawArg) {
        return CTypeConversion.toJavaString(rawArg);
    }
}
