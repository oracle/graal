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

    /**
     * An auto-closable that holds a Java {@link CharSequence}[] array as a null-terminated array of
     * null-terminated C char[]s. The C pointers are only valid as long as the auto-closeable has
     * not been closed.
     */
    public static final class CCharPointerPointerHolder implements AutoCloseable {

        private final CTypeConversion.CCharPointerHolder[] ccpHolderArray;
        private final PinnedObject pinnedCCPArray;

        /** Construct a pinned CCharPointers[] from a CharSequence[]. */
        private CCharPointerPointerHolder(CharSequence[] csArray) {
            /* An array to hold the pinned null-terminated C strings. */
            ccpHolderArray = new CTypeConversion.CCharPointerHolder[csArray.length + 1];
            /* An array to hold the &char[0] behind the corresponding C string. */
            final CCharPointer[] ccpArray = new CCharPointer[csArray.length + 1];
            for (int i = 0; i < csArray.length; i += 1) {
                /* Null-terminate and pin each of the CharSequences. */
                ccpHolderArray[i] = CTypeConversion.toCString(csArray[i]);
                /* Save the CCharPointer of each of the CharSequences. */
                ccpArray[i] = ccpHolderArray[i].get();
            }
            /* Null-terminate the CCharPointer[]. */
            ccpArray[csArray.length] = WordFactory.nullPointer();
            /* Pin the CCharPointer[] so I can get the &ccpArray[0]. */
            pinnedCCPArray = PinnedObject.create(ccpArray);
        }

        public CCharPointerPointer get() {
            return pinnedCCPArray.addressOfArrayElement(0);
        }

        @Override
        public void close() {
            /* Close the pins on each of the pinned C strings. */
            for (int i = 0; i < ccpHolderArray.length - 1; i += 1) {
                ccpHolderArray[i].close();
            }
            /* Close the pin on the pinned CCharPointer[]. */
            pinnedCCPArray.close();
        }
    }

    public static CCharPointerPointerHolder toCStrings(CharSequence[] javaStrings) {
        return new CCharPointerPointerHolder(javaStrings);
    }
}
