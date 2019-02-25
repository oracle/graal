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

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

@EspressoSubstitutions
public final class Target_java_lang_Thread {

    static final String HIDDEN_HOST_THREAD = "$$host_thread";
    private static final ConcurrentHashMap<Thread, StaticObject> host2guest = new ConcurrentHashMap<>();

    // TODO(peterssen): Remove single thread shim, support real threads.
    @Substitution
    public static @Host(Thread.class) StaticObject currentThread() {
        EspressoContext context = EspressoLanguage.getCurrentContext();
        if (context.getMainThread() == null) {
            Meta meta = context.getMeta();
            StaticObjectImpl mainThread = (StaticObjectImpl) meta.Thread.allocateInstance();
            StaticObject threadGroup = meta.ThreadGroup.allocateInstance();
            meta.ThreadGroup_maxPriority.set(threadGroup, Thread.MAX_PRIORITY);
            meta.Thread_group.set(mainThread, threadGroup);
            meta.Thread_name.set(mainThread, meta.toGuestString("mainThread"));
            meta.Thread_priority.set(mainThread, 5);

            mainThread.setHiddenField(HIDDEN_HOST_THREAD, Thread.currentThread());
            // host2guest should be in the context
            host2guest.put(Thread.currentThread(), mainThread);

            // Lock object used by NIO.
            meta.Thread_blockerLock.set(mainThread, meta.Object.allocateInstance());
            context.setMainThread(mainThread);
        }
        return host2guest.get(Thread.currentThread());
    }

    @Substitution
    public static void yield() {
        Thread.yield();
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void setPriority0(@Host(Thread.class) StaticObject self, int newPriority) {
        /* nop */
    }

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
        Thread hostThread = EspressoLanguage.getCurrentContext().getEnv().createThread(new Runnable() {
            @Override
            public void run() {
                self.getKlass().lookupMethod(Name.run, Signature._void).invokeDirect(self);
            }
        });

        ((StaticObjectImpl) self).setHiddenField(HIDDEN_HOST_THREAD, hostThread);
        host2guest.put(hostThread, self);

        System.err.println("Starting thread: " + self.getKlass());
        hostThread.setDaemon((boolean) self.getKlass().getMeta().Thread_daemon.get(self));
        hostThread.start();
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static boolean isInterrupted(@Host(Thread.class) StaticObject self, boolean ClearInterrupted) {
        return false;
    }

    @Substitution
    public static boolean holdsLock(@Host(Object.class) StaticObject object) {
        return Thread.holdsLock(object);
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
