/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.InternalResource.OS;
import com.oracle.truffle.api.InternalResource.CPUArchitecture;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.Accessor.JavaLangSupport;
import com.oracle.truffle.api.impl.Accessor.ModulesAccessor;
import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.FieldVisitor;
import com.oracle.truffle.api.impl.asm.MethodVisitor;
import com.oracle.truffle.api.impl.asm.Opcodes;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.module.Modules;

final class JDKSupport {

    /*
     * We use reflective access to addEnableAccess and addEnableNativeAccessToAllUnnamed to make
     * Truffle compilable on JDK-17.
     */
    private static final Method ADD_ENABLE_NATIVE_ACCESS;
    private static final Method ADD_ENABLE_NATIVE_ACCESS_TO_ALL_UNNAMED;
    static {
        if (Runtime.version().feature() >= 21) {
            try {
                Class<?> langAccessClass = Class.forName("jdk.internal.access.JavaLangAccess");
                ADD_ENABLE_NATIVE_ACCESS = langAccessClass.getDeclaredMethod("addEnableNativeAccess", Module.class);
                ADD_ENABLE_NATIVE_ACCESS_TO_ALL_UNNAMED = langAccessClass.getDeclaredMethod("addEnableNativeAccessToAllUnnamed");
            } catch (ReflectiveOperationException re) {
                throw new InternalError(re);
            }
        } else {
            // On JDK-17 do nothing
            ADD_ENABLE_NATIVE_ACCESS = null;
            ADD_ENABLE_NATIVE_ACCESS_TO_ALL_UNNAMED = null;
        }
    }

    private static final ModulesAccessor MODULES_ACCESSOR = initializeModuleAccessor();

    @SuppressWarnings("restricted")
    private static ModulesAccessor initializeModuleAccessor() {
        String attachLibPath = System.getProperty("truffle.attach.library");
        if (attachLibPath == null) {
            if (isUnsupportedPlatform()) {
                performTruffleAttachLoadFailureAction("Truffle is running on an unsupported platform where the TruffleAttach library is unavailable.", null);
                return null;
            }
            try {
                Path truffleAttachRoot = InternalResourceCache.installRuntimeResource(new LibTruffleAttachResource(), LibTruffleAttachResource.ID);
                Path libAttach = truffleAttachRoot.resolve("bin").resolve(System.mapLibraryName("truffleattach"));
                attachLibPath = libAttach.toString();
            } catch (IOException ioe) {
                performTruffleAttachLoadFailureAction("The Truffle API JAR is missing the 'truffleattach' resource, likely due to issues when Truffle was repackaged into a fat JAR.", ioe);
                return null;
            }
        }
        try {
            try {
                System.load(attachLibPath);
            } catch (UnsatisfiedLinkError failedToLoad) {
                performTruffleAttachLoadFailureAction("Unable to load the TruffleAttach library.", failedToLoad);
                return null;
            } catch (IllegalCallerException illegalCaller) {
                String vmOption = "--enable-native-access=" + (JDKSupport.class.getModule().isNamed() ? "org.graalvm.truffle" : "ALL-UNNAMED");
                performTruffleAttachLoadFailureAction(String.format("Failed to load the TruffleAttach library. The Truffle module does not have native access enabled. " +
                                "To resolve this, pass the following VM option: %s.", vmOption), illegalCaller);
                return null;
            }
            ModulesAccessor accessor;
            if (ModulesAccessor.class.getModule().isNamed()) {
                accessor = new DirectImpl(Accessor.ModulesAccessor.class);
            } else {
                accessor = new IsolatedImpl(Accessor.ModulesAccessor.class);
            }
            Module javaBase = ModuleLayer.boot().findModule("java.base").orElseThrow();
            addExports0(javaBase, "jdk.internal.module", accessor.getTargetModule());
            addExports0(javaBase, "jdk.internal.access", accessor.getTargetModule());
            return accessor;
        } catch (ReflectiveOperationException re) {
            throw new InternalError(re);
        }
    }

    private static boolean isUnsupportedPlatform() {
        try {
            return OS.getCurrent() == OS.UNSUPPORTED || CPUArchitecture.getCurrent() == CPUArchitecture.UNSUPPORTED;
        } catch (IllegalStateException ise) {
            /*
             * We suppress this error in the JDKSupport static initializer to prevent it from being
             * obscured by a long and unreadable chain of exception causes. If this error occurs
             * here, it will be handled later during engine creation.
             */
            return true;
        }
    }

    private static native void addExports0(Module m1, String pn, Module m2);

    private static void performTruffleAttachLoadFailureAction(String reason, Throwable t) {
        String action = System.getProperty("polyglotimpl.AttachLibraryFailureAction", "warn");
        switch (action) {
            case "ignore" -> {
            }
            case "warn" -> {
                PolyglotEngineImpl.logFallback(formatErrorMessage(reason));
            }
            case "diagnose" -> {
                StringWriter message = new StringWriter();
                try (PrintWriter err = new PrintWriter(message)) {
                    err.println(formatErrorMessage(reason));
                    t.printStackTrace(err);
                }
                PolyglotEngineImpl.logFallback(message.toString());
            }
            case "throw" -> throw new InternalError(formatErrorMessage(reason), t);
            default -> throw new IllegalArgumentException("Invalid polyglotimpl.AttachLibraryFailureAction system property value. Supported values are ignore, warn, diagnose, throw");
        }
    }

    private static String formatErrorMessage(String reason) {
        return String.format(
                        """
                                        [engine] WARNING: %s
                                        As a result, the optimized Truffle runtime is unavailable, and Truffle cannot provide native access to languages and tools.
                                        To customize the behavior of this warning, use the 'polyglotimpl.AttachLibraryFailureAction' system property.
                                        Allowed values are:
                                          - ignore:    Do not print this warning.
                                          - warn:      Print this warning (default value).
                                          - diagnose:  Print this warning along with the exception cause.
                                          - throw:     Throw an exception instead of printing this warning.
                                        """, reason);
    }

    private JDKSupport() {
    }

    static void exportTransitivelyTo(Module clientModule) {
        if (isExportedTo(clientModule)) {
            return;
        }
        Module truffleModule = Truffle.class.getModule();
        forEach(clientModule, EnumSet.of(Edge.READS, Edge.USES), (m) -> m != truffleModule && m.canRead(truffleModule), JDKSupport::exportFromTo);
    }

    static void enableNativeAccess(Module clientModule) {
        forEach(clientModule, EnumSet.of(Edge.READS), (m) -> true, (m) -> {
            ModulesAccessor accessor = getModulesAccessor();
            /*
             * The accessor is null if libtruffleattach cannot be loaded, such as when Truffle with
             * a fallback runtime is loaded by multiple class loaders. In this case, we do not
             * delegate enable-native-access and let the JDK emit native access warnings.
             */
            if (accessor != null) {
                if (m.isNamed()) {
                    getModulesAccessor().addEnableNativeAccess(m);
                } else {
                    getModulesAccessor().addEnableNativeAccessToAllUnnamed();
                }
            }
        });
    }

    @SuppressWarnings("restricted")
    static ModulesAccessor getModulesAccessor() {
        return MODULES_ACCESSOR;
    }

    private static void forEach(Module rootModule, Set<Edge> edges, Predicate<? super Module> filter, Consumer<? super Module> action) {
        ModuleLayer layer = rootModule.getLayer();
        Set<Module> targetModules = new HashSet<>();
        Deque<Module> todo = new ArrayDeque<>();
        /*
         * The module graph with reads and provides edges is not a DAG. We need to keeping track of
         * visited modules to detect cycles.
         */
        Set<Module> visited = new HashSet<>();
        todo.add(rootModule);
        Map<String, Set<Module>> serviceDictionary = null;
        while (!todo.isEmpty()) {
            Module module = todo.removeFirst();
            if (visited.add(module) && Objects.equals(module.getLayer(), layer) && filter.test(module)) {
                targetModules.add(module);
                ModuleDescriptor descriptor = module.getDescriptor();
                if (descriptor == null) {
                    /*
                     * Unnamed module: Deprecated. The unnamed module does not have a module
                     * descriptor, but reads the entire module graph. For unnamed modules we do not
                     * do transitive export because we would have to open the Truffle module to all
                     * modules in the module layer.
                     */
                } else if (descriptor.isAutomatic()) {
                    /*
                     * Automatic module: An unnamed module has an artificial module descriptor, with
                     * only the mandated `requires java.base` directive. But an automatic module
                     * reads the entire module graph. For automatic modules we do not do transitive
                     * export because we would have to open the Truffle module to all modules in the
                     * module layer.
                     */
                } else {
                    /*
                     * Named module with a module descriptor: Export transitively to all modules
                     * required by the named module.
                     */
                    if (edges.contains(Edge.READS)) {
                        for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                            Module requiredModule = findModule(layer, requires);
                            if (requiredModule != null) {
                                todo.add(requiredModule);
                            }
                        }
                    }
                    if (edges.contains(Edge.USES)) {
                        // Open also to modules providing a service consumed by the module.
                        Set<String> usedServices = descriptor.uses();
                        if (!usedServices.isEmpty()) {
                            if (serviceDictionary == null) {
                                serviceDictionary = new HashMap<>();
                                for (Module m : layer.modules()) {
                                    if (filter.test(m)) {
                                        for (ModuleDescriptor.Provides provides : m.getDescriptor().provides()) {
                                            serviceDictionary.computeIfAbsent(provides.service(), (k) -> new HashSet<>()).add(m);
                                        }
                                    }
                                }
                            }
                            for (String service : usedServices) {
                                todo.addAll(serviceDictionary.getOrDefault(service, Set.of()));
                            }
                        }
                    }
                }
            }
        }
        targetModules.forEach(action);
    }

    private static Module findModule(ModuleLayer layer, ModuleDescriptor.Requires requires) {
        Optional<Module> moduleOrNull = layer.findModule(requires.name());
        if (moduleOrNull.isPresent()) {
            return moduleOrNull.get();
        } else if (requires.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC)) {
            // Optional runtime dependency may not be available.
            return null;
        } else {
            throw new AssertionError(String.format("A non-optional module %s not found in the module layer %s.", requires.name(), layer));
        }
    }

    private static boolean isExportedTo(Module clientModule) {
        Module truffleModule = Truffle.class.getModule();
        for (String pack : truffleModule.getPackages()) {
            if (!truffleModule.isExported(pack, clientModule)) {
                return false;
            }
        }
        return true;
    }

    private static void exportFromTo(Module clientModule) {
        Module truffleModule = Truffle.class.getModule();
        if (truffleModule != clientModule) {
            Set<String> packages = truffleModule.getPackages();
            for (String pkg : packages) {
                boolean exported = truffleModule.isExported(pkg, clientModule);
                if (!exported) {
                    truffleModule.addExports(pkg, clientModule);
                }
            }
        }
    }

    private enum Edge {
        READS,
        USES
    }

    /*
     * When using a named module we do not need to do anything and we can just call the Modules
     * class. We used a qualified export to export it to this class.
     */
    private static final class DirectImpl extends Accessor.ModulesAccessor {

        private final Class<?> baseClass;

        DirectImpl(Class<?> baseClass) {
            if (!baseClass.getModule().isNamed()) {
                throw new IllegalStateException("");
            }
            this.baseClass = baseClass;
        }

        @Override
        public void addExports(Module base, String p, Module target) {
            Modules.addExports(base, p, target);
        }

        @Override
        public void addExportsToAllUnnamed(Module base, String p) {
            Modules.addExportsToAllUnnamed(base, p);
        }

        @Override
        public void addOpens(Module base, String p, Module target) {
            Modules.addOpens(base, p, target);
        }

        @Override
        public void addOpensToAllUnnamed(Module base, String p) {
            Modules.addOpensToAllUnnamed(base, p);
        }

        @Override
        public void addEnableNativeAccess(Module module) {
            if (ADD_ENABLE_NATIVE_ACCESS != null) {
                try {
                    ADD_ENABLE_NATIVE_ACCESS.invoke(SharedSecrets.getJavaLangAccess(), module);
                } catch (ReflectiveOperationException re) {
                    throw new InternalError(re);
                }
            }
        }

        @Override
        public void addEnableNativeAccessToAllUnnamed() {
            if (ADD_ENABLE_NATIVE_ACCESS_TO_ALL_UNNAMED != null) {
                try {
                    ADD_ENABLE_NATIVE_ACCESS_TO_ALL_UNNAMED.invoke(SharedSecrets.getJavaLangAccess());
                } catch (ReflectiveOperationException re) {
                    throw new InternalError(re);
                }
            }
        }

        @Override
        public Module getTargetModule() {
            return baseClass.getModule();
        }

        @Override
        public JavaLangSupport getJavaLangSupport() {
            return JavaLangSupportImpl.INSTANCE;
        }

        private static final class JavaLangSupportImpl extends JavaLangSupport {

            private static final JavaLangAccess JAVA_LANG_ACCESS = SharedSecrets.getJavaLangAccess();
            static final JavaLangSupport INSTANCE = new JavaLangSupportImpl();

            private JavaLangSupportImpl() {
            }

            @Override
            public Thread currentCarrierThread() {
                return JAVA_LANG_ACCESS.currentCarrierThread();
            }
        }
    }

    /*
     * When using a named module we do not need to do anything and we can just call the Modules
     * class. We used a qualified export to export it to this class.
     */
    private static final class IsolatedImpl extends Accessor.ModulesAccessor {

        private final MethodHandle addExports;
        private final MethodHandle addExportsToAllUnnamed;
        private final MethodHandle addOpens;
        private final MethodHandle addOpensToAllUnnamed;
        private final MethodHandle addEnableNativeAccess;
        private final MethodHandle addEnableNativeAccessToAllUnnamed;
        private final MethodHandle currentCarrierThread;
        private final Module targetModule;

        IsolatedImpl(Class<?> baseClass) throws ReflectiveOperationException {
            final String moduleName = "org.graalvm.truffle.generated";
            final String targetPackage = baseClass.getPackageName() + ".generated";
            final String className = targetPackage + ".GeneratedModules";
            final String binaryClassName = className.replace('.', '/');

            ClassWriter cw = new ClassWriter(0);
            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, binaryClassName, null, "java/lang/Object", null);

            FieldVisitor fv = cw.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL, "javaLangAccess", "Ljdk/internal/access/JavaLangAccess;", null, null);
            fv.visitEnd();

            MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.visitCode();
            clinit.visitMethodInsn(Opcodes.INVOKESTATIC, "jdk/internal/access/SharedSecrets", "getJavaLangAccess", "()Ljdk/internal/access/JavaLangAccess;", false);
            clinit.visitFieldInsn(Opcodes.PUTSTATIC, binaryClassName, "javaLangAccess", "Ljdk/internal/access/JavaLangAccess;");
            clinit.visitInsn(Opcodes.RETURN);
            clinit.visitMaxs(1, 0);
            clinit.visitEnd();

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

            MethodVisitor mv5 = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "addEnableNativeAccess",
                            "(Ljava/lang/Module;)V", null, null);
            mv5.visitCode();
            if (ADD_ENABLE_NATIVE_ACCESS != null) {
                mv5.visitMethodInsn(Opcodes.INVOKESTATIC, "jdk/internal/access/SharedSecrets", "getJavaLangAccess",
                                "()Ljdk/internal/access/JavaLangAccess;", false);
                mv5.visitVarInsn(Opcodes.ALOAD, 0); // Load first argument (Module module)
                mv5.visitMethodInsn(Opcodes.INVOKEINTERFACE, "jdk/internal/access/JavaLangAccess", "addEnableNativeAccess",
                                "(Ljava/lang/Module;)Ljava/lang/Module;", true);
            }
            mv5.visitInsn(Opcodes.RETURN);
            mv5.visitMaxs(2, 2);
            mv5.visitEnd();

            MethodVisitor mv6 = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "addEnableNativeAccessToAllUnnamed",
                            "()V", null, null);
            mv6.visitCode();
            if (ADD_ENABLE_NATIVE_ACCESS_TO_ALL_UNNAMED != null) {
                mv6.visitMethodInsn(Opcodes.INVOKESTATIC, "jdk/internal/access/SharedSecrets", "getJavaLangAccess",
                                "()Ljdk/internal/access/JavaLangAccess;", false);
                mv6.visitMethodInsn(Opcodes.INVOKEINTERFACE, "jdk/internal/access/JavaLangAccess", "addEnableNativeAccessToAllUnnamed",
                                "()V", true);
            }
            mv6.visitInsn(Opcodes.RETURN);
            mv6.visitMaxs(1, 1);
            mv6.visitEnd();

            MethodVisitor mv7 = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "currentCarrierThread",
                            "()Ljava/lang/Thread;", null, null);
            mv7.visitCode();
            mv7.visitFieldInsn(Opcodes.GETSTATIC, binaryClassName, "javaLangAccess", "Ljdk/internal/access/JavaLangAccess;");
            mv7.visitMethodInsn(Opcodes.INVOKEINTERFACE, "jdk/internal/access/JavaLangAccess", "currentCarrierThread", "()Ljava/lang/Thread;", true);
            mv7.visitInsn(Opcodes.ARETURN);
            mv7.visitMaxs(1, 0);
            mv7.visitEnd();

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

            MethodHandles.Lookup l = MethodHandles.lookup();
            l.accessClass(generatedClass);
            this.addExports = l.findStatic(generatedClass, "addExports", MethodType.methodType(void.class, Module.class, String.class, Module.class));
            this.addExportsToAllUnnamed = l.findStatic(generatedClass, "addExportsToAllUnnamed", MethodType.methodType(void.class, Module.class, String.class));
            this.addOpens = l.findStatic(generatedClass, "addOpens", MethodType.methodType(void.class, Module.class, String.class, Module.class));
            this.addOpensToAllUnnamed = l.findStatic(generatedClass, "addOpensToAllUnnamed", MethodType.methodType(void.class, Module.class, String.class));
            this.addEnableNativeAccess = l.findStatic(generatedClass, "addEnableNativeAccess", MethodType.methodType(void.class, Module.class));
            this.addEnableNativeAccessToAllUnnamed = l.findStatic(generatedClass, "addEnableNativeAccessToAllUnnamed", MethodType.methodType(void.class));
            this.currentCarrierThread = l.findStatic(generatedClass, "currentCarrierThread", MethodType.methodType(Thread.class));
        }

        @Override
        public Module getTargetModule() {
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
        public void addOpens(Module base, String p, Module target) {
            try {
                addOpens.invokeExact(base, p, target);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }

        @Override
        public void addOpensToAllUnnamed(Module base, String p) {
            try {
                addOpensToAllUnnamed.invokeExact(base, p);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }

        @Override
        public void addEnableNativeAccess(Module module) {
            try {
                addEnableNativeAccess.invokeExact(module);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }

        @Override
        public void addEnableNativeAccessToAllUnnamed() {
            try {
                addEnableNativeAccessToAllUnnamed.invokeExact();
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }

        @Override
        public JavaLangSupport getJavaLangSupport() {
            return JavaLangSupportImpl.INSTANCE;
        }

        private static final class JavaLangSupportImpl extends JavaLangSupport {

            /*
             * For performance reasons, it is necessary for CURRENT_CARRIER_THREAD to be declared as
             * static final.
             */
            private static final MethodHandle CURRENT_CARRIER_THREAD;
            static {
                if (JDKSupport.MODULES_ACCESSOR == null) {
                    throw new IllegalStateException("JavaLangAccessorImpl initialized before JDKSupport.");
                } else if (!(JDKSupport.MODULES_ACCESSOR instanceof IsolatedImpl)) {
                    throw new IllegalStateException("JDKSupport.MODULES_ACCESSOR initialized with wrong type " + JDKSupport.MODULES_ACCESSOR.getClass());
                } else {
                    CURRENT_CARRIER_THREAD = ((IsolatedImpl) JDKSupport.MODULES_ACCESSOR).currentCarrierThread;
                }
            }

            private static final JavaLangSupport INSTANCE = new JavaLangSupportImpl();

            private JavaLangSupportImpl() {
                /*
                 * Ensure the CURRENT_CARRIER_THREAD method handle is initialized by invoking it.
                 * This prevents the interpreter from triggering class initialization during the
                 * virtual thread hooks which must not trigger class loading or suspend the
                 * VirtualThread.
                 */
                currentCarrierThread();
            }

            @Override
            public Thread currentCarrierThread() {
                try {
                    return (Thread) CURRENT_CARRIER_THREAD.invokeExact();
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }
    }

    @InternalResource.Id(value = LibTruffleAttachResource.ID, componentId = "engine", optional = true)
    static final class LibTruffleAttachResource implements InternalResource {

        static final String ID = "libtruffleattach";

        LibTruffleAttachResource() {
        }

        @Override
        public void unpackFiles(Env env, Path targetDirectory) throws IOException {
            if (env.inNativeImageBuild() && !env.inContextPreinitialization()) {
                // The truffleattach library is not needed in native-image.
                return;
            }
            Path base = basePath(env);
            env.unpackResourceFiles(base.resolve("files"), targetDirectory, base);
        }

        @Override
        public String versionHash(Env env) throws IOException {
            Path base = basePath(env);
            return env.readResourceLines(base.resolve("sha256")).get(0);
        }

        private static Path basePath(Env env) {
            return Path.of("META-INF", "resources", "engine", ID, env.getOS().toString(), env.getCPUArchitecture().toString());
        }
    }
}
