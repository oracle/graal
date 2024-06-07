/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.hotspot;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.impl.AbstractFastThreadLocal;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationSupport;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.compiler.hotspot.HotSpotTruffleCompiler;
import com.oracle.truffle.runtime.BackgroundCompileQueue;
import com.oracle.truffle.runtime.CompilationTask;
import com.oracle.truffle.runtime.EngineData;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedOSRLoopNode;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.TruffleCallBoundary;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalTruffleCompilationSupport;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.runtime.JVMCI;
import sun.misc.Unsafe;

/**
 * HotSpot specific implementation of a Graal-enabled Truffle runtime.
 *
 * This class contains functionality for interfacing with a HotSpot-based Truffle compiler,
 * independent of where the compiler resides (i.e., co-located in the HotSpot heap or running in a
 * native-image shared library).
 */
public final class HotSpotTruffleRuntime extends OptimizedTruffleRuntime {
    static final int JAVA_SPEC = Runtime.version().feature();

    static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final JavaLangAccess JAVA_LANG_ACCESS = SharedSecrets.getJavaLangAccess();
    private static final long THREAD_EETOP_OFFSET;
    static {
        try {
            THREAD_EETOP_OFFSET = HotSpotTruffleRuntime.getObjectFieldOffset(Thread.class.getDeclaredField("eetop"));
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    /**
     * Contains lazily computed data such as the compilation queue and helper for stack
     * introspection.
     */
    static final class Lazy extends BackgroundCompileQueue {
        StackIntrospection stackIntrospection;

        Lazy(HotSpotTruffleRuntime runtime) {
            super(runtime);
            runtime.installDefaultListeners();
        }

        @Override
        protected void notifyIdleCompilerThread() {
            TruffleCompiler compiler = ((HotSpotTruffleRuntime) runtime).truffleCompiler;
            // truffleCompiler should never be null outside unit-tests, this check avoids transient
            // failures.
            if (compiler != null) {
                ((HotSpotTruffleCompiler) compiler).purgePartialEvaluationCaches();
            }
        }
    }

    private int pendingTransferToInterpreterOffset = -1;
    private boolean traceTransferToInterpreter;
    private Boolean profilingEnabled;

    private volatile Lazy lazy;

    private Lazy lazy() {
        if (lazy == null) {
            synchronized (this) {
                if (lazy == null) {
                    lazy = new Lazy(this);
                }
            }
        }
        return lazy;
    }

    private volatile CompilationTask initializationTask;
    private volatile boolean truffleCompilerInitialized;
    private volatile Throwable truffleCompilerInitializationException;

    private final HotSpotVMConfigAccess vmConfigAccess;
    private final int jvmciReservedLongOffset0;

    public HotSpotTruffleRuntime(TruffleCompilationSupport compilationSupport) {
        super(compilationSupport, Arrays.asList(HotSpotOptimizedCallTarget.class, InstalledCode.class, HotSpotThreadLocalHandshake.class, HotSpotTruffleRuntime.class));
        installCallBoundaryMethods(null);

        this.vmConfigAccess = new HotSpotVMConfigAccess(HotSpotJVMCIRuntime.runtime().getConfigStore());

        int longOffset;
        try {
            longOffset = vmConfigAccess.getFieldOffset("JavaThread::_jvmci_reserved0", Integer.class, "jlong", -1);
        } catch (NoSuchMethodError error) {
            throw CompilerDirectives.shouldNotReachHere("This JDK does not have JavaThread::_jvmci_reserved0", error);
        } catch (JVMCIError error) {
            try {
                // the type of the jvmci reserved field might still be old.
                longOffset = vmConfigAccess.getFieldOffset("JavaThread::_jvmci_reserved0", Integer.class, "intptr_t*", -1);
            } catch (NoSuchMethodError e) {
                e.initCause(error);
                throw CompilerDirectives.shouldNotReachHere("This JDK does not have JavaThread::_jvmci_reserved0", e);
            }
        }
        if (longOffset == -1) {
            throw CompilerDirectives.shouldNotReachHere("This JDK does not have JavaThread::_jvmci_reserved0");
        }
        this.jvmciReservedLongOffset0 = longOffset;

        int jvmciReservedReference0Offset = vmConfigAccess.getFieldOffset("JavaThread::_jvmci_reserved_oop0", Integer.class, "oop", -1);
        if (jvmciReservedReference0Offset == -1) {
            throw CompilerDirectives.shouldNotReachHere("This JDK does not have JavaThread::_jvmci_reserved_oop0");
        }

        installReservedOopMethods(null);
    }

    public int getJVMCIReservedLongOffset0() {
        return jvmciReservedLongOffset0;
    }

    @Override
    public ThreadLocalHandshake getThreadLocalHandshake() {
        return HotSpotThreadLocalHandshake.SINGLETON;
    }

    @Override
    protected StackIntrospection getStackIntrospection() {
        Lazy l = lazy();
        if (l.stackIntrospection == null) {
            l.stackIntrospection = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getStackIntrospection();
        }
        return l.stackIntrospection;
    }

    /**
     * This is called reflectively from the compiler side in HotSpotTruffleHostEnvironment.
     */
    @Override
    public TruffleCompiler getTruffleCompiler(TruffleCompilable compilable) {
        Objects.requireNonNull(compilable, "Compilable must be non null.");
        if (truffleCompiler == null) {
            initializeTruffleCompiler(compilable);
            rethrowTruffleCompilerInitializationException();
            assert truffleCompiler != null : "TruffleCompiler must be non null";
        }
        return truffleCompiler;
    }

    /**
     * We need to trigger initialization of the Truffle compiler when the first call target is
     * created. Truffle call boundary methods are installed when the truffle compiler is
     * initialized, as it requires the compiler to do so. Until then the call boundary methods are
     * interpreted with the HotSpot interpreter (see
     * {@link #setNotInlinableOrCompilable(ResolvedJavaMethod)} ). This is very slow and we want to
     * avoid doing this as soon as possible. It can also be a real issue when compilation is turned
     * off completely and no call targets would ever be compiled. Without ensureInitialized the
     * stubs (see {@link HotSpotTruffleCompiler#installTruffleCallBoundaryMethod}) would never be
     * installed in that case and we would use the HotSpot interpreter indefinitely.
     */
    private void ensureInitialized(OptimizedCallTarget firstCallTarget) {
        if (truffleCompilerInitialized) {
            return;
        }
        CompilationTask localTask = initializationTask;
        if (localTask == null) {
            final Object lock = this;
            synchronized (lock) {
                localTask = initializationTask;
                if (localTask == null && !truffleCompilerInitialized) {
                    rethrowTruffleCompilerInitializationException();
                    initializationTask = localTask = getCompileQueue().submitInitialization(firstCallTarget, new Consumer<CompilationTask>() {
                        @Override
                        public void accept(CompilationTask task) {
                            synchronized (lock) {
                                initializeTruffleCompiler(firstCallTarget);
                                assert truffleCompilerInitialized || truffleCompilerInitializationException != null;
                                assert initializationTask != null;
                                initializationTask = null;
                            }
                        }
                    });
                }
            }
        }
        if (localTask != null) {
            firstCallTarget.maybeWaitForTask(localTask);
            rethrowTruffleCompilerInitializationException();
        } else {
            assert truffleCompilerInitialized || truffleCompilerInitializationException != null;
        }
    }

    /*
     * Used reflectively in CompilerInitializationTest.
     */
    public void resetCompiler() {
        truffleCompiler = null;
        truffleCompilerInitialized = false;
        truffleCompilerInitializationException = null;
    }

    private synchronized void initializeTruffleCompiler(TruffleCompilable compilable) {
        // might occur for multiple compiler threads at the same time.
        if (!truffleCompilerInitialized) {
            rethrowTruffleCompilerInitializationException();
            try {
                OptimizedCallTarget callTarget = (OptimizedCallTarget) compilable;
                EngineData engine = callTarget.engine;

                /*
                 * The init call target deliberately only saves the engine data and does not keep a
                 * strong reference to the root node to avoid memory leaks.
                 */
                OptimizedCallTarget initCallTarget = createInitializationCallTarget(engine);

                profilingEnabled = engine.profilingEnabled;
                HotSpotTruffleCompiler compiler = (HotSpotTruffleCompiler) newTruffleCompiler();
                compiler.initialize(initCallTarget, true);
                this.initializeCallTarget = initCallTarget;

                pendingTransferToInterpreterOffset = compiler.pendingTransferToInterpreterOffset(callTarget);
                if (pendingTransferToInterpreterOffset == -1) {
                    throw CompilerDirectives.shouldNotReachHere("Invalid offset for JavaThread::_pending_transfer_to_interpreter");
                }

                installCallBoundaryMethods(compiler);
                installReservedOopMethods(compiler);

                truffleCompiler = compiler;
                traceTransferToInterpreter = engine.traceTransferToInterpreter;
                truffleCompilerInitialized = true;
            } catch (Throwable e) {
                truffleCompilerInitializationException = e;
            }
        }
    }

    private void rethrowTruffleCompilerInitializationException() {
        if (truffleCompilerInitializationException != null) {
            throw sthrow(RuntimeException.class, truffleCompilerInitializationException);
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T sthrow(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    @Override
    public OptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget source, RootNode rootNode) {
        OptimizedCallTarget target = new HotSpotOptimizedCallTarget(source, rootNode);
        ensureInitialized(target);
        return target;
    }

    @Override
    protected OptimizedCallTarget createInitializationCallTarget(EngineData engine) {
        return new HotSpotOptimizedCallTarget(engine);
    }

    @Override
    public void onCodeInstallation(TruffleCompilable compilable, InstalledCode installedCode) {
        HotSpotOptimizedCallTarget callTarget = (HotSpotOptimizedCallTarget) compilable;
        callTarget.setInstalledCode(installedCode);
    }

    /**
     * Creates a log that {@code HotSpotSpeculationLog#managesFailedSpeculations() manages} a native
     * failed speculations list. An important invariant is that an nmethod compiled with this log
     * can never be executing once the log object dies. When the log object dies, it frees the
     * failed speculations list thus invalidating the
     * {@code HotSpotSpeculationLog#getFailedSpeculationsAddress() failed speculations address}
     * embedded in the nmethod. If the nmethod were to execute after this point and fail a
     * speculation, it would append the failed speculation to the already freed list.
     * <p>
     * Truffle ensures this cannot happen as it only attaches managed speculation logs to
     * {@link OptimizedCallTarget}s and {@link OptimizedOSRLoopNode}s. Executions of nmethods
     * compiled for an {@link OptimizedCallTarget} or {@link OptimizedOSRLoopNode} object will have
     * a strong reference to the object (i.e., as the receiver). This guarantees that such an
     * nmethod cannot be executing after the object has died.
     */
    @Override
    public SpeculationLog createSpeculationLog() {
        return new HotSpotSpeculationLog();
    }

    public static void setDontInlineCallBoundaryMethod(List<ResolvedJavaMethod> callBoundaryMethods) {
        for (ResolvedJavaMethod method : callBoundaryMethods) {
            setNotInlinableOrCompilable(method);
        }
    }

    static MetaAccessProvider getMetaAccess() {
        return JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
    }

    /**
     * Prevents C1 or C2 from inlining a call to and compiling a method annotated by
     * {@link TruffleCallBoundary} (i.e., <code>OptimizedCallTarget.callBoundary(Object[])</code>)
     * so that we never miss the chance to jump from the Truffle interpreter to compiled code.
     *
     * This is quite slow as it forces every call to
     * <code>OptimizedCallTarget.callBoundary(Object[])</code> to run in the HotSpot interpreter, so
     * later on we manually compile {@code callBoundary()} with Graal. This then lets a
     * C1/C2-compiled caller jump to Graal-compiled {@code callBoundary()}, instead of having to go
     * back to the HotSpot interpreter for every execution of {@code callBoundary()}.
     */
    private static void setNotInlinableOrCompilable(ResolvedJavaMethod method) {
        // JDK-8180487 and JDK-8186478 introduced breaking API changes so reflection is required.
        Method[] methods = HotSpotResolvedJavaMethod.class.getMethods();
        for (Method m : methods) {
            if (m.getName().equals("setNotInlineable") || m.getName().equals("setNotInlinableOrCompilable") || m.getName().equals("setNotInlineableOrCompileable")) {
                try {
                    m.invoke(method);
                    return;
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new InternalError(e);
                }
            }
        }
        throw new InternalError(String.format(
                        "Could not find setNotInlineable, setNotInlinableOrCompilable or setNotInlineableOrCompileable in %s",
                        HotSpotResolvedJavaMethod.class));
    }

    @Override
    public BackgroundCompileQueue getCompileQueue() {
        return lazy();
    }

    @SuppressWarnings("unused")
    private boolean verifyCompilerConfiguration(String name) {
        String lazyName = getCompilerConfigurationName();
        if (!name.equals(lazyName)) {
            throw new AssertionError("Expected compiler configuration name " + name + " but was " + lazyName + ".");
        }
        return true;
    }

    @SuppressWarnings("try")
    @Override
    @TruffleBoundary
    public void bypassedInstalledCode(OptimizedCallTarget target) {
        if (!truffleCompilerInitialized) {
            // do not wait for initialization
            return;
        }
        installCallBoundaryMethods((HotSpotTruffleCompiler) truffleCompiler);
    }

    public boolean bypassedReservedOop() {
        CompilationTask task = initializationTask;
        if (task != null) {
            /*
             * We were currently initializing. No need to reinstall the code stubs. The caller can
             * use oop accessor methods (setJVMCIReservedReference0, getJVMCIReservedReference0)
             * instead.
             */
            return true;
        }

        if (!truffleCompilerInitialized) {
            /*
             * If the initialization did not yet complete here, then this means that initializing
             * the compiler failed. We can therefore not continue installing the stubs. So we
             * re-throw the compiler initialization error or we return false which will likely
             * trigger an assertion error in the caller at a later point.
             */
            if (truffleCompilerInitializationException != null) {
                throw new AssertionError("Compiler initialization failed cannot continue.", truffleCompilerInitializationException);
            }
            return false;
        }

        /*
         * If we reached this point we are not initializing anymore and the compiler is successfully
         * initialized. If bypassedReservedOop was called this also means that we skipped the
         * installed code for the JVMCI reserved oop accessor. This can happen if the debugger steps
         * over the code and invalidates any installed Java code stub, the HotSpot code cache
         * decides to clean up the the stub for the accessor method or this happened due to an
         * initialization race condition. In all three cases the best we can do is to try to install
         * the stub code again even if this means repeated compilation and installation of this
         * method during debug-stepping. Unfortunately there is no known way to detect invalidation
         * of HotSpot installed code reliably.
         */
        installReservedOopMethods((HotSpotTruffleCompiler) truffleCompiler);

        /*
         * We have reinstalled the stubs. Returning true indicates that the caller should retry
         * calling the stubs or use other available means like the oop accessor methods
         * (setJVMCIReservedReference0, getJVMCIReservedReference0).
         */
        return true;
    }

    private void installCallBoundaryMethods(HotSpotTruffleCompiler compiler) {
        ResolvedJavaType type = getMetaAccess().lookupJavaType(OptimizedCallTarget.class);
        for (ResolvedJavaMethod method : type.getDeclaredMethods(false)) {
            if (method.getAnnotation(TruffleCallBoundary.class) != null) {
                if (compiler != null) {
                    OptimizedCallTarget initCallTarget = initializeCallTarget;
                    Objects.requireNonNull(initCallTarget);
                    compiler.installTruffleCallBoundaryMethod(method, initCallTarget);
                } else {
                    setNotInlinableOrCompilable(method);
                }
            }
        }
    }

    private void installReservedOopMethods(HotSpotTruffleCompiler compiler) {
        ResolvedJavaType local = getMetaAccess().lookupJavaType(HotSpotFastThreadLocal.class);
        for (ResolvedJavaMethod method : local.getDeclaredMethods(false)) {
            String name = method.getName();
            switch (name) {
                case "set":
                case "get":
                    if (compiler != null) {
                        OptimizedCallTarget initCallTarget = initializeCallTarget;
                        Objects.requireNonNull(initCallTarget);
                        compiler.installTruffleReservedOopMethod(method, initCallTarget);
                    } else {
                        setNotInlinableOrCompilable(method);
                    }
                    break;
            }
        }
    }

    @Override
    public KnownMethods getKnownMethods() {
        if (knownMethods == null) {
            knownMethods = new KnownMethods(getMetaAccess());
        }
        return knownMethods;
    }

    @Override
    public void notifyTransferToInterpreter() {
        if (CompilerDirectives.inInterpreter() && traceTransferToInterpreter) {
            traceTransferToInterpreter();
        }
    }

    private void traceTransferToInterpreter() {
        TruffleCompiler compiler = truffleCompiler;
        assert compiler != null;
        assert pendingTransferToInterpreterOffset != -1;

        long threadStruct = UNSAFE.getLong(JAVA_LANG_ACCESS.currentCarrierThread(), THREAD_EETOP_OFFSET);
        long pendingTransferToInterpreterAddress = threadStruct + pendingTransferToInterpreterOffset;
        boolean deoptimized = UNSAFE.getByte(pendingTransferToInterpreterAddress) != 0;
        if (deoptimized) {
            logTransferToInterpreter(pendingTransferToInterpreterAddress);
        }
    }

    private void logTransferToInterpreter(long pendingTransferToInterpreterAddress) {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) iterateFrames(FrameInstance::getCallTarget);
        if (callTarget == null) {
            return;
        }
        StackTraceHelper.logHostAndGuestStacktrace("transferToInterpreter", callTarget);
        UNSAFE.putByte(pendingTransferToInterpreterAddress, (byte) 0);
    }

    @Override
    public boolean isProfilingEnabled() {
        if (profilingEnabled == null) {
            return true;
        }
        return profilingEnabled;
    }

    @Override
    protected JavaConstant forObject(final Object object) {
        final HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getConstantReflection();
        return constantReflection.forObject(object);
    }

    @Override
    protected int getBaseInstanceSize(Class<?> type) {
        if (type.isArray() || type.isPrimitive()) {
            throw new IllegalArgumentException("Class " + type.getName() + " is a primitive type or an array class!");
        }

        HotSpotMetaAccessProvider meta = (HotSpotMetaAccessProvider) getMetaAccess();
        HotSpotResolvedObjectType resolvedType = (HotSpotResolvedObjectType) meta.lookupJavaType(type);
        return Math.abs(resolvedType.instanceSize());
    }

    private static boolean fieldIsNotEligible(Class<?> clazz, ResolvedJavaField f) {
        /*
         * Fields of Reference class are excluded because they are handled in a special way by the
         * VM. In any case, we have to check that the field declared in the Reference class, because
         * Reference class has private fields and so subclasses can have fields of the same names.
         */
        return (Reference.class.isAssignableFrom(clazz) && f.getDeclaringClass().isAssignableFrom(getMetaAccess().lookupJavaType(Reference.class)));
    }

    @Override
    protected int[] getFieldOffsets(Class<?> type, boolean includePrimitive, boolean includeSuperclasses) {
        if (type.isArray() || type.isPrimitive()) {
            throw new IllegalArgumentException("Class " + type.getName() + " is a primitive type or an array class!");
        }
        HotSpotMetaAccessProvider meta = (HotSpotMetaAccessProvider) getMetaAccess();
        ResolvedJavaType javaType = meta.lookupJavaType(type);
        ResolvedJavaField[] fields = javaType.getInstanceFields(includeSuperclasses);
        int[] fieldOffsets = new int[fields.length];
        int fieldsCount = 0;
        for (int i = 0; i < fields.length; i++) {
            final ResolvedJavaField f = fields[i];
            if ((includePrimitive || !f.getJavaKind().isPrimitive()) && !fieldIsNotEligible(type, f)) {
                fieldOffsets[fieldsCount++] = f.getOffset();
            }
        }
        return Arrays.copyOf(fieldOffsets, fieldsCount);
    }

    private <T> T getVMOptionValue(String name, Class<T> type) {
        try {
            return vmConfigAccess.getFlag(name, type);
        } catch (JVMCIError jvmciError) {
            // The option was not found. Throw rather IllegalArgumentException than JVMCIError
            throw new IllegalArgumentException(jvmciError);
        }
    }

    @Override
    protected int getObjectAlignment() {
        return getVMOptionValue("ObjectAlignmentInBytes", Integer.class);
    }

    @Override
    protected int getArrayIndexScale(Class<?> componentType) {
        MetaAccessProvider meta = getMetaAccess();
        ResolvedJavaType resolvedType = meta.lookupJavaType(componentType);

        return ((HotSpotJVMCIRuntime) JVMCI.getRuntime()).getArrayIndexScale(resolvedType.getJavaKind());
    }

    @Override
    protected int getArrayBaseOffset(Class<?> componentType) {
        MetaAccessProvider meta = getMetaAccess();
        ResolvedJavaType resolvedType = meta.lookupJavaType(componentType);

        return ((HotSpotJVMCIRuntime) JVMCI.getRuntime()).getArrayBaseOffset(resolvedType.getJavaKind());
    }

    @Override
    public long getStackOverflowLimit() {
        int stackOverflowLimitOffset = vmConfigAccess.getFieldOffset(JAVA_SPEC >= 16 ? "JavaThread::_stack_overflow_state._stack_overflow_limit" : "JavaThread::_stack_overflow_limit",
                        Integer.class, "address");
        long eetop = UNSAFE.getLong(JAVA_LANG_ACCESS.currentCarrierThread(), THREAD_EETOP_OFFSET);
        return UNSAFE.getLong(eetop + stackOverflowLimitOffset);
    }

    @SuppressWarnings("deprecation" /* JDK-8277863 */)
    static long getObjectFieldOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    @Override
    protected <T> T asObject(final Class<T> type, final JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        final HotSpotObjectConstant hsConstant = (HotSpotObjectConstant) constant;
        return hsConstant.asObject(type);
    }

    @Override
    protected AbstractFastThreadLocal getFastThreadLocalImpl() {
        return HotSpotFastThreadLocal.SINGLETON;
    }

    public static HotSpotTruffleRuntime getRuntime() {
        return (HotSpotTruffleRuntime) OptimizedTruffleRuntime.getRuntime();
    }

    public boolean isLibGraalCompilationEnabled() {
        return compilationSupport instanceof LibGraalTruffleCompilationSupport;
    }

}
