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
package com.oracle.truffle.espresso.runtime;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.oracle.truffle.espresso.substitutions.Target_java_lang_ref_Reference;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jdwp.api.JDWPOptions;
import com.oracle.truffle.espresso.jdwp.api.VMListener;
import com.oracle.truffle.espresso.jdwp.impl.EmptyListener;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.substitutions.EspressoReference;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

import static com.oracle.truffle.espresso.EspressoLanguage.EspressoLogger;

public final class EspressoContext {

    public static final int DEFAULT_STACK_SIZE = 32;
    public static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];

    private final EspressoLanguage language;
    private final TruffleLanguage.Env env;
    private final StringTable strings;
    private final ClassRegistries registries;
    private final Substitutions substitutions;
    private final MethodHandleIntrinsics methodHandleIntrinsics;
    private final EspressoThreadManager threadManager;
    private StaticObject mainThreadGroup;

    private final AtomicInteger klassIdProvider = new AtomicInteger();
    public long initVMDoneMs;
    private boolean mainThreadCreated;
    private JDWPContextImpl jdwpContext;
    private VMListener eventListener;
    private boolean contextReady;

    public int getNewId() {
        return klassIdProvider.getAndIncrement();
    }

    private boolean initialized = false;

    private Classpath bootClasspath;
    private String[] mainArguments;
    private Source mainSourceFile;

    // Must be initialized after the context instance creation.
    @CompilationFinal private InterpreterToVM interpreterToVM;
    @CompilationFinal private Meta meta;
    @CompilationFinal private JniEnv jniEnv;
    @CompilationFinal private VM vm;
    @CompilationFinal private EspressoProperties vmProperties;

    @CompilationFinal private EspressoException stackOverflow;
    @CompilationFinal private EspressoException outOfMemory;
    @CompilationFinal private ArrayList<Method> frames;

    // Set on calling guest Thread.stop0(), or when closing context.
    @CompilationFinal private Assumption noThreadStop = Truffle.getRuntime().createAssumption();
    @CompilationFinal private Assumption noSuspend = Truffle.getRuntime().createAssumption();
    @CompilationFinal private Assumption noThreadDeprecationCalled = Truffle.getRuntime().createAssumption();
    private boolean isClosing = false;

    public EspressoContext(TruffleLanguage.Env env, EspressoLanguage language) {
        this.env = env;
        this.language = language;
        this.registries = new ClassRegistries(this);
        this.strings = new StringTable(this);
        this.substitutions = new Substitutions(this);
        this.methodHandleIntrinsics = new MethodHandleIntrinsics(this);
        this.threadManager = new EspressoThreadManager(this);
        this.JDWPOptions = env.getOptions().get(EspressoOptions.JDWPOptions); // null if not
                                                                              // specified
        this.InlineFieldAccessors = JDWPOptions != null ? false : env.getOptions().get(EspressoOptions.InlineFieldAccessors);
        this.InlineMethodHandle = JDWPOptions != null ? false : env.getOptions().get(EspressoOptions.InlineMethodHandle);
        this.SplitMethodHandles = JDWPOptions != null ? false : env.getOptions().get(EspressoOptions.SplitMethodHandles);
        this.Verify = env.getOptions().get(EspressoOptions.Verify);
        this.SpecCompliancyMode = env.getOptions().get(EspressoOptions.SpecCompliancy);
        this.EnableManagement = env.getOptions().get(EspressoOptions.EnableManagement);
    }

    public ClassRegistries getRegistries() {
        return registries;
    }

    public InputStream in() {
        return env.in();
    }

    public OutputStream out() {
        return env.out();
    }

    public OutputStream err() {
        return env.err();
    }

    public StringTable getStrings() {
        return strings;
    }

    public TruffleLanguage.Env getEnv() {
        return env;
    }

    public EspressoLanguage getLanguage() {
        return language;
    }

    /**
     * @return The {@link String}[] array passed to the main function.
     */
    public String[] getMainArguments() {
        return mainArguments;
    }

    public void setMainArguments(String[] mainArguments) {
        this.mainArguments = mainArguments;
    }

    public Classpath getBootClasspath() {
        if (bootClasspath == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            bootClasspath = new Classpath(
                            getVmProperties().bootClasspath().stream().map(new Function<Path, String>() {
                                @Override
                                public String apply(Path path) {
                                    return path.toString();
                                }
                            }).collect(Collectors.joining(File.pathSeparator)));
        }
        return bootClasspath;
    }

    public EspressoProperties getVmProperties() {
        assert vmProperties != null;
        return vmProperties;
    }

    /**
     * @return The source code unit of the main function.
     */
    public Source getMainSourceFile() {
        return mainSourceFile;
    }

    public void setMainSourceFile(Source mainSourceFile) {
        this.mainSourceFile = mainSourceFile;
    }

    public void initializeContext() {
        assert !this.initialized;
        eventListener = new EmptyListener();
        // Inject PublicFinalReference in the host VM.
        Target_java_lang_ref_Reference.init();
        spawnVM();
        this.initialized = true;
        this.jdwpContext = new JDWPContextImpl(this);
        this.eventListener = jdwpContext.jdwpInit(env);
        eventListener.vmStarted(getMainThread());
        hostToGuestReferenceDrainThread.start();
    }

    public VMListener getJDWPListener() {
        return eventListener;
    }

    public Source findOrCreateSource(Method method) {
        String sourceFile = method.getSourceFile();
        if (sourceFile == null) {
            return null;
        } else {
            TruffleFile file = env.getInternalTruffleFile(sourceFile);
            Source source = Source.newBuilder("java", file).content(Source.CONTENT_NONE).build();
            // sources are interned so no cache needed (hopefully)
            return source;
        }
    }

    private Thread hostToGuestReferenceDrainThread;

    public Meta getMeta() {
        return meta;
    }

    public ReferenceQueue<StaticObject> getReferenceQueue() {
        return referenceQueue;
    }

    private final ReferenceQueue<StaticObject> referenceQueue = new ReferenceQueue<>();

    private void spawnVM() {

        long ticks = System.currentTimeMillis();

        initVmProperties();

        this.meta = new Meta(this);

        this.interpreterToVM = new InterpreterToVM(this);

        // Spawn JNI first, then the VM.
        this.vm = VM.create(getJNI()); // Mokapot is loaded

        initializeKnownClass(Type.java_lang_Object);

        for (Symbol<Type> type : Arrays.asList(
                        Type.java_lang_String,
                        Type.java_lang_System,
                        Type.java_lang_ThreadGroup,
                        Type.java_lang_Thread,
                        Type.java_lang_Class,
                        Type.java_lang_reflect_Method)) {
            initializeKnownClass(type);
        }

        createMainThread();

        initializeKnownClass(Type.java_lang_ref_Finalizer);

        // Initialize ReferenceQueues
        this.hostToGuestReferenceDrainThread = getEnv().createThread(new Runnable() {
            @SuppressWarnings("rawtypes")
            @Override
            public void run() {
                final StaticObject lock = (StaticObject) meta.java_lang_ref_Reference_lock.get(meta.java_lang_ref_Reference.tryInitializeAndGetStatics());
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Based on HotSpot's ReferenceProcessor::enqueue_discovered_reflist.
                        // HotSpot's "new behavior": Walk down the list, self-looping the next field
                        // so that the References are not considered active.
                        EspressoReference head;
                        do {
                            head = (EspressoReference) referenceQueue.remove();
                            assert head != null;
                        } while (StaticObject.notNull((StaticObject) meta.java_lang_ref_Reference_next.get(head.getGuestReference())));

                        lock.getLock().lock();
                        try {
                            assert Target_java_lang_Thread.holdsLock(lock) : "must hold Reference.lock at the guest level";
                            casNextIfNullAndMaybeClear(head);

                            EspressoReference prev = head;
                            EspressoReference ref;
                            while ((ref = (EspressoReference) referenceQueue.poll()) != null) {
                                if (StaticObject.notNull((StaticObject) meta.java_lang_ref_Reference_next.get(ref.getGuestReference()))) {
                                    continue;
                                }
                                meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), ref.getGuestReference());
                                casNextIfNullAndMaybeClear(ref);
                                prev = ref;
                            }

                            meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), prev.getGuestReference());
                            StaticObject obj = meta.java_lang_ref_Reference_pending.getAndSetObject(meta.java_lang_ref_Reference.getStatics(), head.getGuestReference());
                            meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), obj);

                            getVM().JVM_MonitorNotify(lock);
                        } finally {
                            lock.getLock().unlock();
                        }
                    } catch (InterruptedException e) {
                        // ignore
                        return;
                    }
                }
            }
        });

        meta.java_lang_System_initializeSystemClass.invokeDirect(null);

        // System exceptions.
        for (Symbol<Type> type : Arrays.asList(
                        Type.java_lang_OutOfMemoryError,
                        Type.java_lang_NullPointerException,
                        Type.java_lang_ClassCastException,
                        Type.java_lang_ArrayStoreException,
                        Type.java_lang_ArithmeticException,
                        Type.java_lang_StackOverflowError,
                        Type.java_lang_IllegalMonitorStateException,
                        Type.java_lang_IllegalArgumentException)) {
            initializeKnownClass(type);
        }

        // Init memoryError instances
        StaticObject stackOverflowErrorInstance = meta.java_lang_StackOverflowError.allocateInstance();
        StaticObject outOfMemoryErrorInstance = meta.java_lang_OutOfMemoryError.allocateInstance();
        meta.java_lang_StackOverflowError.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(stackOverflowErrorInstance, meta.toGuestString("VM StackOverFlow"));
        meta.java_lang_OutOfMemoryError.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(outOfMemoryErrorInstance, meta.toGuestString("VM OutOfMemory"));

        stackOverflowErrorInstance.setHiddenField(meta.HIDDEN_FRAMES, new VM.StackTrace());
        stackOverflowErrorInstance.setField(meta.java_lang_Throwable_backtrace, stackOverflowErrorInstance);
        outOfMemoryErrorInstance.setHiddenField(meta.HIDDEN_FRAMES, new VM.StackTrace());
        outOfMemoryErrorInstance.setField(meta.java_lang_Throwable_backtrace, outOfMemoryErrorInstance);

        this.stackOverflow = new EspressoException(stackOverflowErrorInstance);
        this.outOfMemory = new EspressoException(outOfMemoryErrorInstance);

        // Create application (system) class loader.
        meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);

        EspressoLogger.log(Level.FINE, "VM booted in {0} ms", System.currentTimeMillis() - ticks);
        initVMDoneMs = System.currentTimeMillis();
    }

    private void casNextIfNullAndMaybeClear(@SuppressWarnings("rawtypes") EspressoReference wrapper) {
        StaticObject ref = wrapper.getGuestReference();
        // Cleaner references extends PhantomReference but are cleared.
        // See HotSpot's ReferenceProcessor::process_discovered_references in referenceProcessor.cpp
        if (InterpreterToVM.instanceOf(ref, ref.getKlass().getMeta().sun_misc_Cleaner)) {
            wrapper.clear();
        }
        ref.compareAndSwapField(meta.java_lang_ref_Reference_next, StaticObject.NULL, ref);
    }

    /**
     * The order in which methods are called and fields are set here is important, it mimics
     * HotSpot's implementation.
     */
    private void createMainThread() {
        StaticObject systemThreadGroup = meta.java_lang_ThreadGroup.allocateInstance();
        meta.java_lang_ThreadGroup.lookupDeclaredMethod(Name.INIT, Signature._void) // private
                                                                                    // ThreadGroup()
                        .invokeDirect(systemThreadGroup);
        StaticObject mainThread = meta.java_lang_Thread.allocateInstance();
        // Allow guest Thread.currentThread() to work.
        mainThread.setIntField(meta.java_lang_Thread_priority, Thread.NORM_PRIORITY);
        mainThread.setHiddenField(meta.HIDDEN_HOST_THREAD, Thread.currentThread());
        mainThread.setHiddenField(meta.HIDDEN_DEATH, Target_java_lang_Thread.KillStatus.NORMAL);
        mainThreadGroup = meta.java_lang_ThreadGroup.allocateInstance();

        threadManager.registerMainThread(Thread.currentThread(), mainThread);

        // Guest Thread.currentThread() must work as this point.
        meta.java_lang_ThreadGroup // public ThreadGroup(ThreadGroup parent, String name)
                        .lookupDeclaredMethod(Name.INIT, Signature._void_ThreadGroup_String) //
                        .invokeDirect(mainThreadGroup,
                                        /* parent */ systemThreadGroup,
                                        /* name */ meta.toGuestString("main"));

        meta.java_lang_Thread // public Thread(ThreadGroup group, String name)
                        .lookupDeclaredMethod(Name.INIT, Signature._void_ThreadGroup_String) //
                        .invokeDirect(mainThread,
                                        /* group */ mainThreadGroup,
                                        /* name */ meta.toGuestString("main"));
        mainThread.setIntField(meta.java_lang_Thread_threadStatus, Target_java_lang_Thread.State.RUNNABLE.value);

        mainThreadCreated = true;
    }

    /**
     * Creates a new guest thread from the host thread, and adds it to the main thread group.
     */
    public synchronized void createThread(Thread hostThread) {
        if (meta == null) {
            // initial thread used to initialize the context and spawn the VM.
            // Don't attempt guest thread creation
            return;
        }
        if (getGuestThreadFromHost(hostThread) != null) {
            // already a live guest thread for this host thread
            return;
        }
        StaticObject guestThread = meta.java_lang_Thread.allocateInstance();
        // Allow guest Thread.currentThread() to work.
        guestThread.setIntField(meta.java_lang_Thread_priority, Thread.NORM_PRIORITY);
        guestThread.setHiddenField(meta.HIDDEN_HOST_THREAD, Thread.currentThread());
        guestThread.setHiddenField(meta.HIDDEN_DEATH, Target_java_lang_Thread.KillStatus.NORMAL);

        // register the new guest thread
        threadManager.registerThread(hostThread, guestThread);

        meta.java_lang_Thread // public Thread(ThreadGroup group, String name)
                        .lookupDeclaredMethod(Name.INIT, Signature._void_ThreadGroup_Runnable) //
                        .invokeDirect(guestThread,
                                        /* group */ mainThreadGroup,
                                        /* runnable */ StaticObject.NULL);
        guestThread.setIntField(meta.java_lang_Thread_threadStatus, Target_java_lang_Thread.State.RUNNABLE.value);

        // now add to the main thread group
        meta.java_lang_ThreadGroup // public void add(Thread t)
                        .lookupDeclaredMethod(Name.add, Signature._void_Thread).invokeDirect(mainThreadGroup,
                                        /* thread */ guestThread);
    }

    public void disposeThread(@SuppressWarnings("unused") Thread hostThread) {
        // simply calling Thread.exit() will do most of what's needed
        // TODO(Gregersen) - /browse/GR-20077
    }

    public void interruptActiveThreads() {
        isClosing = true;
        invalidateNoThreadStop("Killing the VM");
        Thread initiatingThread = Thread.currentThread();
        for (StaticObject guest : threadManager.activeThreads()) {
            Thread t = Target_java_lang_Thread.getHostFromGuestThread(guest);
            if (t != initiatingThread) {
                try {
                    if (t.isDaemon()) {
                        Target_java_lang_Thread.killThread(guest);
                        Target_java_lang_Thread.interrupt0(guest);
                        t.join(10);
                        if (t.isAlive()) {
                            Target_java_lang_Thread.setThreadStop(guest, Target_java_lang_Thread.KillStatus.DISSIDENT);
                            t.join();
                        }
                    } else {
                        Target_java_lang_Thread.interrupt0(guest);
                        t.join();
                    }
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while stopping thread in closing context.");
                }
            }
        }

        hostToGuestReferenceDrainThread.interrupt();
        try {
            hostToGuestReferenceDrainThread.join();
        } catch (InterruptedException e) {
            // ignore
        }
        initiatingThread.interrupt();
    }

    private void initVmProperties() {
        EspressoProperties.Builder builder;
        if (EspressoOptions.RUNNING_ON_SVM) {
            builder = EspressoProperties.newPlatformBuilder() //
                            .javaHome(Engine.findHome().resolve("jre")) //
                            .espressoLibraryPath(Collections.singletonList(Paths.get(getLanguage().getEspressoHome()).resolve("lib")));
        } else {
            builder = EspressoProperties.inheritFromHostVM();
            String espressoLibraryPath = System.getProperty("espresso.library.path");
            if (espressoLibraryPath != null) {
                builder.espressoLibraryPath(Utils.parsePaths(espressoLibraryPath));
            }
        }

        vmProperties = EspressoProperties.processOptions(builder, getEnv().getOptions()).build();

    }

    private void initializeKnownClass(Symbol<Type> type) {
        Klass klass = getRegistries().loadKlassWithBootClassLoader(type);
        klass.safeInitialize();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public InterpreterToVM getInterpreterToVM() {
        return interpreterToVM;
    }

    public VM getVM() {
        return vm;
    }

    public Types getTypes() {
        return getLanguage().getTypes();
    }

    public Signatures getSignatures() {
        return getLanguage().getSignatures();
    }

    public JniEnv getJNI() {
        if (jniEnv == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            jniEnv = JniEnv.create(this);
        }
        return jniEnv;
    }

    public void disposeContext() {
        if (initialized) {
            getVM().dispose();
            getJNI().dispose();
        }
    }

    public Substitutions getSubstitutions() {
        return substitutions;
    }

    public void setBootstrapMeta(Meta meta) {
        this.meta = meta;
    }

    public Names getNames() {
        return getLanguage().getNames();
    }

    public MethodHandleIntrinsics getMethodHandleIntrinsics() {
        return methodHandleIntrinsics;
    }

    public EspressoException getStackOverflow() {
        return stackOverflow;
    }

    public EspressoException getOutOfMemory() {
        return outOfMemory;
    }
    // Thread management

    public StaticObject getGuestThreadFromHost(Thread host) {
        return threadManager.getGuestThreadFromHost(host);
    }

    public StaticObject getCurrentThread() {
        return threadManager.getGuestThreadFromHost(Thread.currentThread());
    }

    /**
     * Returns the maximum number of alive (registered) threads at any point, since the VM started.
     */
    public long getPeakThreadCount() {
        return threadManager.peakThreadCount.get();
    }

    /**
     * Returns the number of created threads since the VM started.
     */
    public long getCreatedThreadCount() {
        return threadManager.createdThreadCount.get();
    }

    public StaticObject[] getActiveThreads() {
        return threadManager.activeThreads();
    }

    public void registerThread(Thread host, StaticObject self) {
        threadManager.registerThread(host, self);
        if (eventListener != null) {
            eventListener.threadStarted(self);
        }
    }

    public void unregisterThread(StaticObject self) {
        threadManager.unregisterThread(self);
        if (eventListener != null) {
            eventListener.threadDied(self);
        }
    }

    public void invalidateNoThreadStop(String message) {
        noThreadDeprecationCalled.invalidate();
        noThreadStop.invalidate(message);
    }

    public boolean shouldCheckStop() {
        return !noThreadStop.isValid();
    }

    public void invalidateNoSuspend(String message) {
        noThreadDeprecationCalled.invalidate();
        noSuspend.invalidate(message);
    }

    public boolean shouldCheckDeprecationStatus() {
        return !noThreadDeprecationCalled.isValid();
    }

    public boolean shouldCheckSuspend() {
        return !noSuspend.isValid();
    }

    public boolean isClosing() {
        return isClosing;
    }

    // region Options

    // Checkstyle: stop field name check

    public final boolean InlineFieldAccessors;
    public final boolean InlineMethodHandle;
    public final boolean SplitMethodHandles;

    public final EspressoOptions.VerifyMode Verify;
    public final JDWPOptions JDWPOptions;
    public final EspressoOptions.SpecCompliancyMode SpecCompliancyMode;
    public final boolean EnableManagement;

    public EspressoOptions.SpecCompliancyMode specCompliancyMode() {
        return SpecCompliancyMode;
    }

    // Checkstyle: resume field name check

    // endregion Options

    public boolean isMainThreadCreated() {
        return mainThreadCreated;
    }

    public StaticObject getMainThread() {
        return threadManager.getMainThread();
    }

    public StaticObject getMainThreadGroup() {
        return mainThreadGroup;
    }

    public void prepareDispose() {
        jdwpContext.finalizeContext();
    }

    public void begin() {
        this.contextReady = true;
    }

    public boolean canEnterOtherThread() {
        return contextReady;
    }
}
