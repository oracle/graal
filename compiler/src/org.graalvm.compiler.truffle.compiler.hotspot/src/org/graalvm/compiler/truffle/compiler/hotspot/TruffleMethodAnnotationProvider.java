package org.graalvm.compiler.truffle.compiler.hotspot;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodes.spi.MethodAnnotationProvider;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

import java.lang.annotation.Annotation;

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
}
