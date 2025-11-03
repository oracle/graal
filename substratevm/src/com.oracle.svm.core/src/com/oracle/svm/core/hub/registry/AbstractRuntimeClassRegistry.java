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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.impl.ClassLoading;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.hub.registry.SVMSymbols.SVMTypes;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ClassfileParser;
import com.oracle.svm.espresso.classfile.ClassfileStream;
import com.oracle.svm.espresso.classfile.ParserException;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.attributes.Attribute;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.ValidationException;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.loader.ClassLoaders;
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
    public static final Object UNINITIALIZED_DECLARING_CLASS_SENTINEL = new Object();
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    private static final ClassLoader bootLoader;
    static {
        Method method = ReflectionUtil.lookupMethod(ClassLoaders.class, "bootLoader");
        bootLoader = ReflectionUtil.invokeMethod(method, null);
    }
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
        // GR-62338: for parallel class loaders this synchronization should be skipped.
        Object syncObject = getClassLoader();
        if (syncObject == null) {
            syncObject = this;
        }
        synchronized (syncObject) {
            return defineClassInner(typeOrNull, b, off, len, info);
        }
    }

    private Class<?> defineClassInner(Symbol<Type> typeOrNull, byte[] b, int off, int len, ClassDefinitionInfo info) {
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
        assert typeOrNull == null || type == parsed.getType() : typeOrNull + " vs. " + parsed.getType();
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
        int typeID = TypeIDs.singleton().nextTypeId();
        if (info.isHidden) {
            parsed = ParserKlass.forHiddenClass(parsed, typeOrNull, typeID, ClassRegistries.getParsingContext());
        }
        Class<?> clazz;
        try {
            clazz = createClass(parsed, info, type, typeID);
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

    private Class<?> createClass(ParserKlass parsed, ClassDefinitionInfo info, Symbol<Type> type, int typeID) {
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

        checkNotHybrid(parsed);

        // GR-62339
        Module module;
        ClassLoader classLoader = getClassLoader();
        if (classLoader == null) {
            module = bootLoader.getUnnamedModule();
        } else {
            module = classLoader.getUnnamedModule();
        }

        String externalName = getExternalName(parsed, info);
        DynamicHub hub = CremaSupport.singleton().createHub(parsed, info, typeID, externalName, module, classLoader, superClass, superInterfaces);
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
        try (var _ = ClassLoading.allowArbitraryClassLoading()) {
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
