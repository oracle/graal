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
package com.oracle.truffle.espresso.runtime;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.oracle.truffle.espresso.debugger.VMEventListeners;
import com.oracle.truffle.espresso.debugger.jdwp.JDWPDebuggerController;
import com.oracle.truffle.espresso.debugger.jdwp.JDWPInstrument;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

public final class EspressoContext {

    public static final int DEFAULT_STACK_SIZE = 32;
    public static StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];

    private final EspressoLanguage language;
    private final TruffleLanguage.Env env;
    private final StringTable strings;
    private final ClassRegistries registries;
    private final Substitutions substitutions;
    private final MethodHandleIntrinsics methodHandleIntrinsics;

    private final ConcurrentHashMap<Thread, StaticObject> host2guest = new ConcurrentHashMap<>();
    private final Set<Thread> activeThreads = Collections.newSetFromMap(new ConcurrentHashMap<Thread, Boolean>());

    private final AtomicInteger klassIdProvider = new AtomicInteger();

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

    public EspressoContext(TruffleLanguage.Env env, EspressoLanguage language) {
        this.env = env;
        this.language = language;
        this.registries = new ClassRegistries(this);
        this.strings = new StringTable(this);
        this.substitutions = new Substitutions(this);
        this.methodHandleIntrinsics = new MethodHandleIntrinsics(this);

        this.InlineFieldAccessors = env.getOptions().get(EspressoOptions.InlineFieldAccessors);
        this.Verify = env.getOptions().get(EspressoOptions.Verify);
        this.JDWPOptions = env.getOptions().get(EspressoOptions.JDWPOptions); // null if not
                                                                              // specified
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
                            getVmProperties().bootClasspath().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
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
        jdwpInit();
        spawnVM();
        this.initialized = true;
        VMInitializedListeners.getDefault().fire();
    }

    private void jdwpInit() {
        // enable JDWP instrumenter only if options are set (assumed valid if non-null)
        if (JDWPOptions != null) {
            JDWPDebuggerController controller = env.lookup(env.getInstruments().get(JDWPInstrument.ID), JDWPDebuggerController.class);
            controller.initialize(JDWPOptions, this);
        }
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

    public Meta getMeta() {
        return meta;
    }

    private void spawnVM() {

        long ticks = System.currentTimeMillis();

        initVmProperties();

        this.meta = new Meta(this);

        this.interpreterToVM = new InterpreterToVM(this);

        // Spawn JNI first, then the VM.
        this.vm = VM.create(getJNI()); // Mokapot is loaded

        initializeKnownClass(Type.Object);

        for (Symbol<Type> type : Arrays.asList(
                        Type.String,
                        Type.System,
                        Type.ThreadGroup,
                        Type.Thread,
                        Type.Class,
                        Type.Method)) {
            initializeKnownClass(type);
        }

        createMainThread();

        // Finalizer is not public.
        initializeKnownClass(Type.java_lang_ref_Finalizer);

        meta.System_initializeSystemClass.invokeDirect(null);

        // System exceptions.
        for (Symbol<Type> type : Arrays.asList(
                        Type.OutOfMemoryError,
                        Type.NullPointerException,
                        Type.ClassCastException,
                        Type.ArrayStoreException,
                        Type.ArithmeticException,
                        Type.StackOverflowError,
                        Type.IllegalMonitorStateException,
                        Type.IllegalArgumentException)) {
            initializeKnownClass(type);
        }

        // Init memoryError instances
        StaticObject stackOverflowErrorInstance = meta.StackOverflowError.allocateInstance();
        StaticObject outOfMemoryErrorInstance = meta.OutOfMemoryError.allocateInstance();
        meta.StackOverflowError.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(stackOverflowErrorInstance, meta.toGuestString("VM StackOverFlow"));
        meta.OutOfMemoryError.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(outOfMemoryErrorInstance, meta.toGuestString("VM OutOfMemory"));
        this.stackOverflow = new EspressoException(stackOverflowErrorInstance);
        this.outOfMemory = new EspressoException(outOfMemoryErrorInstance);

        System.err.println("spawnVM: " + (System.currentTimeMillis() - ticks) + " ms");
    }

    /**
     * The order in which methods are called and fields are set here is important, it mimics
     * HotSpot's implementation.
     */
    private void createMainThread() {

        StaticObject systemThreadGroup = meta.ThreadGroup.allocateInstance();
        meta.ThreadGroup.lookupDeclaredMethod(Name.INIT, Signature._void) // private ThreadGroup()
                        .invokeDirect(systemThreadGroup);

        StaticObject mainThread = meta.Thread.allocateInstance();
        StaticObject threadGroup = meta.ThreadGroup.allocateInstance();
        meta.ThreadGroup_maxPriority.set(threadGroup, Thread.MAX_PRIORITY);
        meta.Thread_group.set(mainThread, threadGroup);
        meta.Thread_name.set(mainThread, meta.toGuestString("mainThread"));
        meta.Thread_priority.set(mainThread, 5);
        mainThread.setHiddenField(meta.HIDDEN_HOST_THREAD, Thread.currentThread());
        mainThread.setIntField(meta.Thread_threadStatus, Target_java_lang_Thread.State.RUNNABLE.value);
        putHost2Guest(Thread.currentThread(), mainThread);
        meta.Thread_priority.set(mainThread, Thread.NORM_PRIORITY);

        // Allow guest Thread.currentThread() to work.
        mainThread.setHiddenField(this.meta.HIDDEN_HOST_THREAD, Thread.currentThread());
        host2guest.put(Thread.currentThread(), mainThread);
        activeThreads.add(Thread.currentThread());

        StaticObject mainThreadGroup = meta.ThreadGroup.allocateInstance();
        meta.ThreadGroup // public ThreadGroup(ThreadGroup parent, String name)
                        .lookupDeclaredMethod(Name.INIT, Signature._void_ThreadGroup_String) //
                        .invokeDirect(mainThreadGroup,
                                        /* parent */ systemThreadGroup,
                                        /* name */ meta.toGuestString("main"));

        meta.Thread // public Thread(ThreadGroup group, String name)
                        .lookupDeclaredMethod(Name.INIT, Signature._void_ThreadGroup_String) //
                        .invokeDirect(mainThread,
                                        /* group */ mainThreadGroup,
                                        /* name */ meta.toGuestString("main"));

        meta.Thread_threadStatus.set(mainThread, /* JVMTI_THREAD_STATE_ALIVE */ 0x01 + /* JVMTI_THREAD_STATE_RUNNABLE */ 0x04);
    }

    public void interruptActiveThreads() {
        Thread initiatingThread = Thread.currentThread();
        for (Thread t : activeThreads) {
            if (t != initiatingThread) {
                try {
                    if (t.isDaemon()) {
                        t.interrupt();
                        t.join();
                    } else {
                        t.join();
                    }
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while stopping thread in closing context.");
                }
            }
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
            CompilerAsserts.neverPartOfCompilation();
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

    public final Names getNames() {
        return getLanguage().getNames();
    }

    public final MethodHandleIntrinsics getMethodHandleIntrinsics() {
        return methodHandleIntrinsics;
    }

    public final EspressoException getStackOverflow() {
        return stackOverflow;
    }

    public EspressoException getOutOfMemory() {
        return outOfMemory;
    }

    public void putHost2Guest(Thread hostThread, StaticObject guest) {
        host2guest.put(hostThread, guest);
        VMEventListeners.getDefault().threadStarted(guest);
    }

    public StaticObject getHost2Guest(Thread hostThread) {
        return host2guest.get(hostThread);
    }

    public void registerThread(Thread thread) {
        activeThreads.add(thread);

    }

    public void unregisterThread(Thread thread) {
        activeThreads.remove(thread);
        StaticObject staticObject = host2guest.get(thread);
        if (staticObject != null) {
            VMEventListeners.getDefault().threadDied(staticObject);
        }
    }

    public StaticObject[] getAllGuestThreads() {
        return host2guest.values().toArray(new StaticObject[0]);
    }

    // region Options

    public final boolean InlineFieldAccessors;
    public final EspressoOptions.VerifyMode Verify;
    public final EspressoOptions.JDWPOptions JDWPOptions;

    // endregion Options
}
