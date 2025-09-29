/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.NeverInline.CALLER_CATCHES_IMPLICIT_EXCEPTIONS;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.InternalPlatform.NATIVE_ONLY;

import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.c.InvokeJavaFunctionPointer;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;
import jdk.internal.reflect.Reflection;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Information about the runtime class initialization state of a {@link DynamicHub class}, and
 * {@link #tryInitialize implementation} of class initialization according to the Java VM
 * specification.
 * <p>
 * The information is not directly stored in {@link DynamicHub} because 1) the class initialization
 * state is mutable while {@link DynamicHub} must be immutable, and 2) few classes require
 * initialization at runtime so factoring out the information reduces image size.
 * <p>
 * Note that methods of this class never show up in exception stack traces (i.e., all related frames
 * will be filtered from the stack trace, see {@link InternalVMMethod} annotation below). This can
 * be especially confusing for implicit exceptions such as {@link NullPointerException} or
 * {@link AssertionError} as the stack trace will look as if the caller caused the exception.
 * <p>
 * The {@link #typeReached} for all super types must have a value whose ordinal is greater or equal
 * to its own value. Concretely, {@link TypeReached#REACHED} types must have all super types
 * {@link TypeReached#REACHED} or {@link TypeReached#UNTRACKED}, and {@link TypeReached#UNTRACKED}
 * types' super types must be {@link TypeReached#UNTRACKED}. This is verified in
 * {@code com.oracle.svm.hosted.meta.UniverseBuilder.checkHierarchyForTypeReachedConstraints}.
 */
@InternalVMMethod
public final class ClassInitializationInfo {
    /*
     * These singletons are used for build-time initialized classes that are UNTRACKED for type
     * reached, which reduces image size.
     */
    private static final ClassInitializationInfo INITIALIZED_NO_CLINIT_NO_TRACKING = new ClassInitializationInfo(InitState.FullyInitialized, false, false);
    private static final ClassInitializationInfo INITIALIZED_HAS_CLINIT_NO_TRACKING = new ClassInitializationInfo(InitState.FullyInitialized, true, false);
    private static final ClassInitializationInfo FAILED_NO_CLINIT_NO_TRACKING = new ClassInitializationInfo(InitState.InitializationError, false, false);
    private static final ClassInitializationInfo FAILED_HAS_CLINIT_NO_TRACKING = new ClassInitializationInfo(InitState.InitializationError, true, false);

    /**
     * Marks that a runtime-loaded class has a class initializer that should be executed in the
     * interpreter.
     */
    private static final FunctionPointerHolder INTERPRETER_INITIALIZATION_MARKER = new FunctionPointerHolder(null);

    /**
     * Function pointer to the class initializer that should be called at run-time. In some cases,
     * this may be an arbitrary helper method instead of the {@code <clinit>} of the class. This
     * field may be null if the class does not have a class initializer or if class initialization
     * already happened at build-time.
     */
    private final FunctionPointerHolder runtimeClassInitializer;

    /**
     * Indicates if the class has a {@code <clinit>} method, no matter if it should be initialized
     * at build-time or run-time.
     */
    private final boolean hasInitializer;

    /** Returns true if class initialization was successful at build-time. */
    private final boolean buildTimeInitialized;

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

    /**
     * Indicates whether the {@link #slowPath} must be called. This field is declared volatile so
     * that {@link EnsureClassInitializedSnippets} emits a LOAD_LOAD barrier when this field is
     * accessed. Without this barrier, subsequent reads could be reordered before the read of
     * {@link #slowPathRequired}, allowing threads to observe uninitialized values for fields that
     * are written in the static initializer. We could directly emit a LOAD_LOAD barrier instead,
     * but it doesn't make any difference in terms of the used instructions on any of the relevant
     * CPU architectures. So, it is safer to mark the field as volatile instead.
     */
    private volatile boolean slowPathRequired;

    /**
     * The current initialization state. Needs to be volatile because it can be accessed by the
     * application.
     */
    private volatile InitState initState;

    /**
     * Determines if {@link #markReached} still needs to be executed. May only be true if
     * {@link #slowPathRequired} is true as well.
     */
    private volatile boolean typeReachedTracked;

    /**
     * If the reachability of this type is tracked, this field stores
     * {@link TypeReached#NOT_REACHED} or {@link TypeReached#REACHED}. Once a previously unreached
     * class is reached at run-time, this state changes to {@link TypeReached#REACHED}. Super types
     * of build-time initialized types are always considered as reached (see
     * {@link #setTypeReached()}).
     *
     * If the reachability of this type is not tracked, then {@link TypeReached#UNTRACKED} is stored
     * in this field.
     */
    private volatile TypeReached typeReached;

    /** The thread that is currently initializing the class. */
    private IsolateThread initThread;

    /** For classes that are loaded and initialized at build-time. */
    @Platforms(Platform.HOSTED_ONLY.class)
    private ClassInitializationInfo(InitState initState, boolean hasInitializer, boolean typeReachedTracked) {
        assert initState == InitState.FullyInitialized || initState == InitState.InitializationError;

        this.buildTimeInitialized = (initState == InitState.FullyInitialized);
        this.hasInitializer = hasInitializer;
        this.runtimeClassInitializer = null;
        this.slowPathRequired = typeReachedTracked || initState != InitState.FullyInitialized;
        this.initLock = buildTimeInitialized ? null : new ReentrantLock();
        this.initState = initState;
        this.typeReachedTracked = typeReachedTracked;
        this.typeReached = typeReachedTracked ? TypeReached.NOT_REACHED : TypeReached.UNTRACKED;

        assert !this.typeReachedTracked || slowPathRequired;
    }

    /** For classes that are loaded at build-time but initialized at run-time. */
    @Platforms(Platform.HOSTED_ONLY.class)
    private ClassInitializationInfo(CFunctionPointer runtimeClassInitializer, boolean typeReachedTracked) {
        assert runtimeClassInitializer == null || runtimeClassInitializer.isNonNull();

        this.buildTimeInitialized = false;
        this.hasInitializer = runtimeClassInitializer != null;
        this.runtimeClassInitializer = hasInitializer ? new FunctionPointerHolder(runtimeClassInitializer) : null;
        this.slowPathRequired = true;
        this.initLock = new ReentrantLock();
        this.initState = InitState.Linked;
        this.typeReachedTracked = typeReachedTracked;
        this.typeReached = typeReachedTracked ? TypeReached.NOT_REACHED : TypeReached.UNTRACKED;

        assert !this.typeReachedTracked || slowPathRequired;
    }

    /** For classes that are loaded at run-time. */
    private ClassInitializationInfo(boolean typeReachedTracked, boolean hasClassInitializer) {
        assert RuntimeClassLoading.isSupported();

        this.buildTimeInitialized = false;
        this.hasInitializer = hasClassInitializer;
        this.runtimeClassInitializer = hasClassInitializer ? INTERPRETER_INITIALIZATION_MARKER : null;
        this.slowPathRequired = true;
        this.initLock = new ReentrantLock();
        /* GR-59739: Needs a new state "Loaded". */
        this.initState = InitState.Linked;
        this.typeReachedTracked = typeReachedTracked;
        this.typeReached = typeReachedTracked ? TypeReached.NOT_REACHED : TypeReached.UNTRACKED;

        assert !this.typeReachedTracked || slowPathRequired;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static ClassInitializationInfo forBuildTimeInitializedClass(InitState initState, boolean hasInitializer, boolean isTracked) {
        if (isTracked) {
            return new ClassInitializationInfo(initState, hasInitializer, isTracked);
        }

        /* Return a cached object. */
        if (initState == InitState.FullyInitialized) {
            return hasInitializer ? INITIALIZED_HAS_CLINIT_NO_TRACKING : INITIALIZED_NO_CLINIT_NO_TRACKING;
        } else {
            assert initState == InitState.InitializationError;
            return hasInitializer ? FAILED_HAS_CLINIT_NO_TRACKING : FAILED_NO_CLINIT_NO_TRACKING;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static ClassInitializationInfo forRuntimeTimeInitializedClass(CFunctionPointer methodPointer, boolean typeReachedTracked) {
        return new ClassInitializationInfo(methodPointer, typeReachedTracked);
    }

    public static ClassInitializationInfo forRuntimeLoadedClass(boolean typeReachedTracked, boolean hasClassInitializer) {
        return new ClassInitializationInfo(typeReachedTracked, hasClassInitializer);
    }

    public boolean isBuildTimeInitialized() {
        return buildTimeInitialized;
    }

    public boolean isSlowPathRequired() {
        return slowPathRequired;
    }

    public boolean hasInitializer() {
        return hasInitializer;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public InitState getInitState() {
        return initState;
    }

    public boolean isInitialized() {
        return initState == InitState.FullyInitialized;
    }

    public boolean isInErrorState() {
        return initState == InitState.InitializationError;
    }

    private boolean isBeingInitialized() {
        return initState == InitState.BeingInitialized;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isLinked() {
        return initState == InitState.Linked;
    }

    public boolean isTypeReached(DynamicHub caller) {
        assert typeReached != TypeReached.UNTRACKED : "We should never emit a check for untracked types as this was known at build time: " + caller.getName();
        return typeReached == TypeReached.REACHED;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public TypeReached getTypeReached() {
        return typeReached;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setTypeReached() {
        VMError.guarantee(typeReached != TypeReached.UNTRACKED, "Must not modify untracked types as nodes for checks have already been omitted.");
        typeReached = TypeReached.REACHED;
        typeReachedTracked = false;

        /* Disable the slow-path if it was only needed for markReached(...). */
        if (buildTimeInitialized) {
            slowPathRequired = false;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isTracked() {
        return typeReached != TypeReached.UNTRACKED;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public FunctionPointerHolder getRuntimeClassInitializer() {
        return runtimeClassInitializer;
    }

    /**
     * The class initialization slow-path. This should only be called after checking
     * {@link #isSlowPathRequired}.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void slowPath(Class<?> clazz) {
        DynamicHub hub = DynamicHub.fromClass(clazz);
        ClassInitializationInfo info = hub.getClassInitializationInfo();
        info.tryInitialize(hub, false);
    }

    /**
     * Performs class initialization. Multiple threads can execute this method concurrently for the
     * same {@link ClassInitializationInfo} instance.
     * <p>
     * Note that this is a normal Java method, so {@link StackOverflowError}s,
     * {@link OutOfMemoryError}s, or other exceptions can occur in many places. At the start of this
     * method, it is perfectly fine to throw such exceptions directly. We only need to be careful to
     * leave the object in a consistent state as the code may get executed again at a later point.
     * As class initialization progresses, exceptions need to be treated more carefully (see
     * comments in the code).
     */
    private void tryInitialize(DynamicHub hub, boolean superClassInitialization) {
        /* Check if there is anything to do. */
        if (!isSlowPathRequired()) {
            return;
        }

        if (typeReachedTracked) {
            /*
             * Types are marked as reached before any initialization is performed. Reason: the
             * results should be visible in class initializers of the whole hierarchy as they could
             * use reflection.
             */
            markReached(hub);

            /* Don't execute this code again once the whole hierarchy was marked as reached. */
            typeReachedTracked = false;

            /* Exit early if the slow-path was only entered because of markReached(...). */
            if (buildTimeInitialized) {
                slowPathRequired = false;
                return;
            }
        }

        /*
         * GR-43118: If a predefined class is not loaded, and the caller class is loaded, set the
         * classloader of the initialized class to the class loader of the caller class.
         *
         * This does not work in general as class loading happens in more places than class
         * initialization, e.g., on class literals. However, this workaround makes most of the cases
         * work until we have a proper implementation of class loading.
         */
        if (!hub.isLoaded()) {
            Class<?> callerClass = Reflection.getCallerClass();
            if (DynamicHub.fromClass(callerClass).isLoaded()) {
                PredefinedClassesSupport.loadClassIfNotLoaded(callerClass.getClassLoader(), null, DynamicHub.toClass(hub));
            }
        }

        /*
         * Before acquiring the lock, make the yellow zone available and disable recurring callback
         * execution (otherwise, deadlocks may occur or the same static initializer may be executed
         * more than once).
         *
         * If the current method is called as a side effect of another class initialization because
         * a super class or interface needs to be initialized, then this logic is skipped. This is
         * necessary so that the yellow zone can be protected again before executing arbitrary Java
         * code (such as a class initializer).
         */
        if (Platform.includedIn(NATIVE_ONLY.class) && !superClassInitialization) {
            StackOverflowCheck.singleton().makeYellowZoneAvailable();
            RecurringCallbackSupport.suspendCallbackTimer("Prevent deadlocks and other issues.");
        }
        try {
            tryInitialize0(hub);
        } finally {
            if (Platform.includedIn(NATIVE_ONLY.class) && !superClassInitialization) {
                RecurringCallbackSupport.resumeCallbackTimer();
                StackOverflowCheck.singleton().protectYellowZone();
            }
        }
    }

    /**
     * Marks the class hierarchy of {@code hub} as reached. Multiple threads may execute this code
     * concurrently, however locking is not needed because it doesn't matter if {@link #typeReached}
     * is set to {@link TypeReached#REACHED} multiple times. If an exception happens while executing
     * this code (e.g., a {@link StackOverflowError}), the execution can be retried at a later point
     * by the same or a different thread.
     */
    private static void markReached(DynamicHub hub) {
        var current = hub;
        do {
            ClassInitializationInfo info = current.getClassInitializationInfo();
            if (info.typeReached == TypeReached.UNTRACKED) {
                break;
            }
            info.typeReached = TypeReached.REACHED;

            reachInterfaces(current);
            current = current.getSuperHub();
        } while (current != null);
    }

    private static void reachInterfaces(DynamicHub hub) {
        for (DynamicHub superInterface : hub.getInterfaces()) {
            ClassInitializationInfo superInfo = superInterface.getClassInitializationInfo();
            if (superInfo.typeReached != TypeReached.UNTRACKED) {
                superInfo.typeReached = TypeReached.REACHED;
                reachInterfaces(superInterface);
            }
        }
    }

    /**
     * The steps in this method refer to the
     * <a href="https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-5.html#jvms-5.5">JVM
     * specification for class initialization</a>. Be careful with explicit and implicit exceptions
     * in this method (especially after step 6). Note that HotSpot uses slightly different numbers
     * for some of the steps.
     * <p>
     * For some cases, we could probably optimize the code so that it doesn't need to acquire
     * {@link #initLock}. However, we want to stick as close as possible to the specification, so we
     * explicitly don't do any optimizations in that regard.
     */
    @NeverInline(CALLER_CATCHES_IMPLICIT_EXCEPTIONS)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+13/src/hotspot/share/oops/instanceKlass.cpp#L1184-L1364")
    private void tryInitialize0(DynamicHub hub) {
        assert !Platform.includedIn(NATIVE_ONLY.class) || StackOverflowCheck.singleton().isYellowZoneAvailable();
        /*
         * Step 1: Synchronize on the initialization lock, LC, for C. This involves waiting until
         * the current thread can acquire LC.
         */
        initLock.lock();
        try {
            /*
             * Step 2: If the initialization state of C indicates that initialization is in progress
             * for C by some other thread, then release LC and block the current thread until
             * informed that the in-progress initialization has completed, at which time repeat this
             * procedure. Thread interrupt status is unaffected by execution of the initialization
             * procedure.
             */
            while (isBeingInitialized() && !isReentrantInitialization()) {
                if (initCondition == null) {
                    /*
                     * We are holding initLock, so there cannot be any races installing the
                     * initCondition.
                     */
                    initCondition = initLock.newCondition();
                }
                initCondition.awaitUninterruptibly();
            }

            /*
             * Step 3: If the initialization state of C indicates that initialization is in progress
             * for C by the current thread, then this must be a recursive request for
             * initialization. Release LC and complete normally.
             */
            if (isBeingInitialized() && isReentrantInitialization()) {
                return;
            }

            /*
             * Step 4: If the initialization state of C indicates that C has already been
             * initialized, then no further action is required. Release LC and complete normally.
             */
            if (isInitialized()) {
                assert !isSlowPathRequired();
                return;
            }

            /*
             * Step 5: If the initialization state of C is in an erroneous state, then
             * initialization is not possible. Release LC and throw a NoClassDefFoundError.
             */
            if (isInErrorState()) {
                throw new NoClassDefFoundError("Could not initialize class " + hub.getName());
            }

            /*
             * Step 6: Otherwise, record the fact that initialization of C is in progress by the
             * current thread, and release LC.
             *
             * Be careful: from this point on, any unhandled exceptions will result in deadlocks.
             */
            initState = InitState.BeingInitialized;
            setInitThread();
        } finally {
            initLock.unlock();
        }

        /*
         * Step 7: Next, if C is a class rather than an interface, initialize its super class and
         * super interfaces.
         */
        if (!hub.isInterface()) {
            try {
                DynamicHub superHub = hub.getSuperHub();
                if (superHub != null) {
                    ClassInitializationInfo superInfo = superHub.getClassInitializationInfo();
                    superInfo.tryInitialize(superHub, true);
                }
                /*
                 * If C implements any interfaces that declare non-abstract, non-static methods, the
                 * initialization of C triggers initialization of its super interfaces.
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
                 * acquire LC, label C as erroneous, notify all waiting threads, release LC, and
                 * complete abruptly, throwing the same exception that resulted from initializing S.
                 */
                setInitializationStateAndNotify(InitState.InitializationError);
                throw ex;
            }
        }

        /*
         * Step 8: Next, determine whether assertions are enabled for C by querying its defining
         * loader.
         *
         * Nothing to do for this step, Substrate VM fixes the assertion status during image
         * building.
         */

        /*
         * Step 9: Next, if C declares a class or interface initialization method, execute that
         * method.
         */
        Throwable exception = null;
        try {
            invokeClassInitializer(hub);
        } catch (Throwable ex) {
            exception = ex;
        }

        if (exception == null) {
            /*
             * Step 10: If the execution of the class or interface initialization method completes
             * normally, or if C declares no class or interface initialization method, then acquire
             * LC, label C as fully initialized, notify all waiting threads, release LC, and
             * complete this procedure normally.
             */
            setInitializationStateAndNotify(InitState.FullyInitialized);
        } else {
            /*
             * Step 11: Otherwise, the class or interface initialization method must have completed
             * abruptly by throwing some exception E. If the class of E is not Error or one of its
             * subclasses, then create a new instance of the class ExceptionInInitializerError with
             * E as the argument, and use this object in place of E in the following step. If a new
             * instance of ExceptionInInitializerError cannot be created because an OutOfMemoryError
             * occurs, then use an OutOfMemoryError object in place of E in the following step.
             */
            if (!(exception instanceof Error)) {
                exception = createExceptionInInitializerObject(exception);
            }
            /*
             * Step 12: Acquire LC, label C as erroneous, notify all waiting threads, release LC,
             * and complete this procedure abruptly with reason E or its replacement as determined
             * in the previous step.
             */
            setInitializationStateAndNotify(InitState.InitializationError);
            throw (Error) exception;
        }
    }

    private boolean isReentrantInitialization() {
        return CurrentIsolate.getCurrentThread() == initThread;
    }

    /** This method must not throw any exceptions as this could result in deadlocks. */
    private void setInitThread() {
        try {
            setInitThread0();
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * We use the platform thread instead of a potential virtual thread because initializers like
     * that of {@code sun.nio.ch.Poller} can switch to the carrier thread and encounter the class
     * that is being initialized again and would wait for its initialization in the virtual thread
     * to complete and therefore deadlock.
     * <p>
     * We also pin the virtual thread because it must not continue initialization on a different
     * platform thread, and also because if the platform thread switches to a different virtual
     * thread which encounters the class being initialized, it would wrongly be considered reentrant
     * initialization and enable use of the incompletely initialized class.
     */
    @NeverInline(CALLER_CATCHES_IMPLICIT_EXCEPTIONS)
    private void setInitThread0() {
        assert initThread.isNull();
        if (ContinuationSupport.isSupported() && JavaThreads.isCurrentThreadVirtual()) {
            Target_jdk_internal_vm_Continuation.pin();
        }
        initThread = CurrentIsolate.getCurrentThread();
    }

    /** This method must not throw any exceptions as this could result in deadlocks. */
    private void clearInitThread() {
        try {
            clearInitThread0();
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @NeverInline(CALLER_CATCHES_IMPLICIT_EXCEPTIONS)
    private void clearInitThread0() {
        assert initThread == CurrentIsolate.getCurrentThread();
        initThread = Word.nullPointer();
        if (ContinuationSupport.isSupported() && JavaThreads.isCurrentThreadVirtual()) {
            Target_jdk_internal_vm_Continuation.unpin();
        }
    }

    /** Eagerly initialize superinterfaces that declare default methods. May throw exceptions. */
    @NeverInline(CALLER_CATCHES_IMPLICIT_EXCEPTIONS)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+13/src/hotspot/share/oops/instanceKlass.cpp#L1099-L1117")
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
                ClassInitializationInfo ifaceInfo = iface.getClassInitializationInfo();
                ifaceInfo.tryInitialize(iface, true);
            }
        }
    }

    /** This method must not throw any exceptions as this could result in deadlocks. */
    private static Throwable createExceptionInInitializerObject(Throwable exception) {
        try {
            return createExceptionInInitializerObject0(exception);
        } catch (Throwable e) {
            return e;
        }
    }

    @NeverInline(CALLER_CATCHES_IMPLICIT_EXCEPTIONS)
    private static ExceptionInInitializerError createExceptionInInitializerObject0(Throwable exception) {
        return new ExceptionInInitializerError(exception);
    }

    /**
     * Acquire lock, set state, and notify all waiting threads. This method must not throw any
     * exceptions as this could result in deadlocks.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+13/src/hotspot/share/oops/instanceKlass.cpp#L1367-L1380")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+13/src/hotspot/share/oops/instanceKlass.cpp#L802-L811")
    private void setInitializationStateAndNotify(InitState state) {
        try {
            setInitializationStateAndNotify0(state);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @NeverInline(CALLER_CATCHES_IMPLICIT_EXCEPTIONS)
    private void setInitializationStateAndNotify0(InitState state) {
        initLock.lock();
        try {
            clearInitThread();
            initState = state;
            if (state == InitState.FullyInitialized) {
                slowPathRequired = false;
            }

            if (initCondition != null) {
                initCondition.signalAll();
                initCondition = null;
            }
        } finally {
            initLock.unlock();
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+13/src/hotspot/share/oops/instanceKlass.cpp#L1675-L1715")
    private void invokeClassInitializer(DynamicHub hub) {
        if (runtimeClassInitializer == null) {
            return;
        }

        /* Protect the yellow zone before executing arbitrary Java code. */
        if (Platform.includedIn(NATIVE_ONLY.class)) {
            StackOverflowCheck.singleton().protectYellowZone();
        }
        try {
            invokeClassInitializer0(hub);
        } finally {
            if (Platform.includedIn(NATIVE_ONLY.class)) {
                StackOverflowCheck.singleton().makeYellowZoneAvailable();
            }
        }
    }

    private void invokeClassInitializer0(DynamicHub hub) {
        if (RuntimeClassLoading.isSupported() && runtimeClassInitializer == INTERPRETER_INITIALIZATION_MARKER) {
            ResolvedJavaMethod classInitializer = hub.getInterpreterType().getClassInitializer();
            CremaSupport.singleton().execute(classInitializer, new Object[0]);
        } else {
            ClassInitializerFunctionPointer functionPointer = (ClassInitializerFunctionPointer) runtimeClassInitializer.functionPointer;
            VMError.guarantee(functionPointer.isNonNull());
            functionPointer.invoke();
        }
    }

    public enum InitState {
        /**
         * Successfully linked/verified (but not initialized yet). Linking happens during image
         * building, so we do not need to track states before linking.
         */
        Linked,
        /**
         * Currently running class initializer.
         */
        BeingInitialized,
        /**
         * Initialized (successful final state).
         */
        FullyInitialized,
        /**
         * Error happened during initialization.
         */
        InitializationError
    }

    public enum TypeReached {
        NOT_REACHED,
        REACHED,
        UNTRACKED,
    }

    interface ClassInitializerFunctionPointer extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        void invoke();
    }

    public static class TestingBackdoor {
        public static void uninitialize(Class<?> clazz) {
            ClassInitializationInfo info = DynamicHub.fromClass(clazz).getClassInitializationInfo();
            assert info.initState == InitState.FullyInitialized;
            assert !info.slowPathRequired;

            info.initState = InitState.Linked;
            info.slowPathRequired = true;
        }
    }
}
