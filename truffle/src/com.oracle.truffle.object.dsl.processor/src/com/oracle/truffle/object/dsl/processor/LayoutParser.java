/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.object.dsl.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.object.dsl.processor.model.LayoutModel;
import com.oracle.truffle.object.dsl.processor.model.NameUtils;
import com.oracle.truffle.object.dsl.processor.model.PropertyBuilder;
import com.oracle.truffle.object.dsl.processor.model.PropertyModel;

public class LayoutParser {

    private final LayoutProcessor processor;

    private TypeMirror objectTypeSuperclass;
    private LayoutModel superLayout;
    private String name;
    private String packageName;
    private String interfaceFullName;
    private boolean hasObjectTypeGuard;
    private boolean hasObjectGuard;
    private boolean hasDynamicObjectGuard;
    private boolean hasShapeProperties;
    private boolean hasCreate;
    private boolean hasBuilder;
    private final List<String> constructorProperties = new ArrayList<>();
    private final Map<String, PropertyBuilder> properties = new HashMap<>();
    private List<VariableElement> implicitCasts = new ArrayList<>();
    private final TruffleTypes types = ProcessorContext.getInstance().getTypes();
    private TypeMirror dispatch;

    public LayoutParser(LayoutProcessor processor) {
        this.processor = processor;
    }

    public void parse(TypeElement layoutElement) {
        if (layoutElement.getKind() != ElementKind.INTERFACE) {
            processor.reportError(layoutElement, "@Layout should only be applied to interfaces");
        }

        parseName(layoutElement);

        if (!layoutElement.getInterfaces().isEmpty()) {
            if (layoutElement.getInterfaces().size() > 1) {
                processor.reportError(layoutElement, "@Layout interfaces can have at most one super-interface");
            }

            final DeclaredType superInterface = (DeclaredType) layoutElement.getInterfaces().get(0);
            parseSuperLayout((TypeElement) superInterface.asElement());
        }

        for (AnnotationMirror annotationMirror : layoutElement.getAnnotationMirrors()) {
            if (isSameType(annotationMirror.getAnnotationType(), types.Layout)) {
                objectTypeSuperclass = ElementUtils.getAnnotationValue(TypeMirror.class, annotationMirror, "objectTypeSuperclass");

                if (ElementUtils.getAnnotationValue(Boolean.class, annotationMirror, "implicitCastIntToLong")) {
                    VariableElement var = ElementUtils.findVariableElement(types.Layout_ImplicitCast, "IntToLong");
                    implicitCasts.add(var);
                }

                if (ElementUtils.getAnnotationValue(Boolean.class, annotationMirror, "implicitCastIntToDouble")) {
                    VariableElement var = ElementUtils.findVariableElement(types.Layout_ImplicitCast, "IntToDouble");
                    implicitCasts.add(var);
                }

                dispatch = ElementUtils.getAnnotationValue(TypeMirror.class, annotationMirror, "dispatch");
            }
        }

        if (superLayout != null && !implicitCasts.isEmpty()) {
            processor.reportError(layoutElement, "@Layout implicit casts need to be specified in the base layout");
        }

        for (Element element : layoutElement.getEnclosedElements()) {
            if (element.getKind() == ElementKind.FIELD) {
                final String simpleName = element.getSimpleName().toString();

                if (simpleName.endsWith("_IDENTIFIER")) {
                    parseIdentifier((VariableElement) element);
                } else {
                    processor.reportError(element, "@Layout interface fields should only be identifier fields, ending with _IDENTIFIER");
                }
            }
        }

        for (Element element : layoutElement.getEnclosedElements()) {
            if (element.getKind() == ElementKind.METHOD) {
                final String simpleName = element.getSimpleName().toString();

                if (simpleName.equals("create" + name + "Shape")) {
                    parseShapeConstructor((ExecutableElement) element);
                }
            }
        }

        for (Element element : layoutElement.getEnclosedElements()) {
            if (element.getKind() == ElementKind.METHOD) {
                final String simpleName = element.getSimpleName().toString();

                if (simpleName.equals("create" + name + "Shape")) {
                    // Handled above
                } else if (simpleName.equals("create" + name)) {
                    parseConstructor((ExecutableElement) element);
                } else if (simpleName.equals("build")) {
                    parseBuilder((ExecutableElement) element);
                } else if (simpleName.equals("is" + name)) {
                    parseGuard((ExecutableElement) element);
                } else if (simpleName.startsWith("getAndSet")) {
                    parseGetAndSet((ExecutableElement) element);
                } else if (simpleName.startsWith("compareAndSet")) {
                    parseCompareAndSet((ExecutableElement) element);
                } else if (simpleName.startsWith("get")) {
                    parseGetter((ExecutableElement) element);
                } else if (simpleName.startsWith("set")) {
                    parseSetter((ExecutableElement) element);
                } else {
                    processor.reportError(element, "Unknown method prefix in @Layout interface - wouldn't know how to implement this method");
                }
            }
        }

        for (Element element : layoutElement.getEnclosedElements()) {
            if (element.getKind() != ElementKind.FIELD && element.getKind() != ElementKind.METHOD) {
                processor.reportError(element, "@Layout interfaces can only contain fields and methods");
            }
        }
    }

    private void parseName(TypeElement layoutElement) {
        parsePackageName(layoutElement);
        interfaceFullName = ElementUtils.getQualifiedName(layoutElement);
        final String nameString = layoutElement.getSimpleName().toString();

        if (!nameString.endsWith("Layout")) {
            processor.reportError(layoutElement, "@Layout interfaces should have a name ending with -Layout");
        }

        name = nameString.substring(0, nameString.length() - "Layout".length());
    }

    private void parseSuperLayout(TypeElement superTypeElement) {
        final LayoutParser superParser = new LayoutParser(processor);
        superParser.parse(superTypeElement);
        superLayout = superParser.build();
    }

    private void parseIdentifier(VariableElement fieldElement) {
        final String identifierName = fieldElement.getSimpleName().toString();
        final String propertyName = NameUtils.constantToIdentifier(identifierName.substring(0, identifierName.length() - "_IDENTIFIER".length()));
        getProperty(propertyName).setHasIdentifier(true);
    }

    private void parseShapeConstructor(ExecutableElement methodElement) {
        List<? extends VariableElement> parameters = methodElement.getParameters();
        if (!parameters.isEmpty()) {
            hasShapeProperties = true;
        }

        if (superLayout != null) {
            final List<PropertyModel> superShapeProperties = superLayout.getAllShapeProperties();
            checkSharedParameters(methodElement, parameters, superShapeProperties);
            parameters = parameters.subList(superShapeProperties.size(), parameters.size());
        }

        for (VariableElement element : parameters) {
            final String parameterName = element.getSimpleName().toString();
            constructorProperties.add(parameterName);
            final PropertyBuilder property = getProperty(parameterName);
            setPropertyType(element, property, element.asType());
            parseConstructorParameterAnnotations(property, element);
            property.setIsShapeProperty(true);
        }
    }

    private void parseConstructor(ExecutableElement methodElement) {
        hasCreate = true;
        checkCreateAndBuilder(methodElement);

        List<? extends VariableElement> parameters = methodElement.getParameters();

        if (hasShapeProperties) {
            if (parameters.isEmpty()) {
                processor.reportError(methodElement, "If an @Layout has shape properties the constructor must have parameters");
            }

            final VariableElement firstParameter = parameters.get(0);
            final String firstParameterName = firstParameter.getSimpleName().toString();

            if (!matches(firstParameterName, "factory")) {
                processor.reportError(firstParameter, "If an @Layout has shape properties, the first parameter of the constructor must be called factory (was %s)",
                                firstParameter.getSimpleName());
            }

            if (!isSameType(firstParameter.asType(), types.DynamicObjectFactory)) {
                processor.reportError(firstParameter, "If an @Layout has shape properties, the first parameter of the constructor must be of type DynamicObjectFactory (was %s)",
                                firstParameter.asType());
            }

            parameters = parameters.subList(1, parameters.size());
        }

        addConstructorProperties(methodElement, parameters);
    }

    private void parseBuilder(ExecutableElement methodElement) {
        hasBuilder = true;
        checkCreateAndBuilder(methodElement);

        List<? extends VariableElement> parameters = methodElement.getParameters();

        if (!isSameType(methodElement.getReturnType(), ProcessorContext.getInstance().getType(Object[].class))) {
            processor.reportError(methodElement, "build() must have Object[] for return type");
        }

        addConstructorProperties(methodElement, parameters);
    }

    private void checkCreateAndBuilder(ExecutableElement methodElement) {
        if (hasCreate && hasBuilder) {
            processor.reportError(methodElement, "Only one of create<Layout>() or build() may be specified.");
            return;
        }
    }

    private void addConstructorProperties(ExecutableElement methodElement, List<? extends VariableElement> parameters) {
        List<? extends VariableElement> ownParameters = parameters;
        if (superLayout != null) {
            final List<PropertyModel> superProperties = superLayout.getAllInstanceProperties();
            checkSharedParameters(methodElement, parameters, superProperties);
            ownParameters = parameters.subList(superProperties.size(), parameters.size());
        }

        for (VariableElement element : ownParameters) {
            final String parameterName = element.getSimpleName().toString();

            if (parameterName.equals("factory")) {
                processor.reportError(methodElement, "Factory is a confusing name for a property");
            }

            if (constructorProperties.contains(parameterName)) {
                processor.reportError(methodElement, "The property %s is duplicated");
            } else {
                constructorProperties.add(parameterName);
                final PropertyBuilder property = getProperty(parameterName);
                setPropertyType(element, property, element.asType());
                parseConstructorParameterAnnotations(property, element);
            }
        }
    }

    private void checkSharedParameters(Element element, List<? extends VariableElement> parameters, List<PropertyModel> sharedProperties) {
        if (parameters.size() < sharedProperties.size()) {
            processor.reportError(element, "@Layout constructor cannot have less parameters than the super layout constructor " + parameters + " " + sharedProperties);
        }

        for (int n = 0; n < sharedProperties.size(); n++) {
            final VariableElement parameter = parameters.get(n);
            final String parameterName = parameter.getSimpleName().toString();
            final PropertyModel superLayoutProperty = sharedProperties.get(n);

            if (superLayoutProperty.hasGeneratedName()) {
                // Assume the name is right if we cannot check it during incremental compilation
                superLayoutProperty.fixName(parameterName);
            }

            if (!parameterName.equals(superLayoutProperty.getName())) {
                processor.reportError(element, "@Layout constructor parameter %d needs to have the same name as the super layout constructor (is %s, should be %s)",
                                n, parameter.getSimpleName(), superLayoutProperty.getName());
            }

            if (!isSameType(parameter.asType(), superLayoutProperty.getType())) {
                processor.reportError(element, "@Layout constructor parameter %d needs to have the same type as the super layout constructor (is %s, should be %s)",
                                n, parameter.asType(), superLayoutProperty.getType());
            }
        }
    }

    private void parsePackageName(TypeElement layoutElement) {
        final String[] packageComponents = ElementUtils.getQualifiedName(layoutElement).split("\\.");

        final StringBuilder packageBuilder = new StringBuilder();

        for (int n = 0; n < packageComponents.length; n++) {
            if (Character.isUpperCase(packageComponents[n].charAt(0))) {
                break;
            }

            if (n > 0) {
                packageBuilder.append('.');
            }

            packageBuilder.append(packageComponents[n]);
        }

        packageName = packageBuilder.toString();
    }

    private void parseGuard(ExecutableElement methodElement) {
        if (methodElement.getParameters().size() != 1) {
            processor.reportError(methodElement, "@Layout guard methods must have just one parameter");
        }

        final VariableElement parameter = methodElement.getParameters().get(0);

        final TypeMirror type = parameter.asType();

        final String parameterName = parameter.getSimpleName().toString();
        final String expectedParameterName;

        if (isSameType(type, types.DynamicObject)) {
            hasDynamicObjectGuard = true;
            expectedParameterName = "object";
        } else if (isSameType(type, types.ObjectType)) {
            hasObjectTypeGuard = true;
            expectedParameterName = "objectType";
        } else if (isSameType(type, ProcessorContext.getInstance().getType(Object.class))) {
            hasObjectGuard = true;
            expectedParameterName = "object";
        } else {
            processor.reportError(methodElement, "@Layout guard method with unknown parameter type %s - don't know how to guard on this", type);
            expectedParameterName = null;
        }

        if (expectedParameterName != null && !matches(parameterName, expectedParameterName)) {
            processor.reportError(methodElement, "@Layout guard method should have a parameter named %s", expectedParameterName);
        }
    }

    private void parseGetter(ExecutableElement methodElement) {
        if (methodElement.getParameters().size() != 1) {
            processor.reportError(methodElement, "@Layout getter methods must have just one parameter");
        }

        final VariableElement parameter = methodElement.getParameters().get(0);
        final TypeMirror parameterType = parameter.asType();
        final String parameterName = parameter.getSimpleName().toString();

        final boolean isShapeGetter;
        final boolean isObjectTypeGetter;
        final String expectedParameterName;

        if (isSameType(parameterType, types.DynamicObject)) {
            isShapeGetter = false;
            isObjectTypeGetter = false;
            expectedParameterName = "object";
        } else if (isSameType(parameterType, types.DynamicObjectFactory)) {
            isShapeGetter = true;
            isObjectTypeGetter = false;
            expectedParameterName = "factory";
        } else if (isSameType(parameterType, types.ObjectType)) {
            isShapeGetter = false;
            isObjectTypeGetter = true;
            expectedParameterName = "objectType";
        } else {
            isShapeGetter = false;
            isObjectTypeGetter = false;
            expectedParameterName = null;
            processor.reportError(methodElement, "@Layout getter methods must have a parameter of type DynamicObject or, for shape properties, DynamicObjectFactory or ObjectType");
        }

        if (expectedParameterName != null && !matches(parameterName, expectedParameterName)) {
            processor.reportError(methodElement, "@Layout getter method should have a parameter named %s", expectedParameterName);
        }

        final String propertyName = NameUtils.titleToCamel(methodElement.getSimpleName().toString().substring("get".length()));
        final PropertyBuilder property = getProperty(propertyName);

        if (isShapeGetter) {
            property.setHasShapeGetter(true);
        } else if (isObjectTypeGetter) {
            property.setHasObjectTypeGetter(true);
        } else {
            property.setHasGetter(true);
        }

        setPropertyType(methodElement, property, methodElement.getReturnType());
    }

    private void parseSetter(ExecutableElement methodElement) {
        if (methodElement.getParameters().size() != 2) {
            processor.reportError(methodElement, "@Layout guard methods must have two parameters");
        }

        final VariableElement parameter = methodElement.getParameters().get(0);
        final TypeMirror parameterType = parameter.asType();
        final String parameterName = parameter.getSimpleName().toString();

        final boolean isShapeSetter;
        final String expectedParameterName;

        if (isSameType(parameterType, types.DynamicObject)) {
            isShapeSetter = false;
            expectedParameterName = "object";
        } else if (isSameType(parameterType, types.DynamicObjectFactory)) {
            isShapeSetter = true;
            expectedParameterName = "factory";
        } else {
            isShapeSetter = false;
            expectedParameterName = null;
            processor.reportError(methodElement, "@Layout setter methods must have a first parameter of type DynamicObject or, for shape properties, DynamicObjectFactory");
        }

        if (expectedParameterName != null && !matches(parameterName, expectedParameterName)) {
            processor.reportError(methodElement, "@Layout getter method should have a first parameter named %s", expectedParameterName);
        }

        final VariableElement secondParameter = methodElement.getParameters().get(1);
        final String secondParameterName = secondParameter.getSimpleName().toString();

        if (!matches(secondParameterName, "value")) {
            processor.reportError(methodElement, "@Layout getter method should have a second parameter named value");
        }

        final boolean isUnsafeSetter = methodElement.getSimpleName().toString().endsWith("Unsafe");

        String propertyName = NameUtils.titleToCamel(methodElement.getSimpleName().toString().substring("set".length()));

        if (isUnsafeSetter) {
            propertyName = propertyName.substring(0, propertyName.length() - "Unsafe".length());
        }

        final PropertyBuilder property = getProperty(propertyName);

        if (isShapeSetter) {
            property.setHasShapeSetter(true);
        } else {
            if (isUnsafeSetter) {
                property.setHasUnsafeSetter(isUnsafeSetter);
            } else {
                property.setHasSetter(true);
            }
        }

        setPropertyType(methodElement, property, methodElement.getParameters().get(1).asType());
    }

    private void parseCompareAndSet(ExecutableElement methodElement) {
        if (methodElement.getParameters().size() != 3) {
            processor.reportError(methodElement, "@Layout compare and set methods must have three parameters");
        }

        final VariableElement objectParameter = methodElement.getParameters().get(0);
        final VariableElement currentValueParameter = methodElement.getParameters().get(1);
        final VariableElement newValueParameter = methodElement.getParameters().get(2);

        if (!isSameType(objectParameter.asType(), types.DynamicObject)) {
            processor.reportError(methodElement, "@Layout compare and set method should have a first parameter of type DynamicObject");
        }

        if (!objectParameter.getSimpleName().toString().equals("object")) {
            processor.reportError(methodElement, "@Layout compare and set method should have a first parameter named object");
        }

        if (!currentValueParameter.getSimpleName().toString().equals("expectedValue")) {
            processor.reportError(methodElement, "@Layout compare and set method should have a second parameter named expectedValue");
        }

        if (!newValueParameter.getSimpleName().toString().equals("value")) {
            processor.reportError(methodElement, "@Layout compare and set method should have a third parameter named value");
        }

        String propertyName = NameUtils.titleToCamel(methodElement.getSimpleName().toString().substring("compareAndSet".length()));
        final PropertyBuilder property = getProperty(propertyName);

        property.setHasCompareAndSet(true);

        setPropertyType(methodElement, property, methodElement.getParameters().get(1).asType());
        setPropertyType(methodElement, property, methodElement.getParameters().get(2).asType());
    }

    private void parseGetAndSet(ExecutableElement methodElement) {
        if (methodElement.getParameters().size() != 2) {
            processor.reportError(methodElement, "@Layout get and set methods must have two parameters");
        }

        final VariableElement objectParameter = methodElement.getParameters().get(0);
        final VariableElement newValueParameter = methodElement.getParameters().get(1);

        if (!isSameType(objectParameter.asType(), types.DynamicObject)) {
            processor.reportError(methodElement, "@Layout get and set method should have a first parameter of type DynamicObject");
        }

        if (!objectParameter.getSimpleName().toString().equals("object")) {
            processor.reportError(methodElement, "@Layout get and set method should have a first parameter named object");
        }

        if (!newValueParameter.getSimpleName().toString().equals("value")) {
            processor.reportError(methodElement, "@Layout get and set method should have a second parameter named value");
        }

        String propertyName = NameUtils.titleToCamel(methodElement.getSimpleName().toString().substring("getAndSet".length()));
        final PropertyBuilder property = getProperty(propertyName);

        property.setHasGetAndSet(true);

        setPropertyType(methodElement, property, methodElement.getParameters().get(1).asType());
        setPropertyType(methodElement, property, methodElement.getReturnType());
    }

    private void parseConstructorParameterAnnotations(PropertyBuilder property, Element element) {
        if (ElementUtils.findAnnotationMirror(element, types.Nullable) != null) {
            property.setNullable(true);
        }

        if (ElementUtils.findAnnotationMirror(element, types.Volatile) != null) {
            property.setVolatile(true);
        }
    }

    private boolean isSameType(TypeMirror a, TypeMirror b) {
        return processor.getProcessingEnv().getTypeUtils().isSameType(a, b);
    }

    // Whether the parameter name is a fake generated one (argX).
    // Happens only during superLayout parsing.
    public static boolean isGeneratedName(String name) {
        return name.length() > 3 && name.startsWith("arg") && Character.isDigit(name.charAt(3));
    }

    private static boolean matches(String parameterName, String expected) {
        if (isGeneratedName(parameterName)) {
            return true;
        }
        return parameterName.equals(expected);
    }

    private void setPropertyType(Element element, PropertyBuilder builder, TypeMirror type) {
        if (builder.getType() == null) {
            builder.setType(type);
        } else if (!isSameType(type, builder.getType())) {
            processor.reportError(element, "@Layout property types are inconsistent - was previously %s but now %s",
                            builder.getType(), type);
        }
    }

    private PropertyBuilder getProperty(String propertyName) {
        PropertyBuilder builder = properties.get(propertyName);

        if (builder == null) {
            builder = new PropertyBuilder(propertyName);
            properties.put(propertyName, builder);
        }

        return builder;
    }

    public LayoutModel build() {
        return new LayoutModel(objectTypeSuperclass, superLayout, name, packageName, hasObjectTypeGuard, hasObjectGuard,
                        hasDynamicObjectGuard, hasBuilder, buildProperties(), interfaceFullName, implicitCasts, dispatch);
    }

    private List<PropertyModel> buildProperties() {
        final List<PropertyModel> models = new ArrayList<>();

        for (String propertyName : constructorProperties) {
            models.add(getProperty(propertyName).build());
        }

        return models;
    }

}
