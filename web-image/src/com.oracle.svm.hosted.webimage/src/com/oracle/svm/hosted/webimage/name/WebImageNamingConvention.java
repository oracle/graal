/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.name;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.webimage.JSNameGenerator;
import com.oracle.svm.webimage.NamingConvention;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class WebImageNamingConvention implements NamingConvention {

    public enum NamingMode {
        /**
         * Minifies each name as far as possible.
         */
        MINIMAL,
        /**
         * Try to use the plain type name where possible.
         *
         * If there is a naming conflict, the canonical name will be used
         */
        REDUCED,
        /**
         * Uses full names as exposed by the JVM class file format (e.g. for methods the method
         * descriptor name).
         */
        FULL
    }

    private final NamingMode mode;
    private final NamingConvention instance;

    public static WebImageNamingConvention singletonInstance;

    public static void initialize() {
        GraalError.guarantee(singletonInstance == null, "A naming convention instance has already been created");
        singletonInstance = new WebImageNamingConvention(WebImageOptions.NamingConvention.getValue(HostedOptionValues.singleton()));
    }

    public static WebImageNamingConvention getInstance() {
        GraalError.guarantee(singletonInstance != null, "A naming convention instance has not been created yet");
        return singletonInstance;
    }

    public static void clear() {
        singletonInstance = null;
    }

    private WebImageNamingConvention(NamingMode mode) {
        this.mode = mode;
        switch (mode) {
            case MINIMAL:
                instance = new MinifiedNamingConvention();
                break;
            case REDUCED:
                instance = new ReducedNamingConvention();
                break;
            case FULL:
                instance = new FullNamingConvention();
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public NamingMode mode() {
        return mode;
    }

    @Override
    public String identForType(ResolvedJavaType t) {
        return instance.identForType(t);
    }

    @Override
    public String identForMethod(ResolvedJavaMethod m) {
        return instance.identForMethod(m);
    }

    @Override
    public String identForProperty(ResolvedJavaField field) {
        return instance.identForProperty(field);
    }

    @Override
    public String identForArtificialProperty(String name, ResolvedJavaType type) {
        return instance.identForArtificialProperty(name, type);
    }

    static class FullNamingConvention implements NamingConvention {

        @Override
        public String identForType(ResolvedJavaType t) {
            return NamingConventionUtil.formatClassName(t);
        }

        @Override
        public String identForMethod(ResolvedJavaMethod m) {
            return NamingConventionUtil.formatVMFunctionName(m, this);
        }

        @Override
        public String identForArtificialProperty(String name, ResolvedJavaType type) {
            return FieldPrefix + name + "_" + identForType(type);
        }

    }

    /**
     * Tries to reduce names as much as possible while keeping them human readable.
     *
     * Will use the unqualified name if possible. If multiple types have the same unqualified name,
     * whichever type is encountered first will get the reduced name and all subsequent types will
     * use their full name.
     */
    static class ReducedNamingConvention implements NamingConvention {

        /**
         * Maps reduced type names to their full names.
         *
         * If a type cannot be reduced (because of a conflict), it won't appear in this map and will
         * always use the full name.
         */
        private final ConcurrentMap<String, String> typeIdent = new ConcurrentHashMap<>();

        @Override
        public String identForType(ResolvedJavaType t) {
            String name = t.getName();
            /*
             * We prefix the reduced name with an underscore so that it can't conflict with one of
             * JavaScript's built-in objects.
             */
            String reducedName = "_" + NamingConventionUtil.cleanTypeName(t.getUnqualifiedName());

            String oldName = typeIdent.putIfAbsent(reducedName, name);

            /*
             * Use the reduced name for this type if the reduced name was not seen before or if the
             * reduced name was already registered for the same type.
             */
            if (oldName == null || oldName.equals(name)) {
                return reducedName;
            } else {
                /*
                 * Otherwise, the reduced name is already used for another type and we use the full
                 * name, which is always unique.
                 */
                return NamingConventionUtil.formatClassName(t);
            }
        }

        @Override
        public String identForMethod(ResolvedJavaMethod m) {
            return NamingConventionUtil.formatVMFunctionName(m, this);
        }

        @Override
        public String identForArtificialProperty(String name, ResolvedJavaType type) {
            return FieldPrefix + name + "_" + identForType(type);
        }

        @Override
        public String identForStaticProperty(String name, ResolvedJavaType type) {
            return FieldPrefix + name + "_";
        }
    }

    public static final String FieldPrefix = "";

    /**
     * Tries to minify names as much as possible.
     *
     * For that is uses {@link JSNameGenerator} to generate unique names and the names generated by
     * {@link FullNamingConvention} as names in the cache.
     */
    static class MinifiedNamingConvention extends FullNamingConvention {
        /**
         * We prefix '$' so that the resulting name doesn't overwrite some built-in field (e.g.
         * `toString()`).
         */
        private final JSNameGenerator.NameCache<String> nameCache = new JSNameGenerator.NameCache<>("$");

        private String queryCache(String identifier) {
            return nameCache.get(identifier);
        }

        @Override
        public String identForType(ResolvedJavaType t) {
            return queryCache(super.identForType(t));
        }

        @Override
        public String identForMethod(ResolvedJavaMethod m) {
            return queryCache(super.identForMethod(m));
        }

        @Override
        public String identForArtificialProperty(String name, ResolvedJavaType type) {
            return queryCache(super.identForArtificialProperty(name, type));
        }
    }

}
