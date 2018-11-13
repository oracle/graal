/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.jni.JniVersion;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@Surrogate("java.lang.ClassLoader$NativeLibrary")
interface NativeLibrary {
}

@EspressoIntrinsics(NativeLibrary.class)
public class Target_java_lang_ClassLoader_NativeLibrary {

    private static TruffleObject loadLibrary(String lib) {
        try {
            return com.oracle.truffle.espresso.jni.NativeLibrary.loadLibrary(lib);
        } catch (UnsatisfiedLinkError e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(UnsatisfiedLinkError.class, e.getMessage());
        }
    }

    @Intrinsic(hasReceiver = true)
    public static void load(StaticObject self, @Type(String.class) StaticObject name, boolean isBuiltin) {
        String hostName = Meta.toHost(name);
        TruffleObject lib = null;
        try {
            lib = loadLibrary(hostName);
        } catch (UnsatisfiedLinkError e) {
            meta(self).field("handle").set(0L);
            throw meta(self).getMeta().throwEx(UnsatisfiedLinkError.class);
        }
        long handle = EspressoLanguage.getCurrentContext().addNativeLibrary(lib);
        // TODO(peterssen): Should call JNI_OnLoad, if it exists and get the JNI version, check if
        // compatible. Setting the default version as a workaround.
        meta(self).field("jniVersion").set(JniVersion.JNI_VERSION_ESPRESSO);
        meta(self).field("handle").set(handle);
        meta(self).field("loaded").set(true);
    }

    @Intrinsic(hasReceiver = true)
    public static long find(StaticObject self, @Type(String.class) StaticObject name) {
        long libHandle = (long) meta(self).field("handle").get();
        if (libHandle != 0) {
            TruffleObject library = self.getKlass().getContext().getNativeLibraries().get(libHandle);
            assert library != null;
            try {
                ForeignAccess.sendRead(Message.READ.createNode(), library, Meta.toHost(name));
                System.err.println("Found " + Meta.toHost(name) + " in " + libHandle);
                return libHandle;
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                return 0;
            }
        }
        return 0;
    }
}
