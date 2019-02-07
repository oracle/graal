/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Feature;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.MethodPointer;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticFeature
public class ClassInitializationFeature implements Feature {

    private ClassInitializationSupport classInitializationSupport;

    public static class Options {
        @APIOption(name = "delay-class-initialization-to-runtime")//
        @Option(help = "A comma-separated list of classes (and implicitly all of their subclasses) that are initialized at runtime and not during image building", type = OptionType.User)//
        public static final HostedOptionKey<String[]> DelayClassInitialization = new HostedOptionKey<>(null);

        @APIOption(name = "rerun-class-initialization-at-runtime") //
        @Option(help = "A comma-separated list of classes (and implicitly all of their subclasses) that are initialized both at runtime and during image building", type = OptionType.User)//
        public static final HostedOptionKey<String[]> RerunClassInitialization = new HostedOptionKey<>(null);
    }

    public static void processClassInitializationOptions(FeatureImpl.AfterRegistrationAccessImpl access, ClassInitializationSupport initializationSupport) {
        processOption(access, ClassInitializationFeature.Options.DelayClassInitialization, initializationSupport::delayClassInitialization);
        processOption(access, ClassInitializationFeature.Options.RerunClassInitialization, initializationSupport::rerunClassInitialization);
    }

    private static void processOption(FeatureImpl.AfterRegistrationAccessImpl access, HostedOptionKey<String[]> option, Consumer<Class<?>[]> handler) {
        for (String className : OptionUtils.flatten(",", option.getValue())) {
            if (className.length() > 0) {
                Class<?> clazz = access.findClassByName(className);
                if (clazz == null) {
                    throw UserError.abort("Could not find class " + className +
                                    " that is provided by the option " + SubstrateOptionsParser.commandArgument(option, className));
                }
                handler.accept(new Class<?>[]{clazz});
            }
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        classInitializationSupport = access.getHostVM().getClassInitializationSupport();
        ((ClassInitializationSupportImpl) classInitializationSupport).setUnsupportedFeatures(access.getBigBang().getUnsupportedFeatures());
        access.registerObjectReplacer(this::checkImageHeapInstance);
    }

    private Object checkImageHeapInstance(Object obj) {
        /*
         * Note that computeInitKind also memoizes the class as InitKind.EAGER, which means that the
         * user cannot later manually register it as RERUN or DELAY.
         */
        if (obj != null && classInitializationSupport.shouldInitializeAtRuntime(obj.getClass())) {
            throw new UnsupportedFeatureException("No instances are allowed in the image heap for a class that is initialized or reinitialized at image runtime: " + obj.getClass().getTypeName());
        }
        return obj;
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        /*
         * Check early and often during static analysis if any class that must not have been
         * initialized during image building got initialized. We want to fail as early as possible,
         * even though we cannot pinpoint the exact time and reason why initialization happened.
         */
        classInitializationSupport.checkDelayedInitialization();

        for (AnalysisType type : access.getUniverse().getTypes()) {
            if (type.isInTypeCheck() || type.isInstantiated()) {
                DynamicHub hub = access.getHostVM().dynamicHub(type);
                if (hub.getClassInitializationInfo() == null) {
                    buildClassInitializationInfo(access, type, hub);
                    access.requireAnalysisIteration();
                }
            }
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        ((ClassInitializationSupportImpl) classInitializationSupport).setUnsupportedFeatures(null);
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess a) {
        /*
         * This is the final time to check if any class that must not have been initialized during
         * image building got initialized.
         */
        classInitializationSupport.checkDelayedInitialization();
    }

    private void buildClassInitializationInfo(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisType type, DynamicHub hub) {
        ClassInitializationInfo info;
        if (classInitializationSupport.shouldInitializeAtRuntime(type)) {
            AnalysisMethod classInitializer = type.getClassInitializer();
            /*
             * If classInitializer.getCode() returns null then the type failed to initialize due to
             * verification issues triggered by missing types.
             */
            if (classInitializer != null && classInitializer.getCode() != null) {
                access.registerAsCompiled(classInitializer);
            }
            info = new ClassInitializationInfo(MethodPointer.factory(classInitializer));

        } else {
            info = ClassInitializationInfo.INITIALIZED_INFO_SINGLETON;
        }

        hub.setClassInitializationInfo(info, hasDefaultMethods(type), declaresDefaultMethods(type));
    }

    private static boolean hasDefaultMethods(ResolvedJavaType type) {
        if (!type.isInterface() && type.getSuperclass() != null && hasDefaultMethods(type.getSuperclass())) {
            return true;
        }
        for (ResolvedJavaType iface : type.getInterfaces()) {
            if (hasDefaultMethods(iface)) {
                return true;
            }
        }
        return declaresDefaultMethods(type);
    }

    static boolean declaresDefaultMethods(ResolvedJavaType type) {
        if (!type.isInterface()) {
            /* Only interfaces can declare default methods. */
            return false;
        }
        /*
         * We call getDeclaredMethods() directly on the wrapped type. We avoid calling it on the
         * AnalysisType because it resolves all the methods in the AnalysisUniverse.
         */
        for (ResolvedJavaMethod method : toWrappedType(type).getDeclaredMethods()) {
            if (method.isDefault()) {
                assert !Modifier.isStatic(method.getModifiers()) : "Default method that is static?";
                return true;
            }
        }
        return false;
    }

    private static ResolvedJavaType toWrappedType(ResolvedJavaType type) {
        if (type instanceof AnalysisType) {
            return ((AnalysisType) type).getWrappedWithoutResolve();
        } else if (type instanceof HostedType) {
            return ((HostedType) type).getWrapped().getWrappedWithoutResolve();
        } else {
            return type;
        }
    }

}
