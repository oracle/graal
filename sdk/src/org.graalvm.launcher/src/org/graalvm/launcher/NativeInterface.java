/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.launcher;

import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

@CContext(NativeInterface.NativeDirectives.class)
class NativeInterface {

    class NativeDirectives implements CContext.Directives {
        @Override
        public List<String> getHeaderFiles() {
            return Arrays.asList("<sys/errno.h>", "<unistd.h>", "<string.h>");
        }

        @Override
        public List<String> getMacroDefinitions() {
            return Arrays.asList("_GNU_SOURCE", "_LARGEFILE64_SOURCE");
        }
    }

    public static int errno() {
        if (Platform.includedIn(Platform.LINUX.class)) {
            return LinuxNativeInterface.errno();
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            return DarwinNativeInterface.errno();
        } else {
            // TODO: this should fail at image generation time
            throw new Error("Unsupported platform: " + ImageSingletons.lookup(Platform.class));
        }
    }

    @CFunction
    public static native CCharPointer strerror(int errnum);

    /** Execute PATH with arguments ARGV and environment from `environ'. */
    @CFunction
    public static native int execv(CCharPointer path, CCharPointerPointer argv);
}
