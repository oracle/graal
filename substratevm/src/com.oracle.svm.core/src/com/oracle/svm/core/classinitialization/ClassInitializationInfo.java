/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.classinitialization;

// Checkstyle: stop
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.annotate.InvokeJavaFunctionPointer;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

import sun.misc.Unsafe;
// Checkstyle: resume

/**
 * Information about the runtime class initialization state of a {@link DynamicHub class}, and
 * {@link #initialize implementation} of class initialization according to the Java VM
 * specification.
 *
 * The information is not directly stored in {@link DynamicHub} because 1) the class initialization
 * state is mutable while {@link DynamicHub} must be immutable, and 2) few classes require
 * initialization at runtime so factoring out the information reduces image size.
 */
public final class ClassInitializationInfo {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /**
     * Singleton for classes that are already initialized during image building and do not need
     * class initialization at runtime.
     */
    public static final ClassInitializationInfo INITIALIZED_INFO_SINGLETON = new ClassInitializationInfo(InitState.FullyInitialized);

    /** Singleton for classes that failed to link during image building. */
    public static final ClassInitializationInfo FAILED_INFO_SINGLETON = new ClassInitializationInfo(InitState.InitializationError);

    enum InitState {
        /**
         * Successfully linked/verified (but not initialized yet). Linking happens during image
         * building, so we do not need to track states before linking.
         */
        Linked,
        /** Currently running class initializer. */
        BeingInitialized,
        /** Initialized (successful final state). */
        FullyInitialized,
        /** Error happened during initialization. */
        InitializationError
    }

    interface ClassInitializerFunctionPointer extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        void invoke();
    }

    /**
     * Isolates require that all function pointers to image methods are in immutable classes.
     * {@link ClassInitializationInfo} is mutable, so we use this class as an immutable indirection.
     */
    public static class ClassInitializerFunctionPointerHolder {
        /**
         * We cannot declare the field to have type {@link ClassInitializerFunctionPointer} because
         * during image building the field refers to a wrapper object that cannot implement custom
         * interfaces.
         */
        final CFunctionPointer functionPointer;

        ClassInitializerFunctionPointerHolder(CFunctionPointer functionPointer) {
            this.functionPointer = functionPointer;
        }
    }

    /**
     * Function pointer to the class initializer, or null if the class does not have a class
     * initializer.
     */
    private final ClassInitializerFunctionPointerHolder classInitializer;

    /**
     * The current initialization state.
     */
    private InitState initState;
    /**
     * The thread that is currently initializing the class.
     */
    private Thread initThread;

    /**
     * The lock held during initialization of the class. Allocated during image building, otherwise
     * we would need synchronization or atomic operations to install the lock at runtime.
     */
    private final ReentrantLock initLock;
    /**
     * The condition used to wait when class initialization is requested by multiple threads at the
     * same time. Lazily initialized without races because we are holding {@link #initLock} already.
     */
    private Condition initCondition;

    @Platforms(Platform.HOSTED_ONLY.class)
    private ClassInitializationInfo(InitState initState) {
        this.classInitializer = null;
        this.initState = initState;
        this.initLock = initState == InitState.FullyInitialized ? null : new ReentrantLock();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public ClassInitializationInfo(CFunctionPointer classInitializer) {
        this.classInitializer = classInitializer == null || classInitializer.isNull() ? null : new ClassInitializerFunctionPointerHolder(classInitializer);
        this.initState = InitState.Linked;
        this.initLock = new ReentrantLock();
    }

    public boolean isInitialized() {
        return initState == InitState.FullyInitialized;
    }

    private boolean isBeingInitialized() {
        return initState == InitState.BeingInitialized;
    }

    private boolean isInErrorState() {
        return initState == InitState.InitializationError;
    }

    private boolean isReentrantInitialization(Thread thread) {
        return thread == initThread;
    }

    /**
     * Perform class initialization. This is the slow-path that should only be called after checking
     * {@link #isInitialized}.
     *
     * Steps refer to the JVM specification for class initialization:
     * https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.5
     */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void initialize(ClassInitializationInfo info, DynamicHub hub) {
        Thread self = Thread.currentThread();

        /*
         * Step 1: Synchronize on the initialization lock, LC, for C. This involves waiting until
         * the current thread can acquire LC
         */
        info.initLock.lock();
        try {
            /*
             * Step 2: If the Class object for C indicates that initialization is in progress for C
             * by some other thread, then release LC and block the current thread until informed
             * that the in-progress initialization has completed, at which time repeat this
             * procedure.
             *
             * Thread interrupt status is unaffected by execution of the initialization procedure.
             */
            while (info.isBeingInitialized() && !info.isReentrantInitialization(self)) {
                if (info.initCondition == null) {
                    /*
                     * We are holding initLock, so there cannot be any races installing the
                     * initCondition.
                     */
                    info.initCondition = info.initLock.newCondition();
                }
                info.initCondition.awaitUninterruptibly();
            }

            /*
             * Step 3: If the Class object for C indicates that initialization is in progress for C
             * by the current thread, then this must be a recursive request for initialization.
             * Release LC and complete normally.
             */
            if (info.isBeingInitialized() && info.isReentrantInitialization(self)) {
                return;
            }

            /*
             * Step 4: If the Class object for C indicates that C has already been initialized, then
             * no further action is required. Release LC and complete normally.
             */
            if (info.isInitialized()) {
                return;
            }

            /*
             * Step 5: If the Class object for C is in an erroneous state, then initialization is
             * not possible. Release LC and throw a NoClassDefFoundError.
             */
            if (info.isInErrorState()) {
                throw new NoClassDefFoundError("Could not initialize class " + hub.getName());
            }

            /*
             * Step 6: Record the fact that initialization of the Class object for C is in progress
             * by the current thread, and release LC.
             */
            info.initState = InitState.BeingInitialized;
            info.initThread = self;

        } finally {
            info.initLock.unlock();
        }

        /*
         * Step 7: Next, if C is a class rather than an interface, initialize its super class and
         * super interfaces.
         */
        if (!hub.isInterface()) {
            try {
                if (hub.getSuperHub() != null) {
                    hub.getSuperHub().ensureInitialized();
                }
                /*
                 * If C implements any interfaces that declares a non-abstract, non-static method,
                 * the initialization of C triggers initialization of its super interfaces.
                 *
                 * Only need to recurse if hasDefaultMethods is set, which includes declaring and
                 * inheriting default methods.
                 */
                if (hub.hasDefaultMethods()) {
                    initializeSuperInterfaces(hub);
                }
            } catch (Throwable ex) {
                /*
                 * If the initialization of S completes abruptly because of a thrown exception, then
                 * acquire LC, label the Class object for C as erroneous, notify all waiting
                 * threads, release LC, and complete abruptly, throwing the same exception that
                 * resulted from initializing SC.
                 */
                info.setInitializationStateAndNotify(InitState.InitializationError);
                throw ex;
            }
        }

        /*
         * Step 8: Next, determine whether assertions are enabled for C by querying its defining
         * class loader.
         *
         * Nothing to do for this step, Substrate VM fixes the assertion status during image
         * building.
         */

        Throwable exception = null;
        try {
            /* Step 9: Next, execute the class or interface initialization method of C. */
            info.invokeClassInitializer();
        } catch (Throwable ex) {
            exception = ex;
        }

        if (exception == null) {
            /*
             * Step 10: If the execution of the class or interface initialization method completes
             * normally, then acquire LC, label the Class object for C as fully initialized, notify
             * all waiting threads, release LC, and complete this procedure normally.
             */
            info.setInitializationStateAndNotify(InitState.FullyInitialized);
        } else {
            /*
             * Step 11: Otherwise, the class or interface initialization method must have completed
             * abruptly by throwing some exception E. If the class of E is not Error or one of its
             * subclasses, then create a new instance of the class ExceptionInInitializerError with
             * E as the argument, and use this object in place of E in the following step.
             */
            if (!(exception instanceof Error)) {
                exception = new ExceptionInInitializerError(exception);
            }
            /*
             * Step 12: Acquire LC, label the Class object for C as erroneous, notify all waiting
             * threads, release LC, and complete this procedure abruptly with reason E or its
             * replacement as determined in the previous step.
             */
            info.setInitializationStateAndNotify(InitState.InitializationError);
            throw (Error) exception;
        }
    }

    /**
     * Eagerly initialize superinterfaces that declare default methods.
     */
    private static void initializeSuperInterfaces(DynamicHub hub) {
        assert hub.hasDefaultMethods() : "caller should have checked this";
        for (DynamicHub iface : hub.getInterfaces()) {
            /*
             * Initialization is depth first search, i.e., we start with top of the inheritance
             * tree. hasDefaultMethods drives searching superinterfaces since it means
             * hasDefaultMethods in its superinterface hierarchy.
             */
            if (iface.hasDefaultMethods()) {
                initializeSuperInterfaces(iface);
            }

            /*
             * Only initialize interfaces that "declare" concrete methods. This logic follows the
             * implementation in the Java HotSpot VM, it does not seem to be mentioned in the Java
             * VM specification.
             */
            if (iface.declaresDefaultMethods()) {
                iface.ensureInitialized();
            }
        }
    }

    /**
     * Acquire lock, set state, and notify all waiting threads.
     */
    private void setInitializationStateAndNotify(InitState state) {
        initLock.lock();
        try {
            this.initState = state;
            this.initThread = null;
            /* Make sure previous stores are all done, notably the initState. */
            UNSAFE.storeFence();

            if (initCondition != null) {
                initCondition.signalAll();
                initCondition = null;
            }
        } finally {
            initLock.unlock();
        }
    }

    private void invokeClassInitializer() {
        if (classInitializer != null) {
            ((ClassInitializerFunctionPointer) classInitializer.functionPointer).invoke();
        }
    }
}
