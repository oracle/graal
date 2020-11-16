/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

/**
 * Represents the host access policy of a polyglot context. The host access policy specifies which
 * methods and fields are accessible to the guest application, whenever Java host objects are
 * accessed.
 * <p>
 * There are three predefined instances of host access policies:
 * <ul>
 * <li>{@link #EXPLICIT} - Java host methods or fields, must be public and be annotated with
 * {@link Export @Export} to make them accessible to the guest language.
 * <li>{@link #NONE} - Does not allow any access to methods or fields of host objects. Java host
 * objects may still be passed into a context, but they cannot be accessed.
 * <li>{@link #ALL} - Does allow full unrestricted access to public methods or fields of host
 * objects. Note that this policy allows unrestricted access to reflection. It is highly discouraged
 * from using this policy in environments where the guest application is not fully trusted.
 * </ul>
 * Custom host access policies can be created using {@link #newBuilder()}. The builder allows to
 * specify a custom export annotation and allowed and denied methods or fields.
 *
 * @since 19.0
 */
public final class HostAccess {

    private final String name;
    private final EconomicSet<Class<? extends Annotation>> accessAnnotations;
    private final EconomicSet<Class<? extends Annotation>> implementableAnnotations;
    private final EconomicMap<Class<?>, Boolean> excludeTypes;
    private final EconomicSet<AnnotatedElement> members;
    private final EconomicSet<Class<?>> implementableTypes;
    private final List<Object> targetMappings;
    private final boolean allowPublic;
    private final boolean allowAllInterfaceImplementations;
    private final boolean allowAllClassImplementations;
    final boolean allowArrayAccess;
    final boolean allowListAccess;
    volatile Object impl;

    private static final HostAccess EMPTY = new HostAccess(null, null, null, null, null, null, null, false, false, false, false, false);

    /**
     * Predefined host access policy that allows access to public host methods or fields that were
     * annotated with {@linkplain Export @Export} and were declared in public class. This is the
     * default configuration if {@link Context.Builder#allowAllAccess(boolean)} is
     * <code>false</code>.
     * <p>
     * Equivalent of using the following builder configuration:
     *
     * <pre>
     * HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).//
     *                 allowImplementationsAnnotatedBy(HostAccess.Implementable.class).build();
     * </pre>
     *
     * @since 19.0
     */
    public static final HostAccess EXPLICIT = newBuilder().//
                    allowAccessAnnotatedBy(HostAccess.Export.class).//
                    allowImplementationsAnnotatedBy(HostAccess.Implementable.class).//
                    allowImplementationsAnnotatedBy(FunctionalInterface.class).//
                    name("HostAccess.EXPLICIT").build();

    /**
     * Predefined host access policy that allows full unrestricted access to public methods or
     * fields of public host classes. Note that this policy allows unrestricted access to
     * reflection. It is highly discouraged from using this policy in environments where the guest
     * application is not fully trusted. This is the default configuration if
     * {@link Context.Builder#allowAllAccess(boolean)} is <code>true</code>.
     * <p>
     * Equivalent of using the following builder configuration:
     *
     * <pre>
     * <code>
     * HostAccess.newBuilder()
     *           .allowPublicAccess(true)
     *           .allowAllImplementations(true)
     *           .allowAllClassImplementations(true)
     *           .allowArrayAccess(true)
     *           .allowListAccess(true)
     *           .build();
     * </code>
     * </pre>
     *
     * @since 19.0
     */
    public static final HostAccess ALL = newBuilder().//
                    allowPublicAccess(true).//
                    allowAllImplementations(true).//
                    allowAllClassImplementations(true).//
                    allowArrayAccess(true).allowListAccess(true).//
                    name("HostAccess.ALL").build();

    /**
     * Predefined host access policy that disallows any access to public host methods or fields.
     * <p>
     * Equivalent of using the following builder configuration:
     *
     * <pre>
     * HostAccess.newBuilder().build();
     * </pre>
     *
     * @since 19.0
     */
    public static final HostAccess NONE = newBuilder().name("HostAccess.NONE").build();

    HostAccess(EconomicSet<Class<? extends Annotation>> annotations, EconomicMap<Class<?>, Boolean> excludeTypes, EconomicSet<AnnotatedElement> members,
                    EconomicSet<Class<? extends Annotation>> implementableAnnotations,
                    EconomicSet<Class<?>> implementableTypes, List<Object> targetMappings,
                    String name,
                    boolean allowPublic, boolean allowAllImplementations, boolean allowAllClassImplementations, boolean allowArrayAccess, boolean allowListAccess) {
        // create defensive copies
        this.accessAnnotations = copySet(annotations, Equivalence.IDENTITY);
        this.excludeTypes = copyMap(excludeTypes, Equivalence.IDENTITY);
        this.members = copySet(members, Equivalence.DEFAULT);
        this.implementableAnnotations = copySet(implementableAnnotations, Equivalence.IDENTITY);
        this.implementableTypes = copySet(implementableTypes, Equivalence.IDENTITY);
        this.targetMappings = targetMappings != null ? new ArrayList<>(targetMappings) : null;
        this.name = name;
        this.allowPublic = allowPublic;
        this.allowAllInterfaceImplementations = allowAllImplementations;
        this.allowAllClassImplementations = allowAllClassImplementations;
        this.allowArrayAccess = allowArrayAccess;
        this.allowListAccess = allowListAccess;
    }

    /**
     * {@inheritDoc}
     *
     * @since 20.3
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HostAccess)) {
            return false;
        }
        HostAccess other = (HostAccess) obj;
        return allowPublic == other.allowPublic//
                        && allowAllInterfaceImplementations == other.allowAllInterfaceImplementations//
                        && allowAllClassImplementations == other.allowAllClassImplementations//
                        && allowArrayAccess == other.allowArrayAccess//
                        && allowListAccess == other.allowListAccess//
                        && equalsMap(excludeTypes, other.excludeTypes)//
                        && equalsSet(members, other.members)//
                        && equalsSet(implementableAnnotations, other.implementableAnnotations)//
                        && equalsSet(implementableTypes, other.implementableTypes)//
                        && Objects.equals(targetMappings, other.targetMappings)//
                        && equalsSet(accessAnnotations, other.accessAnnotations);
    }

    /**
     * {@inheritDoc}
     *
     * @since 20.3
     */
    @Override
    public int hashCode() {
        return Objects.hash(allowPublic,
                        allowAllInterfaceImplementations,
                        allowAllClassImplementations,
                        allowArrayAccess,
                        allowListAccess,
                        hashMap(excludeTypes),
                        hashSet(members),
                        hashSet(implementableAnnotations),
                        hashSet(implementableTypes),
                        hashSet(members),
                        targetMappings,
                        hashSet(accessAnnotations));
    }

    private static <T, V> int hashMap(EconomicMap<T, V> map) {
        int h = 0;
        if (map != null) {
            MapCursor<T, V> cursor = map.getEntries();
            while (cursor.advance()) {
                h += Objects.hashCode(cursor.getKey()) ^
                                Objects.hashCode(cursor.getValue());
            }
        }
        return h;
    }

    private static <V> int hashSet(EconomicSet<V> set) {
        int h = 0;
        if (set != null) {
            for (V v : set) {
                if (v != null) {
                    h += v.hashCode();
                }
            }
        }
        return h;
    }

    private static <T, V> boolean equalsMap(EconomicMap<T, V> map0, EconomicMap<T, V> map1) {
        if (Objects.equals(map0, map1)) {
            return true;
        } else if (map0 == null) {
            return false;
        } else if (map0.size() != map1.size()) {
            return false;
        }
        MapCursor<T, V> cursor = map0.getEntries();
        while (cursor.advance()) {
            if (!map1.containsKey(cursor.getKey())) {
                return false;
            }
            V v0 = cursor.getValue();
            V v1 = map1.get(cursor.getKey());
            if (!Objects.equals(v0, v1)) {
                return false;
            }
        }
        return true;
    }

    private static <T> boolean equalsSet(EconomicSet<T> set0, EconomicSet<T> set1) {
        if (Objects.equals(set0, set1)) {
            return true;
        } else if (set0 == null) {
            return false;
        } else if (set0.size() != set1.size()) {
            return false;
        }
        for (T v : set0) {
            if (!set1.contains(v)) {
                return false;
            }
        }
        return true;
    }

    private static <T> EconomicSet<T> copySet(EconomicSet<T> values, Equivalence equivalence) {
        if (values == null) {
            return null;
        }
        return EconomicSet.create(equivalence, values);
    }

    private static <K, T> EconomicMap<K, T> copyMap(EconomicMap<K, T> values, Equivalence equivalence) {
        if (values == null) {
            return null;
        }
        return EconomicMap.create(equivalence, values);
    }

    /**
     * Creates a new builder that allows to create a custom host access policy. The builder
     * configuration needs to be completed using the {@link Builder#build() method}.
     *
     * @since 19.0
     */
    public static Builder newBuilder() {
        return EMPTY.new Builder();
    }

    /**
     * Creates a new builder that allows to create a custom host access policy based of a preset
     * configuration. The preset configuration is copied and used as a template for the returned
     * buidler. The builder configuration needs to be completed using the {@link Builder#build()
     * method}.
     *
     * @since 19.0
     */
    public static Builder newBuilder(HostAccess conf) {
        Objects.requireNonNull(conf);
        return EMPTY.new Builder(conf);
    }

    List<Object> getTargetMappings() {
        return targetMappings;
    }

    boolean allowsImplementation(Class<?> type) {
        if (allowAllInterfaceImplementations && type.isInterface()) {
            return true;
        } else if (allowAllClassImplementations && !type.isInterface()) {
            return true;
        }
        if (implementableTypes != null && implementableTypes.contains(type)) {
            return true;
        }
        if (implementableAnnotations != null) {
            for (Class<? extends Annotation> ann : implementableAnnotations) {
                if (type.getAnnotation(ann) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean allowsAccess(AnnotatedElement member) {
        if (excludeTypes != null) {
            Class<?> owner = getDeclaringClass(member);
            MapCursor<Class<?>, Boolean> cursor = excludeTypes.getEntries();
            while (cursor.advance()) {
                Class<?> ban = cursor.getKey();
                if (cursor.getValue()) {
                    // include subclasses
                    if (ban.isAssignableFrom(owner)) {
                        return false;
                    }
                } else {
                    if (ban == owner) {
                        return false;
                    }
                }
            }
        }
        if (allowPublic) {
            return true;
        }
        if (members != null && members.contains(member)) {
            return true;
        }
        if (accessAnnotations != null) {
            for (Class<? extends Annotation> ann : accessAnnotations) {
                if (hasAnnotation(member, ann)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String toString() {
        return name == null ? super.toString() : name;
    }

    private static boolean hasAnnotation(AnnotatedElement member, Class<? extends Annotation> annotationType) {
        if (member instanceof Field) {
            Field f = (Field) member;
            return f.getAnnotation(annotationType) != null;
        }
        if (member instanceof Method) {
            Method m = (Method) member;
            return m.getAnnotation(annotationType) != null;
        }
        if (member instanceof Constructor) {
            Constructor<?> c = (Constructor<?>) member;
            return c.getAnnotation(annotationType) != null;
        }
        return false;
    }

    private static Class<?> getDeclaringClass(AnnotatedElement member) {
        if (member instanceof Field) {
            Field f = (Field) member;
            return f.getDeclaringClass();
        }
        if (member instanceof Method) {
            Method m = (Method) member;
            return m.getDeclaringClass();
        }
        if (member instanceof Constructor) {
            Constructor<?> c = (Constructor<?>) member;
            return c.getDeclaringClass();
        }
        return Object.class;
    }

    /**
     * Annotation used by the predefined {@link #EXPLICIT} access policy to mark public
     * constructors, methods and fields in public classes that should be accessible by the guest
     * application.
     * <p>
     * <b>Example</b> using a Java object from JavaScript:
     *
     * <pre>
     * public class JavaRecord {
     *     &#64;HostAccess.Export public int x;
     *
     *     &#64;HostAccess.Export
     *     public String name() {
     *         return "foo";
     *     }
     * }
     * try (Context context = Context.create()) {
     *     JavaRecord record = new JavaRecord();
     *     context.getBindings("js").putMember("javaRecord", record);
     *     context.eval("js", "javaRecord.x = 42");
     *     context.eval("js", "javaRecord.name()").asString().equals("foo");
     * }
     * </pre>
     *
     * @see Context.Builder#allowHostAccess(HostAccess)
     * @see HostAccess#EXPLICIT
     * @since 19.0
     */
    @Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Export {
    }

    /**
     * Allows guest language to implement a Java type. Implementable is required if the
     * {@link HostAccess#EXPLICIT explicit} host access policy is set. The annotation to use for
     * this purpose can be customized with {@link Builder#allowImplementationsAnnotatedBy(Class)}.
     * Allowing implementations for all Java interfaces can be enabled with
     * {@link Builder#allowAllImplementations(boolean)}.
     *
     * @see Context.Builder#allowHostAccess(HostAccess)
     * @see Value#as(Class)
     * @see HostAccess#EXPLICIT
     * @since 19.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface Implementable {
    }

    /**
     * Represents the precedence of a target type mapping. The precedence influences target type
     * mappings in two ways:
     * <ul>
     * <li>The conversion order in which target mappings are performed. {@link #HIGHEST Highest} and
     * {@link #HIGH high} precedences are invoked before all {@link Value#as(Class) default
     * mappings}. {@link #LOW Low} after all loss less conversions and {@link #LOWEST lowest} after
     * all other default mappings.
     * <li>To disambiguate multiple selected overloads on method invocation. The overload precedence
     * defines which method has precedence over other applicable methods. {@link #HIGHEST Highest}
     * have higher and {@link #HIGH high} have the same precedence as the default loss-less mapping.
     * The precedence {@link #LOW low} declares equal precedence than all lossy coercions and
     * {@link #LOWEST lowest} defines precedence lower than all default mappings.
     * </ul>
     *
     * @see Value#as(Class) for detailed information on conversion order.
     * @since 20.3
     */
    public enum TargetMappingPrecedence {

        /**
         * Defines higher precedence and conversion order as all default mappings and target type
         * mappings with lower precedence.
         *
         * @since 20.3
         */
        HIGHEST,

        /**
         * Defines high or default precedence and conversion order for a target type mapping. This
         * precedence makes mappings be used before all other default mappings and treated with
         * equal overload precedence as default loss less mappings like primitive coercions.
         *
         * @since 20.3
         */
        HIGH,

        /**
         * Defines low precedence and conversion order for a target type mapping. This precedence
         * makes mappings be used before all other default lossy mappings and treated with equal
         * overload precedence as default lossy mappings, like mappings to Map.
         *
         * @since 20.3
         */
        LOW,

        /**
         * Defines lowest precedence and conversion order for a target type mapping. This precedence
         * makes mappings be used after all other default mappings and treated with lower overload
         * precedence as all default mappings or other target type mappings.
         *
         * @since 20.3
         */
        LOWEST

    }

    /**
     * Builder to create a custom {@link HostAccess host access policy}.
     *
     * @since 19.0
     */
    public final class Builder {
        private EconomicSet<Class<? extends Annotation>> accessAnnotations;
        private EconomicSet<Class<? extends Annotation>> implementationAnnotations;
        private EconomicMap<Class<?>, Boolean> excludeTypes;
        private EconomicSet<Class<?>> implementableTypes;
        private EconomicSet<AnnotatedElement> members;
        private List<Object> targetMappings;
        private boolean allowPublic;
        private boolean allowListAccess;
        private boolean allowArrayAccess;
        private boolean allowAllImplementations;
        private boolean allowAllClassImplementations;
        private String name;

        Builder() {
        }

        Builder(HostAccess access) {
            this.accessAnnotations = copySet(access.accessAnnotations, Equivalence.IDENTITY);
            this.excludeTypes = copyMap(access.excludeTypes, Equivalence.IDENTITY);
            this.members = copySet(access.members, Equivalence.DEFAULT);
            this.implementationAnnotations = copySet(access.implementableAnnotations, Equivalence.IDENTITY);
            this.implementableTypes = copySet(access.implementableTypes, Equivalence.IDENTITY);
            this.targetMappings = access.targetMappings != null ? new ArrayList<>(access.targetMappings) : null;
            this.excludeTypes = access.excludeTypes;
            this.members = access.members;
            this.targetMappings = access.targetMappings;
            this.allowPublic = access.allowPublic;
            this.allowListAccess = access.allowListAccess;
            this.allowArrayAccess = access.allowArrayAccess;
            this.allowAllImplementations = access.allowAllInterfaceImplementations;
            this.allowAllClassImplementations = access.allowAllClassImplementations;
        }

        /**
         * Allows access to public constructors, methods or fields of public classes that were
         * annotated by the given annotation class.
         *
         * @since 19.0
         */
        public Builder allowAccessAnnotatedBy(Class<? extends Annotation> annotation) {
            Objects.requireNonNull(annotation);
            if (accessAnnotations == null) {
                accessAnnotations = EconomicSet.create(Equivalence.IDENTITY);
            }
            accessAnnotations.add(annotation);
            return this;
        }

        /**
         * Allows unrestricted access to all public constructors, methods or fields of public
         * classes. Note that this policy allows unrestricted access to reflection. It is highly
         * discouraged from using this option in environments where the guest application is not
         * fully trusted.
         *
         * @since 19.0
         */
        public Builder allowPublicAccess(boolean allow) {
            allowPublic = allow;
            return this;
        }

        /**
         * Allows access to a given constructor or method. Note that the method or constructor must
         * be public in order to have any effect.
         *
         * @since 19.0
         */
        public Builder allowAccess(Executable element) {
            Objects.requireNonNull(element);
            if (members == null) {
                members = EconomicSet.create();
            }
            members.add(element);
            return this;
        }

        /**
         * Allows access to a given field. Note that the field must be public in order to have any
         * effect.
         *
         * @since 19.0
         */
        public Builder allowAccess(Field element) {
            Objects.requireNonNull(element);
            if (members == null) {
                members = EconomicSet.create();
            }
            members.add(element);
            return this;
        }

        /**
         * Prevents access to members of given class and its subclasses.
         *
         * @param clazz the class to deny access to
         * @return this builder
         * @since 19.0
         */
        public Builder denyAccess(Class<?> clazz) {
            return denyAccess(clazz, true);
        }

        /**
         * Prevents access to members of given class.
         *
         * @param clazz the class to deny access to
         * @param includeSubclasses should subclasses be excuded as well?
         * @return this builder
         * @since 19.0
         */
        public Builder denyAccess(Class<?> clazz, boolean includeSubclasses) {
            Objects.requireNonNull(clazz);
            if (excludeTypes == null) {
                excludeTypes = EconomicMap.create(Equivalence.IDENTITY);
            }
            excludeTypes.put(clazz, includeSubclasses);
            return this;
        }

        /**
         * Allow guest languages to implement any Java interface.
         * <p>
         * Note that implementations implicitly export all their methods, i.e., allowing
         * implementations of a type implies allowing access its methods via its implementations,
         * regardless of whether the methods have been explicitly exported.
         *
         * @see HostAccess#ALL
         * @see #allowImplementations(Class)
         * @see #allowImplementationsAnnotatedBy(Class)
         * @since 19.0
         */
        public Builder allowAllImplementations(boolean allow) {
            this.allowAllImplementations = allow;
            return this;
        }

        /**
         * Allow guest languages to implement (extend) any Java class. Note that the default host
         * type mappings and {@link Value#as(Class)} only implement abstract classes.
         * <p>
         * Note that implementations implicitly export all their methods, i.e., allowing
         * implementations of a type implies allowing access its methods via its implementations,
         * regardless of whether the methods have been explicitly exported.
         *
         * @see HostAccess#ALL
         * @see #allowImplementations(Class)
         * @see #allowImplementationsAnnotatedBy(Class)
         * @see #allowAllImplementations(boolean)
         * @since 20.3.0
         */
        public Builder allowAllClassImplementations(boolean allow) {
            this.allowAllClassImplementations = allow;
            return this;
        }

        /**
         * Allow implementations of types annotated with the given annotation. For the
         * {@link HostAccess#EXPLICIT explicit} host access present the {@link Implementable}
         * annotation is configured for this purpose. Applies to interfaces and classes.
         * <p>
         * Note that implementations implicitly export all their methods, i.e., allowing
         * implementations of a type implies allowing access its methods via its implementations,
         * regardless of whether the methods have been explicitly exported.
         *
         * @see HostAccess.Implementable
         * @see #allowImplementations(Class)
         * @see Value#as(Class)
         * @since 19.0
         */
        public Builder allowImplementationsAnnotatedBy(Class<? extends Annotation> annotation) {
            Objects.requireNonNull(annotation);
            if (implementationAnnotations == null) {
                implementationAnnotations = EconomicSet.create(Equivalence.IDENTITY);
            }
            implementationAnnotations.add(annotation);
            return this;
        }

        /**
         * Allow implementations of this type by the guest language.
         * <p>
         * Note that implementations implicitly export all their methods, i.e., allowing
         * implementations of a type implies allowing access its methods via its implementations,
         * regardless of whether the methods have been explicitly exported.
         *
         * @param type an interface that may be implemented or a class that may be extended.
         * @see #allowImplementationsAnnotatedBy(Class)
         * @see Value#as(Class)
         * @since 19.0
         */
        public Builder allowImplementations(Class<?> type) {
            Objects.requireNonNull(type);
            if (implementableTypes == null) {
                implementableTypes = EconomicSet.create(Equivalence.IDENTITY);
            }
            implementableTypes.add(type);
            return this;
        }

        /**
         * Allows the guest application to access arrays as values with
         * {@link Value#hasArrayElements() array elements}. By default no array access is allowed.
         *
         * @see Value#hasArrayElements()
         * @since 19.0
         */
        public Builder allowArrayAccess(boolean arrayAccess) {
            this.allowArrayAccess = arrayAccess;
            return this;
        }

        /**
         * Allows the guest application to access lists as values with
         * {@link Value#hasArrayElements() array elements}. By default no array access is allowed.
         *
         * @see Value#hasArrayElements()
         * @since 19.0
         */
        public Builder allowListAccess(boolean listAccess) {
            this.allowListAccess = listAccess;
            return this;
        }

        /**
         * Adds a custom source to target type mapping for Java host calls, host field assignments
         * and {@link Value#as(Class) explicit value conversions}. Method is equivalent to calling
         * the targetTypeMapping method with precedence {@link TargetMappingPrecedence#HIGH}.
         *
         * @since 19.0
         */
        public <S, T> Builder targetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> accepts, Function<S, T> converter) {
            return targetTypeMapping(sourceType, targetType, accepts, converter, TargetMappingPrecedence.HIGH);
        }

        /**
         * Adds a custom source to target type mapping for Java host calls, host field assignments
         * and {@link Value#as(Class) explicit value conversions}. The source type specifies the
         * static source type for the conversion. The target type specifies the exact and static
         * target type of the mapping. Sub or base target types won't trigger the mapping. Custom
         * target type mappings always have precedence over default mappings specified in
         * {@link Value#as(Class)}, therefore allow to customize their behavior. The provided
         * converter takes a value of the source type and converts it to the target type. If the
         * mapping is only conditionally applicable then an accepts predicate may be specified. If
         * the mapping is applicable for all source values with the specified source type then a
         * <code>null</code> accepts predicate should be specified. The converter may throw a
         * {@link ClassCastException} if the mapping is not applicable. It is recommended to return
         * <code>false</code> in the accepts predicate if the mapping is not applicable instead of
         * throwing an exception. Implementing the accepts predicate instead of throwing an
         * exception also allows the implementation to perform better overload selection when a
         * method with multiple overloads is invoked.
         * <p>
         * All type mappings are applied recursively to generic types. A type mapping with the
         * target type <code>String.class</code> will also be applied to the elements of a
         * <code>List<String></code> mapping. This works for lists, maps, arrays and varargs
         * parameters.
         * <p>
         * The source type uses the semantics of {@link Value#as(Class)} to convert to the source
         * value. Custom type mappings are not applied there. If the source type is not applicable
         * to a value then the mapping will not be applied. For conversions that may accept any
         * value the {@link Value} should be used as source type.
         * <p>
         * Multiple mappings may be added for a source or target class. Multiple mappings are
         * applied in the order they were added, grouped by the {@link TargetMappingPrecedence
         * priority} where the highest priority group is applied first. See {@link Value#as(Class)}
         * for a detailed ordered list of the conversion order used. The first mapping that accepts
         * the source value will be used. If the {@link TargetMappingPrecedence#HIGH default
         * priority} is used then all custom target type mappings use the same precedence when an
         * overloaded method is selected. This means that if two methods with a custom target type
         * mapping are applicable for a set of arguments, an {@link IllegalArgumentException} is
         * thrown at runtime. Using a non-default priority for the mapping allows to configure
         * whether the method will be prioritized or deprioritized depending on the precedence.
         * <p>
         * For example take a configured target mapping from <code>String</code> to <code>int</code>
         * and two overloaded methods that takes an int or a {@link String} parameter. If this
         * method is invoked with a <code>String</code> value then there are three possible outcomes
         * depending on the precedence that was used for the custom mapping:
         * <ul>
         * <li>{@link TargetMappingPrecedence#HIGHEST}: The int method overload will be selected and
         * invoked as the target mapping has a higher precedence than default.
         * <li>{@link TargetMappingPrecedence#HIGH}: The execution fails with an error as all
         * overloads have equivalent precedence.
         * <li>{@link TargetMappingPrecedence#LOW} or {@link TargetMappingPrecedence#LOWEST}: The
         * String method overload will be selected and invoked as the target mapping has a lower
         * precedence than default.
         * <li>In this example the outcome of low and lowest are equivalent. There are differences
         * between low and lowest. See {@link TargetMappingPrecedence} for details.
         * </ul>
         * <p>
         * Primitive boxed target types will be applied to the primitive and boxed values. It is
         * therefore enough to specify a target mapping to {@link Integer} to also map to the target
         * type <code>int.class</code>. Primitive target types can not be used as target types. They
         * throw an {@link IllegalArgumentException} if used.
         * <p>
         * If the converter function or the accepts predicate calls {@link Value#as(Class)}
         * recursively then custom target mappings are applied. Special care must be taken in order
         * to not trigger stack overflow errors. It is recommended to use a restricted source type
         * instead of {@link Value#as(Class)} where possible. It is strongly discouraged that accept
         * predicates or converter cause any side-effects or escape values for permanent storage.
         * <p>
         *
         * Usage example:
         *
         * <pre>
         * public static class MyClass {
         *
         *     &#64;HostAccess.Export
         *     public void json(JsonObject c) {
         *     }
         *
         *     &#64;HostAccess.Export
         *     public String intToString(String c) {
         *         return c;
         *     }
         * }
         *
         * public static class JsonObject {
         *     JsonObject(Value v) {
         *     }
         * }
         *
         * public static void main(String[] args) {
         *     HostAccess.Builder builder = HostAccess.newBuilder();
         *     builder.allowAccessAnnotatedBy(HostAccess.Export.class);
         *     builder.targetTypeMapping(Value.class, JsonObject.class,
         *                     (v) -> v.hasMembers() || v.hasArrayElements(),
         *                     (v) -> new JsonObject(v)).build();
         *
         *     builder.targetTypeMapping(Integer.class, String.class, null,
         *                     (v) -> v.toString());
         *
         *     HostAccess access = builder.build();
         *     try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
         *         c.getBindings("js").putMember("javaObject", new MyClass());
         *         c.eval("js", "javaObject.json({})"); // works!
         *         c.eval("js", "javaObject.json([])"); // works!
         *         try {
         *             c.eval("js", "javaObject.json(42)"); // fails!
         *         } catch (PolyglotException e) {
         *         }
         *
         *         c.eval("js", "javaObject.intToString(42)"); // returns "42"
         *     }
         * }
         * </pre>
         *
         * @param sourceType the static source type to convert from with this mapping. The source
         *            type must be applicable for a mapping to be accepted.
         * @param targetType the exact and static target type to convert to with this mapping.
         * @param accepts the predicate to check whether a mapping is applicable. Returns
         *            <code>true</code> if the mapping is applicable else false. If set to
         *            <code>null</code> then all values of a given source type are applicable.
         * @param converter a function that produces the converted value of the mapping. May return
         *            <code>null</code>. May throw {@link ClassCastException} if the source value is
         *            not convertible.
         * @param precedence the precedence of the defined mapping which influences conversion order
         *            and precedence with default mappings and other target type mappings.
         * @throws IllegalArgumentException for primitive target types.
         * @since 20.3
         */
        public <S, T> Builder targetTypeMapping(Class<S> sourceType, Class<T> targetType,
                        Predicate<S> accepts, Function<S, T> converter, TargetMappingPrecedence precedence) {
            Objects.requireNonNull(sourceType);
            Objects.requireNonNull(targetType);
            Objects.requireNonNull(converter);
            Objects.requireNonNull(precedence);
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("Primitive target type is not supported as target mapping. Use boxed primitives instead.");
            }
            if (targetMappings == null) {
                targetMappings = new ArrayList<>();
            }
            targetMappings.add(Engine.getImpl().newTargetTypeMapping(sourceType, targetType, accepts, converter, precedence));
            return this;
        }

        HostAccess.Builder name(String givenName) {
            this.name = givenName;
            return this;
        }

        /**
         * Creates an instance of the custom host access configuration.
         *
         * @since 19.0
         */
        public HostAccess build() {
            return new HostAccess(accessAnnotations, excludeTypes, members, implementationAnnotations, implementableTypes, targetMappings, name, allowPublic,
                            allowAllImplementations, allowAllClassImplementations, allowArrayAccess, allowListAccess);
        }
    }

}
