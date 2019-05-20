/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import static com.oracle.svm.core.util.UserError.guarantee;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

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
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This class allows to provide substitutions (see {@link TargetClass}) using a JSON file instead of
 * annotations. Projects that do not depend on Substrate VM can provide such a JSON file.
 */
public class DeclarativeSubstitutionProcessor extends AnnotationSubstitutionProcessor {

    public static class Options {

        @Option(help = "Comma-separated list of resource file names with declarative substitutions", type = OptionType.User)//
        public static final HostedOptionKey<String[]> SubstitutionResources = new HostedOptionKey<>(null);
    }

    private final Map<Class<?>, ClassDescriptor> classDescriptors;
    private final Map<Executable, MethodDescriptor> methodDescriptors;
    private final Map<Field, FieldDescriptor> fieldDescriptors;

    public DeclarativeSubstitutionProcessor(ImageClassLoader imageClassLoader, MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport) {
        super(imageClassLoader, metaAccess, classInitializationSupport);

        classDescriptors = new HashMap<>();
        methodDescriptors = new HashMap<>();
        fieldDescriptors = new HashMap<>();

        for (String substitutionFileName : OptionUtils.flatten(",", ConfigurationFiles.Options.SubstitutionFiles.getValue())) {
            try {
                loadFile(new FileReader(substitutionFileName));
            } catch (FileNotFoundException ex) {
                throw UserError.abort("Substitution file " + substitutionFileName + " not found.");
            } catch (IOException | JSONParserException ex) {
                throw UserError.abort("Could not parse substitution file " + substitutionFileName + ": " + ex.getMessage());
            }
        }
        for (String substitutionResourceName : OptionUtils.flatten(",", Options.SubstitutionResources.getValue())) {
            try {
                InputStream substitutionStream = imageClassLoader.findResourceAsStreamByName(substitutionResourceName);
                if (substitutionStream == null) {
                    throw UserError.abort("Substitution resource not found: " + substitutionResourceName);
                }
                loadFile(new InputStreamReader(substitutionStream));
            } catch (IOException | JSONParserException ex) {
                throw UserError.abort("Could not parse substitution resource " + substitutionResourceName + ": " + ex.getMessage());
            }
        }
    }

    private void loadFile(Reader reader) throws IOException {
        Set<Class<?>> annotatedClasses = new HashSet<>(imageClassLoader.findAnnotatedClasses(TargetClass.class, false));

        JSONParser parser = new JSONParser(reader);
        @SuppressWarnings("unchecked")
        List<Object> allDescriptors = (List<Object>) parser.parse();

        for (Object classDescriptorData : allDescriptors) {
            if (classDescriptorData == null) {
                /* Empty or trailing array elements are parsed to null. */
                continue;
            }
            ClassDescriptor classDescriptor = new ClassDescriptor(classDescriptorData);

            Class<?> annotatedClass = imageClassLoader.findClassByName(classDescriptor.annotatedClass());

            if (annotatedClasses.contains(annotatedClass)) {
                throw UserError.abort("target class already registered using explicit @TargetClass annotation: " + annotatedClass);
            } else if (classDescriptors.containsKey(annotatedClass)) {
                throw UserError.abort("target class already registered using substitution file: " + annotatedClass);
            }
            classDescriptors.put(annotatedClass, classDescriptor);

            for (Object methodDescriptorData : classDescriptor.methods()) {
                if (methodDescriptorData == null) {
                    /* Empty or trailing array elements are parsed to null. */
                    continue;
                }
                MethodDescriptor methodDescriptor = new MethodDescriptor(methodDescriptorData);
                Executable annotatedMethod;
                if (methodDescriptor.parameterTypes() != null) {
                    annotatedMethod = findMethod(annotatedClass, methodDescriptor.annotatedName(), methodDescriptor.parameterTypes());
                } else {
                    annotatedMethod = findMethod(annotatedClass, methodDescriptor.annotatedName(), true);
                }
                methodDescriptors.put(annotatedMethod, methodDescriptor);
            }
            for (Object fieldDescriptorData : classDescriptor.fields()) {
                FieldDescriptor fieldDescriptor = new FieldDescriptor(fieldDescriptorData);
                Field annotatedField = findField(annotatedClass, fieldDescriptor.annotatedName());
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
                result = classDescriptor.platforms() != null ? classDescriptor.new PlatformsImpl(imageClassLoader) : null;
            } else if (annotationClass == TargetClass.class) {
                result = classDescriptor.new TargetClassImpl();
            } else if (annotationClass == Delete.class) {
                result = classDescriptor.delete() ? new DeleteImpl() : null;
            } else if (annotationClass == Substitute.class) {
                result = classDescriptor.substitute() ? new SubstituteImpl() : null;
            } else {
                throw VMError.shouldNotReachHere("Unexpected annotation type: " + annotationClass.getName());
            }

        } else if (element instanceof Executable && methodDescriptors.containsKey(element)) {
            MethodDescriptor methodDescriptor = methodDescriptors.get(element);
            if (annotationClass == Platforms.class) {
                result = methodDescriptor.platforms() != null ? methodDescriptor.new PlatformsImpl(imageClassLoader) : null;
            } else if (annotationClass == TargetElement.class) {
                result = methodDescriptor.new TargetElementImpl();
            } else if (annotationClass == Delete.class) {
                result = methodDescriptor.delete() ? new DeleteImpl() : null;
            } else if (annotationClass == Substitute.class) {
                result = methodDescriptor.substitute() ? new SubstituteImpl() : null;
            } else if (annotationClass == AnnotateOriginal.class) {
                result = methodDescriptor.annotateOriginal() ? new AnnotateOriginalImpl() : null;
            } else if (annotationClass == KeepOriginal.class) {
                result = methodDescriptor.keepOriginal() ? new KeepOriginalImpl() : null;
            } else if (annotationClass == Alias.class) {
                result = methodDescriptor.alias() ? new AliasImpl() : null;
            } else {
                throw VMError.shouldNotReachHere("Unexpected annotation type: " + annotationClass.getName());
            }

        } else if (element instanceof Field && fieldDescriptors.containsKey(element)) {
            FieldDescriptor fieldDescriptor = fieldDescriptors.get(element);
            if (annotationClass == Platforms.class) {
                result = fieldDescriptor.platforms() != null ? fieldDescriptor.new PlatformsImpl(imageClassLoader) : null;
            } else if (annotationClass == TargetElement.class) {
                result = fieldDescriptor.new TargetElementImpl();
            } else if (annotationClass == Delete.class) {
                result = fieldDescriptor.delete() ? new DeleteImpl() : null;
            } else if (annotationClass == Alias.class) {
                result = fieldDescriptor.alias() ? new AliasImpl() : null;
            } else if (annotationClass == Inject.class) {
                result = fieldDescriptor.inject() ? new InjectImpl() : null;
            } else if (annotationClass == RecomputeFieldValue.class) {
                result = fieldDescriptor.kind() != null ? fieldDescriptor.new RecomputeFieldValueImpl() : null;
            } else if (annotationClass == InjectAccessors.class) {
                result = fieldDescriptor.injectAccessors() != null ? fieldDescriptor.new InjectAccessorsImpl(imageClassLoader) : null;
            } else {
                throw VMError.shouldNotReachHere("Unexpected annotation type: " + annotationClass.getName());
            }

        } else {
            result = super.lookupAnnotation(element, annotationClass);
        }
        return annotationClass.cast(result);
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

class DataObject {
    final Map<String, Object> data;

    @SuppressWarnings("unchecked")
    DataObject(Object data) {
        this.data = (Map<String, Object>) data;
    }

    @SuppressWarnings("unchecked")
    <T> T get(String propertyName, T defaultValue) {
        if (data.containsKey(propertyName)) {
            return (T) data.get(propertyName);
        } else {
            return defaultValue;
        }
    }
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class PlatformsDescriptor extends DataObject {

    PlatformsDescriptor(Object data) {
        super(data);
    }

    List<String> platforms() {
        return get("platforms", null);
    }

    @SuppressWarnings("all")
    @SuppressFBWarnings(value = "NP", justification = "Fields written by GSON using reflection")
    class PlatformsImpl extends AnnotationImpl implements Platforms {
        private final ImageClassLoader loader;

        PlatformsImpl(ImageClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public Class<? extends Platform>[] value() {
            List<String> platforms = platforms();
            Class<?>[] result = new Class<?>[platforms.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = loader.findClassByName(platforms.get(i));
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

    ClassDescriptor(Object data) {
        super(data);
    }

    String annotatedClass() {
        return get("annotatedClass", null);
    }

    boolean delete() {
        return get("delete", false);
    }

    boolean substitute() {
        return get("substitute", false);
    }

    List<Object> methods() {
        return get("methods", Collections.emptyList());
    }

    List<Object> fields() {
        return get("fields", Collections.emptyList());
    }

    @SuppressWarnings("all")
    class TargetClassImpl extends AnnotationImpl implements TargetClass {
        @Override
        public Class<?> value() {
            return TargetClass.class;
        }

        @Override
        public String className() {
            return get("originalClass", null);
        }

        @Override
        public Class<? extends Function<TargetClass, String>> classNameProvider() {
            return TargetClass.NoClassNameProvider.class;
        }

        @Override
        public String[] innerClass() {
            return new String[0];
        }

        /*
         * Javac requires @SuppressWarnings("unchecked") but then ecj compilation reports
         * "Unnecessary @SuppressWarnings("unchecked")". Using @SuppressWarnings("unchecked") on the
         * class to workaround this issue.
         */
        @Override
        public Class<? extends BooleanSupplier>[] onlyWith() {
            return (Class<? extends BooleanSupplier>[]) new Class<?>[]{TargetClass.AlwaysIncluded.class};
        }
    }
}

@SuppressWarnings("unchecked")
@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class ElementDescriptor extends PlatformsDescriptor {

    ElementDescriptor(Object data) {
        super(data);
    }

    String annotatedName() {
        return get("annotatedName", null);
    }

    @SuppressWarnings("all")
    class TargetElementImpl extends AnnotationImpl implements TargetElement {
        @Override
        public String name() {
            return get("originalName", "");
        }

        @Override
        public Class<? extends Predicate<Class<?>>>[] onlyWith() {
            return (Class<? extends Predicate<Class<?>>>[]) new Class<?>[]{TargetClass.AlwaysIncluded.class};
        }
    }
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class MethodDescriptor extends ElementDescriptor {

    MethodDescriptor(Object data) {
        super(data);
    }

    String[] parameterTypes() {
        return get("parameterTypes", null);
    }

    boolean delete() {
        return get("delete", false);
    }

    boolean substitute() {
        return get("substitute", false);
    }

    boolean annotateOriginal() {
        return get("annotateOriginal", false);
    }

    boolean keepOriginal() {
        return get("keepOriginal", false);
    }

    boolean alias() {
        return get("alias", false);
    }
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class FieldDescriptor extends ElementDescriptor {

    FieldDescriptor(Object data) {
        super(data);
    }

    boolean delete() {
        return get("delete", false);
    }

    boolean alias() {
        return get("alias", false);
    }

    boolean inject() {
        return get("inject", false);
    }

    RecomputeFieldValue.Kind kind() {
        return RecomputeFieldValue.Kind.valueOf(get("kind", null));
    }

    String injectAccessors() {
        return get("injectAccessors", null);
    }

    @SuppressWarnings("all")
    class RecomputeFieldValueImpl extends AnnotationImpl implements RecomputeFieldValue {
        @Override
        public Kind kind() {
            Kind result = get("kind", null);
            assert result != null;
            return result;
        }

        @Override
        public Class<?> declClass() {
            return RecomputeFieldValue.class;
        }

        @Override
        public String declClassName() {
            return get("declClassName", "");
        }

        @Override
        public String name() {
            return get("name", "");
        }

        @Override
        public boolean isFinal() {
            return get("isFinal", false);
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
            assert injectAccessors() != null;
            return loader.findClassByName(injectAccessors());
        }
    }
}
