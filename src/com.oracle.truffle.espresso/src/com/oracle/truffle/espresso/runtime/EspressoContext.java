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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

public final class EspressoContext {

    private final EspressoLanguage language;

    private final TruffleLanguage.Env env;

    // Must be initialized after the context instance creation.
    private InterpreterToVM interpreterToVM;
    private final StringTable strings;
    private final ClassRegistries registries;
    private boolean initialized = false;
    private Classpath bootClasspath;
    private String[] mainArguments;
    private Source mainSourceFile;
    private StaticObject appClassLoader;
    private Meta meta;
    private StaticObject mainThread;

    @CompilationFinal //
    private JniEnv jniEnv;

    @CompilationFinal //
    private VM vm;

    public EspressoContext(TruffleLanguage.Env env, EspressoLanguage language) {
        this.env = env;
        this.language = language;
        this.registries = new ClassRegistries(this);
        this.strings = new StringTable(this);
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
            bootClasspath = new Classpath(getVmProperties().getBootClasspath());
        }
        return bootClasspath;
    }

    public EspressoProperties getVmProperties() {
        if (vmProperties == null) {
            throw EspressoError.shouldNotReachHere();
        }
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
        createVm();
        this.initialized = true;
    }

    public Meta getMeta() {
        return meta;
    }

    private void createVm() {
        // FIXME(peterssen): Contextualize the JniENv, even if shared libraries are isolated,
        // currently we assume a singleton context.

        initVmProperties();

        this.meta = new Meta(this);

        this.interpreterToVM = new InterpreterToVM(language);
        // Spawn JNI first, then the VM.
        this.vm = VM.create(getJNI()); // Mokapot is loaded

        initializeClass(Object.class);

        // Primitive classes have no dependencies.
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive()) {
                initializeClass(kind.toJavaClass());
            }
        }

        for (Class<?> clazz : new Class<?>[]{
                        String.class,
                        System.class,
                        ThreadGroup.class,
                        Thread.class,
                        Class.class,
                        Method.class}) {
            initializeClass(clazz);
        }

        // Finalizer is not public.
        initializeClass("Ljava/lang/ref/Finalizer;");

        // Call System.initializeSystemClass
        meta.knownKlass(System.class).staticMethod("initializeSystemClass", void.class).invokeDirect();

        // System exceptions.
        for (Class<?> clazz : new Class<?>[]{
                        OutOfMemoryError.class,
                        NullPointerException.class,
                        ClassCastException.class,
                        ArrayStoreException.class,
                        ArithmeticException.class,
                        StackOverflowError.class,
                        IllegalMonitorStateException.class,
                        IllegalArgumentException.class}) {
            initializeClass(clazz);
        }

        // Load system class loader.
        appClassLoader = (StaticObject) meta.knownKlass(ClassLoader.class).staticMethod("getSystemClassLoader", ClassLoader.class).invokeDirect();
    }

    private EspressoProperties vmProperties;

    private void initVmProperties() {
        vmProperties = EspressoProperties.getDefault().processOptions(getEnv().getOptions());
    }

    private void initializeClass(Class<?> clazz) {
        initializeClass(MetaUtil.toInternalName(clazz.getName()));
    }

    private void initializeClass(String name) {
        Klass klass = getRegistries().resolve(getTypes().make(name), StaticObject.NULL);
        klass.initialize();
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

    public StaticObject getMainThread() {
        return mainThread;
    }

    public void setMainThread(StaticObject mainThread) {
        this.mainThread = mainThread;
    }

//
//    public SymbolTable getSymbolTable() {
//        return getLanguage().getSymbolTable();
//    }

    public Types getTypes() {
        return getLanguage().getTypes();
    }

    public Signatures getSignatures() {
        return getLanguage().getSignatures();
    }

    public StaticObject getAppClassLoader() {
        return appClassLoader;
    }

    public JniEnv getJNI() {
        if (jniEnv == null) {
            CompilerAsserts.neverPartOfCompilation();
            jniEnv = JniEnv.create(this);
        }
        return jniEnv;
    }

    public void disposeContext() {
        getJNI().dispose();
    }
}
