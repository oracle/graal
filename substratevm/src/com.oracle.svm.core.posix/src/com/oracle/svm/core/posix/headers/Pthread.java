/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.posix.headers.Time.timespec;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file pthread.h.
 */
@CContext(PosixDirectives.class)
@CLibrary("pthread")
public class Pthread {

    /*
     * Thread identifiers. The structure of the attribute type is not exposed on purpose.
     */

    public interface pthread_t extends WordBase {
    }

    @CPointerTo(nameOfCType = "pthread_t")
    public interface pthread_tPointer extends PointerBase {

        pthread_t read();

    }

    @CStruct
    public interface pthread_attr_t extends PointerBase {
    }

    /*
     * Data structures for mutex handling. The structure of the attribute type is not exposed on
     * purpose.
     */

    @CStruct
    public interface pthread_mutex_t extends PointerBase {
        // int lock;
        // unsigned int count;
        // int owner;
        // unsigned int nusers;
        // int kind;
        // short spins;
        // short elision;
        // pthread_list_t list;
    }

    public interface pthread_mutexattr_t extends PointerBase {
    }

    /*
     * Data structure for conditional variable handling. The structure of the attribute type is not
     * exposed on purpose.
     */

    @CStruct
    public interface pthread_cond_t extends PointerBase {
        // int lock;
        // unsigned int futex;
        // unsigned long long int total_seq;
        // unsigned long long int wakeup_seq;
        // unsigned long long int woken_seq;
        // void * mutex;
        // unsigned int nwaiters;
        // unsigned int broadcast_seq;
    }

    @CStruct
    public interface pthread_condattr_t extends PointerBase {
    }

    /*
     * Data structure for read-write lock variable handling. The structure of the attribute type is
     * not exposed on purpose.
     */

    @CStruct
    public interface pthread_rwlock_t extends PointerBase {
        // int lock;
        // unsigned int nr_readers;
        // unsigned int readers_wakeup;
        // unsigned int writer_wakeup;
        // unsigned int nr_readers_queued;
        // unsigned int nr_writers_queued;
        // int writer;
        // int shared;
        // unsigned int flags;

    }

    @CStruct
    public interface pthread_rwlockattr_t extends PointerBase {
    }

    /*
     * POSIX barriers data type. The structure of the type is deliberately not exposed.
     */

    // FIXME: @CStruct
    // FIXME: public interface pthread_barrier_t extends PointerBase {
    // FIXME: }

    // FIXME: @CStruct
    // FIXME: public interface pthread_barrierattr_t extends PointerBase {
    // FIXME: }

    /* Detach state. */

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_CREATE_JOINABLE();

    @CConstant
    public static native int PTHREAD_CREATE_DETACHED();

    /* Mutex types. */

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_MUTEX_TIMED_NP();

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_MUTEX_RECURSIVE_NP();

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_MUTEX_ERRORCHECK_NP();

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_MUTEX_ADAPTIVE_NP();

    @CConstant
    public static native int PTHREAD_STACK_MIN();

    @CConstant
    public static native int PTHREAD_MUTEX_NORMAL();

    @CConstant
    public static native int PTHREAD_MUTEX_RECURSIVE();

    @CConstant
    public static native int PTHREAD_MUTEX_ERRORCHECK();

    @CConstant
    public static native int PTHREAD_MUTEX_DEFAULT();

    /* Mutex protocols. */

    @CConstant
    public static native int PTHREAD_PRIO_NONE();

    @CConstant
    public static native int PTHREAD_PRIO_INHERIT();

    @CConstant
    public static native int PTHREAD_PRIO_PROTECT();

    /* Read-write lock types. */

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_RWLOCK_PREFER_READER_NP();

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_RWLOCK_PREFER_WRITER_NP();

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_RWLOCK_PREFER_WRITER_NONRECURSIVE_NP();

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_RWLOCK_DEFAULT_NP();

    /* Scheduler inheritance. */

    @CConstant
    public static native int PTHREAD_INHERIT_SCHED();

    @CConstant
    public static native int PTHREAD_EXPLICIT_SCHED();

    /* Scope handling. */

    @CConstant
    public static native int PTHREAD_SCOPE_SYSTEM();

    @CConstant
    public static native int PTHREAD_SCOPE_PROCESS();

    /* Process shared or private flag. */

    @CConstant
    public static native int PTHREAD_PROCESS_PRIVATE();

    @CConstant
    public static native int PTHREAD_PROCESS_SHARED();

    /* Cancellation */

    @CConstant
    public static native int PTHREAD_CANCEL_ENABLE();

    @CConstant
    public static native int PTHREAD_CANCEL_DISABLE();

    @CConstant
    public static native int PTHREAD_CANCEL_DEFERRED();

    @CConstant
    public static native int PTHREAD_CANCEL_ASYNCHRONOUS();

    @CConstant
    public static native WordBase PTHREAD_CANCELED();

    /* Single execution handling. */

    // FIXME: @CConstant
    // FIXME: public static native int PTHREAD_ONCE_INIT();

    /**
     * Create a new thread, starting with execution of START-ROUTINE getting passed ARG. Creation
     * attributed come from ATTR. The new handle is stored in *NEWTHREAD.
     */
    @CFunction
    public static native int pthread_create(pthread_tPointer newthread, pthread_attr_t attr, WordBase start_routine, WordBase arg);

    /** Terminate calling thread. */
    @CFunction
    public static native void pthread_exit(PointerBase retval);

    /**
     * Make calling thread wait for termination of the thread TH. The exit status of the thread is
     * stored in *THREAD_RETURN, if THREAD_RETURN is not NULL.
     */
    @CFunction
    public static native int pthread_join(pthread_t th, WordPointer thread_return);

    /**
     * Check whether thread TH has terminated. If yes return the status of the thread in
     * *THREAD_RETURN, if THREAD_RETURN is not NULL.
     */
    @CFunction
    public static native int pthread_tryjoin_np(pthread_t th, WordPointer thread_return);

    /**
     * Make calling thread wait for termination of the thread TH, but only until TIMEOUT. The exit
     * status of the thread is stored in THREAD_RETURN, if THREAD_RETURN is not NULL.
     */
    @CFunction
    public static native int pthread_timedjoin_np(pthread_t th, WordPointer thread_return, timespec abstime);

    /**
     * Indicate that the thread TH is never to be joined with PTHREAD_JOIN. The resources of TH will
     * therefore be freed immediately when it terminates, instead of waiting for another thread to
     * perform PTHREAD_JOIN on it.
     */
    @CFunction
    public static native int pthread_detach(pthread_t th);

    /** Obtain the identifier of the current thread. */
    @CFunction
    public static native pthread_t pthread_self();

    /** Compare two thread identifiers. */
    @CFunction
    public static native int pthread_equal(pthread_t thread1, pthread_t thread2);

    /* Thread attribute handling. */

    /**
     * Initialize thread attribute *ATTR with default attributes (detachstate is
     * PTHREAD_CREATE_JOINABLE, scheduling policy is SCHED_OTHER, no user-provided stack).
     */
    @CFunction
    public static native int pthread_attr_init(pthread_attr_t attr);

    /** Destroy thread attribute *ATTR. */
    @CFunction
    public static native int pthread_attr_destroy(pthread_attr_t attr);

    /** Get detach state attribute. */
    @CFunction
    public static native int pthread_attr_getdetachstate(pthread_attr_t attr, CIntPointer detachstate);

    /**
     * Set detach state attribute. The detachstate must be either PTHREAD_CREATE_DETACHED or
     * PTHREAD_CREATE_JOINABLE.
     */
    @CFunction
    public static native int pthread_attr_setdetachstate(pthread_attr_t attr, int detachstate);

    /** Get the size of the guard area created for stack overflow protection. */
    @CFunction
    public static native int pthread_attr_getguardsize(pthread_attr_t attr, WordPointer guardsize);

    /** Set the size of the guard area created for stack overflow protection. */
    @CFunction
    public static native int pthread_attr_setguardsize(pthread_attr_t attr, UnsignedWord guardsize);

    /** Return in *PARAM the scheduling parameters of *ATTR. */
    @CFunction
    public static native int pthread_attr_getschedparam(pthread_attr_t attr, CIntPointer param);

    /** Set scheduling parameters (priority, etc) in *ATTR according to PARAM. */
    @CFunction
    public static native int pthread_attr_setschedparam(pthread_attr_t attr, CIntPointer param);

    /** Return in *POLICY the scheduling policy of *ATTR. */
    @CFunction
    public static native int pthread_attr_getschedpolicy(pthread_attr_t attr, CIntPointer policy);

    /** Set scheduling policy in *ATTR according to POLICY. */
    @CFunction
    public static native int pthread_attr_setschedpolicy(pthread_attr_t attr, int policy);

    /** Return in *INHERIT the scheduling inheritance mode of *ATTR. */
    @CFunction
    public static native int pthread_attr_getinheritsched(pthread_attr_t attr, CIntPointer inherit);

    /** Set scheduling inheritance mode in *ATTR according to INHERIT. */
    @CFunction
    public static native int pthread_attr_setinheritsched(pthread_attr_t attr, int inherit);

    /** Return in *SCOPE the scheduling contention scope of *ATTR. */
    @CFunction
    public static native int pthread_attr_getscope(pthread_attr_t attr, CIntPointer scope);

    /** Set scheduling contention scope in *ATTR according to SCOPE. */
    @CFunction
    public static native int pthread_attr_setscope(pthread_attr_t attr, int scope);

    /** Return the previously set address for the stack. */
    @CFunction
    public static native int pthread_attr_getstackaddr(pthread_attr_t attr, WordPointer stackaddr);

    /**
     * Set the starting address of the stack of the thread to be created. Depending on whether the
     * stack grows up or down the value must either be higher or lower than all the address in the
     * memory block. The minimal size of the block must be PTHREAD_STACK_MIN.
     */
    @CFunction
    public static native int pthread_attr_setstackaddr(pthread_attr_t attr, PointerBase stackaddr);

    /** Return the currently used minimal stack size. */
    @CFunction
    public static native int pthread_attr_getstacksize(pthread_attr_t attr, WordPointer stacksize);

    /**
     * Add information about the minimum stack size needed for the thread to be started. This size
     * must never be less than PTHREAD_STACK_MIN and must also not exceed the system limits.
     */
    @CFunction
    public static native int pthread_attr_setstacksize(pthread_attr_t attr, UnsignedWord stacksize);

    /** Return the previously set address for the stack. */
    @CFunction
    public static native int pthread_attr_getstack(pthread_attr_t attr, WordPointer stackaddr, WordPointer stacksize);

    /**
     * The following two interfaces are intended to replace the last two. They require setting the
     * address as well as the size since only setting the address will make the implementation on
     * some architectures impossible.
     */
    @CFunction
    public static native int pthread_attr_setstack(pthread_attr_t attr, PointerBase stackaddr, UnsignedWord stacksize);

    /**
     * Thread created with attribute ATTR will be limited to run only on the processors represented
     * in CPUSET.
     */
    @CFunction
    public static native int pthread_attr_setaffinity_np(pthread_attr_t attr, UnsignedWord cpusetsize, PointerBase cpuset);

    /**
     * Get bit set in CPUSET representing the processors threads created with ATTR can run on.
     */
    @CFunction
    public static native int pthread_attr_getaffinity_np(pthread_attr_t attr, UnsignedWord cpusetsize, PointerBase cpuset);

    /** Get the default attributes used by pthread_create in this process. */
    @CFunction
    public static native int pthread_getattr_default_np(pthread_attr_t attr);

    /**
     * Set the default attributes to be used by pthread_create in this process.
     */
    @CFunction
    public static native int pthread_setattr_default_np(pthread_attr_t attr);

    /**
     * Initialize thread attribute *ATTR with attributes corresponding to the already running thread
     * TH. It shall be called on uninitialized ATTR and destroyed with pthread_attr_destroy when no
     * longer needed.
     */
    @CFunction
    public static native int pthread_getattr_np(pthread_t th, pthread_attr_t attr);

    /* Functions for scheduling control. */

    /**
     * Set the scheduling parameters for TARGET_THREAD according to POLICY and *PARAM.
     */
    @CFunction
    public static native int pthread_setschedparam(pthread_t target_thread, int policy, CIntPointer param);

    /** Return in *POLICY and *PARAM the scheduling parameters for TARGET_THREAD. */
    @CFunction
    public static native int pthread_getschedparam(pthread_t target_thread, CIntPointer policy, CIntPointer param);

    /** Set the scheduling priority for TARGET_THREAD. */
    @CFunction
    public static native int pthread_setschedprio(pthread_t target_thread, int prio);

    /** Determine level of concurrency. */
    @CFunction
    public static native int pthread_getconcurrency();

    /** Set new concurrency level to LEVEL. */
    @CFunction
    public static native int pthread_setconcurrency(int level);

    /**
     * Yield the processor to another thread or process. This function is similar to the POSIX
     * `sched_yield' function but might be differently implemented in the case of a m-on-n thread
     * implementation.
     */
    @CFunction
    public static native int pthread_yield();

    /**
     * Limit specified thread TH to run only on the processors represented in CPUSET.
     */
    @CFunction
    public static native int pthread_setaffinity_np(pthread_t th, UnsignedWord cpusetsize, PointerBase cpuset);

    /** Get bit set in CPUSET representing the processors TH can run on. */
    @CFunction
    public static native int pthread_getaffinity_np(pthread_t th, UnsignedWord cpusetsize, PointerBase cpuset);

    /* Functions for handling initialization. */

    /**
     * Guarantee that the initialization function INIT_ROUTINE will be called only once, even if
     * pthread_once is executed several times with the same ONCE_CONTROL argument. ONCE_CONTROL must
     * point to a static or extern variable initialized to PTHREAD_ONCE_INIT.
     */
    @CFunction
    public static native int pthread_once(CIntPointer once_control, PointerBase init_routine);

    /*
     * Functions for handling cancellation.
     *
     * Note that these functions are explicitly not marked to not throw an exception in C++ code. If
     * cancellation is implemented by unwinding this is necessary to have the compiler generate the
     * unwind information.
     */

    /**
     * Set cancelability state of current thread to STATE, returning old state in *OLDSTATE if
     * OLDSTATE is not NULL.
     */
    @CFunction
    public static native int pthread_setcancelstate(int state, CIntPointer oldstate);

    /**
     * Set cancellation state of current thread to TYPE, returning the old type in *OLDTYPE if
     * OLDTYPE is not NULL.
     */
    @CFunction
    public static native int pthread_setcanceltype(int type, CIntPointer oldtype);

    /** Cancel THREAD immediately or at the next possibility. */
    @CFunction
    public static native int pthread_cancel(pthread_t th);

    /**
     * Test for pending cancellation for the current thread and terminate the thread as per
     * pthread_exit(PTHREAD_CANCELED) if it has been cancelled.
     * <p>
     * This method does not transition to C code because (a) I do not want a test for a safepoint if
     * I return because I am probably in the midst of safepointing, and (b) I do not need a test for
     * a safepoint if this call cancels this thread.
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void pthread_testcancel();

    /* Mutex handling. */

    /** Initialize a mutex. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_mutex_init(pthread_mutex_t mutex, pthread_mutexattr_t mutexattr);

    /** Destroy a mutex. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_mutex_destroy(pthread_mutex_t mutex);

    /** Lock a mutex if it is available. */
    @CFunction(transition = Transition.TO_NATIVE)
    public static native int pthread_mutex_trylock(pthread_mutex_t mutex);

    /** Lock a mutex. */
    @CFunction(transition = Transition.TO_NATIVE)
    public static native int pthread_mutex_lock(pthread_mutex_t mutex);

    /** Lock a mutex without a transition. */
    @CFunction(value = "pthread_mutex_lock", transition = Transition.NO_TRANSITION)
    public static native int pthread_mutex_lock_no_transition(pthread_mutex_t mutex);

    /** Wait until lock becomes available, or specified time passes. */
    @CFunction(transition = Transition.TO_NATIVE)
    public static native int pthread_mutex_timedlock(pthread_mutex_t mutex, timespec abstime);

    /** Unlock a mutex. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_mutex_unlock(pthread_mutex_t mutex);

    /** Get the priority ceiling of MUTEX. */
    @CFunction
    public static native int pthread_mutex_getprioceiling(pthread_mutex_t mutex, CIntPointer prioceiling);

    /**
     * Set the priority ceiling of MUTEX to PRIOCEILING, return old priority ceiling value in
     * *OLD_CEILING.
     */
    @CFunction
    public static native int pthread_mutex_setprioceiling(pthread_mutex_t mutex, int prioceiling, CIntPointer old_ceiling);

    /** Declare the state protected by MUTEX as consistent. */
    @CFunction
    public static native int pthread_mutex_consistent(pthread_mutex_t mutex);

    @CFunction
    public static native int pthread_mutex_consistent_np(pthread_mutex_t mutex);

    /* Functions for handling mutex attributes. */

    /**
     * Initialize mutex attribute object ATTR with default attributes (kind is
     * PTHREAD_MUTEX_TIMED_NP).
     */
    @CFunction
    public static native int pthread_mutexattr_init(pthread_mutexattr_t attr);

    /** Destroy mutex attribute object ATTR. */
    @CFunction
    public static native int pthread_mutexattr_destroy(pthread_mutexattr_t attr);

    /** Get the process-shared flag of the mutex attribute ATTR. */
    @CFunction
    public static native int pthread_mutexattr_getpshared(pthread_mutexattr_t attr, CIntPointer pshared);

    /** Set the process-shared flag of the mutex attribute ATTR. */
    @CFunction
    public static native int pthread_mutexattr_setpshared(pthread_mutexattr_t attr, int pshared);

    /** Return in *KIND the mutex kind attribute in *ATTR. */
    @CFunction
    public static native int pthread_mutexattr_gettype(pthread_mutexattr_t attr, CIntPointer kind);

    /**
     * Set the mutex kind attribute in *ATTR to KIND (either PTHREAD_MUTEX_NORMAL,
     * PTHREAD_MUTEX_RECURSIVE, PTHREAD_MUTEX_ERRORCHECK, or PTHREAD_MUTEX_DEFAULT).
     */
    @CFunction
    public static native int pthread_mutexattr_settype(pthread_mutexattr_t attr, int kind);

    /** Return in *PROTOCOL the mutex protocol attribute in *ATTR. */
    @CFunction
    public static native int pthread_mutexattr_getprotocol(pthread_mutexattr_t attr, CIntPointer protocol);

    /**
     * Set the mutex protocol attribute in *ATTR to PROTOCOL (either PTHREAD_PRIO_NONE,
     * PTHREAD_PRIO_INHERIT, or PTHREAD_PRIO_PROTECT).
     */
    @CFunction
    public static native int pthread_mutexattr_setprotocol(pthread_mutexattr_t attr, int protocol);

    /** Return in *PRIOCEILING the mutex prioceiling attribute in *ATTR. */
    @CFunction
    public static native int pthread_mutexattr_getprioceiling(pthread_mutexattr_t attr, CIntPointer prioceiling);

    /** Set the mutex prioceiling attribute in *ATTR to PRIOCEILING. */
    @CFunction
    public static native int pthread_mutexattr_setprioceiling(pthread_mutexattr_t attr, int prioceiling);

    /** Get the robustness flag of the mutex attribute ATTR. */
    @CFunction
    public static native int pthread_mutexattr_getrobust(pthread_mutexattr_t attr, CIntPointer robustness);

    @CFunction
    public static native int pthread_mutexattr_getrobust_np(pthread_mutexattr_t attr, CIntPointer robustness);

    /** Set the robustness flag of the mutex attribute ATTR. */
    @CFunction
    public static native int pthread_mutexattr_setrobust(pthread_mutexattr_t attr, int robustness);

    @CFunction
    public static native int pthread_mutexattr_setrobust_np(pthread_mutexattr_t attr, int robustness);

    /* Functions for handling read-write locks. */

    /**
     * Initialize read-write lock RWLOCK using attributes ATTR, or use the default values if later
     * is NULL.
     */
    @CFunction
    public static native int pthread_rwlock_init(pthread_rwlock_t rwlock, pthread_rwlockattr_t attr);

    /** Destroy read-write lock RWLOCK. */
    @CFunction
    public static native int pthread_rwlock_destroy(pthread_rwlock_t rwlock);

    /** Acquire read lock for RWLOCK. */
    @CFunction
    public static native int pthread_rwlock_rdlock(pthread_rwlock_t rwlock);

    /** Try to acquire read lock for RWLOCK. */
    @CFunction
    public static native int pthread_rwlock_tryrdlock(pthread_rwlock_t rwlock);

    /** Try to acquire read lock for RWLOCK or return after specfied time. */
    @CFunction
    public static native int pthread_rwlock_timedrdlock(pthread_rwlock_t rwlock, timespec abstime);

    /** Acquire write lock for RWLOCK. */
    @CFunction
    public static native int pthread_rwlock_wrlock(pthread_rwlock_t rwlock);

    /** Try to acquire write lock for RWLOCK. */
    @CFunction
    public static native int pthread_rwlock_trywrlock(pthread_rwlock_t rwlock);

    /** Try to acquire write lock for RWLOCK or return after specfied time. */
    @CFunction
    public static native int pthread_rwlock_timedwrlock(pthread_rwlock_t rwlock, timespec abstime);

    /** Unlock RWLOCK. */
    @CFunction
    public static native int pthread_rwlock_unlock(pthread_rwlock_t rwlock);

    /* Functions for handling read-write lock attributes. */

    /** Initialize attribute object ATTR with default values. */
    @CFunction
    public static native int pthread_rwlockattr_init(pthread_rwlockattr_t attr);

    /** Destroy attribute object ATTR. */
    @CFunction
    public static native int pthread_rwlockattr_destroy(pthread_rwlockattr_t attr);

    /** Return current setting of process-shared attribute of ATTR in PSHARED. */
    @CFunction
    public static native int pthread_rwlockattr_getpshared(pthread_rwlockattr_t attr, CIntPointer pshared);

    /** Set process-shared attribute of ATTR to PSHARED. */
    @CFunction
    public static native int pthread_rwlockattr_setpshared(pthread_rwlockattr_t attr, int pshared);

    /** Return current setting of reader/writer preference. */
    @CFunction
    public static native int pthread_rwlockattr_getkind_np(pthread_rwlockattr_t attr, CIntPointer pref);

    /** Set reader/write preference. */
    @CFunction
    public static native int pthread_rwlockattr_setkind_np(pthread_rwlockattr_t attr, int pref);

    /* Functions for handling conditional variables. */

    /**
     * Initialize condition variable COND using attributes ATTR, or use the default values if later
     * is NULL.
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_init(pthread_cond_t cond, pthread_condattr_t cond_attr);

    /** Destroy condition variable COND. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_destroy(pthread_cond_t cond);

    /** Wake up one thread waiting for condition variable COND. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_signal(pthread_cond_t cond);

    /** Wake up all threads waiting for condition variables COND. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_broadcast(pthread_cond_t cond);

    /**
     * Wait for condition variable COND to be signaled or broadcast. MUTEX is assumed to be locked
     * before.
     */
    @CFunction
    public static native int pthread_cond_wait(pthread_cond_t cond, pthread_mutex_t mutex);

    @CFunction(value = "pthread_cond_wait", transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_wait_no_transition(pthread_cond_t cond, pthread_mutex_t mutex);

    /**
     * Wait for condition variable COND to be signaled or broadcast until ABSTIME. MUTEX is assumed
     * to be locked before. ABSTIME is an absolute time specification; zero is the beginning of the
     * epoch (00:00:00 GMT, January 1, 1970).
     */
    @CFunction
    public static native int pthread_cond_timedwait(pthread_cond_t cond, pthread_mutex_t mutex, timespec abstime);

    @CFunction(value = "pthread_cond_timedwait", transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_timedwait_no_transition(pthread_cond_t cond, pthread_mutex_t mutex, timespec abstime);

    /* Functions for handling condition variable attributes. */

    /** Initialize condition variable attribute ATTR. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_condattr_init(pthread_condattr_t attr);

    /** Destroy condition variable attribute ATTR. */
    @CFunction
    public static native int pthread_condattr_destroy(pthread_condattr_t attr);

    /** Get the process-shared flag of the condition variable attribute ATTR. */
    @CFunction
    public static native int pthread_condattr_getpshared(pthread_condattr_t attr, CIntPointer pshared);

    /** Set the process-shared flag of the condition variable attribute ATTR. */
    @CFunction
    public static native int pthread_condattr_setpshared(pthread_condattr_t attr, int pshared);

    /** Get the clock selected for the condition variable attribute ATTR. */
    @CFunction
    public static native int pthread_condattr_getclock(pthread_condattr_t attr, CIntPointer clock_id);

    /** Set the clock selected for the condition variable attribute ATTR. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_condattr_setclock(pthread_condattr_t attr, int clock_id);

    /* Functions to handle spinlocks. */

    /**
     * Initialize the spinlock LOCK. If PSHARED is nonzero the spinlock can be shared between
     * different processes.
     */
    @CFunction
    public static native int pthread_spin_init(CIntPointer lock, int pshared);

    /** Destroy the spinlock LOCK. */
    @CFunction
    public static native int pthread_spin_destroy(CIntPointer lock);

    /** Wait until spinlock LOCK is retrieved. */
    @CFunction
    public static native int pthread_spin_lock(CIntPointer lock);

    /** Try to lock spinlock LOCK. */
    @CFunction
    public static native int pthread_spin_trylock(CIntPointer lock);

    /** Release spinlock LOCK. */
    @CFunction
    public static native int pthread_spin_unlock(CIntPointer lock);

    /* Functions to handle barriers. */

    // FIXME: /**
    // FIXME: * Initialize BARRIER with the attributes in ATTR. The barrier is opened when COUNT
    // waiters
    // FIXME: * arrived.
    // FIXME: */
    // FIXME: @CFunction
    // FIXME: public static native int pthread_barrier_init(pthread_barrier_t barrier,
    // pthread_barrierattr_t attr, int count);

    // FIXME: /** Destroy a previously dynamically initialized barrier BARRIER. */
    // FIXME: @CFunction
    // FIXME: public static native int pthread_barrier_destroy(pthread_barrier_t barrier);

    // FIXME: /** Wait on barrier BARRIER. */
    // FIXME: @CFunction
    // FIXME: public static native int pthread_barrier_wait(pthread_barrier_t barrier);

    // FIXME: /** Initialize barrier attribute ATTR. */
    // FIXME: @CFunction
    // FIXME: public static native int pthread_barrierattr_init(pthread_barrierattr_t attr);

    // FIXME: /** Destroy previously dynamically initialized barrier attribute ATTR. */
    // FIXME: @CFunction
    // FIXME: public static native int pthread_barrierattr_destroy(pthread_barrierattr_t attr);

    // FIXME: /** Get the process-shared flag of the barrier attribute ATTR. */
    // FIXME: @CFunction
    // FIXME: public static native int pthread_barrierattr_getpshared(pthread_barrierattr_t attr,
    // CIntPointer pshared);

    // FIXME: /** Set the process-shared flag of the barrier attribute ATTR. */
    // FIXME: @CFunction
    // FIXME: public static native int pthread_barrierattr_setpshared(pthread_barrierattr_t attr,
    // int pshared);

    /* Functions for handling thread-specific data. */

    /**
     * Create a key value identifying a location in the thread-specific data area. Each thread
     * maintains a distinct thread-specific data area. DESTR_FUNCTION, if non-NULL, is called with
     * the value associated to that key when the key is destroyed. DESTR_FUNCTION is not called if
     * the value associated is NULL when the key is destroyed.
     */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int pthread_key_create(CIntPointer key, PointerBase destr_function);

    /** Destroy KEY. */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_key_delete(int key);

    /** Return current value of the thread-specific data slot identified by KEY. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends WordBase> T pthread_getspecific(int key);

    /** Store POINTER in the thread-specific data slot identified by KEY. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int pthread_setspecific(int key, WordBase pointer);

    /** Get ID of CPU-time clock for thread THREAD_ID. */
    @CFunction
    public static native int pthread_getcpuclockid(pthread_t thread_id, PointerBase clock_id);

    /**
     * Install handlers to be called when a new process is created with FORK. The PREPARE handler is
     * called in the parent process just before performing FORK. The PARENT handler is called in the
     * parent process just after FORK. The CHILD handler is called in the child process. Each of the
     * three handlers can be NULL, meaning that no handler needs to be called at that point.
     * PTHREAD_ATFORK can be called several times, in which case the PREPARE handlers are called in
     * LIFO order (last added with PTHREAD_ATFORK, first called before FORK), and the PARENT and
     * CHILD handlers are called in FIFO (first added, first called).
     */
    @CFunction
    public static native int pthread_atfork(PointerBase prepare, PointerBase parent, PointerBase child);

    /*
     * The pthread_kill() function sends the signal sig to thread, another thread in the same
     * process as the caller. The signal is asynchronously directed to thread.
     */
    @CFunction
    public static native int pthread_kill(pthread_t thread, Signal.SignalEnum sig);
}
