/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

// @formatter:off
// Checkstyle: stop
/**
 * Thread state manipulation:
 *
 * public static State toThreadState(int var0) {
 *         if ((var0 & 4) != 0) {
 *             return State.RUNNABLE;
 *         } else if ((var0 & 1024) != 0) {
 *             return State.BLOCKED;
 *         } else if ((var0 & 16) != 0) {
 *             return State.WAITING;
 *         } else if ((var0 & 32) != 0) {
 *             return State.TIMED_WAITING;
 *         } else if ((var0 & 2) != 0) {
 *             return State.TERMINATED;
 *         } else {
 *             return (var0 & 1) == 0 ? State.NEW : State.RUNNABLE;
 *         }
 *     }
 */
// @formatter:on
// Checkstyle: resume

@EspressoSubstitutions
public final class Target_java_lang_Thread {

    private static int stateToInt(Thread.State state) {
        switch (state) {
            case NEW:
                return 0;
            case RUNNABLE:
                return 4;
            case BLOCKED:
                return 1024;
            case WAITING:
                return 16;
            case TIMED_WAITING:
                return 32;
            case TERMINATED:
                return 2;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    public enum State {
        NEW(0),
        RUNNABLE(4),
        BLOCKED(1024),
        WAITING(16),
        TIMED_WAITING(32),
        TERMINATED(2);

        public final int value;

        State(int value) {
            this.value = value;
        }

    }

    @Substitution
    public static @Host(Thread.class) StaticObject currentThread() {
        return EspressoLanguage.getCurrentContext().getHost2Guest(Thread.currentThread());
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void start0(@Host(Thread.class) StaticObject self) {
        if (EspressoOptions.ENABLE_THREADS) {
            // Thread.start() is synchronized.
            EspressoContext context = self.getKlass().getContext();
            Meta meta = context.getMeta();
            KillStatus killStatus = getKillStatus(self);
            if (killStatus != null || context.isClosing()) {
                return;
            }
            setThreadStop(self, KillStatus.NORMAL);
            Thread hostThread = context.getEnv().createThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Execute the payload
                        self.getKlass().vtableLookup(meta.Thread_run.getVTableIndex()).invokeDirect(self);
                    } catch (EspressoException uncaught) {
                        meta.Thread_dispatchUncaughtException.invokeDirect(self, uncaught.getException());
                    } finally {
                        self.setIntField(meta.Thread_threadStatus, State.TERMINATED.value);
                        setThreadStop(self, KillStatus.EXITING);
                        meta.Thread_exit.invokeDirect(self);
                        synchronized (self) {
                            // Notify waiting threads you are done working
                            self.notifyAll();
                        }
                        // Cleanup.
                        context.unregisterThread(self);
                        if (context.isClosing()) {
                            // Ignore exceptions that arise during closing.
                            return;
                        }
                    }
                }
            });

            self.setHiddenField(meta.HIDDEN_HOST_THREAD, hostThread);
            context.putHost2Guest(hostThread, self);
            context.registerThread(self);
            hostThread.setDaemon(self.getBooleanField(meta.Thread_daemon));
            self.setIntField(meta.Thread_threadStatus, State.RUNNABLE.value);
            hostThread.start();
        } else {
            System.err.println(
                            "Thread.start() called on " + self.getKlass() + " but thread support is disabled. Use -Despresso.EnableThreads=true to enable thread support.");
        }
    }

    @Substitution
    public static void yield() {
        Thread.yield();
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void setPriority0(@Host(Thread.class) StaticObject self, int newPriority) {
        Thread hostThread = getHostFromGuestThread(self);
        if (hostThread == null) {
            return;
        }
        hostThread.setPriority(newPriority);
    }

    @Substitution(hasReceiver = true)
    public static boolean isAlive(@Host(Thread.class) StaticObject self) {
        int state = self.getIntField(self.getKlass().getMeta().Thread_threadStatus);
        return state != State.NEW.value && state != State.TERMINATED.value;
    }

    @Substitution(hasReceiver = true)
    public static @Host(typeName = "Ljava/lang/Thread$State;") StaticObject getState(@Host(Thread.class) StaticObject self) {
        Thread hostThread = getHostFromGuestThread(self);
        // If hostThread is null, start hasn't been called yet -> NEW state.
        return (StaticObject) self.getKlass().getMeta().VM_toThreadState.invokeDirect(null, hostThread == null ? State.NEW.value : (hostThread.getState()));
    }

    @SuppressWarnings("unused")
    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution
    public static boolean holdsLock(@Host(Object.class) StaticObject object) {
        if (StaticObject.isNull(object)) {
            Meta meta = EspressoLanguage.getCurrentContext().getMeta();
            throw meta.throwEx(meta.NullPointerException);
        }
        return Thread.holdsLock(object);
    }

    @TruffleBoundary
    @Substitution
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException | IllegalArgumentException e) {
            Meta meta = EspressoLanguage.getCurrentContext().getMeta();
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    private static void setInterrupt(StaticObject self, boolean value) {
        self.setHiddenField(self.getKlass().getMeta().HIDDEN_INTERRUPTED, value);
    }

    private static boolean checkInterrupt(StaticObject self) {
        Boolean interrupt = (Boolean) self.getHiddenField(self.getKlass().getMeta().HIDDEN_INTERRUPTED);
        return interrupt != null && interrupt;
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void interrupt0(@Host(Object.class) StaticObject self) {
        Thread hostThread = getHostFromGuestThread(self);
        if (hostThread == null) {
            return;
        }
        setInterrupt(self, true);
        hostThread.interrupt();
    }

    @Substitution(hasReceiver = true)
    public static boolean isInterrupted(@Host(Thread.class) StaticObject self, boolean clear) {
        Thread hostThread = getHostFromGuestThread(self);
        if (hostThread == null) {
            return false;
        }
        boolean result = checkInterrupt(self);
        if (clear) {
            setInterrupt(self, false);
        }
        return result;
    }

    @TruffleBoundary
    @SuppressWarnings({"unused", "deprecation"})
    @Substitution(hasReceiver = true)
    public static void resume0(@Host(Object.class) StaticObject self) {
        if (EspressoOptions.RUNNING_ON_SVM) {
            /* nop */
        } else {
            Thread hostThread = getHostFromGuestThread(self);
            if (hostThread == null) {
                return;
            }
            hostThread.resume();
        }
    }

    @TruffleBoundary
    @SuppressWarnings({"unused", "deprecation"})
    @Substitution(hasReceiver = true)
    public static void suspend0(@Host(Object.class) StaticObject self) {
        if (EspressoOptions.RUNNING_ON_SVM) {
            /* nop */
        } else {
            Thread hostThread = getHostFromGuestThread(self);
            if (hostThread == null) {
                return;
            }
            hostThread.suspend();
        }
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static void stop0(@Host(Object.class) StaticObject self, Object unused) {
        Thread hostThread = getHostFromGuestThread(self);
        if (hostThread == null) {
            return;
        }
        self.getKlass().getContext().invalidateNoThreadStop("Calling thread.stop()");
        killThread(self);
        setInterrupt(self, true);
        hostThread.interrupt();
    }

    @Substitution(hasReceiver = true)
    public static void setNativeName(@Host(Object.class) StaticObject self, @Host(String.class) StaticObject name) {
        Thread hostThread = getHostFromGuestThread(self);
        hostThread.setName(Meta.toHostString(name));
    }

    public static Thread getHostFromGuestThread(@Host(Object.class) StaticObject self) {
        return (Thread) self.getHiddenField(self.getKlass().getMeta().HIDDEN_HOST_THREAD);
    }

    public static boolean checkThreadStatus(StaticObject thread, KillStatus status) {
        KillStatus stop = (KillStatus) thread.getHiddenField(thread.getKlass().getMeta().HIDDEN_DEATH);
        return stop != null && stop == status;
    }

    public static void setThreadStop(StaticObject thread, KillStatus value) {
        thread.setHiddenField(thread.getKlass().getMeta().HIDDEN_DEATH, value);
    }

    public static void killThread(StaticObject thread) {
        thread.setHiddenField(thread.getKlass().getMeta().HIDDEN_DEATH, KillStatus.KILL);
    }

    public static KillStatus getKillStatus(StaticObject thread) {
        return (KillStatus) thread.getHiddenField(thread.getKlass().getMeta().HIDDEN_DEATH);
    }

    public enum KillStatus {
        // Normal state: no Thread.stop() called, or ThreadDeath has already been thrown.
        NORMAL,
        // Thread will be thrown an asynchronous ThreadDeath whenever possible.
        KILL,
        // Was killed, but we are in context closing. If the thread is alive for a while in that
        // state, it will be considered uncooperative.
        KILLED,
        // Was killed, and is calling Thread.exit(). Ignore further kill signals.
        EXITING,
        // Thread is uncooperative: needs to be killed with a host exception. Very dangerous state
        // to be in.
        DISSIDENT
    }

}
