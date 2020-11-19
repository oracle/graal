package org.graalvm.compiler.nodes.spi;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public interface MethodAnnotationProvider {
    /**
     * Determines if {@code method} has an annotation of type {@code annotationClassName}.
     */
    boolean hasAnnotation(ResolvedJavaMethod method, String annotationClassName);

    /**
     * Gets the value the element {@code elementName} from the annotation of type
     * {@code annotationClassName} on {@code method}.
     *
     * @return {@code null} if the annotation or element does not exist
     */
    <T> T getAnnotationElement(ResolvedJavaMethod method, String annotationClassName, String elementName, Class<?> elementType);
}
