/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements;

import java.lang.reflect.Type;
import java.util.Arrays;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIIntrinsics;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VectorIntrinsics {

    public static class Options {
        // @formatter:off
        @Option(help = "Enables vectorization. This is a global switch to enable/disable all vectorization-related optimizations.", stability = OptionStability.STABLE, type = OptionType.Expert)
        public static final OptionKey<Boolean> Vectorization = new OptionKey<>(true) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                if (!newValue) {
                    values.put(VectorAPIIntrinsics.Options.OptimizeVectorAPI, false);
                    values.put(GraalOptions.TargetVectorLowering, false);
                }
            }
        };

        @Option(help = "Enables vectorized array copy intrinsics. This can improve performance if " +
                       "the generated code uses vectorized intrinsics for array copy.", type = OptionType.Expert)
        public static final OptionKey<Boolean> VectorIntrinsics = new OptionKey<>(true);

        @Option(help = "Unroll vectorized loops.", type = OptionType.Debug)
        public static final OptionKey<Integer> VectorUnroll = new OptionKey<>(1);

        @Option(help = "Maximum length of linear-code vector operations")
        public static final OptionKey<Integer> MaxVectorUnroll = new OptionKey<>(16);

        @Option(help = "Emit a pointer alignment header for vectorized loops, where supported.")
        public static final OptionKey<Boolean> VectorAlignment = new OptionKey<>(true);

        @Option(help = "Maximum number of unrolled alignment instructions")
        public static final OptionKey<Integer> MaxVectorAlignmentUnroll = new OptionKey<>(4);
        // @formatter:on
    }

    private static final class CopyOfPrimitivePlugin extends InlineOnlyInvocationPlugin {

        private final JavaKind elementKind;
        private final boolean needsExplicitException;

        private CopyOfPrimitivePlugin(JavaKind elementKind, boolean needsExplicitException, String name, Type... argumentTypes) {
            super(name, argumentTypes);
            this.elementKind = elementKind;
            this.needsExplicitException = needsExplicitException;
        }

        /**
         * @see Arrays#copyOf(byte[], int)
         */
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode original, ValueNode newLength) {
            if (Options.Vectorization.getValue(b.getOptions()) && Options.VectorIntrinsics.getValue(b.getOptions())) {
                CopyOfNode.copyOfPrimitive(b, targetMethod, this.elementKind, original, newLength, needsExplicitException);
                return true;
            } else {
                return false;
            }
        }

        /**
         * @see Arrays#copyOfRange(byte[], int, int)
         */
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode original, ValueNode from, ValueNode to) {
            if (Options.Vectorization.getValue(b.getOptions()) && Options.VectorIntrinsics.getValue(b.getOptions())) {
                CopyOfNode.copyOfRangePrimitive(b, targetMethod, this.elementKind, original, from, to, needsExplicitException);
                return true;
            } else {
                return false;
            }
        }
    }

    private static final class CopyOfObjectPlugin extends InlineOnlyInvocationPlugin {

        private final boolean needsExplicitException;

        private CopyOfObjectPlugin(boolean needsExplicitException, String name, Type... argumentTypes) {
            super(name, argumentTypes);
            this.needsExplicitException = needsExplicitException;
        }

        /**
         * @see Arrays#copyOf(Object[], int, Class)
         */
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode original, ValueNode newLength, ValueNode newArrayType) {
            if (Options.Vectorization.getValue(b.getOptions()) && Options.VectorIntrinsics.getValue(b.getOptions())) {
                CopyOfNode.copyOfObject(b, targetMethod, b.getInvokeReturnStamp(b.getAssumptions()), original, newLength, newArrayType, needsExplicitException);
                return true;
            } else {
                return false;
            }
        }

        /**
         * @see Arrays#copyOfRange(Object[], int, int, Class)
         */
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode original, ValueNode from, ValueNode to, ValueNode newArrayType) {
            if (Options.Vectorization.getValue(b.getOptions()) && Options.VectorIntrinsics.getValue(b.getOptions())) {
                CopyOfNode.copyOfRangeObject(b, targetMethod, b.getInvokeReturnStamp(b.getAssumptions()), original, from, to, newArrayType, needsExplicitException);
                return true;
            } else {
                return false;
            }
        }
    }

    private static void registerCopyOfPrimitiveArrayPlugin(Registration r, JavaKind elementKind, Class<?> arrayClass, boolean needsExplicitException) {
        r.register(new CopyOfPrimitivePlugin(elementKind, needsExplicitException, "copyOf", arrayClass, int.class));
        r.register(new CopyOfPrimitivePlugin(elementKind, needsExplicitException, "copyOfRange", arrayClass, int.class, int.class));
    }

    private static void registerCopyOfObjectArrayPlugins(InvocationPlugins plugins, boolean needsExplicitException) {
        Registration r = new Registration(plugins, Arrays.class);
        r.register(new CopyOfObjectPlugin(needsExplicitException, "copyOf", Object[].class, int.class, Class.class));
        r.register(new CopyOfObjectPlugin(needsExplicitException, "copyOfRange", Object[].class, int.class, int.class, Class.class));
    }

    public static void registerPlugins(InvocationPlugins plugins, OptionValues options, boolean needsExplicitException) {
        if (Options.Vectorization.getValue(options) && Options.VectorIntrinsics.getValue(options)) {
            registerCopyOfObjectArrayPlugins(plugins, needsExplicitException);
            Registration r = new Registration(plugins, Arrays.class);
            registerCopyOfPrimitiveArrayPlugin(r, JavaKind.Boolean, boolean[].class, needsExplicitException);
            registerCopyOfPrimitiveArrayPlugin(r, JavaKind.Byte, byte[].class, needsExplicitException);
            registerCopyOfPrimitiveArrayPlugin(r, JavaKind.Short, short[].class, needsExplicitException);
            registerCopyOfPrimitiveArrayPlugin(r, JavaKind.Char, char[].class, needsExplicitException);
            registerCopyOfPrimitiveArrayPlugin(r, JavaKind.Int, int[].class, needsExplicitException);
            registerCopyOfPrimitiveArrayPlugin(r, JavaKind.Long, long[].class, needsExplicitException);
            registerCopyOfPrimitiveArrayPlugin(r, JavaKind.Float, float[].class, needsExplicitException);
            registerCopyOfPrimitiveArrayPlugin(r, JavaKind.Double, double[].class, needsExplicitException);
        }
    }
}
