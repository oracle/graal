/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.interop;

import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.AASTORE;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ACC_PUBLIC;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ACC_STATIC;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ACC_SUPER;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ACC_VARARGS;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ALOAD;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ANEWARRAY;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ARETURN;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ASTORE;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ATHROW;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.BIPUSH;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.CHECKCAST;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.DLOAD;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.DRETURN;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.DUP;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.FLOAD;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.FRETURN;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.GETSTATIC;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ICONST_0;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.ILOAD;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.IRETURN;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.LLOAD;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.LRETURN;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.NEW;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.POP;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.RETURN;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.SIPUSH;
import static com.oracle.truffle.espresso.shadowed.asm.Opcodes.V1_8;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PackageTable;
import com.oracle.truffle.espresso.impl.ParameterizedEspressoType;
import com.oracle.truffle.espresso.impl.generics.parser.SignatureParser;
import com.oracle.truffle.espresso.impl.generics.reflectiveObjects.ParameterizedTypeVariable;
import com.oracle.truffle.espresso.impl.generics.tree.ClassSignature;
import com.oracle.truffle.espresso.impl.generics.tree.FormalTypeParameter;
import com.oracle.truffle.espresso.jni.Mangle;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shadowed.asm.ClassWriter;
import com.oracle.truffle.espresso.shadowed.asm.Label;
import com.oracle.truffle.espresso.shadowed.asm.MethodVisitor;
import com.oracle.truffle.espresso.shadowed.asm.Type;
import com.oracle.truffle.espresso.vm.ModulesHelperVM;

/**
 * EspressoForeignProxyGenerator contains the code to generate a dynamic Espresso proxy class for
 * foreign objects.
 *
 * Large parts of this class is copied from java.lang.reflect.ProxyGenerator.
 */
public final class EspressoForeignProxyGenerator extends ClassWriter {

    private static final String JL_OBJECT = "java/lang/Object";
    private static final String JL_THROWABLE = "java/lang/Throwable";
    private static final String JLR_UNDECLARED_THROWABLE_EX = "java/lang/reflect/UndeclaredThrowableException";
    private static final String TYPE_LITERAL_TYPE = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;";
    private static final String INTEROP = "com/oracle/truffle/espresso/polyglot/Interop";
    private static final int VARARGS = 0x00000080;

    private final Meta meta;

    private final EspressoContext context;

    private final StaticObject proxyClassLoader;

    /* name of proxy class */
    private final String className;

    /* proxy interfaces */
    private final ObjectKlass[] interfaces;

    private final ObjectKlass superKlass;

    /* proxy class access flags */
    private final int accessFlags;

    /*
     * Maps method signature string to list of ProxyMethod objects for proxy methods with that
     * signature.
     */
    private final Map<String, List<ProxyMethod>> proxyMethods = new HashMap<>();

    private final Map<Symbol<Name>, EspressoType> staticGenericReturnTypes = new HashMap<>();
    private final Map<Integer, EspressoType> typeVariableGenericReturnTypes = new HashMap<>();
    private final Map<String, Integer> genericClassTypes;
    private int nextGenericIndex;
    private int nextStaticGenericIndex;

    // next number to use for generation of unique proxy class names
    private static final AtomicLong nextUniqueNumber = new AtomicLong();

    public static final String PROXY_PACKAGE_PREFIX = "com.oracle.truffle.espresso.polyglot";
    public static final String PROXY_NAME_PREFIX = "Foreign$Proxy$";
    public static final String GENERIC_TYPE_FIELD_PREFIX = "genericType$field$name";

    /**
     * Construct a ProxyGenerator to generate a proxy class with the specified name and for the
     * given interfaces.
     *
     * A ProxyGenerator object contains the state for the ongoing generation of a particular proxy
     * class.
     */
    private EspressoForeignProxyGenerator(Meta meta, ObjectKlass[] parents, ObjectKlass superKlass, EspressoContext context) {
        super(ClassWriter.COMPUTE_FRAMES);

        this.meta = meta;
        this.context = context;
        this.interfaces = parents;
        this.superKlass = superKlass;
        this.accessFlags = ACC_PUBLIC | ACC_FINAL | ACC_SUPER;
        this.proxyClassLoader = context.getBindingsLoader();
        this.className = nextClassName(proxyClassContext(referencedTypes()));
        this.genericClassTypes = extractGenericTypes(superKlass, parents);
    }

    private static Map<String, Integer> extractGenericTypes(ObjectKlass superKlazz, ObjectKlass[] parents) {
        Map<String, Integer> result = new HashMap<>();
        int nextIndex = 0;
        if (superKlazz != superKlazz.getMeta().java_lang_Object) {
            String klassSig = superKlazz.getGenericTypeAsString();
            if (klassSig != null && !klassSig.isEmpty()) {
                ClassSignature classSignature = SignatureParser.make().parseClassSig(klassSig);
                for (FormalTypeParameter formalTypeParameter : classSignature.getFormalTypeParameters()) {
                    result.put(formalTypeParameter.getName(), nextIndex++);
                }
            }
        } else {
            for (ObjectKlass parent : parents) {
                String genericType = parent.getGenericTypeAsString();
                if (genericType != null && !genericType.isEmpty()) {
                    ClassSignature classSignature = SignatureParser.make().parseClassSig(genericType);
                    for (FormalTypeParameter formalTypeParameter : classSignature.getFormalTypeParameters()) {
                        if (!result.containsKey(formalTypeParameter.getName())) {
                            result.put(formalTypeParameter.getName(), nextIndex++);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static class GeneratedProxyBytes {
        public final byte[] bytes;
        public final String name;
        private final ObjectKlass superklass;
        private final Map<Symbol<Name>, EspressoType> staticGenericReturnTypes;
        private final EspressoType[] genericTypeArray;
        private final Map<String, Integer> typeVariableIdentifiers;

        GeneratedProxyBytes(byte[] bytes, String name, ObjectKlass superKlass, Map<Symbol<Name>, EspressoType> staticGenericReturnTypes,
                        EspressoType[] genericTypeArray, Map<String, Integer> typeVariableIdentifiers) {
            this.bytes = bytes;
            this.name = name;
            this.superklass = superKlass;
            this.staticGenericReturnTypes = staticGenericReturnTypes;
            this.genericTypeArray = genericTypeArray;
            this.typeVariableIdentifiers = typeVariableIdentifiers;
        }

        public WrappedProxyKlass getWrappedProxyKlass(ObjectKlass proxyKlass) {
            return new WrappedProxyKlass(proxyKlass, genericTypeArray, typeVariableIdentifiers);
        }

        public ObjectKlass getSuperklass() {
            return superklass;
        }

        public Map<Symbol<Name>, EspressoType> getStaticGenericReturnTypes() {
            return staticGenericReturnTypes;
        }
    }

    @TruffleBoundary
    public static GeneratedProxyBytes getProxyKlassBytes(String metaName, ObjectKlass[] parents, ObjectKlass superKlass, EspressoContext context) {
        synchronized (context) {
            GeneratedProxyBytes generatedProxyBytes = context.getProxyBytesOrNull(metaName);
            if (generatedProxyBytes == null) {
                EspressoForeignProxyGenerator generator = new EspressoForeignProxyGenerator(context.getMeta(), parents, superKlass, context);
                generatedProxyBytes = new GeneratedProxyBytes(
                                generator.generateClassFile(),
                                generator.className,
                                superKlass,
                                generator.getStaticGenericReturnTypes(),
                                generator.getTypeVariableGenericReturnTypes(),
                                generator.getGenericClassTypes());
                context.registerProxyBytes(metaName, generatedProxyBytes);
            }
            return generatedProxyBytes;
        }
    }

    private Map<Symbol<Name>, EspressoType> getStaticGenericReturnTypes() {
        return staticGenericReturnTypes;
    }

    public EspressoType[] getTypeVariableGenericReturnTypes() {
        EspressoType[] result = new EspressoType[genericClassTypes.size() + typeVariableGenericReturnTypes.size()];
        EspressoType[] src = typeVariableGenericReturnTypes.values().toArray(new EspressoType[0]);
        System.arraycopy(src, 0, result, genericClassTypes.size(), typeVariableGenericReturnTypes.size());
        return result;
    }

    public Map<String, Integer> getGenericClassTypes() {
        return genericClassTypes;
    }

    /*
     * The below logic relating to ProxyClassContext and helper functions is directly inspired by
     * the logic located at:
     * https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/reflect/
     * Proxy.java
     */

    /*
     * Returns all types referenced by all public non-static method signatures of the proxy
     * interfaces
     */
    private Set<Klass> referencedTypes() {
        HashSet<Klass> types = new HashSet<>();
        for (ObjectKlass intf : interfaces) {
            for (Method m : intf.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    addElementType(types, m.resolveReturnKlass());
                    addElementTypes(types, m.resolveParameterKlasses());
                    addElementTypes(types, m.getCheckedExceptions());
                }
            }
        }
        return types;
    }

    private static void addElementTypes(HashSet<Klass> types, Klass... classes) {
        for (var cls : classes) {
            addElementType(types, cls);
        }
    }

    private static void addElementType(HashSet<Klass> types, Klass cls) {
        var type = cls.getElementalType();
        if (!type.isPrimitive()) {
            types.add(type);
        }
    }

    private final class ProxyClassContext {

        private final String packageName;

        private ProxyClassContext(ModuleTable.ModuleEntry module, PackageTable.PackageEntry packageEntry, int accessFlags) {
            if (module.isNamed()) {
                if (packageEntry == null) {
                    // Per JLS 7.4.2, unnamed package can only exist in unnamed modules.
                    // This means a package-private superinterface exist in the unnamed
                    // package of a named module.
                    throw new InternalError("Unnamed package cannot be added to " + module);
                }
            } else {
                if (Modifier.isPublic(accessFlags)) {
                    // All proxy superinterfaces are public, must be in named dynamic module
                    throw new InternalError("public proxy in unnamed module: " + module);
                }
            }

            if ((accessFlags & ~Modifier.PUBLIC) != 0) {
                throw new InternalError("proxy access flags must be Modifier.PUBLIC or 0");
            }
            this.packageName = packageEntry.getNameAsString();
        }
    }

    private ProxyClassContext proxyClassContext(Set<Klass> refTypes) {
        Map<ObjectKlass, ModuleTable.ModuleEntry> packagePrivateTypes = new HashMap<>();
        boolean nonExported = false;

        for (ObjectKlass intf : interfaces) {
            ModuleTable.ModuleEntry m = intf.module();
            if (!Modifier.isPublic(intf.getModifiers())) {
                packagePrivateTypes.put(intf, m);
            } else {
                if (!m.isOpen() && !intf.packageEntry().isUnqualifiedExported()) {
                    // module-private types
                    nonExported = true;
                }
            }
        }

        if (!packagePrivateTypes.isEmpty()) {
            // all package-private types must be in the same runtime package
            // i.e. same package name and same module (named or unnamed)
            //
            // Configuration will fail if M1 and in M2 defined by the same loader
            // and both have the same package p (so no need to check class loader)
            ModuleTable.ModuleEntry targetModule = null;
            PackageTable.PackageEntry targetPackage = null;
            for (Map.Entry<ObjectKlass, ModuleTable.ModuleEntry> e : packagePrivateTypes.entrySet()) {
                PackageTable.PackageEntry currentPackage = e.getKey().packageEntry();
                ModuleTable.ModuleEntry m = e.getValue();
                if ((targetModule != null && targetModule != m) ||
                                (targetPackage != null && !targetPackage.equals(currentPackage))) {
                    throw new IllegalArgumentException(
                                    "cannot have non-public interfaces in different packages");
                }
                if (m.classLoader() != proxyClassLoader) {
                    // the specified loader is not the same class loader
                    // of the non-public interface
                    throw new IllegalArgumentException(
                                    "non-public interface is not defined by the given loader");
                }

                targetModule = m;
                targetPackage = currentPackage;
            }

            // validate if the target module can access all other interfaces
            for (ObjectKlass intf : interfaces) {
                ModuleTable.ModuleEntry m = intf.module();
                if (m == targetModule) {
                    continue;
                }

                if (!targetModule.canRead(m, context.isJavaBase(m)) || (!m.isOpen() && !intf.packageEntry().isUnqualifiedExported())) {
                    throw new IllegalArgumentException(targetModule + " can't access " + intf.getName());
                }
            }
            // return the module of the package-private interface
            return new ProxyClassContext(targetModule, targetPackage, 0);
        }

        // All proxy interfaces are public. So maps to a dynamic proxy module
        // and add reads edge and qualified exports, if necessary
        ModuleTable.ModuleEntry targetModule = getDynamicModule(context.getBindingsLoader());

        // set up proxy class access to proxy interfaces and types
        // referenced in the method signature
        Set<Klass> types = new HashSet<>(Arrays.asList(interfaces));
        types.add(superKlass);
        types.addAll(refTypes);
        for (Klass c : types) {
            ensureAccess(targetModule, c);
        }

        String pkgName = nonExported ? PROXY_PACKAGE_PREFIX + '.' + targetModule.getName()
                        : targetModule.getNameAsString();
        try {
            PackageTable.PackageEntry packageEntry = ModulesHelperVM.extractPackageEntry(pkgName.replace('.', '/'), targetModule, meta, null);
            return new ProxyClassContext(targetModule, packageEntry, Modifier.PUBLIC);
        } catch (EspressoException ex) {
            throw new InternalError(pkgName + " not exist in " + targetModule.getName());
        }
    }

    private ModuleTable.ModuleEntry getDynamicModule(StaticObject loader) {
        ClassRegistry classRegistry = context.getRegistries().getClassRegistry(proxyClassLoader);
        ClassRegistry.DynamicModuleWrapper proxyDynamicModuleWrapper = classRegistry.getProxyDynamicModuleWrapper();
        synchronized (proxyDynamicModuleWrapper) {
            ModuleTable.ModuleEntry result = proxyDynamicModuleWrapper.getDynamicProxyModule();
            if (result == null) {
                // call VM helper to get the ModuleDescriptor
                String moduleName = "foreign.proxy";
                String pkgName = PROXY_PACKAGE_PREFIX + "." + moduleName;

                StaticObject moduleDescriptor = (StaticObject) meta.polyglot.VMHelper_getDynamicModuleDescriptor.invokeDirectStatic(meta.toGuestString(moduleName), meta.toGuestString(pkgName));
                // define the module in guest
                StaticObject module = (StaticObject) meta.jdk_internal_module_Modules_defineModule.invokeDirectStatic(loader, moduleDescriptor, StaticObject.NULL);
                ModuleTable.ModuleEntry moduleEntry = ModulesHelperVM.extractToModuleEntry(module, meta, null);
                moduleEntry.setCanReadAllUnnamed();

                ModuleTable.ModuleEntry javaBaseModule = context.getRegistries().getJavaBaseModule();
                moduleEntry.addReads(javaBaseModule);

                // needed for using Interop API in the proxies
                ModuleTable.ModuleEntry polyglotAPIModule = context.getRegistries().getPolyglotAPIModule();
                moduleEntry.addReads(polyglotAPIModule);

                String pn = PROXY_PACKAGE_PREFIX + "." + moduleEntry.getNameAsString();

                PackageTable.PackageEntry pkgEntry = ModulesHelperVM.extractPackageEntry(pn.replace('.', '/'), moduleEntry, meta, null);
                pkgEntry.addExports(javaBaseModule);
                proxyDynamicModuleWrapper.setDynamicProxyModule(moduleEntry);
                result = moduleEntry;
            }
            return result;
        }
    }

    /*
     * Ensure the given module can access the given class.
     */
    private void ensureAccess(ModuleTable.ModuleEntry target, Klass c) {
        ModuleTable.ModuleEntry m = c.module();
        // add read edge and qualified export for the target module to access
        if (!target.canRead(m, context.isJavaBase(m))) {
            target.addReads(m);
        }
        PackageTable.PackageEntry pe = c.packageEntry();
        if (!pe.isUnqualifiedExported() && !pe.isQualifiedExportTo(target)) {
            pe.addExports(target);
        }
    }

    private static String nextClassName(ProxyClassContext proxyClassContext) {
        return proxyClassContext.packageName + "." + PROXY_NAME_PREFIX + nextUniqueNumber.getAndIncrement();
    }

    private int nextGenericTypeIndex() {
        return genericClassTypes.size() + nextGenericIndex++;
    }

    private int nextStaticGenericTypeIndex() {
        return nextStaticGenericIndex++;
    }

    /**
     * Generate a class file for the proxy class. This method drives the class file generation
     * process.
     */
    private byte[] generateClassFile() {
        visit(V1_8, accessFlags, dotToSlash(className), null,
                        superKlass.getNameAsString(), typeNames(interfaces));

        // toString is implemented by interop protocol by means of
        // toDisplayString and asString
        generateToStringMethod();

        /*
         * First add methods from a potential optimized super class
         */
        if (superKlass != meta.java_lang_Object) {
            // add all methods implemented by the superclass, and it's transitive super hierarchy
            addSuperImplementedMethods(superKlass);
        }

        /*
         * Accumulate all of the methods from the proxy interfaces.
         */
        for (ObjectKlass intf : interfaces) {
            for (Method m : intf.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && !Modifier.isPrivate(m.getModifiers())) {
                    addProxyMethod(m, false);
                }
            }
        }

        /*
         * For each set of proxy methods with the same signature, verify that the methods' return
         * types are compatible.
         */
        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            checkReturnTypes(sigmethods);
        }

        generateConstructor();

        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            for (ProxyMethod pm : sigmethods) {
                // Generate code for proxy method
                pm.generateMethod(this);
            }
        }

        return toByteArray();
    }

    private void addSuperImplementedMethods(ObjectKlass klass) {
        if (klass == klass.getMeta().java_lang_Object) {
            return;
        }
        for (Method m : klass.getDeclaredMethods()) {
            if (!m.isAbstract()) {
                addProxyMethod(m, true);
            }
        }
        for (ObjectKlass itf : klass.getSuperInterfaces()) {
            for (Method m : itf.getDeclaredMethods()) {
                if (!m.isAbstract()) {
                    addProxyMethod(m, true);
                }
            }
            addSuperImplementedMethods(itf);
        }
        addSuperImplementedMethods(klass.getSuperKlass());
    }

    private void generateToStringMethod() {
        MethodVisitor mv = visitMethod(ACC_PUBLIC | ACC_FINAL,
                        "toString", "()Ljava/lang/String;", null,
                        null);

        mv.visitCode();
        Label startBlock = new Label();
        Label endBlock = new Label();
        Label runtimeHandler = new Label();
        Label throwableHandler = new Label();

        mv.visitLabel(startBlock);

        mv.visitVarInsn(ALOAD, 0);

        mv.visitMethodInsn(INVOKESTATIC, INTEROP,
                        "toDisplayString",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        false);

        mv.visitMethodInsn(INVOKESTATIC, INTEROP,
                        "asString",
                        "(Ljava/lang/Object;)Ljava/lang/String;",
                        false);

        mv.visitInsn(ARETURN);

        mv.visitLabel(endBlock);

        // Generate exception handler
        mv.visitLabel(runtimeHandler);
        mv.visitInsn(ATHROW);   // just rethrow the exception

        mv.visitLabel(throwableHandler);
        mv.visitVarInsn(ASTORE, 1);
        mv.visitTypeInsn(NEW, JLR_UNDECLARED_THROWABLE_EX);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, JLR_UNDECLARED_THROWABLE_EX,
                        "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(ATHROW);
        // Maxs computed by ClassWriter.COMPUTE_FRAMES, these arguments ignored
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    /**
     * Return an array of the class and interface names from an array of Classes.
     *
     * @param classes an array of classes or interfaces
     * @return the array of class and interface names; or null if classes is null or empty
     */
    private static String[] typeNames(Klass[] classes) {
        if (classes == null || classes.length == 0) {
            return null;
        }
        int size = classes.length;
        String[] ifaces = new String[size];
        for (int i = 0; i < size; i++) {
            ifaces[i] = dotToSlash(classes[i].getNameAsString());
        }
        return ifaces;
    }

    /**
     * Generate the constructor method for the proxy class.
     */
    private void generateConstructor() {
        MethodVisitor ctor = visitMethod(Modifier.PUBLIC, "<init>",
                        "()V", null, null);
        ctor.visitParameter(null, 0);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, superKlass.getNameAsString(), "<init>",
                        "()V", false);
        ctor.visitInsn(RETURN);

        // Maxs computed by ClassWriter.COMPUTE_FRAMES, these arguments ignored
        ctor.visitMaxs(-1, -1);
        ctor.visitEnd();
    }

    /**
     * Add another method to be proxied, either by creating a new ProxyMethod object or augmenting
     * an old one for a duplicate method.
     *
     * "fromClass" indicates the proxy interface that the method was found through, which may be
     * different from (a subinterface of) the method's "declaring class". Note that the first Method
     * object passed for a given name and descriptor identifies the Method object (and thus the
     * declaring class) that will be passed to the invocation handler's "invoke" method for a given
     * set of duplicate methods.
     */
    private void addProxyMethod(Method m, boolean isSuperKlassMethod) {
        String name = m.getNameAsString();
        Klass[] parameterTypes = m.resolveParameterKlasses();
        EspressoType returnType = m.getGenericReturnType();
        ObjectKlass[] exceptionTypes = m.getCheckedExceptions();
        Symbol<Signature> signature = m.getRawSignature();

        String sig = name + getParameterDescriptors(parameterTypes);
        List<ProxyMethod> sigmethods = proxyMethods.get(sig);
        if (sigmethods != null) {
            for (ProxyMethod pm : sigmethods) {
                if (returnType.getRawType() == pm.returnType.getRawType()) {
                    /*
                     * Found a match: reduce exception types to the greatest set of exceptions that
                     * can thrown compatibly with the throws clauses of both overridden methods.
                     */
                    List<Klass> legalExceptions = new ArrayList<>();
                    collectCompatibleTypes(
                                    exceptionTypes, pm.exceptionTypes, legalExceptions);
                    collectCompatibleTypes(
                                    pm.exceptionTypes, exceptionTypes, legalExceptions);
                    pm.exceptionTypes = new Klass[legalExceptions.size()];
                    pm.exceptionTypes = legalExceptions.toArray(pm.exceptionTypes);
                    return;
                }
            }
        } else {
            sigmethods = new ArrayList<>(3);
            proxyMethods.put(sig, sigmethods);
        }

        int typeLiteralIndex = -1;
        String resolvedGenericTypeField = "";

        if (!isSuperKlassMethod) {
            if (returnType instanceof ParameterizedTypeVariable parameterizedTypeVariable) {
                String identifier = parameterizedTypeVariable.getIdentifier();
                typeLiteralIndex = genericClassTypes.getOrDefault(identifier, -1);
            } else if (returnType instanceof ParameterizedEspressoType parameterizedReturnType) {
                // if statically fully resolved, then we store in a static field.
                // If not, we store in the EspressoType array property for the foreign object
                // wrapper.
                resolvedGenericTypeField = parameterizedReturnType.containsTypeVariable() ? "" : registerStaticGenericReturnTypeForMethod(m);
                if (resolvedGenericTypeField.isEmpty()) {
                    typeLiteralIndex = registerGenericReturnTypeForMethod(m);
                }
            }
        }
        sigmethods.add(new ProxyMethod(name, parameterTypes, returnType,
                        exceptionTypes, isVarArgs(m.getModifiers()), signature, isSuperKlassMethod, typeLiteralIndex, resolvedGenericTypeField));
    }

    private int registerGenericReturnTypeForMethod(Method m) {
        int index = nextGenericTypeIndex();
        typeVariableGenericReturnTypes.put(index, m.getGenericReturnType());
        return index;
    }

    private String registerStaticGenericReturnTypeForMethod(Method m) {
        String fieldName = GENERIC_TYPE_FIELD_PREFIX + nextStaticGenericTypeIndex();
        Symbol<Name> name = m.getContext().getNames().getOrCreate(fieldName);
        staticGenericReturnTypes.put(name, m.getGenericReturnType());
        visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, fieldName, TYPE_LITERAL_TYPE, null, null);
        return fieldName;
    }

    private static boolean isVarArgs(int modifiers) {
        return (modifiers & VARARGS) != 0;
    }

    /**
     * For a given set of proxy methods with the same signature, check that their return types are
     * compatible according to the Proxy specification.
     *
     * Specifically, if there is more than one such method, then all of the return types must be
     * reference types, and there must be one return type that is assignable to each of the rest of
     * them.
     */
    private static void checkReturnTypes(List<ProxyMethod> methods) {
        /*
         * If there is only one method with a given signature, there cannot be a conflict. This is
         * the only case in which a primitive (or void) return type is allowed.
         */
        if (methods.size() < 2) {
            return;
        }

        /*
         * List of return types that are not yet known to be assignable from ("covered" by) any of
         * the others.
         */
        LinkedList<Klass> uncoveredReturnTypes = new LinkedList<>();

        nextNewReturnType: for (ProxyMethod pm : methods) {
            Klass newReturnType = pm.returnType.getRawType();
            if (newReturnType.isPrimitive()) {
                throw new IllegalArgumentException(
                                "methods with same signature " +
                                                getFriendlyMethodSignature(pm.methodName,
                                                                pm.parameterTypes) +
                                                " but incompatible return types: " +
                                                newReturnType.getName() + " and others");
            }
            boolean added = false;

            /*
             * Compare the new return type to the existing uncovered return types.
             */
            ListIterator<Klass> liter = uncoveredReturnTypes.listIterator();
            while (liter.hasNext()) {
                Klass uncoveredReturnType = liter.next();

                /*
                 * If an existing uncovered return type is assignable to this new one, then we can
                 * forget the new one.
                 */
                if (newReturnType.isAssignableFrom(uncoveredReturnType)) {
                    assert !added;
                    continue nextNewReturnType;
                }

                /*
                 * If the new return type is assignable to an existing uncovered one, then should
                 * replace the existing one with the new one (or just forget the existing one, if
                 * the new one has already be put in the list).
                 */
                if (uncoveredReturnType.isAssignableFrom(newReturnType)) {
                    // (we can assume that each return type is unique)
                    if (!added) {
                        liter.set(newReturnType);
                        added = true;
                    } else {
                        liter.remove();
                    }
                }
            }

            /*
             * If we got through the list of existing uncovered return types without an
             * assignability relationship, then add the new return type to the list of uncovered
             * ones.
             */
            if (!added) {
                uncoveredReturnTypes.add(newReturnType);
            }
        }

        /*
         * We shouldn't end up with more than one return type that is not assignable from any of the
         * others.
         */
        if (uncoveredReturnTypes.size() > 1) {
            ProxyMethod pm = methods.get(0);
            throw new IllegalArgumentException(
                            "methods with same signature " +
                                            getFriendlyMethodSignature(pm.methodName, pm.parameterTypes) +
                                            " but incompatible return types: " + uncoveredReturnTypes);
        }
    }

    /**
     * Visit a bytecode for a constant.
     *
     * @param mv The MethodVisitor
     * @param cst The constant value
     */
    private static void emitIconstInsn(MethodVisitor mv, final int cst) {
        if (cst >= -1 && cst <= 5) {
            mv.visitInsn(ICONST_0 + cst);
        } else if (cst >= Byte.MIN_VALUE && cst <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, cst);
        } else if (cst >= Short.MIN_VALUE && cst <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, cst);
        } else {
            mv.visitLdcInsn(cst);
        }
    }

    /**
     * A ProxyMethod object represents a proxy method in the proxy class being generated: a method
     * whose implementation will encode and dispatch invocations to the proxy instance's invocation
     * handler.
     */
    private final class ProxyMethod {

        private static final String TYPE_LITERAL = "com/oracle/truffle/espresso/polyglot/TypeLiteral";
        private static final String POLYGLOT = "com/oracle/truffle/espresso/polyglot/Polyglot";
        private static final String GET_REIFIED_TYPE = "getReifiedType";
        private static final String GET_REIFIED_TYPE_SIG = "(Ljava/lang/Object;I)" + TYPE_LITERAL_TYPE;
        private static final String CAST_WITH_GENERICS = "castWithGenerics";
        private static final String CAST_WITH_GENERICS_SIG = "(Ljava/lang/Object;" + TYPE_LITERAL_TYPE + ")Ljava/lang/Object;";
        private static final String INVOKE_MEMBER = "invokeMember";
        private static final String INVOKE_MEMBER_SIG = "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;";
        private static final String INVOKE_MEMBER_WITH_CAST = "invokeMemberWithCast";
        private static final String INVOKE_MEMBER_WITH_CAST_SIG = "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;";

        public String methodName;
        public Klass[] parameterTypes;
        public EspressoType returnType;
        public Klass[] exceptionTypes;
        boolean isVarArgs;
        Symbol<Signature> signature;
        int typeLiteralIndex;
        String resolvedGenericTypeField;
        boolean isOptimizedMethod;

        private ProxyMethod(String methodName, Klass[] parameterTypes,
                        EspressoType returnType, Klass[] exceptionTypes, boolean isVarArgs, Symbol<Signature> signature, boolean isOptimizedMethod,
                        int typeLiteralIndex, String resolvedGenericTypeField) {
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
            this.isVarArgs = isVarArgs;
            this.signature = signature;
            this.isOptimizedMethod = isOptimizedMethod;
            this.typeLiteralIndex = typeLiteralIndex;
            this.resolvedGenericTypeField = resolvedGenericTypeField;
        }

        private void generateMethod(ClassWriter cw) {
            if (isOptimizedMethod) {
                // don't generate delegate methods for optimized methods which are handled by the
                // super class implementation
                return;
            }
            String desc = getMethodDescriptor(parameterTypes, returnType.getRawType());
            int methodAccess = ACC_PUBLIC | ACC_FINAL;

            if (isVarArgs) {
                methodAccess |= ACC_VARARGS;
            }

            MethodVisitor mv = cw.visitMethod(methodAccess,
                            methodName, desc, null,
                            typeNames(exceptionTypes));

            int[] parameterSlot = new int[parameterTypes.length];
            int nextSlot = 1;
            for (int i = 0; i < parameterSlot.length; i++) {
                parameterSlot[i] = nextSlot;
                nextSlot += getWordsPerType(parameterTypes[i]);
            }

            mv.visitCode();
            Label startBlock = new Label();
            Label endBlock = new Label();
            Label runtimeHandler = new Label();
            Label throwableHandler = new Label();

            List<Klass> catchList = computeUniqueCatchList(exceptionTypes);
            if (!catchList.isEmpty()) {
                for (Klass ex : catchList) {
                    mv.visitTryCatchBlock(startBlock, endBlock, runtimeHandler,
                                    dotToSlash(ex.getNameAsString()));
                }

                mv.visitTryCatchBlock(startBlock, endBlock, throwableHandler,
                                JL_THROWABLE);
            }
            mv.visitLabel(startBlock);

            // if generic return type, then first get the raw result from the foreign invocation
            // then use Polyglot.cast with TypeLiteral to convert to Espresso
            String invokeName;
            String invokeDesc;
            Klass rawReturnType = returnType.getRawType();

            if (typeLiteralIndex != -1 || !resolvedGenericTypeField.isEmpty() || (rawReturnType.isPrimitive() && rawReturnType.getJavaKind() != JavaKind.Char)) {
                invokeName = INVOKE_MEMBER;
                invokeDesc = INVOKE_MEMBER_SIG;
            } else {
                invokeName = INVOKE_MEMBER_WITH_CAST;
                invokeDesc = INVOKE_MEMBER_WITH_CAST_SIG;

                mv.visitLdcInsn(Type.getType(rawReturnType.getTypeAsString()));
            }

            mv.visitVarInsn(ALOAD, 0);
            mv.visitLdcInsn(Mangle.truffleJniMethodName(methodName, signature));

            if (parameterTypes.length > 0) {
                // Create an array and fill with the parameters converting primitives to
                // wrappers
                emitIconstInsn(mv, parameterTypes.length);
                mv.visitTypeInsn(ANEWARRAY, JL_OBJECT);
                for (int i = 0; i < parameterTypes.length; i++) {
                    mv.visitInsn(DUP);
                    emitIconstInsn(mv, i);
                    codeWrapArgument(mv, parameterTypes[i], parameterSlot[i]);
                    mv.visitInsn(AASTORE);
                }
            } else {
                mv.visitInsn(ICONST_0);
                mv.visitTypeInsn(ANEWARRAY, JL_OBJECT);
            }
            mv.visitMethodInsn(INVOKESTATIC, INTEROP, invokeName, invokeDesc, false);

            if (returnType == meta._void) {
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
            } else {
                codeUnwrapReturnValue(mv, returnType.getRawType());
            }

            mv.visitLabel(endBlock);

            // Generate exception handler
            mv.visitLabel(runtimeHandler);
            mv.visitInsn(ATHROW);   // just rethrow the exception

            mv.visitLabel(throwableHandler);
            mv.visitVarInsn(ASTORE, 1);
            mv.visitTypeInsn(NEW, JLR_UNDECLARED_THROWABLE_EX);
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, JLR_UNDECLARED_THROWABLE_EX,
                            "<init>", "(Ljava/lang/Throwable;)V", false);
            mv.visitInsn(ATHROW);
            // Maxs computed by ClassWriter.COMPUTE_FRAMES, these arguments ignored
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }

        /**
         * Generate code for wrapping an argument of the given type whose value can be found at the
         * specified local variable index, in order for it to be passed (as an Object) to the
         * invocation handler's "invoke" method.
         */
        private void codeWrapArgument(MethodVisitor mv, Klass type, int slot) {
            if (type.isPrimitive()) {
                JavaKind kind = JavaKind.fromTypeString(type.getTypeAsString());

                switch (kind) {
                    case Int:
                    case Boolean:
                    case Char:
                    case Byte:
                    case Short:
                        mv.visitVarInsn(ILOAD, slot);
                        break;
                    case Long:
                        mv.visitVarInsn(LLOAD, slot);
                        break;
                    case Float:
                        mv.visitVarInsn(FLOAD, slot);
                        break;
                    case Double:
                        mv.visitVarInsn(DLOAD, slot);
                        break;
                    default:
                        throw EspressoError.shouldNotReachHere();
                }

                mv.visitMethodInsn(INVOKESTATIC, dotToSlash(kind.toBoxedJavaClass().getName()), "valueOf",
                                kind.getWrapperValueOfDesc(), false);
            } else {
                mv.visitVarInsn(ALOAD, slot);
            }
        }

        /**
         * Generate code for unwrapping a return value of the given type from the invocation
         * handler's "invoke" method (as type Object) to its correct type.
         */
        private void codeUnwrapReturnValue(MethodVisitor mv, Klass type) {
            if (type.isPrimitive()) {
                JavaKind kind = JavaKind.fromTypeString(type.getTypeAsString());

                switch (kind) {
                    case Char:
                        String wrapperClassName = dotToSlash(kind.toBoxedJavaClass().getName());
                        mv.visitTypeInsn(CHECKCAST, wrapperClassName);
                        mv.visitMethodInsn(INVOKEVIRTUAL,
                                        wrapperClassName,
                                        kind.getUnwrapMethodName(), kind.getUnwrapMethodDesc(), false);
                        mv.visitInsn(IRETURN);
                        break;
                    case Int:
                        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop", "asInt", "(Ljava/lang/Object;)I", false);
                        mv.visitInsn(IRETURN);
                        break;
                    case Boolean:
                        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop", "asBoolean", "(Ljava/lang/Object;)Z", false);
                        mv.visitInsn(IRETURN);
                        break;
                    case Byte:
                        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop", "asByte", "(Ljava/lang/Object;)B", false);
                        mv.visitInsn(IRETURN);
                        break;
                    case Short:
                        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop", "asShort", "(Ljava/lang/Object;)S", false);
                        mv.visitInsn(IRETURN);
                        break;
                    case Long:
                        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop", "asLong", "(Ljava/lang/Object;)J", false);
                        mv.visitInsn(LRETURN);
                        break;
                    case Float:
                        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop", "asFloat", "(Ljava/lang/Object;)F", false);
                        mv.visitInsn(FRETURN);
                        break;
                    case Double:
                        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop", "asDouble", "(Ljava/lang/Object;)D", false);
                        mv.visitInsn(DRETURN);
                        break;
                    default:
                        throw new AssertionError();
                }
            } else {
                if (type.isArray()) {
                    mv.visitTypeInsn(CHECKCAST, type.getTypeAsString());
                } else {
                    if (typeLiteralIndex != -1) {
                        // fetch the Espresso type
                        // and use Polyglot.castWithGenerics(value, TypeLiteral) to convert to type
                        mv.visitVarInsn(ALOAD, 0);
                        emitIconstInsn(mv, typeLiteralIndex);
                        mv.visitMethodInsn(INVOKESTATIC, TYPE_LITERAL, GET_REIFIED_TYPE, GET_REIFIED_TYPE_SIG, false);
                        mv.visitMethodInsn(INVOKESTATIC, POLYGLOT, CAST_WITH_GENERICS, CAST_WITH_GENERICS_SIG, false);
                    } else if (!resolvedGenericTypeField.isEmpty()) {
                        mv.visitFieldInsn(GETSTATIC, dotToSlash(className), resolvedGenericTypeField, TYPE_LITERAL_TYPE);
                        mv.visitMethodInsn(INVOKESTATIC, POLYGLOT, CAST_WITH_GENERICS, CAST_WITH_GENERICS_SIG, false);
                    }
                    mv.visitTypeInsn(CHECKCAST, type.getNameAsString());
                }
                mv.visitInsn(ARETURN);
            }
        }
    }

    /*
     * ==================== General Utility Methods ====================
     */

    /**
     * Convert a fully qualified class name that uses '.' as the package separator, the external
     * representation used by the Java language and APIs, to a fully qualified class name that uses
     * '/' as the package separator, the representation used in the class file format (see JVMS
     * section 4.2).
     */
    private static String dotToSlash(String name) {
        return name.replace('.', '/');
    }

    /**
     * Return the "method descriptor" string for a method with the given parameter types and return
     * type. See JVMS section 4.3.3.
     */
    private String getMethodDescriptor(Klass[] parameterTypes,
                    Klass returnType) {
        return getParameterDescriptors(parameterTypes) +
                        ((returnType == meta._void) ? "V" : getFieldType(returnType));
    }

    /**
     * Return the list of "parameter descriptor" strings enclosed in parentheses corresponding to
     * the given parameter types (in other words, a method descriptor without a return descriptor).
     * This string is useful for constructing string keys for methods without regard to their return
     * type.
     */
    private static String getParameterDescriptors(Klass[] parameterTypes) {
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < parameterTypes.length; i++) {
            desc.append(getFieldType(parameterTypes[i]));
        }
        desc.append(')');
        return desc.toString();
    }

    /**
     * Return the "field type" string for the given type, appropriate for a field descriptor, a
     * parameter descriptor, or a return descriptor other than "void". See JVMS section 4.3.2.
     */
    private static String getFieldType(Klass type) {
        if (type.isPrimitive()) {
            return String.valueOf(JavaKind.fromTypeString(type.getTypeAsString()).getTypeChar());
        } else if (type.isArray()) {
            return type.getTypeAsString();
        } else {
            return "L" + dotToSlash(type.getNameAsString()) + ";";
        }
    }

    /**
     * Returns a human-readable string representing the signature of a method with the given name
     * and parameter types.
     */
    private static String getFriendlyMethodSignature(String name,
                    Klass[] parameterTypes) {
        StringBuilder sig = new StringBuilder(name);
        sig.append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sig.append(',');
            }
            Klass parameterType = parameterTypes[i];
            int dimensions = 0;
            while (parameterType.isArray()) {
                parameterType = ((ArrayKlass) parameterType).getComponentType();
                dimensions++;
            }
            sig.append(parameterType.getName());
            while (dimensions-- > 0) {
                sig.append("[]");
            }
        }
        sig.append(')');
        return sig.toString();
    }

    /**
     * Return the number of abstract "words", or consecutive local variable indexes, required to
     * contain a value of the given type. See JVMS section 3.6.1.
     *
     * Note that the original version of the JVMS contained a definition of this abstract notion of
     * a "word" in section 3.4, but that definition was removed for the second edition.
     */
    private int getWordsPerType(Klass type) {
        if (type == meta._long || type == meta._double) {
            return 2;
        } else {
            return 1;
        }
    }

    /**
     * Add to the given list all of the types in the "from" array that are not already contained in
     * the list and are assignable to at least one of the types in the "with" array.
     *
     * This method is useful for computing the greatest common set of declared exceptions from
     * duplicate methods inherited from different interfaces.
     */
    private static void collectCompatibleTypes(Klass[] from,
                    Klass[] with,
                    List<Klass> list) {
        for (Klass fc : from) {
            if (!list.contains(fc)) {
                for (Klass wc : with) {
                    if (wc.isAssignableFrom(fc)) {
                        list.add(fc);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Given the exceptions declared in the throws clause of a proxy method, compute the exceptions
     * that need to be caught from the invocation handler's invoke method and rethrown intact in the
     * method's implementation before catching other Throwables and wrapping them in
     * UndeclaredThrowableExceptions.
     *
     * The exceptions to be caught are returned in a List object. Each exception in the returned
     * list is guaranteed to not be a subclass of any of the other exceptions in the list, so the
     * catch blocks for these exceptions may be generated in any order relative to each other.
     *
     * Error and RuntimeException are each always contained by the returned list (if none of their
     * superclasses are contained), since those unchecked exceptions should always be rethrown
     * intact, and thus their subclasses will never appear in the returned list.
     *
     * The returned List will be empty if java.lang.Throwable is in the given list of declared
     * exceptions, indicating that no exceptions need to be caught.
     */
    private List<Klass> computeUniqueCatchList(Klass[] exceptions) {
        List<Klass> uniqueList = new ArrayList<>();
        // unique exceptions to catch

        uniqueList.add(meta.java_lang_Error);            // always catch/rethrow these
        uniqueList.add(meta.java_lang_RuntimeException);

        nextException: for (Klass ex : exceptions) {
            if (ex.isAssignableFrom(meta.java_lang_Throwable)) {
                /*
                 * If Throwable is declared to be thrown by the proxy method, then no catch blocks
                 * are necessary, because the invoke can, at most, throw Throwable anyway.
                 */
                uniqueList.clear();
                break;
            } else if (!meta.java_lang_Throwable.isAssignableFrom(ex)) {
                /*
                 * Ignore types that cannot be thrown by the invoke method.
                 */
                continue;
            }
            /*
             * Compare this exception against the current list of exceptions that need to be caught:
             */
            for (int j = 0; j < uniqueList.size();) {
                Klass ex2 = uniqueList.get(j);
                if (ex2.isAssignableFrom(ex)) {
                    /*
                     * if a superclass of this exception is already on the list to catch, then
                     * ignore this one and continue;
                     */
                    continue nextException;
                } else if (ex.isAssignableFrom(ex2)) {
                    /*
                     * if a subclass of this exception is on the list to catch, then remove it;
                     */
                    uniqueList.remove(j);
                } else {
                    j++;        // else continue comparing.
                }
            }
            // This exception is unique (so far): add it to the list to catch.
            uniqueList.add(ex);
        }
        return uniqueList;
    }
}
