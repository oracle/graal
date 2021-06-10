/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.reflect.serialize.hosted;

// Checkstyle: allow reflection

import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.jdk.Package_jdk_internal_reflect;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.jdk.serialize.SerializationRegistry;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.hosted.FeatureImpl.FeatureAccessImpl;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.reflect.serialize.SerializationSupport;
import com.oracle.svm.util.ReflectionUtil;
import jdk.vm.ci.meta.MetaUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.io.Externalizable;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public final class SerializationBuilder {

    private final Object reflectionFactory;
    private final Method newConstructorForSerializationMethod1;
    private final Method newConstructorForSerializationMethod2;
    private final Method getConstructorAccessorMethod;
    private final Method getExternalizableConstructorMethod;
    private final Constructor<?> stubConstructor;
    private FeatureAccessImpl access;
    private final SerializationSupport support;

    public SerializationBuilder(FeatureAccessImpl access) {
        try {
            Class<?> reflectionFactoryClass = access.findClassByName(Package_jdk_internal_reflect.getQualifiedName() + ".ReflectionFactory");
            this.access = access;
            Method getReflectionFactoryMethod = ReflectionUtil.lookupMethod(reflectionFactoryClass, "getReflectionFactory");
            reflectionFactory = getReflectionFactoryMethod.invoke(null);
            newConstructorForSerializationMethod1 = ReflectionUtil.lookupMethod(reflectionFactoryClass, "newConstructorForSerialization", Class.class);
            newConstructorForSerializationMethod2 = ReflectionUtil.lookupMethod(reflectionFactoryClass, "newConstructorForSerialization", Class.class, Constructor.class);
            getConstructorAccessorMethod = ReflectionUtil.lookupMethod(Constructor.class, "getConstructorAccessor");
            getExternalizableConstructorMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getExternalizableConstructor", Class.class);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
        stubConstructor = newConstructorForSerialization(SerializationSupport.StubForAbstractClass.class, null);
        support = (SerializationSupport) ImageSingletons.lookup(SerializationRegistry.class);
    }

    public void registerSerializationTarget(String targetSerializationClassName, String customTargetConstructorClassName) {
        Class<?> serializationTargetClass = resolveClass(targetSerializationClassName);
        registerSerializationTarget(serializationTargetClass, customTargetConstructorClassName);
    }

    public void registerSerializationTarget(Class<?> serializationTargetClass) {
        registerSerializationTarget(serializationTargetClass, null);
    }

    /**
     * Register serialization target class itself and all associated classes. This method
     * helps to register simple target class programmatically at build time instead
     * of providing configurations.
     * <p>
     * According to the Java Object Serialization Specification, the associated classes
     * include 1) all the target class' non-static and non-transient fields types
     * and their associated classes; 2) other fields defined in the customised
     * writeObject(ObjectOutputStream) and readObject(ObjectInputStream).
     * This method can automatically explore all possible serialization target classes
     * in the first scenario, but can't figure out the classes in the second scenario.
     * <p>
     * Another limitation is the specified {@code serializationTargetClass} must
     * have no subclasses (effectively final). Otherwise, the actual serialization
     * target class could be any subclass of the specified class at runtime.
     * @param serializationTargetClass
     */
    public void registerSerializationTargetAtRuntTime(Class<?> serializationTargetClass) {
        if (!support.isRegistered(serializationTargetClass)) {
            support.addRegistered(serializationTargetClass);
            String targetClassName = serializationTargetClass.getName();
            if (serializationTargetClass.isPrimitive()) {
                Class<?> boxedType = null;
                switch (targetClassName) {
                    case "int":
                        boxedType = Integer.class;
                        break;
                    case "float":
                        boxedType = Float.class;
                        break;
                    case "double":
                        boxedType = Double.class;
                        break;
                    case "long":
                        boxedType = Long.class;
                        break;
                    case "byte":
                        boxedType = Byte.class;
                        break;
                    case "char":
                        boxedType = Character.class;
                        break;
                    case "boolean":
                        boxedType = Boolean.class;
                        break;
                    case "short":
                        boxedType = Short.class;
                        break;
                    case "void": // no need to register void for serialization
                        return;
                    default:
                        VMError.shouldNotReachHere(targetClassName + " is not primitive type.");
                }
                registerSerializationTargetAtRuntTime(boxedType);
                return;
            } else if (!Serializable.class.isAssignableFrom(serializationTargetClass)) {
                println("Warning: The given class " + targetClassName + " is not serializable, it wouldn't get registered.\n");
                return;
            } else if (access.findSubclasses(serializationTargetClass).size() > 1) {
                println("Warning: The given class " + targetClassName +
                        " has subclasses. We can't figure out which one should be registered. Skip.\n");
                return;
            }
            try {
                serializationTargetClass.getDeclaredMethod("writeObject", ObjectOutputStream.class);
                println("Warning: The given class " + targetClassName +
                        " has customized serialization method writeObject. " +
                        "We can't figure out what should be registered. Skip.\n");
                return;
            } catch (NoSuchMethodException e) {
                // Expected case. Do nothing
            }
            registerSerializationTarget(serializationTargetClass);

            if (serializationTargetClass.isArray()) {
                registerSerializationTargetAtRuntTime(serializationTargetClass.getComponentType());
                return;
            }
            ObjectStreamClass osc = ObjectStreamClass.lookup(serializationTargetClass);
            try {
                Method getDataLayoutMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getClassDataLayout");
                Object[] dataLayout = (Object[]) getDataLayoutMethod.invoke(osc);
                for (int i = 0; i < dataLayout.length; i++) {
                    Class<?> classDataSlotClazz = Class.forName("java.io.ObjectStreamClass$ClassDataSlot");
                    Field descField = ReflectionUtil.lookupField(classDataSlotClazz, "desc");
                    ObjectStreamClass desc = (ObjectStreamClass) descField.get(dataLayout[i]);
                    if (!desc.equals(osc) && !desc.equals(serializationTargetClass)) {
                        registerSerializationTargetAtRuntTime(desc.forClass());
                    }
                }
            } catch (ReflectiveOperationException e) {
                VMError.shouldNotReachHere("Cannot register serialization classes due to", e);
            }

            ObjectStreamField[] fields = osc.getFields();
            for (int i = 0; i < fields.length; i++) {
                registerSerializationTargetAtRuntTime(fields[i].getType());
            }
        }
    }

    /**
     * Only register the specified serializationTargetClass itself, no associated classes.
     * See {@link SerializationBuilder#registerSerializationTargetAtRuntTime(Class)}
     * for the definition of associated classes.
     * @param serializationTargetClass
     * @param customTargetConstructorClassName
     */
    public void registerSerializationTarget(Class<?> serializationTargetClass, String customTargetConstructorClassName) {
        UserError.guarantee(serializationTargetClass != null, "Cannot find serialization target class %s. The missing of this class can't be ignored even if -H:+AllowIncompleteClasspath is set." +
                        " Please make sure it is in the classpath", serializationTargetClass.getName());
        if (Serializable.class.isAssignableFrom(serializationTargetClass)) {
            Map<Class<?>, Boolean> deniedClasses = support.getDeniedClasses();
            if (deniedClasses.containsKey(serializationTargetClass)) {
                if (deniedClasses.get(serializationTargetClass)) {
                    deniedClasses.put(serializationTargetClass, false); /* Warn only once */
                    println("Warning: Serialization deny list contains " + serializationTargetClass.getName() + ". Image will not support serialization/deserialization of this class.");
                }
            } else {
                Class<?> customTargetConstructorClass = null;
                if (customTargetConstructorClassName != null) {
                    customTargetConstructorClass = resolveClass(customTargetConstructorClassName);
                    UserError.guarantee(customTargetConstructorClass != null, "Cannot find " + SerializationConfigurationParser.CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY + " %s that was specified in" +
                                    " the serialization configuration. The missing of this class can't be ignored even if -H:+AllowIncompleteClasspath is set. Please make sure it is in the classpath",
                                    customTargetConstructorClassName);
                    UserError.guarantee(customTargetConstructorClass.isAssignableFrom(serializationTargetClass),
                                    "The given " + SerializationConfigurationParser.CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY +
                                                    " %s that was specified in the serialization configuration is not a subclass of the serialization target class %s.",
                                    customTargetConstructorClassName, serializationTargetClass.getName());
                }
                Class<?> targetConstructor = addConstructorAccessor(serializationTargetClass, customTargetConstructorClass);
                addReflections(serializationTargetClass, targetConstructor);
            }
        }
    }

    private static void addReflections(Class<?> serializationTargetClass, Class<?> targetConstructorClass) {
        if (targetConstructorClass != null) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(targetConstructorClass));
        }

        if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(serializationTargetClass, (Class<?>[]) null));
        }

        RecordSupport recordSupport = RecordSupport.singleton();
        if (recordSupport.isRecord(serializationTargetClass)) {
            /* Serialization for records uses the canonical record constructor directly. */
            RuntimeReflection.register(recordSupport.getCanonicalRecordConstructor(serializationTargetClass));
            /*
             * Serialization for records invokes Class.getRecordComponents(). Registering all record
             * component accessor methods for reflection ensures that the record components are
             * available at run time.
             */
            RuntimeReflection.register(recordSupport.getRecordComponentAccessorMethods(serializationTargetClass));
        }

        RuntimeReflection.register(serializationTargetClass);
        /*
         * ObjectStreamClass.computeDefaultSUID is always called at runtime to verify serialization
         * class consistency, so need to register all constructors, methods and fields/
         */
        RuntimeReflection.register(serializationTargetClass.getDeclaredConstructors());
        registerMethods(serializationTargetClass);
        registerFields(serializationTargetClass);
    }

    private static void registerMethods(Class<?> serializationTargetClass) {
        RuntimeReflection.register(serializationTargetClass.getDeclaredMethods());
        // computeDefaultSUID will be reflectively called at runtime to verify class consistency
        Method computeDefaultSUID = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "computeDefaultSUID", Class.class);
        RuntimeReflection.register(computeDefaultSUID);
    }

    private static void registerFields(Class<?> serializationTargetClass) {
        RuntimeReflection.register(serializationTargetClass.getDeclaredFields());
    }

    public Class<?> resolveClass(String typeName) {
        String name = typeName;
        if (name.indexOf('[') != -1) {
            /* accept "int[][]", "java.lang.String[]" */
            name = MetaUtil.internalNameToJava(MetaUtil.toInternalName(name), true, true);
        }
        Class<?> ret = access.findClassByName(name);
        if (ret == null) {
            handleError("Could not resolve " + name + " for serialization configuration.");
        }
        return ret;
    }

    private static void handleError(String message) {
        boolean allowIncompleteClasspath = NativeImageOptions.AllowIncompleteClasspath.getValue();
        if (allowIncompleteClasspath) {
            println("WARNING: " + message);
        } else {
            throw new JSONParserException(message + " To allow unresolvable reflection configuration, use option -H:+AllowIncompleteClasspath");
        }
    }

    static void println(String str) {
        // Checkstyle: stop
        System.out.println(str);
        // Checkstyle: resume
    }

    private Constructor<?> newConstructorForSerialization(Class<?> serializationTargetClass, Constructor<?> customConstructorToCall) {
        try {
            if (customConstructorToCall == null) {
                return (Constructor<?>) newConstructorForSerializationMethod1.invoke(reflectionFactory, serializationTargetClass);
            } else {
                return (Constructor<?>) newConstructorForSerializationMethod2.invoke(reflectionFactory, serializationTargetClass, customConstructorToCall);
            }
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object getConstructorAccessor(Constructor<?> constructor) {
        try {
            return getConstructorAccessorMethod.invoke(constructor);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Constructor<?> getExternalizableConstructor(Class<?> serializationTargetClass) {
        try {
            return (Constructor<?>) getExternalizableConstructorMethod.invoke(null, serializationTargetClass);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Class<?> addConstructorAccessor(Class<?> serializationTargetClass, Class<?> customTargetConstructorClass) {
        if (serializationTargetClass.isArray() || Enum.class.isAssignableFrom(serializationTargetClass)) {
            return null;
        }

        // Don't generate SerializationConstructorAccessor class for Externalizable case
        if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            try {
                Constructor<?> externalizableConstructor = getExternalizableConstructor(serializationTargetClass);
                return externalizableConstructor.getDeclaringClass();
            } catch (Exception e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        /*
         * Using reflection to make sure code is compatible with both JDK 8 and above. Reflectively
         * call method ReflectionFactory.newConstructorForSerialization(Class) to get the
         * SerializationConstructorAccessor instance.
         */
        Constructor<?> targetConstructor;
        Class<?> targetConstructorClass;
        if (Modifier.isAbstract(serializationTargetClass.getModifiers())) {
            targetConstructor = stubConstructor;
            targetConstructorClass = targetConstructor.getDeclaringClass();
        } else {
            Constructor<?> customConstructorToCall = null;
            if (customTargetConstructorClass != null) {
                try {
                    customConstructorToCall = customTargetConstructorClass.getDeclaredConstructor();
                } catch (NoSuchMethodException ex) {
                    UserError.abort("The given targetConstructorClass %s does not declare a parameterless constructor.",
                                    customTargetConstructorClass.getTypeName());
                }
            }
            targetConstructor = newConstructorForSerialization(serializationTargetClass, customConstructorToCall);
            targetConstructorClass = targetConstructor.getDeclaringClass();
        }

        support.addConstructorAccessor(serializationTargetClass, targetConstructorClass, getConstructorAccessor(targetConstructor));
        return targetConstructorClass;
    }
}
