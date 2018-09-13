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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_io_FileInputStream {
    @Intrinsic
    public static void initIDs() {
        /* nop */
    }

    private static AtomicInteger fdCount = new AtomicInteger(3);
    private static ConcurrentHashMap<Integer, FileDescriptor> fdMap = new ConcurrentHashMap<>();

    @Intrinsic(hasReceiver = true)
    public static void open0(StaticObject self, @Type(String.class) StaticObject name) {
        try {
            FileInputStream fis = new FileInputStream(Meta.toHost(name));
            FileDescriptor fd = fis.getFD();
            int fakeFd = fdCount.incrementAndGet();
            fdMap.put(fakeFd, fd);
            meta((StaticObject) meta(self).field("fd").get()).field("fd").set(fakeFd);
        } catch (IOException e) {
            throw meta(self).getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic(hasReceiver = true)
    public static int readBytes(StaticObject self, byte b[], int off, int len) {
        // throws IOException;
        int fakeFd = getFileDescriptor(self);
        try {
            // read from stdin
            if (fakeFd == 0) {
                return Utils.getContext().in().read(b, off, len);
            }
            FileDescriptor fd = fdMap.get(fakeFd);
            try (FileInputStream fis = new FileInputStream(fd)) {
                return fis.read(b, off, len);
            }
        } catch (IOException e) {
            throw meta(self).getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic(hasReceiver = true)
    public static int available0(StaticObject self) {
        // throws IOException;
        int fakeFd = getFileDescriptor(self);
        try {
            // read from stdin
            if (fakeFd == 0) {
                return Utils.getContext().in().available();
            }
            FileDescriptor fd = fdMap.get(fakeFd);
            try (FileInputStream fis = new FileInputStream(fd)) {
                return fis.available();
            }
        } catch (IOException e) {
            throw meta(self).getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic(hasReceiver = true)
    public static void close0(StaticObject self) {
        // throws IOException;
        int fakeFd = getFileDescriptor(self);
        try {
            // read from stdin
            if (fakeFd == 0) {
                Utils.getContext().in().close();
            }
            // FileDescriptor fd = fdMap.get(fakeFd);
            // try (FileInputStream fis = new FileInputStream(fd)) {
            // fis.close();
            // }
        } catch (IOException e) {
            throw meta(self).getMeta().throwEx(e.getClass());
        }
    }

    private static int getFileDescriptor(StaticObject self) {
        return (int) meta((StaticObject) meta(self).field("fd").get()).field("fd").get();
    }
}
