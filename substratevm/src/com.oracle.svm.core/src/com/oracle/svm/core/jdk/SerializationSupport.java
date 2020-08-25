package com.oracle.svm.core.jdk;

import java.io.ObjectStreamClass;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

public class SerializationSupport {
    private static final Constructor<ObjectStreamClass> STREAMCLASS_CONSTRUCTOR = ReflectionUtil.lookupConstructor(ObjectStreamClass.class, Class.class);

    private final ConcurrentHashMap<Class<?>, ObjectStreamClass> streamClasses = new ConcurrentHashMap<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addClass(Class<?> clazz) {
        streamClasses.computeIfAbsent(clazz, c -> {
            try {
                RuntimeReflection.register(c); // for forName accesses
                return STREAMCLASS_CONSTRUCTOR.newInstance(c);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw VMError.shouldNotReachHere(e);
            }
        });
    }

    public ObjectStreamClass getStreamClass(Class<?> clazz) {
        return streamClasses.get(clazz);
    }
}
