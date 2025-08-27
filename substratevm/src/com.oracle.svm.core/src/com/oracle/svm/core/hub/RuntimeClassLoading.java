/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import static jdk.graal.compiler.options.OptionStability.EXPERIMENTAL;

import java.security.ProtectionDomain;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.Constants;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.vm.ci.meta.ResolvedJavaType;

public class RuntimeClassLoading {
    public enum VerifyMode {
        /**
         * Disables bytecode verification for all class loaders.
         */
        NONE,
        /**
         * Disables bytecode verification for the boot class loader. Classes loaded for any other
         * loader are still verified. This is usually the default mode.
         */
        REMOTE,
        /**
         * Enables bytecode verification for all class loaders.
         */
        ALL;

        public boolean needsVerification(ClassLoader loader) {
            return switch (this) {
                case NONE -> false;
                case REMOTE -> loader != null;
                case ALL -> true;
            };
        }
    }

    public static final class Options {
        @Option(help = "Enable support for runtime class loading. This implies open world (-ClosedTypeWorld) and respecting class loader hierarchy (+ClassForNameRespectsClassLoader).", stability = EXPERIMENTAL) //
        public static final HostedOptionKey<Boolean> RuntimeClassLoading = new HostedOptionKey<>(false, Options::validateRuntimeClassLoading) {

            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                if (newValue) {
                    /* requires open type world */
                    SubstrateOptions.ClosedTypeWorld.update(values, false);
                    ClassForNameSupport.Options.ClassForNameRespectsClassLoader.update(values, true);
                    PredefinedClassesSupport.Options.SupportPredefinedClasses.update(values, false);
                }
            }
        };

        private static void validateRuntimeClassLoading(HostedOptionKey<Boolean> optionKey) {
            if (!optionKey.getValue()) {
                return;
            }
            if (!SubstrateOptions.SpawnIsolates.getValue()) {
                /*
                 * A metaspace is only supported if there is a contiguous address space, which is
                 * only the case with isolate support enabled.
                 */
                throw UserError.invalidOptionValue(RuntimeClassLoading, RuntimeClassLoading.getValue(),
                                "Requires isolate support, please use " + SubstrateOptionsParser.commandArgument(SubstrateOptions.SpawnIsolates, "+"));
            }
            if (SubstrateOptions.ClosedTypeWorld.getValue()) {
                throw UserError.invalidOptionValue(RuntimeClassLoading, RuntimeClassLoading.getValue(),
                                "Requires an open type world, please use " + SubstrateOptionsParser.commandArgument(SubstrateOptions.ClosedTypeWorld, "-"));
            }
            if (!ClassForNameSupport.Options.ClassForNameRespectsClassLoader.getValue()) {
                throw UserError.invalidOptionValue(RuntimeClassLoading, RuntimeClassLoading.getValue(),
                                "Requires Class.forName to respect the classloader argument, please use " +
                                                SubstrateOptionsParser.commandArgument(ClassForNameSupport.Options.ClassForNameRespectsClassLoader, "+"));
            }
            if (PredefinedClassesSupport.Options.SupportPredefinedClasses.getValue()) {
                throw UserError.invalidOptionValue(RuntimeClassLoading, RuntimeClassLoading.getValue(),
                                "Requires predefined class support to be disabled, please use " +
                                                SubstrateOptionsParser.commandArgument(PredefinedClassesSupport.Options.SupportPredefinedClasses, "-"));
            }
        }

        @Option(help = "Verification mode for runtime class loading.") //
        public static final HostedOptionKey<VerifyMode> ClassVerification = new HostedOptionKey<>(VerifyMode.REMOTE);
    }

    @Fold
    public static boolean isSupported() {
        return Options.RuntimeClassLoading.getValue();
    }

    public static Class<?> defineClass(ClassLoader loader, String expectedName, byte[] b, int off, int len, ClassDefinitionInfo info) {
        if (PredefinedClassesSupport.hasBytecodeClasses()) {
            Class<?> knownClass = PredefinedClassesSupport.knownClass(b, off, len);
            if (knownClass != null) {
                if (expectedName != null) {
                    String dotName = expectedName.replace('/', '.');
                    if (!dotName.equals(knownClass.getName())) {
                        throw new NoClassDefFoundError(knownClass.getName() + " (wrong name: " + dotName + ')');
                    }
                }
                PredefinedClassesSupport.loadClass(loader, info.protectionDomain, knownClass);
                return knownClass;
            }
            String name = (expectedName != null) ? expectedName : "(name not specified)";
            throw VMError.unsupportedFeature(
                            "Class " + name + " with hash " + PredefinedClassesSupport.getHash(b, off, len) +
                                            " was not provided during the image build via the 'predefined-classes-config.json' file. Please see 'BuildConfiguration.md'.");
        } else if (RuntimeClassLoading.isSupported()) {
            return ClassRegistries.defineClass(loader, expectedName, b, off, len, info);
        } else {
            throw throwNoBytecodeClasses(expectedName);
        }
    }

    // This should also mention Crema when it can load non-trivial classes (GR-60118)
    private static final String DEFINITION_NOT_SUPPORTED_MESSAGE = """
                    To make this work, you have the following options:
                      1) Modify or reconfigure your application (or a third-party library) so that it does not generate classes at runtime or load them via non-built-in class loaders.
                      2) If the classes must be generated, try to generate them at build time in a static initializer of a dedicated class.\
                     The generated java.lang.Class objects should be stored in static fields and the dedicated class initialized by passing '--initialize-at-build-time=<class_name>' as the build argument.
                      3) If none of the above is applicable, use the tracing agent to run this application and collect predefined classes with\
                     'java -agentlib:native-image-agent=config-output-dir=<config-dir>,experimental-class-define-support <application-arguments>'.\
                     Note that this is an experimental feature and that it does not guarantee success. Furthermore, the resulting classes can contain entries\
                     from the classpath that should be manually filtered out to reduce image size. The agent should be used only in cases where modifying the source of the project is not possible.
                    """
                    .replace("\n", System.lineSeparator());

    public static RuntimeException throwNoBytecodeClasses(String className) {
        assert !PredefinedClassesSupport.hasBytecodeClasses() && !RuntimeClassLoading.isSupported();
        throw VMError.unsupportedFeature(
                        "Classes cannot be defined at runtime by default when using ahead-of-time Native Image compilation. Tried to define class '" + className + "'" + System.lineSeparator() +
                                        DEFINITION_NOT_SUPPORTED_MESSAGE);
    }

    public static DynamicHub getOrCreateArrayHub(DynamicHub hub) {
        if (hub.getArrayHub() == null) {
            VMError.guarantee(RuntimeClassLoading.isSupported());
            // GR-63452
            throw VMError.unimplemented("array hub creation");
        }
        return hub.getArrayHub();
    }

    public static final class ClassDefinitionInfo {
        public static final ClassDefinitionInfo EMPTY = new ClassDefinitionInfo(null, null, null, false, false, false);

        // Constructor for regular definition, but with a specified protection domain
        public ClassDefinitionInfo(ProtectionDomain protectionDomain) {
            this(protectionDomain, null, null, false, false, false);
        }

        // Constructor for Hidden class definition.
        public ClassDefinitionInfo(ProtectionDomain protectionDomain, Class<?> dynamicNest, Object classData, boolean isStrongHidden, boolean forceAllowVMAnnotations) {
            this(protectionDomain, dynamicNest, classData, true, isStrongHidden, forceAllowVMAnnotations);
        }

        private ClassDefinitionInfo(ProtectionDomain protectionDomain,
                        Class<?> dynamicNest,
                        Object classData,
                        boolean isHidden,
                        boolean isStrongHidden,
                        boolean forceAllowVMAnnotations) {
            assert !isStrongHidden || isHidden;
            assert dynamicNest == null || isHidden;
            assert classData == null || isHidden;
            assert !forceAllowVMAnnotations || isHidden;
            this.protectionDomain = protectionDomain;
            this.dynamicNest = dynamicNest;
            this.classData = classData;
            this.isHidden = isHidden;
            this.isStrongHidden = isStrongHidden;
            this.forceAllowVMAnnotations = forceAllowVMAnnotations;
        }

        public final ProtectionDomain protectionDomain;

        // Hidden class
        public final Class<?> dynamicNest;
        public final Object classData;
        public final boolean isHidden;
        public final boolean isStrongHidden;
        public final boolean forceAllowVMAnnotations;

        public boolean addedToRegistry() {
            return !isHidden();
        }

        public boolean isHidden() {
            return isHidden;
        }

        public boolean isStrongHidden() {
            return isStrongHidden;
        }

        public boolean forceAllowVMAnnotations() {
            return forceAllowVMAnnotations;
        }

        public int patchFlags(int classFlags) {
            int flags = classFlags;
            if (isHidden()) {
                flags |= Constants.ACC_IS_HIDDEN_CLASS;
            }
            return flags;
        }

        @Override
        public String toString() {
            if (this == EMPTY) {
                return "EMPTY";
            }
            if (!isHidden) {
                return "ClassDefinitionInfo{protectionDomain=" + protectionDomain + "}";
            }
            return "ClassDefinitionInfo{" +
                            "protectionDomain=" + protectionDomain +
                            ", dynamicNest=" + dynamicNest +
                            ", classData=" + classData +
                            ", isHidden=" + isHidden +
                            ", isStrongHidden=" + isStrongHidden +
                            ", forceAllowVMAnnotations=" + forceAllowVMAnnotations +
                            '}';
        }
    }

    public static ResolvedJavaType createInterpreterType(DynamicHub hub, ResolvedJavaType analysisType) {
        return CremaSupport.singleton().createInterpreterType(hub, analysisType);
    }

    public static void ensureLinked(DynamicHub dynamicHub) {
        if (dynamicHub.isLinked()) {
            return;
        }
        // GR-59739 runtime linking
    }
}
