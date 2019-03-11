/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Configuration of host access. There are two predefined instances of host access {@link #EXPLICIT}
 * and {@link #PUBLIC} which one can use when building a context
 * {@link Context.Builder#allowHostAccess(org.graalvm.polyglot.HostAccess)}. Should the predefined
 * instances not be enough, one can create own configuration with {@link #newBuilder()}.
 *
 * @since 1.0 RC14
 */
public final class HostAccess {
    private static final BiFunction<HostAccess, AnnotatedElement, Boolean> ACCESS = new BiFunction<HostAccess, AnnotatedElement, Boolean>() {
        @Override
        public Boolean apply(HostAccess t, AnnotatedElement u) {
            return t.allowAccess(u);
        }
    };

    private final String name;
    private final Set<Class<? extends Annotation>> annotations;
    private final Set<Member> members;
    private Object impl;

    /**
     * Configuration via {@link Export}. Default configuration if
     * {@link Context.Builder#allowAllAccess(boolean)} is false.
     * 
     * @since 1.0 RC14
     */
    public static final HostAccess EXPLICIT = new HostAccess(null, null, "HostAccess.EXPLICIT");

    /**
     * All public access, but no reflection access.
     * 
     * @since 1.0 RC14
     */
    public static final HostAccess PUBLIC = new HostAccess(null, null, "HostAccess.PUBLIC");

    HostAccess(Set<Class<? extends Annotation>> annotations, Set<Member> members, String name) {
        this.annotations = annotations;
        this.members = members;
        this.name = name;
    }

    /**
     * Configure your own access configuration.
     *
     * @return new builder
     * @since 1.0 RC14
     */
    public static Builder newBuilder() {
        return EXPLICIT.new Builder();
    }

    boolean allowAccess(AnnotatedElement member) {
        if (this == HostAccess.EXPLICIT) {
            return member.getAnnotation(HostAccess.Export.class) != null;
        }
        if (this == HostAccess.PUBLIC) {
            return true;
        }
        if (members.contains(member)) {
            return true;
        }
        for (Class<? extends Annotation> ann : annotations) {
            if (member.getAnnotation(ann) != null) {
                return true;
            }
        }
        return false;
    }

    synchronized <T> T connectHostAccess(Class<T> type, Function<BiFunction<HostAccess, AnnotatedElement, Boolean>, T> factory) {
        if (impl == null) {
            impl = factory.apply(ACCESS);
        }
        return type.cast(impl);
    }

    /**
     * Textual indentification of the instance.
     *
     * @return new builder
     * @since 1.0 RC14
     */
    @Override
    public String toString() {
        return name == null ? super.toString() : name;
    }

    /**
     * Annotation to export public methods or fields. When {@link #EXPLICIT} access is activated via
     * {@link Context.Builder#allowHostAccess(org.graalvm.polyglot.HostAccess)} only methods and
     * fields annotated by this annotation are available from scripts.
     * 
     * @since 1.0 RC14
     */
    @Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Export {
    }

    /**
     * Builder to create own {@link HostAccess}.
     * 
     * @since 1.0 RC14
     */
    public final class Builder {
        private final Set<Class<? extends Annotation>> annotations = new HashSet<>();
        private final Set<Member> members = new HashSet<>();

        Builder() {
        }

        /**
         * Elements annotated by this annotation can be accessed.
         *
         * @param annotation the annotation class
         * @return this builder
         * @since 1.0 RC14
         */
        public Builder allowAccessAnnotatedBy(Class<? extends Annotation> annotation) {
            annotations.add(annotation);
            return this;
        }

        /**
         * Add an element to the access list.
         *
         * @param element method or constructor
         * @return this builder
         * @since 1.0 RC14
         */
        public Builder allowAccess(Executable element) {
            members.add(element);
            return this;
        }

        /**
         * Add an element to the access list.
         *
         * @param element field that can be accessed
         * @return this builder
         * @since 1.0 RC14
         */
        public Builder allowAccess(Field element) {
            members.add(element);
            return this;
        }

        /**
         * Creates an instance of host access configuration.
         *
         * @return new instance of host access configuration
         * @since 1.0 RC14
         */
        public HostAccess build() {
            return new HostAccess(annotations, members, null);
        }
    }
}
