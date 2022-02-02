/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.threads.State;
import com.oracle.truffle.espresso.threads.ThreadsAccess;

// @formatter:off
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
@EspressoSubstitutions
public final class Target_java_lang_Thread {

    public static void incrementThreadCounter(StaticObject thread, Field hiddenField) {
        assert hiddenField.isHidden();
        AtomicLong atomicCounter = (AtomicLong) hiddenField.getHiddenObject(thread);
        if (atomicCounter == null) {
            hiddenField.setHiddenObject(thread, atomicCounter = new AtomicLong());
        }
        atomicCounter.incrementAndGet();
    }

    public static long getThreadCounter(StaticObject thread, Field hiddenField) {
        assert hiddenField.isHidden();
        AtomicLong atomicCounter = (AtomicLong) hiddenField.getHiddenObject(thread);
        if (atomicCounter == null) {
            return 0L;
        }
        return atomicCounter.get();
    }

    @Substitution(isTrivial = true)
    public static @JavaType(Thread.class) StaticObject currentThread(@Inject EspressoContext context) {
        return context.getCurrentThread();
    }

    @Substitution
    public static @JavaType(Thread[].class) StaticObject getThreads(@Inject Meta meta) {
        return StaticObject.createArray(meta.java_lang_Thread.array(), meta.getContext().getActiveThreads());
    }

    @Substitution
    public static @JavaType(StackTraceElement[][].class) StaticObject dumpThreads(@JavaType(Thread[].class) StaticObject threads, @Inject Meta meta) {
        if (StaticObject.isNull(threads)) {
            throw meta.throwNullPointerException();
        }
        if (threads.length() == 0) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        StaticObject trace = StaticObject.createArray(meta.java_lang_StackTraceElement.array(), StaticObject.EMPTY_ARRAY);
        StaticObject[] toWrap = new StaticObject[threads.length()];
        Arrays.fill(toWrap, trace);
        return StaticObject.createArray(meta.java_lang_StackTraceElement.array().array(), toWrap);
    }

    @Substitution(hasReceiver = true)
    abstract static class Start0 extends SubstitutionNode {
        abstract void execute(@JavaType(Thread.class) StaticObject self);

        @Specialization
        @TruffleBoundary
        void doCached(@JavaType(Thread.class) StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().java_lang_Thread_exit.getCallTarget())") DirectCallNode threadExit,
                        @Cached("create(context.getMeta().java_lang_Thread_dispatchUncaughtException.getCallTarget())") DirectCallNode dispatchUncaught) {
            Meta meta = context.getMeta();
            ThreadsAccess threadAccess = context.getThreadAccess();
            if (context.multiThreadingEnabled()) {
                // Thread.start() is synchronized.
                if (threadAccess.terminateIfStillborn(self)) {
                    return;
                }
                Thread hostThread = threadAccess.createJavaThread(self, threadExit, dispatchUncaught);
                context.getLogger().fine(() -> {
                    String guestName = threadAccess.getThreadName(self);
                    long guestId = threadAccess.getThreadId(self);
                    return String.format("Thread.start0: [HOST:%s, %d], [GUEST:%s, %d]", hostThread.getName(), hostThread.getId(), guestName, guestId);
                });
                hostThread.start();
            } else {
                String reason = context.getMultiThreadingDisabledReason();
                Klass threadKlass = self.getKlass();
                EspressoContext.get(null).getLogger().warning(() -> {
                    String guestName = threadAccess.getThreadName(self);
                    String className = threadKlass.getExternalName();
                    return "Thread.start() called on " + className + " / " + guestName + " but thread support is disabled: " + reason;
                });
                if (threadKlass == meta.java_lang_ref_Finalizer$FinalizerThread || threadKlass == meta.java_lang_ref_Reference$ReferenceHandler || isSystemInnocuousThread(self, meta)) {
                    // no exception: bootstrap code cannot recover from this
                } else {
                    meta.throwExceptionWithMessage(meta.java_lang_OutOfMemoryError, "Thread support is disabled: " + reason);
                }
            }
        }
    }

    private static boolean isSystemInnocuousThread(StaticObject thread, Meta meta) {
        if (!meta.misc_InnocuousThread.isAssignableFrom(thread.getKlass())) {
            return false;
        }
        if (!StaticObject.isNull(meta.java_lang_Thread_contextClassLoader.getObject(thread))) {
            return false;
        }
        return true;
    }

    @TruffleBoundary
    @Substitution(isTrivial = true)
    public static void yield() {
        Thread.yield();
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void setPriority0(@JavaType(Thread.class) StaticObject self, int newPriority,
                    @Inject EspressoContext context) {
        // Priority is set in the guest field in Thread.setPriority().
        Thread hostThread = context.getThreadAccess().getHost(self);
        if (hostThread == null) {
            return;
        }
        hostThread.setPriority(newPriority);
    }

    @Substitution(hasReceiver = true)
    public static boolean isAlive(@JavaType(Thread.class) StaticObject self,
                    @Inject EspressoContext context) {
        return context.getThreadAccess().isAlive(self);
    }

    @Substitution(hasReceiver = true)
    abstract static class GetState extends SubstitutionNode {
        abstract @JavaType(internalName = "Ljava/lang/Thread$State;") StaticObject execute(@JavaType(Thread.class) StaticObject self);

        @Specialization
        @JavaType(internalName = "Ljava/lang/Thread$State;")
        StaticObject execute(@JavaType(Thread.class) StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().sun_misc_VM_toThreadState.getCallTarget())") DirectCallNode toThreadState) {
            Meta meta = context.getMeta();
            return (StaticObject) toThreadState.call(meta.java_lang_Thread_threadStatus.getInt(self));
        }
    }

    @SuppressWarnings("unused")
    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @TruffleBoundary
    @Substitution
    public static boolean holdsLock(@JavaType(Object.class) StaticObject object, @Inject Meta meta) {
        if (StaticObject.isNull(object)) {
            throw meta.throwNullPointerException();
        }
        return object.getLock().isHeldByCurrentThread();
    }

    @TruffleBoundary
    @Substitution
    public static void sleep(long millis, @Inject Meta meta) {
        StaticObject thread = meta.getContext().getCurrentThread();
        try {
            meta.getThreadAccess().fromRunnable(thread, State.TIMED_WAITING);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            if (meta.getThreadAccess().isInterrupted(thread, true)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_InterruptedException, e.getMessage());
            }
            meta.getThreadAccess().checkDeprecation();
        } catch (IllegalArgumentException e) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, e.getMessage());
        } finally {
            meta.getThreadAccess().toRunnable(thread);
        }
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void interrupt0(@JavaType(Object.class) StaticObject self,
                    @Inject EspressoContext context) {
        context.getThreadAccess().interrupt(self);
    }

    @Substitution
    public static void clearInterruptEvent(@Inject EspressoContext context) {
        context.getThreadAccess().clearInterruptEvent();
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true, versionFilter = VersionFilter.Java13OrEarlier.class)
    public static boolean isInterrupted(@JavaType(Thread.class) StaticObject self, boolean clear,
                    @Inject EspressoContext context) {
        return context.getThreadAccess().isInterrupted(self, clear);
    }

    @TruffleBoundary
    @SuppressWarnings({"unused"})
    @Substitution(hasReceiver = true)
    public static void resume0(@JavaType(Object.class) StaticObject self,
                    @Inject EspressoContext context) {
        context.getThreadAccess().resume(self);
    }

    @TruffleBoundary
    @SuppressWarnings({"unused"})
    @Substitution(hasReceiver = true)
    public static void suspend0(@JavaType(Object.class) StaticObject toSuspend,
                    @Inject EspressoContext context) {
        context.invalidateNoSuspend("Calling Thread.suspend()");
        context.getThreadAccess().suspend(toSuspend);
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void stop0(@JavaType(Object.class) StaticObject self, @JavaType(Object.class) StaticObject throwable,
                    @Inject EspressoContext context) {
        context.invalidateNoThreadStop("Calling thread.stop()");
        context.getThreadAccess().stop(self, throwable);
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void setNativeName(@JavaType(Object.class) StaticObject self, @JavaType(String.class) StaticObject name,
                    @Inject Meta meta) {
        Thread hostThread = meta.getThreadAccess().getHost(self);
        hostThread.setName(meta.toHostString(name));
    }
}
