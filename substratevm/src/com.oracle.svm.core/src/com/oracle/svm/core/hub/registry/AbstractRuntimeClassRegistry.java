/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.registry;

import static com.oracle.svm.espresso.classfile.Constants.ACC_SUPER;
import static com.oracle.svm.espresso.classfile.Constants.ACC_VALUE_BASED;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ACC_WRITTEN_FLAGS;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.impl.ClassLoading;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.hub.CremaSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.hub.registry.SVMSymbols.SVMTypes;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ClassfileParser;
import com.oracle.svm.espresso.classfile.ClassfileStream;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.espresso.classfile.ParserException;
import com.oracle.svm.espresso.classfile.ParserField;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.ParserMethod;
import com.oracle.svm.espresso.classfile.attributes.Attribute;
import com.oracle.svm.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.svm.espresso.classfile.attributes.NestHostAttribute;
import com.oracle.svm.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.svm.espresso.classfile.attributes.RecordAttribute;
import com.oracle.svm.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.svm.espresso.classfile.attributes.SourceFileAttribute;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.ValidationException;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.MetaUtil;

/**
 * This class registry is used for ClassLoader instances if runtime class loading is supported.
 * <p>
 * Parallel class loading is currently disabled (see {@link Target_java_lang_ClassLoader}) and not
 * supported by this implementation (GR-62338). Note that the boot class loader is always considered
 * parallel-capable. To prevent issues until parallel class loading is implemented, the
 * {@link BootClassRegistry#doLoadClass(Symbol)} method is synchronized.
 * <p>
 * Classloaders synchronize on a class loading lock in their loadClass implementation. When parallel
 * class loading is disabled, this lock is the classloader object itself.
 * <p>
 * When defining a class, the registry synchronizes on the classloader if it is not
 * parallel-capable.
 * <p>
 * The registry synchronizes on the classloader during class resolution (such as
 * {@code Class.forName} or {@code ClassLoader.findBootstrapClassOrNull}) if the classloader is not
 * parallel-capable.
 * <p>
 * There are exactly 2 types of runtime class registries: the one used for the
 * {@linkplain BootClassRegistry boot class loader} and the one used for
 * {@linkplain UserDefinedClassRegistry all other class loaders}.
 */
public abstract sealed class AbstractRuntimeClassRegistry extends AbstractClassRegistry permits BootClassRegistry, UserDefinedClassRegistry {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    /**
     * Strong hidden classes must be referenced by the class loader data to prevent them from being
     * reclaimed, while not appearing in the actual registry. This field simply keeps those hidden
     * classes strongly reachable from the class registry.
     */
    private final Collection<Class<?>> strongHiddenClasses = new ArrayList<>();

    AbstractRuntimeClassRegistry() {
        super(new ConcurrentHashMap<>());
    }

    @Override
    public final Class<?> loadClass(Symbol<Type> name) throws ClassNotFoundException {
        Class<?> aotClass = findAOTLoadedClass(name);
        if (aotClass != null) {
            return aotClass;
        }
        if (isParallelClassLoader()) {
            return loadClassInner(name);
        } else {
            if (runtimeClasses.get(name) instanceof Class<?> entry) {
                return entry;
            }
            synchronized (getClassLoader()) {
                return loadClassInner(name);
            }
        }
    }

    private Class<?> loadClassInner(Symbol<Type> name) throws ClassNotFoundException {
        if (getClassLoader() != null && isParallelClassLoader()) {
            // GR-62338
            throw VMError.unimplemented("Parallel class loading:" + getClassLoader());
        }
        Object existing = runtimeClasses.get(name);
        if (existing instanceof Class<?> entry) {
            // someone else won the race and populated the slot
            return entry;
        }
        if (existing instanceof Placeholder placeholder && placeholder.isSuperProbingThread()) {
            throw new ClassCircularityError(name.toString());
        }
        Class<?> result = doLoadClass(name);
        if (result == null) {
            // The boot class loader can return null
            return null;
        }
        var prev = runtimeClasses.put(name, result);
        assert prev == null || prev == result : prev;
        return result;
    }

    protected boolean isParallelClassLoader() {
        return isParallelClassLoader(getClassLoader());
    }

    private static boolean isParallelClassLoader(ClassLoader loader) {
        if (loader == null) {
            // The boot class loader is always considered parallel
            return true;
        }
        return SubstrateUtil.cast(loader, Target_java_lang_ClassLoader.class).parallelLockMap != null;
    }

    protected abstract Class<?> doLoadClass(Symbol<Type> type) throws ClassNotFoundException;

    protected abstract boolean loaderIsBootOrPlatform();

    public final Class<?> defineClass(Symbol<Type> typeOrNull, byte[] b, int off, int len, ClassDefinitionInfo info) {
        if (isParallelClassLoader()) {
            return defineClassInner(typeOrNull, b, off, len, info);
        } else {
            synchronized (getClassLoader()) {
                return defineClassInner(typeOrNull, b, off, len, info);
            }
        }
    }

    private Class<?> defineClassInner(Symbol<Type> typeOrNull, byte[] b, int off, int len, ClassDefinitionInfo info) {
        if (isParallelClassLoader() || getClassLoader() == null) {
            // GR-62338
            throw VMError.unimplemented("Parallel class loading:" + getClassLoader());
        }
        byte[] data = b;
        if (off != 0 || b.length != len) {
            if (len < 0) {
                throw new ArrayIndexOutOfBoundsException("Length " + len + " is negative");
            }
            if (off < 0 || off > b.length - len) {
                throw new ArrayIndexOutOfBoundsException("Array region " + off + ".." + ((long) off + len) + " out of bounds for length " + len);
            }
            data = Arrays.copyOfRange(data, off, off + len);
        }
        ParserKlass parsed = parseClass(typeOrNull, info, data);
        Symbol<Type> type = typeOrNull == null ? parsed.getType() : typeOrNull;
        assert typeOrNull == null || type == parsed.getType();
        if (info.addedToRegistry() && findLoadedClass(type) != null) {
            String kind;
            if (Modifier.isInterface(parsed.getFlags())) {
                kind = "interface";
            } else if (Modifier.isAbstract(parsed.getFlags())) {
                kind = "abstract class";
            } else {
                kind = "class";
            }
            String externalName = getExternalName(parsed, info);
            throw new LinkageError("Loader " + ClassRegistries.loaderNameAndId(getClassLoader()) + " attempted duplicate " + kind + " definition for " + externalName + ".");
        }
        Class<?> clazz;
        try {
            clazz = createClass(parsed, info, type);
        } catch (ParserException.ClassFormatError error) {
            throw new ClassFormatError(error.getMessage());
        }
        if (info.addedToRegistry()) {
            registerClass(clazz, type);
        } else if (info.isStrongHidden()) {
            registerStrongHiddenClass(clazz);
        }
        return clazz;
    }

    private ParserKlass parseClass(Symbol<Type> typeOrNull, ClassDefinitionInfo info, byte[] data) {
        boolean verifiable = RuntimeClassLoading.Options.ClassVerification.getValue().needsVerification(getClassLoader());
        try {
            return ClassfileParser.parse(ClassRegistries.getParsingContext(), new ClassfileStream(data, null), verifiable, loaderIsBootOrPlatform(), typeOrNull, info.isHidden(),
                            info.forceAllowVMAnnotations(), verifiable);
        } catch (ValidationException | ParserException.ClassFormatError validationOrBadFormat) {
            throw new ClassFormatError(validationOrBadFormat.getMessage());
        } catch (ParserException.UnsupportedClassVersionError unsupportedClassVersionError) {
            throw new UnsupportedClassVersionError(unsupportedClassVersionError.getMessage());
        } catch (ParserException.NoClassDefFoundError noClassDefFoundError) {
            throw new NoClassDefFoundError(noClassDefFoundError.getMessage());
        } catch (ParserException parserException) {
            throw VMError.shouldNotReachHere("Not a validation nor parser exception", parserException);
        }
    }

    private static List<Class<?>> transitiveSuperInterfaces(Class<?> superClass, Class<?>[] superInterfaces) {
        HashSet<Class<?>> result = new HashSet<>();
        Class<?> current = superClass;
        while (current != null) {
            for (Class<?> interfaceClass : current.getInterfaces()) {
                collectInterfaces(interfaceClass, result);
            }
            current = current.getSuperclass();
        }
        for (Class<?> interfaceClass : superInterfaces) {
            collectInterfaces(interfaceClass, result);
        }
        return new ArrayList<>(result);
    }

    private static void collectInterfaces(Class<?> interfaceClass, HashSet<Class<?>> result) {
        // note that this is and must be called only _after_ class circularity detection
        if (result.add(interfaceClass)) {
            for (Class<?> superInterface : interfaceClass.getInterfaces()) {
                collectInterfaces(superInterface, result);
            }
        }
    }

    private Class<?> createClass(ParserKlass parsed, ClassDefinitionInfo info, Symbol<Type> type) {
        Symbol<Type> superKlassType = parsed.getSuperKlass();
        assert superKlassType != null; // j.l.Object is always AOT
        // Load direct super interfaces
        Symbol<Type>[] superInterfacesTypes = parsed.getSuperInterfaces();
        Class<?>[] superInterfaces = superInterfacesTypes.length == 0 ? EMPTY_CLASS_ARRAY : new Class<?>[superInterfacesTypes.length];
        for (int i = 0; i < superInterfacesTypes.length; ++i) {
            superInterfaces[i] = loadSuperType(type, superInterfacesTypes[i]);
            if (!superInterfaces[i].isInterface()) {
                throw new IncompatibleClassChangeError("Class " + parsed.getType() + " cannot implement " + superInterfaces[i] + ", because it is not an interface");
            }
        }

        Class<?> superClass = loadSuperType(type, superKlassType);
        if (superClass.isInterface()) {
            throw new IncompatibleClassChangeError("Class " + parsed.getType() + " has interface " + superKlassType + " as super class");
        }
        if (Modifier.isFinal(superClass.getModifiers())) {
            throw new IncompatibleClassChangeError("Class " + parsed.getType() + " is a subclass of final class " + superKlassType);
        }
        // GR-62339: Perform super class and interfaces access checks

        String externalName = getExternalName(parsed, info);
        String simpleBinaryName = getSimpleBinaryName(parsed);
        String sourceFile = getSourceFile(parsed);
        Class<?> nestHost = getNestHost(parsed);
        Class<?> enclosingClass = getEnclosingClass(parsed);
        String classSignature = getClassSignature(parsed);

        int modifiers = getClassModifiers(parsed);
        int classFileAccessFlags = parsed.getFlags();

        boolean isInterface = Modifier.isInterface(modifiers);
        boolean isRecord = Modifier.isFinal(modifiers) && superClass == Record.class && parsed.getAttribute(RecordAttribute.NAME) != null;
        // GR-62320 This should be set based on build-time and run-time arguments.
        boolean assertionsEnabled = true;
        boolean isSealed = isSealed(parsed);

        Object interfacesEncoding = null;
        if (superInterfaces.length == 1) {
            interfacesEncoding = DynamicHub.fromClass(superInterfaces[0]);
        } else if (superInterfaces.length > 1) {
            DynamicHub[] superHubs = new DynamicHub[superInterfaces.length];
            for (int i = 0; i < superHubs.length; ++i) {
                superHubs[i] = DynamicHub.fromClass(superInterfaces[i]);
            }
            interfacesEncoding = superHubs;
        }

        List<Class<?>> transitiveSuperInterfaces = transitiveSuperInterfaces(superClass, superInterfaces);
        transitiveSuperInterfaces.sort(Comparator.comparing(c -> DynamicHub.fromClass(c).getTypeID()));

        CremaSupport.CremaDispatchTable dispatchTable = CremaSupport.singleton().getDispatchTable(parsed, superClass, transitiveSuperInterfaces);

        boolean declaresDefaultMethods = isInterface && declaresDefaultMethods(parsed);
        boolean hasDefaultMethods = declaresDefaultMethods || hasInheritedDefaultMethods(superClass, superInterfaces);

        boolean isLambdaFormHidden = false;
        boolean isProxyClass = false;
        short flags = DynamicHub.makeFlags(false, isInterface, info.isHidden(), isRecord, assertionsEnabled, hasDefaultMethods, declaresDefaultMethods, isSealed, false, isLambdaFormHidden, false,
                        isProxyClass);

        /*
         * The dispatch table will look like:
         * @formatter:off
         * [vtable..., itable(I1)..., itable(I2)...]
         *             ^ idx1         ^ idx2
         * @formatter:on
         * First compute idx* in interfaceIndices
         */
        int dispatchTableLength = dispatchTable.vtableLength();
        int[] interfaceIndices = new int[transitiveSuperInterfaces.size()];
        int i = 0;
        for (Class<?> iface : transitiveSuperInterfaces) {
            interfaceIndices[i++] = dispatchTableLength;
            dispatchTableLength += dispatchTable.itableLength(iface);
        }
        /*
         * Compute the type check slots depending on the kind of type
         * @formatter:off
         * ## Instance types
         * [Object.id, Super1.id, ..., Current.id, I1.id, off1, I2.id, off2, ...]
         * - display with all super classes from Object to self (included)
         * - followed by transitive interfaces (ordered by type id)
         * - each interface is followed by its itable offset
         * ## Interface types
         * [Object.id, I1.id, bad, I2.id, bad]
         * - display with Object
         * - followed by transitive interfaces (ordered by type id, including self)
         * - using 0xBADD0D1DL as interface starting index
         * @formatter:on
         */
        DynamicHub superHub = DynamicHub.fromClass(superClass);
        int typeID = TypeIDs.singleton().nextTypeId();
        short numInterfacesTypes = (short) transitiveSuperInterfaces.size();
        short numClassTypes;
        short typeIDDepth;
        if (isInterface) {
            assert superHub.getNumClassTypes() == 1;
            typeIDDepth = -1;
            numClassTypes = 1;
        } else {
            int intDepth = superHub.getTypeIDDepth() + 1;
            int intNumClassTypes = superHub.getNumClassTypes() + 1;
            VMError.guarantee(intDepth == (short) intDepth, "Type depth overflow");
            VMError.guarantee(intNumClassTypes == (short) intNumClassTypes, "Num class types overflow");
            typeIDDepth = (short) intDepth;
            numClassTypes = (short) intNumClassTypes;
        }
        int[] openTypeWorldTypeCheckSlots = new int[numClassTypes + (numInterfacesTypes * 2)];
        int[] superOpenTypeWorldTypeCheckSlots = superHub.getOpenTypeWorldTypeCheckSlots();
        System.arraycopy(superOpenTypeWorldTypeCheckSlots, 0, openTypeWorldTypeCheckSlots, 0, superHub.getNumClassTypes());
        if (!isInterface) {
            openTypeWorldTypeCheckSlots[numClassTypes - 1] = typeID;
        }

        i = 0;
        long vTableBaseOffset = KnownOffsets.singleton().getVTableBaseOffset();
        long vTableEntrySize = KnownOffsets.singleton().getVTableEntrySize();
        for (Class<?> superInterface : transitiveSuperInterfaces) {
            openTypeWorldTypeCheckSlots[i * 2 + numClassTypes] = DynamicHub.fromClass(superInterface).getTypeID();
            int offset;
            if (isInterface) {
                offset = 0xBADD0D1D;
            } else {
                offset = Math.toIntExact(vTableBaseOffset + interfaceIndices[i] * vTableEntrySize);
            }
            openTypeWorldTypeCheckSlots[i * 2 + numClassTypes + 1] = offset;
            i += 1;
        }

        int afterFieldsOffset;
        if (isInterface) {
            afterFieldsOffset = 0;
        } else {
            int superAfterFieldsOffset = CremaSupport.singleton().getAfterFieldsOffset(superHub);
            // GR-60069: field layout
            int numDeclaredInstanceFields = 0;
            for (ParserField field : parsed.getFields()) {
                if (!field.isStatic()) {
                    numDeclaredInstanceFields += 1;
                }
            }
            assert numDeclaredInstanceFields == 0;
            afterFieldsOffset = Math.toIntExact(superAfterFieldsOffset);
        }
        boolean isValueBased = (parsed.getFlags() & ACC_VALUE_BASED) != 0;

        // GR-62339
        Module module = getClassLoader().getUnnamedModule();

        checkNotHybrid(parsed);

        DynamicHub hub = DynamicHub.allocate(externalName, superHub, interfacesEncoding, null,
                        sourceFile, modifiers, classFileAccessFlags, flags, getClassLoader(), nestHost, simpleBinaryName, module, enclosingClass, classSignature,
                        typeID, numClassTypes, typeIDDepth, numInterfacesTypes, openTypeWorldTypeCheckSlots, dispatchTableLength, afterFieldsOffset, isValueBased);

        CremaSupport.singleton().fillDynamicHubInfo(hub, dispatchTable, transitiveSuperInterfaces, interfaceIndices);

        return DynamicHub.toClass(hub);
    }

    private static void checkNotHybrid(ParserKlass parsed) {
        Attribute attribute = parsed.getAttribute(ParserNames.RuntimeVisibleAnnotations);
        if (attribute == null) {
            return;
        }
        ClassfileStream stream = new ClassfileStream(attribute.getData(), null);
        int count = stream.readU2();
        for (int j = 0; j < count; j++) {
            int typeIndex = ClassfileParser.parseAnnotation(stream);
            Symbol<?> annotType = parsed.getConstantPool().utf8At(typeIndex, "annotation type");
            if (SVMTypes.com_oracle_svm_core_hub_Hybrid.equals(annotType)) {
                throw new ClassFormatError("Cannot load @Hybrid classes at runtime");
            }
        }

    }

    private static boolean declaresDefaultMethods(ParserKlass parsed) {
        for (ParserMethod method : parsed.getMethods()) {
            int flags = method.getFlags();
            if (!Modifier.isAbstract(flags) && !Modifier.isStatic(flags)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSealed(ParserKlass parsed) {
        PermittedSubclassesAttribute permittedSubclasses = (PermittedSubclassesAttribute) parsed.getAttribute(PermittedSubclassesAttribute.NAME);
        return permittedSubclasses != null && permittedSubclasses.getClasses().length > 0;
    }

    private static boolean hasInheritedDefaultMethods(Class<?> superClass, Class<?>[] superInterfaces) {
        if (DynamicHub.fromClass(superClass).hasDefaultMethods()) {
            return true;
        }
        for (Class<?> superInterface : superInterfaces) {
            if (DynamicHub.fromClass(superInterface).hasDefaultMethods()) {
                return true;
            }
        }
        return false;
    }

    private static int getClassModifiers(ParserKlass parsed) {
        int modifiers = parsed.getFlags();
        InnerClassesAttribute innerClassesAttribute = (InnerClassesAttribute) parsed.getAttribute(InnerClassesAttribute.NAME);
        if (innerClassesAttribute != null) {
            ParserConstantPool pool = parsed.getConstantPool();
            for (int i = 0; i < innerClassesAttribute.entryCount(); i++) {
                InnerClassesAttribute.Entry entry = innerClassesAttribute.entryAt(i);
                if (entry.innerClassIndex != 0) {
                    Symbol<Name> innerClassName = pool.className(entry.innerClassIndex);
                    if (innerClassName.equals(parsed.getName())) {
                        modifiers = entry.innerClassAccessFlags;
                        break;
                    }
                }
            }
        }
        return modifiers & ~ACC_SUPER & JVM_ACC_WRITTEN_FLAGS;
    }

    private static Class<?> getNestHost(ParserKlass parsed) {
        Class<?> nestHost = null;
        NestHostAttribute nestHostAttribute = (NestHostAttribute) parsed.getAttribute(NestHostAttribute.NAME);
        if (nestHostAttribute != null) {
            // must be lazy, should move to companion
            throw VMError.unimplemented("nest host is not supported yet");
        }
        return nestHost;
    }

    private static String getExternalName(ParserKlass parsed, ClassDefinitionInfo info) {
        String externalName = MetaUtil.internalNameToJava(parsed.getType().toString(), true, true);
        if (info.isHidden()) {
            int idx = externalName.lastIndexOf('+');
            char[] chars = externalName.toCharArray();
            chars[idx] = '/';
            externalName = new String(chars);
        }
        return externalName;
    }

    private static Class<?> getEnclosingClass(ParserKlass parsed) {
        InnerClassesAttribute innerClassesAttribute = (InnerClassesAttribute) parsed.getAttribute(InnerClassesAttribute.NAME);
        if (innerClassesAttribute == null) {
            return null;
        }
        throw VMError.unimplemented("enclosing class is not supported yet");
    }

    private static String getSimpleBinaryName(ParserKlass parsed) {
        InnerClassesAttribute innerClassesAttribute = (InnerClassesAttribute) parsed.getAttribute(InnerClassesAttribute.NAME);
        if (innerClassesAttribute == null) {
            return null;
        }
        ParserConstantPool pool = parsed.getConstantPool();
        for (int i = 0; i < innerClassesAttribute.entryCount(); i++) {
            InnerClassesAttribute.Entry entry = innerClassesAttribute.entryAt(i);
            int innerClassIndex = entry.innerClassIndex;
            if (innerClassIndex != 0) {
                if (pool.className(innerClassIndex) == parsed.getName()) {
                    if (entry.innerNameIndex == 0) {
                        break;
                    } else {
                        Symbol<?> innerName = pool.utf8At(entry.innerNameIndex, "inner class name");
                        return innerName.toString();
                    }
                }
            }
        }
        return null;
    }

    private static String getSourceFile(ParserKlass parsed) {
        String sourceFile = null;
        SourceFileAttribute sourceFileAttribute = (SourceFileAttribute) parsed.getAttribute(ParserNames.SourceFile);
        if (sourceFileAttribute != null) {
            sourceFile = parsed.getConstantPool().utf8At(sourceFileAttribute.getSourceFileIndex()).toString();
        }
        return sourceFile;
    }

    private static String getClassSignature(ParserKlass parsed) {
        String sourceFile = null;
        SignatureAttribute signatureAttribute = (SignatureAttribute) parsed.getAttribute(ParserNames.Signature);
        if (signatureAttribute != null) {
            sourceFile = parsed.getConstantPool().utf8At(signatureAttribute.getSignatureIndex()).toString();
        }
        return sourceFile;
    }

    @SuppressWarnings("try")
    public final Class<?> loadSuperType(Symbol<Type> name, Symbol<Type> superName) {
        Placeholder placeholder = new Placeholder();
        var prev = runtimeClasses.putIfAbsent(name, placeholder);
        if (prev instanceof Placeholder otherPlaceHolder) {
            if (otherPlaceHolder.isSuperProbingThread()) {
                throw new ClassCircularityError(name.toString());
            }
            otherPlaceHolder.addSuperProbingThread();
        }
        assert prev == null : prev;
        try (var scope = ClassLoading.allowArbitraryClassLoading()) {
            return loadClass(superName);
        } catch (ClassNotFoundException e) {
            NoClassDefFoundError error = new NoClassDefFoundError(superName.toString());
            error.initCause(e);
            throw error;
        } finally {
            runtimeClasses.remove(name, placeholder);
        }
    }

    private void registerClass(Class<?> clazz, Symbol<Type> type) {
        // GR-62320: record constraints
        var previous = runtimeClasses.put(type, clazz);
        assert previous == null;
    }

    private void registerStrongHiddenClass(Class<?> clazz) {
        strongHiddenClasses.add(clazz);
    }

    /**
     * See {@link AbstractClassRegistry#runtimeClasses}.
     */
    private static final class Placeholder extends CompletableFuture<Class<?>> {
        private static final long SUPER_PROBING_OFFSET = UNSAFE.objectFieldOffset(Placeholder.class, "otherSuperProbingThreads");
        private static final long THREAD_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(Thread[].class);
        private static final long THREAD_ELEMENT_SIZE = UNSAFE.arrayIndexScale(Thread[].class);
        private static final AtomicInteger NEXT_ID = new AtomicInteger(0);
        private final int id = NEXT_ID.getAndIncrement();
        private final Thread thread;
        private volatile Object otherSuperProbingThreads;

        Placeholder() {
            this.thread = Thread.currentThread();
        }

        @Override
        public String toString() {
            return "#" + id + ' ' + thread;
        }

        public boolean isSuperProbingThread() {
            Thread t = Thread.currentThread();
            if (t == thread) {
                return true;
            }
            Object others = otherSuperProbingThreads;
            if (t == others) {
                return true;
            }
            if (others instanceof Thread[] otherThreads) {
                for (Thread other : otherThreads) {
                    if (other == t) {
                        return true;
                    }
                }
            }
            return false;
        }

        public void addSuperProbingThread() {
            Thread t = Thread.currentThread();
            if (t == thread) {
                return;
            }
            Object others = otherSuperProbingThreads;
            if (others == null) {
                Object found = UNSAFE.compareAndExchangeReference(this, SUPER_PROBING_OFFSET, null, t);
                if (found == null) {
                    return;
                }
                others = found;
            }
            if (others == t) {
                return;
            }
            if (others instanceof Thread otherThread) {
                // extend to array
                Object found = UNSAFE.compareAndExchangeReference(this, SUPER_PROBING_OFFSET, otherThread, new Thread[]{otherThread, t});
                if (found == otherThread) {
                    return;
                }
                others = found;
            }
            Thread[] otherThreads = (Thread[]) others;
            while (true) {
                // try to insert in existing array
                for (int i = 0; i < otherThreads.length; i++) {
                    if (otherThreads[i] == t) {
                        return;
                    }
                    if (otherThreads[i] == null) {
                        if (UNSAFE.compareAndSetReference(otherThreads, THREAD_ARRAY_BASE_OFFSET + i * THREAD_ELEMENT_SIZE, null, t)) {
                            return;
                        }
                    }
                }
                // we have to grow
                Thread[] newArray = Arrays.copyOf(otherThreads, Math.max(otherThreads.length << 1, otherThreads.length + 1));
                newArray[otherThreads.length] = t;
                Object found = UNSAFE.compareAndExchangeReference(this, SUPER_PROBING_OFFSET, otherThreads, newArray);
                if (found == otherThreads) {
                    return;
                }
                otherThreads = (Thread[]) found;
                // try to insert in that new array
            }
        }
    }
}
