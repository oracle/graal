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

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_java_lang_Thread {

    // TODO(peterssen): Remove single thread shim, support real threads.
    @Intrinsic
    public static @Type(Thread.class) StaticObject currentThread() {
        EspressoContext context = EspressoLanguage.getCurrentContext();
        if (context.getMainThread() == null) {
            Meta meta = context.getMeta();
            Meta.Klass threadGroupKlass = meta.knownKlass(ThreadGroup.class);
            Meta.Klass threadKlass = meta.knownKlass(Thread.class);
            StaticObject mainThread = threadKlass.metaNew().fields(
                            Meta.Field.set("priority", 5),
                            Meta.Field.set("name", meta.toGuest("mainThread")),
                            Meta.Field.set("group", threadGroupKlass.allocateInstance())).getInstance();
            context.setMainThread(mainThread);
        }
        return context.getMainThread();
    }

    @Intrinsic(hasReceiver = true)
    public static void setPriority0(@Type(Thread.class) StaticObject self, int newPriority) {
        /* nop */ }

    @Intrinsic(hasReceiver = true)
    public static void setDaemon(@Type(Thread.class) StaticObject self, boolean on) {
        /* nop */ }

    @Intrinsic(hasReceiver = true)
    public static boolean isAlive(@Type(Thread.class) StaticObject self) {
        return false;
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */ }

    @Intrinsic(hasReceiver = true)
    public static void start0(@Type(Thread.class) StaticObject self) {
        /* nop */ }

    @Intrinsic(hasReceiver = true)
    public static boolean isInterrupted(@Type(Thread.class) StaticObject self, boolean ClearInterrupted) {
        return false;
    }

    @Intrinsic
    public static boolean holdsLock(Object object) {
        return Thread.holdsLock(object);
    }

    @Intrinsic
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Meta meta = EspressoLanguage.getCurrentContext().getMeta();
            throw meta.throwEx(InterruptedException.class);
        }
    }
}
