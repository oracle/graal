package org.graalvm.compiler.nodes.spi;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This interface defines a service for reading method annotations. It is intended to be used when
 * the {@code ResolvedJavaMethod} implementation does not proved access to annotations.
 */
public interface MethodAnnotationProvider {
    /**
     * Determines if {@code method} has an annotation of type {@code annotationClassName}.
     */
    boolean hasAnnotation(ResolvedJavaMethod method, String annotationClassName);
}
