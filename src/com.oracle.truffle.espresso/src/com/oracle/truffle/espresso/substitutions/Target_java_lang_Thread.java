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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public final class Target_java_lang_Thread {

    // TODO(peterssen): Remove single thread shim, support real threads.
    @Substitution
    public static @Host(Thread.class) StaticObject currentThread() {
        EspressoContext context = EspressoLanguage.getCurrentContext();
        if (context.getMainThread() == null) {
            Meta meta = context.getMeta();
            StaticObject mainThread = meta.Thread.allocateInstance();
            meta.Thread_group.set(mainThread, meta.ThreadGroup.allocateInstance());
            meta.Thread_name.set(mainThread, meta.toGuestString("mainThread"));
            meta.Thread_priority.set(mainThread, 5);

            // Lock object used by NIO.
            meta.Thread_blockerLock.set(mainThread, meta.Object.allocateInstance());
            context.setMainThread(mainThread);
        }
        return context.getMainThread();
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void setPriority0(@Host(Thread.class) StaticObject self, int newPriority) {
        /* nop */ }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void setDaemon(@Host(Thread.class) StaticObject self, boolean on) {
        /* nop */ }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static boolean isAlive(@Host(Thread.class) StaticObject self) {
        return false;
    }

    @SuppressWarnings("unused")
    @Substitution
    public static void registerNatives() {
        /* nop */ }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void start0(@Host(Thread.class) StaticObject self) {
        /* nop */
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static boolean isInterrupted(@Host(Thread.class) StaticObject self, boolean ClearInterrupted) {
        return false;
    }

    @Substitution
    public static boolean holdsLock(Object object) {
        if (!EspressoOptions.RUNNING_ON_SVM) {
            // Sane behavior on HotSpot.
            return Thread.holdsLock(object);
        }
        // TODO(peterssen): On SVM we incorrectly hold all locks since this method is usually used
        // to ensure that locks are hold.
        return true;
    }

    @Substitution
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException | IllegalArgumentException e) {
            Meta meta = EspressoLanguage.getCurrentContext().getMeta();
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }
}
