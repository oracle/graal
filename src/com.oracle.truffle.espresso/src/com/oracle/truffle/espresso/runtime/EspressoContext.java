/*******************************************************************************
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
 *******************************************************************************/

package com.oracle.truffle.espresso.runtime;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.classfile.StringTable;
import com.oracle.truffle.espresso.classfile.SymbolTable;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.types.SignatureDescriptors;
import com.oracle.truffle.espresso.types.TypeDescriptors;

public class EspressoContext {

    private final EspressoLanguage language;

    private final TruffleLanguage.Env env;
    private final InterpreterToVM vm;

    private boolean initialized = false;

    private Classpath classpath;
    private Classpath bootClasspath;

    private final StringTable strings;
    private String[] mainArguments;
    private Source mainSourceFile;

    private Object appClassLoader;

    public ClassRegistries getRegistries() {
        return registries;
    }

    private final ClassRegistries registries;
    private Meta meta;
    private StaticObject mainThread;

    public EspressoContext(TruffleLanguage.Env env, EspressoLanguage language) {
        this.env = env;
        this.language = language;
        this.vm = new InterpreterToVM(language);
        this.registries = new ClassRegistries(this);
        this.strings = new StringTable(this);
        this.meta = new Meta(this);
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

    public void setMainArguments(String[] mainArguments) {
        this.mainArguments = mainArguments;
    }

    /**
     * @return The {@link String}[] array passed to the main function.
     */
    public String[] getMainArguments() {
        return mainArguments;
    }

    public void setMainSourceFile(Source mainSourceFile) {
        this.mainSourceFile = mainSourceFile;
    }

    public void setClasspath(Classpath classpath) {
        this.classpath = classpath;
    }

    public void setBootClasspath(Classpath bootClasspath) {
        this.bootClasspath = bootClasspath;
    }

    public Classpath getClasspath() {
        return classpath;
    }

    public Classpath getBootClasspath() {
        return bootClasspath;
    }

    /**
     * @return The source code unit of the main function.
     */
    public Source getMainSourceFile() {
        return mainSourceFile;
    }

    public void initializeContext() {
        assert !this.initialized;
        //this.refractor = new Refractor(this);
        createVm();
        this.initialized = true;
    }

    private void createVm() {

        initializeClass(Object.class);

        // Initialize primitive classes.
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive()) {
                initializeClass(kind.toJavaClass());
                // initializeClass(kind.toBoxedJavaClass());
            }
        }

        Stream.of(
                        String.class,
                        System.class,
                        ThreadGroup.class,
                        Thread.class,
                        Class.class,
                        Method.class).forEachOrdered(this::initializeClass);

        // Finalizer is not public.
        initializeClass("Ljava/lang/ref/Finalizer;");

        // Call System.initializeSystemClass
        {
            //MethodInfo initializeSystemClass = systemKlass.findDeclaredMethod("initializeSystemClass", void.class);
            meta.knownKlass(System.class).method("initializeSystemClass", void.class).invoke();
            //initializeSystemClass.getCallTarget().call();
        }

        // System exceptions.
        Stream.of(
                        OutOfMemoryError.class,
                        NullPointerException.class,
                        ClassCastException.class,
                        ArrayStoreException.class,
                        ArithmeticException.class,
                        StackOverflowError.class,
                        IllegalMonitorStateException.class,
                        IllegalArgumentException.class).forEachOrdered(this::initializeClass);

        // Load system class loader.
        {
            appClassLoader = meta.knownKlass(ClassLoader.class).method("getSystemClassLoader", ClassLoader.class).invoke(null);
            //Klass classLoaderKlass = getRegistries().resolve(getTypeDescriptors().make("Ljava/lang/ClassLoader;"), null);
            //MethodInfo getSystemClassLoader = classLoaderKlass.findDeclaredMethod("getSystemClassLoader", ClassLoader.class);
            //appClassLoader = getSystemClassLoader.getCallTarget().call();
        }
    }

    private void initializeClass(Class<?> clazz) {
        initializeClass(MetaUtil.toInternalName(clazz.getName()));
    }

    private void initializeClass(String name) {
        Klass klass = getRegistries().resolve(getTypeDescriptors().make(name), null);
        klass.initialize();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public InterpreterToVM getVm() {
        return vm;
    }

    public StaticObject getMainThread() {
        return mainThread;
    }

    public void setMainThread(StaticObject mainThread) {
        this.mainThread = mainThread;
    }

    public SymbolTable getSymbolTable() {
        return getLanguage().getSymbolTable();
    }

    public TypeDescriptors getTypeDescriptors() {
        return getLanguage().getTypeDescriptors();
    }

    public SignatureDescriptors getSignatureDescriptors() {
        return getLanguage().getSignatureDescriptors();
    }

    public Object getAppClassLoader() {
        return appClassLoader;
    }
}
