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
package com.oracle.truffle.espresso.substitutions.standard;

import static com.oracle.truffle.espresso.substitutions.SubstitutionFlag.IsTrivial;
import static com.oracle.truffle.espresso.threads.EspressoThreadRegistry.getThreadId;
import static com.oracle.truffle.espresso.threads.ThreadState.TIMED_SLEEPING;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.blocking.GuestInterruptedException;
import com.oracle.truffle.espresso.blocking.ThreadRequests;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.VersionFilter;
import com.oracle.truffle.espresso.threads.ThreadAccess;
import com.oracle.truffle.espresso.threads.Transition;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

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

    @Substitution(flags = {IsTrivial})
    public static @JavaType(Thread.class) StaticObject currentThread(@Inject EspressoLanguage language) {
        return language.getCurrentVirtualThread();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java19OrLater.class)
    public static void setCurrentThread(@JavaType(Thread.class) StaticObject self, @JavaType(Thread.class) StaticObject thread, @Inject EspressoLanguage language) {
        assert self == EspressoContext.get(null).getCurrentPlatformThread();
        assert self == thread; // real virtual threads are not supported yet.
        language.setCurrentVirtualThread(thread);
    }

    @Substitution
    public static @JavaType(Thread[].class) StaticObject getThreads(@Inject EspressoContext context) {
        return context.getVM().JVM_GetAllThreads(null);
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
            ThreadAccess threadAccess = context.getThreadAccess();
            if (context.multiThreadingEnabled()) {
                // Thread.start() is synchronized.
                if (threadAccess.terminateIfStillborn(self)) {
                    return;
                }
                Thread hostThread = threadAccess.createJavaThread(self, threadExit, dispatchUncaught);
                context.getLogger().fine(() -> {
                    String guestName = threadAccess.getThreadName(self);
                    long guestId = threadAccess.getThreadId(self);
                    return String.format("Thread.start0: [HOST:%s, %d], [GUEST:%s, %d]", hostThread.getName(), getThreadId(hostThread), guestName, guestId);
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
                Meta meta = context.getMeta();
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
    @Substitution(flags = {IsTrivial}, languageFilter = VersionFilter.Java18OrEarlier.class)
    public static void yield() {
        Thread.yield();
    }

    @TruffleBoundary
    @Substitution(flags = {IsTrivial})
    public static void yield0() {
        Thread.yield();
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    @TruffleBoundary // Host Thread.setPriority inlines too deeply.
    public static void setPriority0(@JavaType(Thread.class) StaticObject self, int newPriority,
                    @Inject EspressoContext context) {
        // Priority is set in the guest field in Thread.setPriority().
        Thread hostThread = context.getThreadAccess().getHost(self);
        if (hostThread == null) {
            return;
        }
        hostThread.setPriority(newPriority);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java18OrEarlier.class)
    public static boolean isAlive(@JavaType(Thread.class) StaticObject self,
                    @Inject EspressoContext context) {
        return context.getThreadAccess().isAlive(self);
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
        return object.getLock(meta.getContext()).isHeldByCurrentThread();
    }

    @TruffleBoundary
    @Substitution(languageFilter = VersionFilter.Java18OrEarlier.class)
    public static void sleep(long amount, @Inject Meta meta, @Inject SubstitutionProfiler location) {
        sleep0(amount, meta, location);
    }

    @TruffleBoundary
    @Substitution(languageFilter = VersionFilter.Java19OrLater.class)
    @SuppressWarnings("try")
    public static void sleep0(long amount, @Inject Meta meta, @Inject SubstitutionProfiler location) {
        if (amount < 0) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "timeout value is negative");
        }
        TimeUnit unit;
        if (meta.getJavaVersion().java21OrLater()) {
            unit = TimeUnit.NANOSECONDS;
        } else {
            unit = TimeUnit.MILLISECONDS;
        }
        StaticObject thread = meta.getContext().getCurrentPlatformThread();
        Transition transition = Transition.transition(TIMED_SLEEPING, location);
        try {
            meta.getContext().getBlockingSupport().sleep(unit.toNanos(amount), location);
        } catch (GuestInterruptedException e) {
            if (meta.getThreadAccess().isInterrupted(thread, true)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_InterruptedException, e.getMessage());
            }
            meta.getThreadAccess().checkDeprecatedThreadStatus(thread);
        } catch (IllegalArgumentException e) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, e.getMessage());
        } finally {
            transition.restore(location);
        }
    }

    @Substitution
    public static void sleepNanos0(long amount, @Inject Meta meta, @Inject SubstitutionProfiler location) {
        sleep0(amount, meta, location);
    }

    @Substitution
    public static long getNextThreadIdOffset() {
        // value should never be used, because we substitute ThreadIdentifiers::next
        return 0x13371337;
    }

    @Substitution
    public static @JavaType(Thread.class) StaticObject currentCarrierThread(@Inject EspressoContext context) {
        return context.getCurrentPlatformThread();
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
    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java13OrEarlier.class)
    public static boolean isInterrupted(@JavaType(Thread.class) StaticObject self, boolean clear,
                    @Inject EspressoContext context) {
        return context.getThreadAccess().isInterrupted(self, clear);
    }

    @TruffleBoundary
    @SuppressWarnings({"unused"})
    @Substitution(hasReceiver = true)
    public static void resume0(@JavaType(Thread.class) StaticObject self,
                    @Inject EspressoContext context) {
        context.getThreadAccess().resume(self);
    }

    @TruffleBoundary
    @SuppressWarnings({"unused"})
    @Substitution(hasReceiver = true)
    public static void suspend0(@JavaType(Thread.class) StaticObject self,
                    @Inject EspressoContext context) {
        context.getThreadAccess().suspend(self);
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void stop0(@JavaType(Thread.class) StaticObject self, @JavaType(Object.class) StaticObject throwable,
                    @Inject EspressoContext context) {
        context.getThreadAccess().stop(self, throwable);
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void setNativeName(@JavaType(Thread.class) StaticObject self, @JavaType(String.class) StaticObject name,
                    @Inject Meta meta) {
        Thread hostThread = meta.getThreadAccess().getHost(self);
        if (hostThread == null) {
            return;
        }
        hostThread.setName(meta.toHostString(name));
    }

    // Return an array of stack traces (arrays of stack trace elements), one for each thread in the
    // threads array, or NULL for threads that were unresponsive.
    @Substitution
    public static @JavaType(StackTraceElement[][].class) StaticObject dumpThreads(@JavaType(Thread[].class) StaticObject threadsArray,
                    @Inject EspressoLanguage language, @Inject Meta meta, @Inject EspressoContext context, @Inject SubstitutionProfiler location) {
        if (StaticObject.isNull(threadsArray)) {
            throw meta.throwNullPointerException();
        }
        if (threadsArray.length(language) == 0) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        VM.StackTrace[] traces = ThreadRequests.getStackTraces(context, InterpreterToVM.MAX_STACK_DEPTH, location, threadsArray.unwrap(language));
        return meta.java_lang_StackTraceElement.array().allocateReferenceArray(traces.length,
                        i -> traces[i] == null ? StaticObject.NULL : traces[i].toGuest(context));
    }

    @Substitution(languageFilter = VersionFilter.Java19OrLater.class, hasReceiver = true)
    abstract static class GetStackTrace0 extends SubstitutionNode {
        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Thread.class) StaticObject self);

        @Specialization
        public @JavaType(Object.class) StaticObject doGetStackTrace(@JavaType(Thread.class) StaticObject self) {
            // JVM_GetStackTrace
            EspressoContext context = EspressoContext.get(this);
            return getStackTrace(self, InterpreterToVM.MAX_STACK_DEPTH, context, this);
        }
    }

    public static StaticObject getStackTrace(StaticObject thread, int maxDepth, EspressoContext context, Node node) {
        Thread hostThread = context.getThreadAccess().getHost(thread);
        if (hostThread == null) {
            return StaticObject.NULL;
        }
        VM.StackTrace stackTrace;
        if (hostThread == Thread.currentThread()) {
            stackTrace = InterpreterToVM.getStackTrace(InterpreterToVM.DefaultHiddenFramesFilter.INSTANCE, maxDepth);
        } else {
            stackTrace = asyncGetStackTrace(thread, maxDepth, context, node);
            if (stackTrace == null) { // unresponsive.
                return StaticObject.NULL;
            }
        }

        return stackTrace.toGuest(context);
    }

    @TruffleBoundary
    private static VM.StackTrace asyncGetStackTrace(StaticObject thread, int maxDepth, EspressoContext context, Node node) {
        assert maxDepth >= 0;
        VM.StackTrace[] stackTraces = ThreadRequests.getStackTraces(context, maxDepth, node, thread);
        return stackTraces[0];
    }

    @Substitution(languageFilter = VersionFilter.Java20OrLater.class)
    public static @JavaType(Object[].class) StaticObject scopedValueCache(@Inject EspressoContext context) {
        StaticObject platformThread = context.getCurrentPlatformThread();
        return context.getThreadAccess().getScopedValueCache(platformThread);
    }

    @Substitution(languageFilter = VersionFilter.Java20OrLater.class)
    public static void setScopedValueCache(@JavaType(Object[].class) StaticObject cache, @Inject EspressoContext context) {
        StaticObject platformThread = context.getCurrentPlatformThread();
        context.getThreadAccess().setScopedValueCache(platformThread, cache);
    }

    @Substitution(languageFilter = VersionFilter.Java20OrLater.class, flags = {IsTrivial})
    public static void ensureMaterializedForStackWalk(@JavaType(Object.class) StaticObject obj) {
        CompilerDirectives.blackhole(obj);
    }
}
