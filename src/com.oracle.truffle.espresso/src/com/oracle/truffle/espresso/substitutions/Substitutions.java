package com.oracle.truffle.espresso.substitutions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.ByteString;
import com.oracle.truffle.espresso.descriptors.ByteString.Name;
import com.oracle.truffle.espresso.descriptors.ByteString.Signature;
import com.oracle.truffle.espresso.descriptors.ByteString.Type;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicReflectionRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Substitutions/intrinsics for Espresso.
 *
 * Some substitutions are statically defined, others runtime-dependent. The static-ones are
 * initialized in the static initializer; which allows using MethodHandles instead of reflection in
 * SVM.
 */
public final class Substitutions implements ContextAccess {

    private final EspressoContext context;

    @Override
    public EspressoContext getContext() {
        return context;
    }

    public interface EspressoRootNodeFactory {
        EspressoRootNode spawnNode(Method method);
    }

    private static final EconomicMap<MethodKey, EspressoRootNodeFactory> STATIC_SUBSTITUTIONS = EconomicMap.create();

    private final ConcurrentHashMap<MethodKey, EspressoRootNodeFactory> runtimeSubstitutions = new ConcurrentHashMap<>();

    private static final List<Class<?>> ESPRESSO_SUBSTITUTIONS = Collections.unmodifiableList(Arrays.asList(
                    Target_java_lang_Class.class,
                    Target_java_lang_ClassLoader.class,
                    Target_java_lang_Object.class,
                    Target_java_lang_Package.class,
                    Target_java_lang_Runtime.class,
                    Target_java_lang_System.class,
                    Target_java_lang_Thread.class,
                    Target_java_lang_reflect_Array.class,
                    Target_java_security_AccessController.class,
                    Target_sun_misc_Perf.class,
                    Target_sun_misc_Signal.class,
                    Target_sun_misc_Unsafe.class,
                    Target_sun_misc_URLClassPath.class,
                    Target_sun_misc_VM.class,
                    Target_sun_reflect_NativeMethodAccessorImpl.class));

    static {
        for (Class<?> clazz : ESPRESSO_SUBSTITUTIONS) {
            registerStaticSubstitutions(clazz);
        }
    }

    public Substitutions(EspressoContext context) {
        this.context = context;
    }

    private static MethodKey getMethodKey(Method method) {
        return new MethodKey(
                        method.getDeclaringKlass().getType(),
                        method.getName(),
                        method.getRawSignature());
    }

    private static final class MethodKey {
        private final ByteString<Type> clazz;
        private final ByteString<Name> methodName;
        private final ByteString<Signature> signature;
        private final int hash;

        public MethodKey(ByteString<Type> clazz, ByteString<Name> methodName, ByteString<Signature> signature) {
            this.clazz = clazz;
            this.methodName = methodName;
            this.signature = signature;
            this.hash = Objects.hash(clazz, methodName, signature);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MethodKey other = (MethodKey) obj;
            return Objects.equals(clazz, other.clazz) &&
                            Objects.equals(methodName, other.methodName) &&
                            Objects.equals(signature, other.signature);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "MethodKey<" + clazz + "." + methodName + " -> " + signature + ">";
        }
    }

    private static void registerStaticSubstitutions(Class<?> clazz) {
        int registered = 0;

        ByteString<Type> classType;
        Class<?> annotatedClass = clazz.getAnnotation(EspressoSubstitutions.class).value();
        if (annotatedClass == EspressoSubstitutions.class) {
            // Target class is derived from class name by simple substitution
            // e.g. Target_java_lang_System becomes java.lang.System
            assert clazz.getSimpleName().startsWith("Target_");
            classType = Types.fromJavaString("L" + clazz.getSimpleName().substring("Target_".length()).replace('_', '/') + ";");
        } else {
            throw EspressoError.shouldNotReachHere("Substitutions class must be decorated with @" + EspressoSubstitutions.class.getName());
        }

        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            Substitution substitution = method.getAnnotation(Substitution.class);
            if (substitution == null) {
                continue;
            }

            final EspressoRootNodeFactory factory = new EspressoRootNodeFactory() {
                @Override
                public EspressoRootNode spawnNode(Method espressoMethod) {
                    return new IntrinsicReflectionRootNode(method, espressoMethod);
                }
            };

            StringBuilder signature = new StringBuilder("(");
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            for (int i = substitution.hasReceiver() ? 1 : 0; i < parameters.length; i++) {
                java.lang.reflect.Parameter parameter = parameters[i];
                ByteString<Type> parameterType;
                Host annotatedType = parameter.getAnnotatedType().getAnnotation(Host.class);
                if (annotatedType != null) {
                    parameterType = Types.fromClass(annotatedType.value());
                } else {
                    parameterType = Types.fromClass(parameter.getType());
                }
                signature.append(parameterType);
            }
            signature.append(')');

            Host annotatedReturnType = method.getAnnotatedReturnType().getAnnotation(Host.class);
            ByteString<Type> returnType;
            if (annotatedReturnType != null) {
                returnType = Types.fromClass(annotatedReturnType.value());
            } else {
                returnType = Types.fromClass(method.getReturnType());
            }
            signature.append(returnType);

            String methodName = substitution.methodName();
            if (methodName.length() == 0) {
                methodName = method.getName();
            }

            ++registered;
            registerStaticSubstitution(classType,
                            ByteString.fromJavaString(methodName),
                            ByteString.fromJavaString(signature.toString()),
                            factory,
                            true);
        }
        assert registered > 0 : "No substitutions found in " + clazz;
    }

    private static void registerStaticSubstitution(ByteString<Type> type, ByteString<Name> methodName, ByteString<Signature> signature, EspressoRootNodeFactory factory, boolean throwIfPresent) {
        MethodKey key = new MethodKey(type, methodName, signature);
        if (throwIfPresent && STATIC_SUBSTITUTIONS.containsKey(key)) {
            throw EspressoError.shouldNotReachHere("substitution already registered" + key);
        }
        STATIC_SUBSTITUTIONS.put(key, factory);
    }

    public void registerRuntimeSubstitution(ByteString<Type> type, ByteString<Name> methodName, ByteString<Signature> signature, EspressoRootNodeFactory factory, boolean throwIfPresent) {
        MethodKey key = new MethodKey(type, methodName, signature);

        EspressoError.warnIf(STATIC_SUBSTITUTIONS.containsKey(key), "Runtime substitution shadowed by static one " + key);

        if (throwIfPresent && runtimeSubstitutions.containsKey(key)) {
            throw EspressoError.shouldNotReachHere("substitution already registered" + key);
        }
        runtimeSubstitutions.put(key, factory);
    }

    public EspressoRootNode get(Method method) {
        MethodKey key = getMethodKey(method);
        EspressoRootNodeFactory factory = STATIC_SUBSTITUTIONS.get(key);
        if (factory == null) {
            factory = runtimeSubstitutions.get(key);
        }
        if (factory == null) {
            return null;
        }
        return factory.spawnNode(method);
    }
}
