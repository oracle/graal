/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.MethodVisitor;
import com.oracle.truffle.api.impl.asm.Opcodes;

import jdk.internal.module.Modules;

/*
 * We want to avoid exporting the Modules class to all classes in the unnamed module. So instead we
 * load it in an isolated class loader and module layer.
 */
abstract sealed class ModulesAccessor permits ModulesAccessor.DirectImpl, ModulesAccessor.IsolatedImpl {

    /**
     * @see Modules#addExports(Module, String, Module)
     */
    abstract void addExports(Module base, String p, Module target);

    /**
     * @see Modules#addExportsToAllUnnamed(Module, String)
     */
    abstract void addExportsToAllUnnamed(Module base, String p);

    /**
     * @see Modules#addOpens(Module, String, Module)
     */
    abstract void addOpens(Module base, String p, Module target);

    /**
     * @see Modules#addOpensToAllUnnamed(Module, String)
     */
    abstract void addOpensToAllUnnamed(Module base, String p);

    abstract Module getTargetModule();

    static ModulesAccessor create(Class<?> accessingClass) {
        if (accessingClass.getModule().isNamed()) {
            return new DirectImpl(accessingClass);
        } else {
            try {
                return new IsolatedImpl(accessingClass);
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }
    }

    /*
     * When using a named module we do not need to do anything and we can just call the Modules
     * class. We used a qualified export to export it to this class.
     */
    static final class DirectImpl extends ModulesAccessor {

        private final Class<?> baseClass;

        DirectImpl(Class<?> baseClass) {
            if (!baseClass.getModule().isNamed()) {
                throw new IllegalStateException("");
            }
            this.baseClass = baseClass;
        }

        @Override
        void addExports(Module base, String p, Module target) {
            Modules.addExports(base, p, target);
        }

        @Override
        void addExportsToAllUnnamed(Module base, String p) {
            Modules.addExportsToAllUnnamed(base, p);
        }

        @Override
        void addOpens(Module base, String p, Module target) {
            Modules.addOpens(base, p, target);
        }

        @Override
        void addOpensToAllUnnamed(Module base, String p) {
            Modules.addOpensToAllUnnamed(base, p);
        }

        @Override
        Module getTargetModule() {
            return baseClass.getModule();
        }
    }

    /*
     * When using a named module we do not need to do anything and we can just call the Modules
     * class. We used a qualified export to export it to this class.
     */
    static final class IsolatedImpl extends ModulesAccessor {

        private final MethodHandle addExports;
        private final MethodHandle addExportsToAllUnnamed;
        private final MethodHandle addOpens;
        private final MethodHandle addOpensToAllUnnamed;
        private final Module targetModule;

        IsolatedImpl(Class<?> baseClass) throws ReflectiveOperationException {
            final String moduleName = "org.graalvm.truffle.runtime.generated";
            final String targetPackage = baseClass.getPackageName() + ".generated";
            final String className = targetPackage + ".GeneratedModules";
            final String binaryClassName = className.replace('.', '/');

            ClassWriter cw = new ClassWriter(0);
            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, binaryClassName, null, "java/lang/Object", null);

            MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(1, 1);
            constructor.visitEnd();

            MethodVisitor mv1 = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "addExports",
                            "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V", null, null);
            mv1.visitCode();
            mv1.visitVarInsn(Opcodes.ALOAD, 0); // Load first argument (Module base)
            mv1.visitVarInsn(Opcodes.ALOAD, 1); // Load second argument (String p)
            mv1.visitVarInsn(Opcodes.ALOAD, 2); // Load third argument (Module target)
            mv1.visitMethodInsn(Opcodes.INVOKESTATIC, "jdk/internal/module/Modules", "addExports",
                            "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V", false);
            mv1.visitInsn(Opcodes.RETURN);
            mv1.visitMaxs(3, 3);
            mv1.visitEnd();

            MethodVisitor mv2 = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "addExportsToAllUnnamed",
                            "(Ljava/lang/Module;Ljava/lang/String;)V", null, null);
            mv2.visitCode();
            mv2.visitVarInsn(Opcodes.ALOAD, 0); // Load first argument (Module target)
            mv2.visitVarInsn(Opcodes.ALOAD, 1); // Load second argument (String p)
            mv2.visitMethodInsn(Opcodes.INVOKESTATIC, "jdk/internal/module/Modules", "addExportsToAllUnnamed",
                            "(Ljava/lang/Module;Ljava/lang/String;)V", false);
            mv2.visitInsn(Opcodes.RETURN);
            mv2.visitMaxs(2, 2);
            mv2.visitEnd();

            MethodVisitor mv3 = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "addOpens",
                            "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V", null, null);
            mv3.visitCode();
            mv3.visitVarInsn(Opcodes.ALOAD, 0); // Load first argument (Module base)
            mv3.visitVarInsn(Opcodes.ALOAD, 1); // Load second argument (String p)
            mv3.visitVarInsn(Opcodes.ALOAD, 2); // Load third argument (Module target)
            mv3.visitMethodInsn(Opcodes.INVOKESTATIC, "jdk/internal/module/Modules", "addOpens",
                            "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V", false);
            mv3.visitInsn(Opcodes.RETURN);
            mv3.visitMaxs(3, 3);
            mv3.visitEnd();

            MethodVisitor mv4 = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "addOpensToAllUnnamed",
                            "(Ljava/lang/Module;Ljava/lang/String;)V", null, null);
            mv4.visitCode();
            mv4.visitVarInsn(Opcodes.ALOAD, 0); // Load first argument (Module target)
            mv4.visitVarInsn(Opcodes.ALOAD, 1); // Load second argument (String p)
            mv4.visitMethodInsn(Opcodes.INVOKESTATIC, "jdk/internal/module/Modules", "addOpensToAllUnnamed",
                            "(Ljava/lang/Module;Ljava/lang/String;)V", false);
            mv4.visitInsn(Opcodes.RETURN);
            mv4.visitMaxs(2, 2);
            mv4.visitEnd();

            cw.visitEnd();

            byte[] classBytes = cw.toByteArray();

            // Create a ModuleDescriptor for the new module
            ModuleDescriptor descriptor = ModuleDescriptor.newModule(moduleName).exports(targetPackage).build();

            // Create a ModuleFinder that finds the module and class
            ModuleFinder finder = new ModuleFinder() {

                public Optional<ModuleReference> find(String name) {
                    if (name.equals(moduleName)) {
                        return Optional.of(new ModuleReference(descriptor, null) {
                            @Override
                            @SuppressWarnings("hiding")
                            public ModuleReader open() throws IOException {
                                return new ModuleReader() {
                                    @Override
                                    public Optional<ByteBuffer> read(String name) throws IOException {
                                        if (name.equals(binaryClassName + ".class")) {
                                            return Optional.of(ByteBuffer.wrap(classBytes));
                                        }
                                        return Optional.empty();
                                    }

                                    @Override
                                    public void close() throws IOException {
                                    }

                                    public Optional<URI> find(String name) throws IOException {
                                        return Optional.empty();
                                    }

                                    public Stream<String> list() throws IOException {
                                        return Stream.empty();
                                    }
                                };
                            }
                        });
                    }
                    return Optional.empty();
                }

                @Override
                public Set<ModuleReference> findAll() {
                    return Collections.singleton(find(moduleName).get());
                }
            };

            // Resolve the configuration for the new module layer based on the boot layer
            ModuleLayer bootLayer = ModuleLayer.boot();
            Configuration cf = bootLayer.configuration().resolve(finder, ModuleFinder.of(), Collections.singleton(moduleName));

            // Define a new module layer with the correct parent layer and class loader
            ModuleLayer layer = bootLayer.defineModulesWithOneLoader(cf, ClassLoader.getSystemClassLoader());

            // Load the class from the module layer
            Class<?> generatedClass = layer.findLoader(moduleName).loadClass(className);
            this.targetModule = generatedClass.getModule();

            Lookup l = MethodHandles.lookup();
            l.accessClass(generatedClass);
            this.addExports = l.findStatic(generatedClass, "addExports", MethodType.methodType(void.class, Module.class, String.class, Module.class));
            this.addExportsToAllUnnamed = l.findStatic(generatedClass, "addExportsToAllUnnamed", MethodType.methodType(void.class, Module.class, String.class));
            this.addOpens = l.findStatic(generatedClass, "addOpens", MethodType.methodType(void.class, Module.class, String.class, Module.class));
            this.addOpensToAllUnnamed = l.findStatic(generatedClass, "addOpensToAllUnnamed", MethodType.methodType(void.class, Module.class, String.class));
        }

        @Override
        Module getTargetModule() {
            return targetModule;
        }

        @Override
        public void addExports(Module base, String p, Module target) {
            try {
                addExports.invokeExact(base, p, target);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }

        @Override
        public void addExportsToAllUnnamed(Module base, String p) {
            try {
                addExportsToAllUnnamed.invokeExact(base, p);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }

        @Override
        void addOpens(Module base, String p, Module target) {
            try {
                addOpens.invokeExact(base, p, target);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }

        @Override
        void addOpensToAllUnnamed(Module base, String p) {
            try {
                addOpensToAllUnnamed.invokeExact(base, p);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }
    }
}
