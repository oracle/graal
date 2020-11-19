package org.graalvm.compiler.truffle.compiler.hotspot;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodes.spi.MethodAnnotationProvider;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

@ServiceProvider(MethodAnnotationProvider.class)
public class TruffleMethodAnnotationProvider implements MethodAnnotationProvider {
    @SuppressWarnings("unchecked")
    @Override
    public boolean hasAnnotation(ResolvedJavaMethod method, String annotationClassName) {
        try {
            final Class<?> cls = Class.forName(annotationClassName);
            return method.isAnnotationPresent((Class<? extends Annotation>) cls);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAnnotationElement(ResolvedJavaMethod method, String annotationClassName, String elementName, Class<?> elementType) {
        try {
            final Class<?> cls = Class.forName(annotationClassName);
            final Annotation annotation = method.getAnnotation((Class<? extends Annotation>) cls);
            if (annotation == null) {
                return null;
            }
            return (T) cls.getDeclaredMethod(elementName).invoke(annotation);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }
}
