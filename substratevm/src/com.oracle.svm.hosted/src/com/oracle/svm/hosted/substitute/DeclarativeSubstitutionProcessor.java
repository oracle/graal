/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.hosted.substitute;

import static com.oracle.svm.core.util.UserError.guarantee;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.shadowed.com.google.gson.Gson;
import com.oracle.shadowed.com.google.gson.GsonBuilder;
import com.oracle.shadowed.com.google.gson.JsonArray;
import com.oracle.shadowed.com.google.gson.JsonObject;
import com.oracle.shadowed.com.google.gson.JsonPrimitive;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This class allows to provide substitutions (see {@link TargetClass}) using a JSON file instead of
 * annotations. Projects that do not depend on Substrate VM can provide such a JSON file.
 */
public class DeclarativeSubstitutionProcessor extends AnnotationSubstitutionProcessor {

    public static class Options {
        @Option(help = "Comma-separated list of file names with declarative substitutions")//
        public static final HostedOptionKey<String> SubstitutionFiles = new HostedOptionKey<>("");

        @Option(help = "Comma-separated list of resource file names with declarative substitutions")//
        public static final HostedOptionKey<String> SubstitutionResources = new HostedOptionKey<>("");

        @Option(help = "Print all substitutions in the format expected by SubstitutionFiles")//
        public static final HostedOptionKey<Boolean> PrintSubstitutions = new HostedOptionKey<>(false);
    }

    private final Map<Class<?>, ClassDescriptor> classDescriptors;
    private final Map<Executable, MethodDescriptor> methodDescriptors;
    private final Map<Field, FieldDescriptor> fieldDescriptors;

    public DeclarativeSubstitutionProcessor(ImageClassLoader imageClassLoader, MetaAccessProvider metaAccess) {
        super(imageClassLoader, metaAccess);

        classDescriptors = new HashMap<>();
        methodDescriptors = new HashMap<>();
        fieldDescriptors = new HashMap<>();

        for (String substitutionFileName : Options.SubstitutionFiles.getValue().split(",")) {
            try {
                if (!substitutionFileName.isEmpty()) {
                    loadFile(new FileReader(substitutionFileName));
                }
            } catch (FileNotFoundException ex) {
                throw UserError.abort("Substitution file " + substitutionFileName + " not found.");
            }
        }
        for (String substitutionResourceName : Options.SubstitutionResources.getValue().split(",")) {
            if (!substitutionResourceName.isEmpty()) {
                InputStream substitutionStream = imageClassLoader.findResourceAsStreamByName(substitutionResourceName);
                guarantee(substitutionStream != null, "substitution file %s does not exist", substitutionResourceName);
                loadFile(new InputStreamReader(substitutionStream));
            }
        }

        if (Options.PrintSubstitutions.getValue()) {
            printAllAnnotations();
        }
    }

    private void loadFile(Reader reader) {
        Set<Class<?>> annotatedClasses = new HashSet<>(imageClassLoader.findAnnotatedClasses(TargetClass.class));

        Gson gson = new GsonBuilder().create();
        ClassDescriptor[] allDescriptors = gson.fromJson(reader, ClassDescriptor[].class);
        for (ClassDescriptor classDescriptor : allDescriptors) {
            if (classDescriptor == null) {
                /* Empty or trailing array elements are parsed to null. */
                continue;
            }

            Class<?> annotatedClass = imageClassLoader.findClassByName(classDescriptor.annotatedClass);

            if (annotatedClasses.contains(annotatedClass)) {
                throw UserError.abort("target class already registered using explicit @TargetClass annotation: " + annotatedClass);
            } else if (classDescriptors.containsKey(annotatedClass)) {
                throw UserError.abort("target class already registered using substitution file: " + annotatedClass);
            }
            classDescriptors.put(annotatedClass, classDescriptor);

            for (MethodDescriptor methodDescriptor : classDescriptor.methods) {
                if (methodDescriptor == null) {
                    /* Empty or trailing array elements are parsed to null. */
                    continue;
                }
                Executable annotatedMethod;
                if (methodDescriptor.parameterTypes != null) {
                    annotatedMethod = findMethod(annotatedClass, methodDescriptor.annotatedName, methodDescriptor.parameterTypes);
                } else {
                    annotatedMethod = findMethod(annotatedClass, methodDescriptor.annotatedName, true);
                }
                methodDescriptors.put(annotatedMethod, methodDescriptor);
            }
            for (FieldDescriptor fieldDescriptor : classDescriptor.fields) {
                Field annotatedField = findField(annotatedClass, fieldDescriptor.annotatedName);
                fieldDescriptors.put(annotatedField, fieldDescriptor);
            }
        }
    }

    private static Executable findMethod(Class<?> declaringClass, String methodName, boolean fatalIfNotUnique) {
        Executable result = null;
        if (methodName.equals(TargetElement.CONSTRUCTOR_NAME)) {
            for (Constructor<?> c : declaringClass.getDeclaredConstructors()) {
                if (result != null) {
                    guarantee(!fatalIfNotUnique, "two constructors found: %s, %s ", result, c);
                    return null;
                }
                result = c;
            }
            guarantee(!fatalIfNotUnique || result != null, "no constructor found: %s", declaringClass);
        } else {
            for (Method m : declaringClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    if (result != null) {
                        guarantee(!fatalIfNotUnique, "two methods with same name found: %s, %s ", result, m);
                        return null;
                    }
                    result = m;
                }
            }
            guarantee(!fatalIfNotUnique || result != null, "method not found: %s, method name %s", declaringClass, methodName);
        }
        return result;
    }

    private Executable findMethod(Class<?> declaringClass, String methodName, String[] parameterTypeNames) {
        Class<?>[] parameterTypes = new Class<?>[parameterTypeNames.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = imageClassLoader.findClassByName(parameterTypeNames[i]);
        }
        try {
            if (methodName.equals(TargetElement.CONSTRUCTOR_NAME)) {
                return declaringClass.getDeclaredConstructor(parameterTypes);
            } else {
                return declaringClass.getDeclaredMethod(methodName, parameterTypes);
            }
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere("method not found: " + declaringClass + ", method name " + methodName + ", parameter types " + Arrays.toString(parameterTypeNames));
        }
    }

    private static Field findField(Class<?> declaringClass, String fieldName) {
        try {
            return declaringClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    protected List<Class<?>> findTargetClasses() {
        List<Class<?>> result = super.findTargetClasses();
        result.addAll(classDescriptors.keySet());
        return result;
    }

    @Override
    protected <T extends Annotation> T lookupAnnotation(AnnotatedElement element, Class<T> annotationClass) {
        Annotation result;
        if (element instanceof Class && classDescriptors.containsKey(element)) {
            ClassDescriptor classDescriptor = classDescriptors.get(element);
            if (annotationClass == Platforms.class) {
                result = classDescriptor.platforms != null ? classDescriptor.new PlatformsImpl(imageClassLoader) : null;
            } else if (annotationClass == TargetClass.class) {
                result = classDescriptor.new TargetClassImpl();
            } else if (annotationClass == Delete.class) {
                result = classDescriptor.delete ? new DeleteImpl() : null;
            } else if (annotationClass == Substitute.class) {
                result = classDescriptor.substitute ? new SubstituteImpl() : null;
            } else {
                throw VMError.shouldNotReachHere("Unexpected annotation type: " + annotationClass.getName());
            }

        } else if (element instanceof Executable && methodDescriptors.containsKey(element)) {
            MethodDescriptor methodDescriptor = methodDescriptors.get(element);
            if (annotationClass == Platforms.class) {
                result = methodDescriptor.platforms != null ? methodDescriptor.new PlatformsImpl(imageClassLoader) : null;
            } else if (annotationClass == TargetElement.class) {
                result = methodDescriptor.new TargetElementImpl();
            } else if (annotationClass == Delete.class) {
                result = methodDescriptor.delete ? new DeleteImpl() : null;
            } else if (annotationClass == Substitute.class) {
                result = methodDescriptor.substitute ? new SubstituteImpl() : null;
            } else if (annotationClass == AnnotateOriginal.class) {
                result = methodDescriptor.annotateOriginal ? new AnnotateOriginalImpl() : null;
            } else if (annotationClass == KeepOriginal.class) {
                result = methodDescriptor.keepOriginal ? new KeepOriginalImpl() : null;
            } else if (annotationClass == Alias.class) {
                result = methodDescriptor.alias ? new AliasImpl() : null;
            } else {
                throw VMError.shouldNotReachHere("Unexpected annotation type: " + annotationClass.getName());
            }

        } else if (element instanceof Field && fieldDescriptors.containsKey(element)) {
            FieldDescriptor fieldDescriptor = fieldDescriptors.get(element);
            if (annotationClass == Platforms.class) {
                result = fieldDescriptor.platforms != null ? fieldDescriptor.new PlatformsImpl(imageClassLoader) : null;
            } else if (annotationClass == TargetElement.class) {
                result = fieldDescriptor.new TargetElementImpl();
            } else if (annotationClass == Delete.class) {
                result = fieldDescriptor.delete ? new DeleteImpl() : null;
            } else if (annotationClass == Alias.class) {
                result = fieldDescriptor.alias ? new AliasImpl() : null;
            } else if (annotationClass == Inject.class) {
                result = fieldDescriptor.inject ? new InjectImpl() : null;
            } else if (annotationClass == RecomputeFieldValue.class) {
                result = fieldDescriptor.kind != null ? fieldDescriptor.new RecomputeFieldValueImpl() : null;
            } else if (annotationClass == InjectAccessors.class) {
                result = fieldDescriptor.injectAccessors != null ? fieldDescriptor.new InjectAccessorsImpl(imageClassLoader) : null;
            } else {
                throw VMError.shouldNotReachHere("Unexpected annotation type: " + annotationClass.getName());
            }

        } else {
            result = super.lookupAnnotation(element, annotationClass);
        }
        return annotationClass.cast(result);
    }

    /**
     * Prints out all substitutions in the JSON format. Only the minimal set of properties is
     * printed to make the file as readable as possible.
     */
    private void printAllAnnotations() {
        List<Class<?>> annotatedClasses = findTargetClasses();
        annotatedClasses.sort(Comparator.comparing(Class::getName));

        JsonArray classesArray = new JsonArray();
        for (Class<?> annotatedClass : annotatedClasses) {
            JsonObject classObject = printClass(annotatedClass);
            classesArray.add(classObject);

            JsonArray methodsArray = new JsonArray();
            for (Constructor<?> annotatedMethod : annotatedClass.getDeclaredConstructors()) {
                printMethod(annotatedMethod, methodsArray);
            }
            Method[] declaredMethods = annotatedClass.getDeclaredMethods();
            Arrays.sort(declaredMethods, Comparator.comparing(Method::getName));
            for (Method annotatedMethod : declaredMethods) {
                printMethod(annotatedMethod, methodsArray);
            }
            JsonArray fieldsArray = new JsonArray();
            Field[] declaredFields = annotatedClass.getDeclaredFields();
            Arrays.sort(declaredFields, Comparator.comparing(Field::getName));
            for (Field annotatedField : declaredFields) {
                printField(annotatedField, fieldsArray);
            }

            if (methodsArray.size() > 0) {
                classObject.add("methods", methodsArray);
            }
            if (fieldsArray.size() > 0) {
                classObject.add("fields", fieldsArray);
            }
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        gson.toJson(classesArray, System.out);
    }

    private JsonObject printClass(Class<?> annotatedClass) {
        TargetClass targetClassAnnotation = lookupAnnotation(annotatedClass, TargetClass.class);
        Delete deleteAnnotation = lookupAnnotation(annotatedClass, Delete.class);
        Substitute substituteAnnotation = lookupAnnotation(annotatedClass, Substitute.class);
        JsonObject element = new JsonObject();
        element.addProperty("annotatedClass", annotatedClass.getName());
        Class<?> originalClass = findTargetClass(annotatedClass, targetClassAnnotation);
        if (originalClass != null) {
            element.addProperty("originalClass", originalClass.getName());
        } else {
            element.addProperty("disabled", "true");
        }

        if (deleteAnnotation != null) {
            element.addProperty("delete", true);
        }
        if (substituteAnnotation != null) {
            element.addProperty("substitute", true);
        }
        printPlatforms(annotatedClass, element);
        return element;
    }

    private void printMethod(Executable annotatedMethod, JsonArray methodsArray) {
        Delete deleteAnnotation = lookupAnnotation(annotatedMethod, Delete.class);
        Substitute substituteAnnotation = lookupAnnotation(annotatedMethod, Substitute.class);
        AnnotateOriginal annotateOriginalAnnotation = lookupAnnotation(annotatedMethod, AnnotateOriginal.class);
        KeepOriginal keepOriginalAnnotation = lookupAnnotation(annotatedMethod, KeepOriginal.class);
        Alias aliasAnnotation = lookupAnnotation(annotatedMethod, Alias.class);

        JsonObject methodObject = new JsonObject();
        String annotatedName;
        if (annotatedMethod instanceof Constructor) {
            annotatedName = TargetElement.CONSTRUCTOR_NAME;
        } else {
            annotatedName = annotatedMethod.getName();
        }
        methodObject.addProperty("annotatedName", annotatedName);

        Executable uniqueMethod = findMethod(annotatedMethod.getDeclaringClass(), annotatedName, false);
        if (uniqueMethod == null) {
            /* We did not find a unique method, so we need the parameter types to disambiguate. */
            JsonArray parameterTypesArray = new JsonArray();
            for (Class<?> parameterType : annotatedMethod.getParameterTypes()) {
                parameterTypesArray.add(new JsonPrimitive(parameterType.getName()));
            }
            methodObject.add("parameterTypes", parameterTypesArray);
        }

        printTargetElement(annotatedMethod, methodObject);

        if (deleteAnnotation != null) {
            methodObject.addProperty("delete", true);
        }
        if (substituteAnnotation != null) {
            methodObject.addProperty("substitute", true);
        }
        if (annotateOriginalAnnotation != null) {
            methodObject.addProperty("annotateOriginal", true);
        }
        if (keepOriginalAnnotation != null) {
            methodObject.addProperty("keepOriginal", true);
        }
        if (aliasAnnotation != null) {
            methodObject.addProperty("alias", true);
        }
        if (methodObject.entrySet().size() > 1) {
            printPlatforms(annotatedMethod, methodObject);
        }
        if (methodObject.entrySet().size() > 1) {
            methodsArray.add(methodObject);
        }
    }

    private void printField(Field annotatedField, JsonArray fieldsArray) {
        Delete deleteAnnotation = lookupAnnotation(annotatedField, Delete.class);
        Alias aliasAnnotation = lookupAnnotation(annotatedField, Alias.class);
        Inject injectAnnotation = lookupAnnotation(annotatedField, Inject.class);
        RecomputeFieldValue recomputeAnnotation = lookupAnnotation(annotatedField, RecomputeFieldValue.class);
        InjectAccessors injectAccessorsAnnotation = lookupAnnotation(annotatedField, InjectAccessors.class);

        JsonObject fieldObject = new JsonObject();
        fieldObject.addProperty("annotatedName", annotatedField.getName());
        printTargetElement(annotatedField, fieldObject);

        if (deleteAnnotation != null) {
            fieldObject.addProperty("delete", true);
        }
        if (aliasAnnotation != null) {
            fieldObject.addProperty("alias", true);
        }
        if (injectAnnotation != null) {
            fieldObject.addProperty("inject", true);
        }
        if (recomputeAnnotation != null) {
            fieldObject.addProperty("kind", recomputeAnnotation.kind().name());
            if (recomputeAnnotation.declClass() != RecomputeFieldValue.class) {
                fieldObject.addProperty("declClassName", recomputeAnnotation.declClass().getName());
            } else if (!recomputeAnnotation.declClassName().isEmpty()) {
                fieldObject.addProperty("declClassName", recomputeAnnotation.declClassName());
            }
            if (!recomputeAnnotation.name().isEmpty()) {
                fieldObject.addProperty("name", recomputeAnnotation.name());
            }
            if (recomputeAnnotation.isFinal()) {
                fieldObject.addProperty("isFinal", true);
            }
        }
        if (injectAccessorsAnnotation != null) {
            fieldObject.addProperty("injectAccessors", injectAccessorsAnnotation.value().getName());
        }
        if (fieldObject.entrySet().size() > 1) {
            printPlatforms(annotatedField, fieldObject);
        }

        if (fieldObject.entrySet().size() > 1) {
            fieldsArray.add(fieldObject);
        }
    }

    private void printPlatforms(AnnotatedElement annotatedElement, JsonObject object) {
        Platforms platformsAnnotation = lookupAnnotation(annotatedElement, Platforms.class);
        if (platformsAnnotation != null) {
            JsonArray platformsArray = new JsonArray();
            for (Class<?> platform : platformsAnnotation.value()) {
                platformsArray.add(new JsonPrimitive(platform.getName()));
            }
            object.add("platforms", platformsArray);
        }
    }

    private void printTargetElement(AnnotatedElement annotatedElement, JsonObject object) {
        TargetElement targetElementAnnotation = lookupAnnotation(annotatedElement, TargetElement.class);

        if (targetElementAnnotation != null && !targetElementAnnotation.name().isEmpty()) {
            object.addProperty("originalName", targetElementAnnotation.name());
        }
        if (targetElementAnnotation != null && targetElementAnnotation.optional()) {
            object.addProperty("optional", true);
        }
    }
}

/**
 * We provide our own implementation of annotations so that all the annotation-based code of
 * {@link AnnotationSubstitutionProcessor} works without changes. We do not need to implement the
 * full Annotation semantics for our fake Annotation objects, because we know how the annotations
 * are used and what methods are not called.
 */
class AnnotationImpl implements Annotation {

    @Override
    public Class<? extends Annotation> annotationType() {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public int hashCode() {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public boolean equals(Object obj) {
        throw VMError.shouldNotReachHere();
    }
}

@SuppressWarnings("all")
class DeleteImpl extends AnnotationImpl implements Delete {
    @Override
    public String value() {
        return "";
    }
}

@SuppressWarnings("all")
class SubstituteImpl extends AnnotationImpl implements Substitute {
}

@SuppressWarnings("all")
class AliasImpl extends AnnotationImpl implements Alias {
}

@SuppressWarnings("all")
class AnnotateOriginalImpl extends AnnotationImpl implements AnnotateOriginal {
}

@SuppressWarnings("all")
class KeepOriginalImpl extends AnnotationImpl implements KeepOriginal {
}

@SuppressWarnings("all")
class InjectImpl extends AnnotationImpl implements Inject {
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class PlatformsDescriptor {
    String[] platforms;

    @SuppressWarnings("all")
    @SuppressFBWarnings(value = "NP", justification = "Fields written by GSON using reflection")
    class PlatformsImpl extends AnnotationImpl implements Platforms {
        private final ImageClassLoader loader;

        PlatformsImpl(ImageClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public Class<? extends Platform>[] value() {
            Class<?>[] result = new Class<?>[platforms.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = loader.findClassByName(platforms[i]);
            }
            return cast(result);
        }
    }

    /*
     * A hack to overcome Java generic array limitations and make both javac and ecj happy.
     */
    @SuppressWarnings("unchecked")
    static <T> T cast(Object o) {
        return (T) o;
    }
}

@SuppressWarnings("unchecked")
@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class ClassDescriptor extends PlatformsDescriptor {
    String annotatedClass;
    String originalClass;

    boolean delete;
    boolean substitute;

    MethodDescriptor[] methods = new MethodDescriptor[0];
    FieldDescriptor[] fields = new FieldDescriptor[0];

    @SuppressWarnings("all")
    class TargetClassImpl extends AnnotationImpl implements TargetClass {
        @Override
        public Class<?> value() {
            return TargetClass.class;
        }

        @Override
        public String className() {
            return originalClass;
        }

        @Override
        public String innerClass() {
            return "";
        }

        /*
         * Javac requires @SuppressWarnings("unchecked") but then ecj compilation reports
         * "Unnecessary @SuppressWarnings("unchecked")". Using @SuppressWarnings("unchecked") on the
         * class to workaround this issue.
         */
        @Override
        public Class<? extends BooleanSupplier>[] onlyWith() {
            return (Class<? extends BooleanSupplier>[]) new Class<?>[]{DEFAULT_TARGETCLASS_PREDICATE};
        }
    }
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class ElementDescriptor extends PlatformsDescriptor {
    String annotatedName;
    String originalName = "";
    boolean optional;

    @SuppressWarnings("all")
    class TargetElementImpl extends AnnotationImpl implements TargetElement {
        @Override
        public String name() {
            return originalName;
        }

        @Override
        public boolean optional() {
            return optional;
        }
    }
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class MethodDescriptor extends ElementDescriptor {
    String[] parameterTypes;

    boolean delete;
    boolean substitute;
    boolean annotateOriginal;
    boolean keepOriginal;
    boolean alias;
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class FieldDescriptor extends ElementDescriptor {
    boolean delete;
    boolean alias;
    boolean inject;

    RecomputeFieldValue.Kind kind;
    String declClassName = "";
    String name = "";
    boolean isFinal;

    String injectAccessors;

    @SuppressWarnings("all")
    class RecomputeFieldValueImpl extends AnnotationImpl implements RecomputeFieldValue {
        @Override
        public Kind kind() {
            assert kind != null;
            return kind;
        }

        @Override
        public Class<?> declClass() {
            return RecomputeFieldValue.class;
        }

        @Override
        public String declClassName() {
            return declClassName;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isFinal() {
            return isFinal;
        }
    }

    @SuppressWarnings("all")
    class InjectAccessorsImpl extends AnnotationImpl implements InjectAccessors {
        private final ImageClassLoader loader;

        InjectAccessorsImpl(ImageClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public Class<?> value() {
            assert injectAccessors != null;
            return loader.findClassByName(injectAccessors);
        }
    }
}
