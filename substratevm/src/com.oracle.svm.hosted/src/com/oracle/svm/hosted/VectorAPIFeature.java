/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.VectorAPIDeoptimizationSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.VectorAPIEnabled;
import com.oracle.svm.core.jdk.VectorAPISupport;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIIntrinsics;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;

@AutomaticallyRegisteredFeature
public class VectorAPIFeature implements InternalFeature {

    public static final String VECTOR_API_PACKAGE_NAME = "jdk.incubator.vector";
    public static final Class<?> PAYLOAD_CLASS = ReflectionUtil.lookupClass("jdk.internal.vm.vector.VectorSupport$VectorPayload");

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @Override
    public String getDescription() {
        return "Registers Vector API classes for initialization at build time and pre-populates some caches.";
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        boolean vectorAPIEnabled = VectorAPIEnabled.getValue();
        boolean vectorAPIAvailable = access.findClassByName(VECTOR_API_PACKAGE_NAME + ".VectorShape") != null;

        if (vectorAPIEnabled && !vectorAPIAvailable) {
            // If vectorAPIEnabled becomes the default, this warning should be removed.
            LogUtils.warning("Native image option %s was used, but the application does not have access to the Vector API module. Did you forget to add '--add-modules %s'?",
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.VectorAPISupport, "+"), VECTOR_API_PACKAGE_NAME);
        }
        if (!vectorAPIEnabled && vectorAPIAvailable) {
            LogUtils.warning("The application has access to the Vector API module %s. Consider using %s to optimize Vector API operations.",
                            VECTOR_API_PACKAGE_NAME, SubstrateOptionsParser.commandArgument(SubstrateOptions.VectorAPISupport, "+"));
        }

        return vectorAPIEnabled && vectorAPIAvailable;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * We will initialize all classes in the Vector API package at build time. This is necessary
         * to avoid class initialization checks in hot paths, and generally because we need to see
         * constant values for static and lazily initialized fields in the various Vector API
         * classes.
         *
         * During class initialization, the VectorShape enum computes the target's maximum vector
         * bit size and uses it to initialize its S_Max_BIT enum key. This bit size is computed for
         * the host Java VM, but we need it to have a value for the target we are building the
         * native image for. Other classes' initializers then derive further constant values from
         * this size. We must therefore register FieldValueTransformers for a large number of fields
         * in various vector classes.
         */
        RuntimeClassInitialization.initializeAtBuildTime(VECTOR_API_PACKAGE_NAME);

        Class<?> vectorShapeClass = ReflectionUtil.lookupClass(VECTOR_API_PACKAGE_NAME + ".VectorShape");
        UNSAFE.ensureClassInitialized(vectorShapeClass);
        /*
         * Like the JDK, use a minimum of 64 bits even if the target vector size computation returns
         * -1 to signal that no vectors are available.
         */
        int maxVectorBits = Math.max(VectorAPISupport.singleton().getMaxVectorBytes() * Byte.SIZE, 64);

        Class<?>[] vectorElements = new Class<?>[]{byte.class, short.class, int.class, float.class, double.class};
        String[] vectorElementNames = new String[]{"Byte", "Short", "Int", "Long", "Float", "Double"};
        String[] vectorSizes = new String[]{"64", "128", "256", "512", "Max"};

        Object maxBitShape = ReflectionUtil.readStaticField(vectorShapeClass, "S_Max_BIT");
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(vectorShapeClass, "vectorBitSize"),
                        (receiver, originalValue) -> receiver == maxBitShape ? maxVectorBits : originalValue);
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(vectorShapeClass, "vectorBitSizeLog2"),
                        (receiver, originalValue) -> receiver == maxBitShape ? Integer.numberOfTrailingZeros(maxVectorBits) : originalValue);

        /*
         * Figure out the preferred shape. It is the same size as the max shape, but if possible
         * named using an explicit bit size, e.g., S_256_BIT rather than S_Max_BIT.
         */
        int maxSizeIndex = Math.min(Integer.numberOfTrailingZeros(maxVectorBits / 64), vectorSizes.length - 1);
        String maxSizeName = vectorSizes[maxSizeIndex];
        Object preferredShape = ReflectionUtil.readStaticField(vectorShapeClass, "S_" + maxSizeName + "_BIT");

        /*
         * We collect data about specific AbstractSpecies instances (ByteVector.SPECIES_MAX,
         * IntVector.SPECIES_MAX, etc.) in this map, then use this data in FieldValueTransformers
         * for fields declared in AbstractSpecies.
         */
        EconomicMap<Object, MaxVectorSizes> maxVectorSizes = EconomicMap.create();

        for (Class<?> vectorElement : vectorElements) {
            String elementName = vectorElement.getName().substring(0, 1).toUpperCase(Locale.ROOT) + vectorElement.getName().substring(1);

            String generalVectorName = VECTOR_API_PACKAGE_NAME + "." + elementName + "Vector";
            Class<?> vectorClass = ReflectionUtil.lookupClass(generalVectorName);
            UNSAFE.ensureClassInitialized(vectorClass);
            Object speciesMax = ReflectionUtil.readStaticField(vectorClass, "SPECIES_MAX");
            maxVectorSizes.put(speciesMax, new MaxVectorSizes(
                            VectorAPISupport.singleton().getMaxLaneCount(vectorElement),
                            Integer.numberOfTrailingZeros(VectorAPISupport.singleton().getMaxLaneCount(vectorElement)) + 1,
                            maxVectorBits,
                            maxVectorBits / Byte.SIZE));
            Method species = ReflectionUtil.lookupMethod(vectorClass, "species", vectorShapeClass);
            access.registerFieldValueTransformer(ReflectionUtil.lookupField(vectorClass, "SPECIES_PREFERRED"),
                            (receiver, originalValue) -> ReflectionUtil.invokeMethod(species, null, preferredShape));

            String maxVectorName = VECTOR_API_PACKAGE_NAME + "." + elementName + "MaxVector";
            Class<?> maxVectorClass = ReflectionUtil.lookupClass(maxVectorName);
            int laneCount = VectorAPISupport.singleton().getMaxLaneCount(vectorElement);
            access.registerFieldValueTransformer(ReflectionUtil.lookupField(maxVectorClass, "VSIZE"),
                            (receiver, originalValue) -> maxVectorBits);
            access.registerFieldValueTransformer(ReflectionUtil.lookupField(maxVectorClass, "VLENGTH"),
                            (receiver, originalValue) -> laneCount);
            access.registerFieldValueTransformer(ReflectionUtil.lookupField(maxVectorClass, "ZERO"),
                            (receiver, originalValue) -> makeZeroVector(maxVectorClass, vectorElement, laneCount));
            access.registerFieldValueTransformer(ReflectionUtil.lookupField(maxVectorClass, "IOTA"),
                            (receiver, originalValue) -> makeIotaVector(maxVectorClass, vectorElement, laneCount));
        }

        Class<?> speciesClass = ReflectionUtil.lookupClass(VECTOR_API_PACKAGE_NAME + ".AbstractSpecies");
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(speciesClass, "laneCount"), new OverrideFromMap(maxVectorSizes, MaxVectorSizes::laneCount));
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(speciesClass, "laneCountLog2P1"), new OverrideFromMap(maxVectorSizes, MaxVectorSizes::laneCountLog2P1));
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(speciesClass, "vectorBitSize"), new OverrideFromMap(maxVectorSizes, MaxVectorSizes::vectorBitSize));
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(speciesClass, "vectorByteSize"), new OverrideFromMap(maxVectorSizes, MaxVectorSizes::vectorByteSize));

        /*
         * Manually initialize some inner classes and mark them as reachable. Due to the way we
         * intrinsify operations, we may need to access information about a type before the analysis
         * has seen it.
         */
        for (Class<?> vectorElement : vectorElements) {
            String elementName = vectorElement.getName().substring(0, 1).toUpperCase(Locale.ROOT) + vectorElement.getName().substring(1);
            for (String size : vectorSizes) {
                String baseName = elementName + size;
                String vectorClassName = VECTOR_API_PACKAGE_NAME + "." + baseName + "Vector";
                Class<?> shuffleClass = ReflectionUtil.lookupClass(vectorClassName + "$" + baseName + "Shuffle");
                UNSAFE.ensureClassInitialized(shuffleClass);
                access.registerAsUsed(shuffleClass);
                Class<?> maskClass = ReflectionUtil.lookupClass(vectorClassName + "$" + baseName + "Mask");
                UNSAFE.ensureClassInitialized(maskClass);
                access.registerAsUsed(maskClass);
                if (size.equals("Max")) {
                    int laneCount = VectorAPISupport.singleton().getMaxLaneCount(vectorElement);
                    Class<?> shuffleElement = (vectorElement == float.class ? int.class : vectorElement == double.class ? long.class : vectorElement);
                    access.registerFieldValueTransformer(ReflectionUtil.lookupField(shuffleClass, "IOTA"),
                                    (receiver, originalValue) -> makeIotaVector(shuffleClass, shuffleElement, laneCount));
                    access.registerFieldValueTransformer(ReflectionUtil.lookupField(maskClass, "TRUE_MASK"),
                                    (receiver, originalValue) -> makeNewInstanceWithBooleanPayload(maskClass, laneCount, true));
                    access.registerFieldValueTransformer(ReflectionUtil.lookupField(maskClass, "FALSE_MASK"),
                                    (receiver, originalValue) -> makeNewInstanceWithBooleanPayload(maskClass, laneCount, false));
                }
            }
        }

        /* Warm up caches of arithmetic and conversion operations. */
        WarmupData warmupData = new WarmupData();

        for (String elementName : vectorElementNames) {
            String vectorClassName = VECTOR_API_PACKAGE_NAME + "." + elementName + "Vector";
            Class<?> vectorClass = ReflectionUtil.lookupClass(vectorClassName);
            UNSAFE.ensureClassInitialized(vectorClass);
            warmupImplCache(vectorClass, "UN_IMPL", "unaryOperations", warmupData);
            warmupImplCache(vectorClass, "BIN_IMPL", "binaryOperations", warmupData);
            warmupImplCache(vectorClass, "TERN_IMPL", "ternaryOperations", warmupData);
            warmupImplCache(vectorClass, "REDUCE_IMPL", "reductionOperations", warmupData);
        }

        /* Warm up caches for mapping between lane types, used by shuffles. */
        Method asIntegral = ReflectionUtil.lookupMethod(speciesClass, "asIntegral");
        Method asFloating = ReflectionUtil.lookupMethod(speciesClass, "asFloating");
        for (String elementName : vectorElementNames) {
            String vectorClassName = VECTOR_API_PACKAGE_NAME + "." + elementName + "Vector";
            Class<?> vectorClass = ReflectionUtil.lookupClass(vectorClassName);
            UNSAFE.ensureClassInitialized(vectorClass);
            for (String size : vectorSizes) {
                String fieldName = "SPECIES_" + size.toUpperCase(Locale.ROOT);
                Object species = ReflectionUtil.readStaticField(vectorClass, fieldName);
                try {
                    asIntegral.invoke(species);
                    if (elementName.equals("Int") || elementName.equals("Long")) {
                        asFloating.invoke(species);
                    }
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    throw VMError.shouldNotReachHere(ex);
                }
            }
        }

        Class<?> conversionImplClass = ReflectionUtil.lookupClass(VECTOR_API_PACKAGE_NAME + ".VectorOperators$ConversionImpl");
        UNSAFE.ensureClassInitialized(conversionImplClass);
        makeConversionOperations(conversionImplClass, warmupData);

        if (DeoptimizationSupport.enabled()) {
            /* Build a table of payload type descriptors for deoptimization. */
            VectorAPIDeoptimizationSupport deoptSupport = new VectorAPIDeoptimizationSupport();
            for (Class<?> vectorElement : vectorElements) {
                int elementBytes = JavaKind.fromJavaClass(vectorElement).getByteCount();
                String elementName = vectorElement.getName().substring(0, 1).toUpperCase(Locale.ROOT) + vectorElement.getName().substring(1);
                for (String size : vectorSizes) {
                    int vectorLength = size.equals("Max")
                                    ? VectorAPISupport.singleton().getMaxLaneCount(vectorElement)
                                    : (Integer.parseInt(size) / Byte.SIZE) / elementBytes;
                    String baseName = elementName + size;
                    String vectorClassName = VECTOR_API_PACKAGE_NAME + "." + baseName + "Vector";

                    Class<?> vectorClass = ReflectionUtil.lookupClass(vectorClassName);
                    deoptSupport.putLayout(vectorClass, new VectorAPIDeoptimizationSupport.PayloadLayout(vectorElement, vectorLength));

                    Class<?> shuffleClass = ReflectionUtil.lookupClass(vectorClassName + "$" + baseName + "Shuffle");
                    Class<?> shuffleElement = (vectorElement == float.class ? int.class : vectorElement == double.class ? long.class : vectorElement);
                    deoptSupport.putLayout(shuffleClass, new VectorAPIDeoptimizationSupport.PayloadLayout(shuffleElement, vectorLength));

                    Class<?> maskClass = ReflectionUtil.lookupClass(vectorClassName + "$" + baseName + "Mask");
                    deoptSupport.putLayout(maskClass, new VectorAPIDeoptimizationSupport.PayloadLayout(boolean.class, vectorLength));
                }
            }
            ImageSingletons.add(VectorAPIDeoptimizationSupport.class, deoptSupport);
        }
    }

    private record MaxVectorSizes(int laneCount, int laneCountLog2P1, int vectorBitSize, int vectorByteSize) {

    }

    /**
     * Helper for overriding a field's value only in specific instances. If the receiver is one of
     * the instances appearing as keys in {@code map}, return the associated value computed via the
     * {@code accessor}. Otherwise, return the field's original value unchanged.
     */
    private record OverrideFromMap(EconomicMap<Object, MaxVectorSizes> map, Function<MaxVectorSizes, Object> accessor) implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            MaxVectorSizes overridingValues = map.get(receiver);
            if (overridingValues != null) {
                return accessor.apply(overridingValues);
            }
            return originalValue;
        }
    }

    /**
     * Reflectively looked up data needed for warming up caches inside vector classes. This is
     * packaged in a class because the relevant classes are not always on the module path, so the
     * reflective lookups could fail. Therefore, we don't put this data in static fields, and we
     * lazily initialize it when it's needed and will be available.
     */
    private static final class WarmupData {
        final Class<?> implCacheClass;
        final Class<?> operatorClass;
        final Method implCacheFind;
        final int[] vectorOpcodes;
        final Class<?> laneTypeClass;
        final Object[] laneTypes;

        private static final String[] LANE_TYPE_NAMES = new String[]{"FLOAT", "DOUBLE", "BYTE", "SHORT", "INT", "LONG"};
        /* Conversions: Identity, Convert (narrow or sign extend), Reinterpret, Zero extend */
        private static final char[] CONVERSION_KINDS = "ICRZ".toCharArray();

        private WarmupData() {
            implCacheClass = ReflectionUtil.lookupClass(VECTOR_API_PACKAGE_NAME + ".VectorOperators$ImplCache");
            operatorClass = ReflectionUtil.lookupClass(VECTOR_API_PACKAGE_NAME + ".VectorOperators$Operator");
            implCacheFind = ReflectionUtil.lookupMethod(implCacheClass, "find", operatorClass, int.class, IntFunction.class);
            Class<?> vectorSupportClass = ReflectionUtil.lookupClass("jdk.internal.vm.vector.VectorSupport");
            ArrayList<Integer> opcodeList = new ArrayList<>();
            for (Field f : vectorSupportClass.getDeclaredFields()) {
                if (f.getType() == int.class && f.accessFlags().contains(AccessFlag.STATIC) && f.accessFlags().contains(AccessFlag.FINAL) && f.getName().startsWith("VECTOR_OP_")) {
                    opcodeList.add(ReflectionUtil.readStaticField(vectorSupportClass, f.getName()));
                }
            }
            int[] opcodes = new int[opcodeList.size()];
            for (int i = 0; i < opcodes.length; i++) {
                opcodes[i] = opcodeList.get(i);
            }
            vectorOpcodes = opcodes;

            this.laneTypeClass = ReflectionUtil.lookupClass(VECTOR_API_PACKAGE_NAME + ".LaneType");
            this.laneTypes = new Object[LANE_TYPE_NAMES.length];
            for (int i = 0; i < laneTypes.length; i++) {
                laneTypes[i] = ReflectionUtil.readStaticField(laneTypeClass, LANE_TYPE_NAMES[i]);
            }
        }
    }

    /**
     * Warms up caches inside vector classes. The various vector classes have static fields of type
     * {@code ImplCache} that map vector opcodes to their fallback Java implementations. For
     * example, {@code IntVector.BIN_IMPL} maps binary opcodes to cached values as returned by the
     * {@code IntVector.binaryOperations} method. Every invocation of an operation involves a lookup
     * in these caches. Internally the cache is backed by a stable array, which is enough for a JIT
     * compiler to constant fold the result of the lookup of a constant opcode. For AOT compilation
     * we pre-populate the cached arrays here so that we can also constant fold at compile time.
     */
    private static void warmupImplCache(Class<?> vectorClass, String cacheName, String cachedMethodName, WarmupData warmupData) {
        Object cacheObject = ReflectionUtil.readStaticField(vectorClass, cacheName);
        Method cachedMethod = ReflectionUtil.lookupMethod(vectorClass, cachedMethodName, int.class);

        IntFunction<?> methodAsIntFunction = (int opc) -> {
            try {
                return cachedMethod.invoke(null, (Object) opc);
            } catch (Throwable ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        };
        for (int opcode : warmupData.vectorOpcodes) {
            try {
                warmupData.implCacheFind.invoke(cacheObject, null, opcode, methodAsIntFunction);
            } catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof UnsupportedOperationException) {
                    /*
                     * Ignore this exception, this is the expected error thrown by the find method
                     * for opcodes that are not implemented in the given vector class.
                     */
                } else {
                    throw VMError.shouldNotReachHere(ex);
                }
            } catch (Throwable ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
    }

    /**
     * Warms up the cache of conversion operations by calling
     * VectorOperations$ConversionImpl.makeConv on all tuples of supported conversion kind and
     * domain (input) and range (output) types.
     */
    public static void makeConversionOperations(Class<?> conversionImplClass, WarmupData warmupData) {
        Method makeConv = ReflectionUtil.lookupMethod(conversionImplClass, "makeConv", char.class, warmupData.laneTypeClass, warmupData.laneTypeClass);
        for (char kind : WarmupData.CONVERSION_KINDS) {
            for (Object dom : warmupData.laneTypes) {
                for (Object ran : warmupData.laneTypes) {
                    ReflectionUtil.invokeMethod(makeConv, null, kind, dom, ran);
                }
            }
        }
    }

    private static Object makeZeroVector(Class<?> vectorClass, Class<?> vectorElement, int laneCount) {
        Object zeroPayload = Array.newInstance(vectorElement, laneCount);
        return ReflectionUtil.newInstance(ReflectionUtil.lookupConstructor(vectorClass, zeroPayload.getClass()), zeroPayload);
    }

    private static Object makeNewInstanceWithBooleanPayload(Class<?> maskClass, int laneCount, boolean fillValue) {
        /*
         * The constructors for Mask classes allocate new arrays based on the species length, which
         * we also substitute but whose substituted value will not be used yet. So instead of just
         * calling a constructor with a boolean array, we brute force this: We allocate a new
         * instance which may have a payload with an incorrect length, then override its payload
         * field.
         */
        Object newInstance = ReflectionUtil.newInstance(ReflectionUtil.lookupConstructor(maskClass, boolean.class), true);
        boolean[] payload = new boolean[laneCount];
        Arrays.fill(payload, fillValue);
        ReflectionUtil.writeField(PAYLOAD_CLASS, "payload", newInstance, payload);
        return newInstance;
    }

    private static Object makeIotaVector(Class<?> vectorClass, Class<?> vectorElement, int laneCount) {
        Object iotaPayload = Array.newInstance(vectorElement, laneCount);
        for (int i = 0; i < laneCount; i++) {
            // adapted from AbstractSpecies.iotaArray
            if ((byte) i == i) {
                Array.setByte(iotaPayload, i, (byte) i);
            } else if ((short) i == i) {
                Array.setShort(iotaPayload, i, (short) i);
            } else {
                Array.setInt(iotaPayload, i, i);
            }
            VMError.guarantee(Array.getDouble(iotaPayload, i) == i, "wrong initialization of iota array: %s at %s", Array.getDouble(iotaPayload, i), i);
        }
        return ReflectionUtil.newInstance(ReflectionUtil.lookupConstructor(vectorClass, iotaPayload.getClass()), iotaPayload);
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (VectorAPIIntrinsics.intrinsificationSupported(HostedOptionValues.singleton())) {
            VectorAPIIntrinsics.registerPlugins(plugins.getInvocationPlugins(), providers.getReplacements());
        }
    }
}
