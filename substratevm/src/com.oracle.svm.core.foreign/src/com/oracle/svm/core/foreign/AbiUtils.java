/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.vm.ci.amd64.AMD64.rax;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.BuildPhaseProvider.AfterAnalysis;
import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.aarch64.SubstrateAArch64MacroAssembler;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.foreign.AbiUtils.Adapter.Adaptation;
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.graal.code.SubstrateBackendWithAssembler;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.headers.WindowsAPIs;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.util.BasedOnJDKClass;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.WordCastNode;
import jdk.internal.foreign.CABI;
import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.NativeEntryPoint;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.abi.aarch64.AArch64Architecture;
import jdk.internal.foreign.abi.x64.X86_64Architecture;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Utils for ABI specific functionalities in the context of the Java Foreign API. Provides methods
 * to transform JDK-internal data-structures into SubstrateVM ones.
 */
@BasedOnJDKClass(jdk.internal.foreign.abi.SharedUtils.class)
public abstract class AbiUtils {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class Adapter {
        private static boolean allEqual(int reference, int... values) {
            return Arrays.stream(values).allMatch(v -> v == reference);
        }

        private static boolean allSameSize(List<?> reference, List<?>... others) {
            return Arrays.stream(others).allMatch(v -> v.size() == reference.size());
        }

        private Adapter() {
        }

        public enum Extracted {
            CallTarget,
            CaptureBufferAddress
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public static class Result {
            @Platforms(Platform.HOSTED_ONLY.class)
            public record FullNativeAdaptation(
                            Map<Extracted, ValueNode> extractedArguments,
                            List<ValueNode> arguments,
                            List<AssignedLocation> parametersAssignment,
                            List<AssignedLocation> returnsAssignment,
                            MethodType callType,
                            List<Node> nodesToAppendToGraph) {
                public ValueNode getArgument(Extracted id) {
                    return extractedArguments.get(id);
                }
            }

            @Platforms(Platform.HOSTED_ONLY.class)
            public record TypeAdaptation(List<AssignedLocation> parametersAssignment, MethodType callType) {
            }
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public static Result.FullNativeAdaptation adaptToNative(AbiUtils self, List<Adaptation> adaptations, List<ValueNode> originalArguments, NativeEntryPointInfo nep) {
            List<ValueNode> originalUnmodifiableArguments = Collections.unmodifiableList(originalArguments);

            AssignedLocation[] originalAssignment = self.toMemoryAssignment(nep.parametersAssignment(), false);
            VMError.guarantee(allEqual(adaptations.size(), originalUnmodifiableArguments.size(), nep.methodType().parameterCount(), originalAssignment.length));

            Map<Extracted, ValueNode> extractedArguments = new EnumMap<>(Extracted.class);
            List<ValueNode> arguments = new ArrayList<>();
            List<AssignedLocation> assignment = new ArrayList<>();
            List<Class<?>> argumentTypes = new ArrayList<>();
            List<Node> nodesToAppendToGraph = new ArrayList<>();

            int i = 0;
            for (Adaptation a : adaptations) {
                Adaptation adaptation = a;
                if (adaptation == null) {
                    adaptation = NOOP;
                }

                arguments.addAll(adaptation.apply(originalUnmodifiableArguments.get(i), extractedArguments, originalUnmodifiableArguments, i, nodesToAppendToGraph::add));
                assignment.addAll(adaptation.apply(originalAssignment[i]));
                argumentTypes.addAll(adaptation.apply(nep.methodType().parameterType(i)));

                VMError.guarantee(allSameSize(arguments, assignment, argumentTypes));
                ++i;
            }
            assert i == nep.methodType().parameterCount();

            // Sanity checks
            VMError.guarantee(extractedArguments.containsKey(Extracted.CallTarget));
            VMError.guarantee(!nep.capturesCallState() || extractedArguments.containsKey(Extracted.CaptureBufferAddress));
            for (int j = 0; j < arguments.size(); ++j) {
                VMError.guarantee(arguments.get(j) != null);
                VMError.guarantee(!assignment.get(j).isPlaceholder() || (j == 0 && nep.needsReturnBuffer()));
            }

            return new Result.FullNativeAdaptation(extractedArguments, arguments, assignment, Arrays.stream(self.toMemoryAssignment(nep.returnsAssignment(), true)).toList(),
                            MethodType.methodType(nep.methodType().returnType(), argumentTypes), nodesToAppendToGraph);
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public static Result.TypeAdaptation adaptFromNative(AbiUtils self, List<Adaptation> adaptations, JavaEntryPointInfo jep) {
            AssignedLocation[] originalAssignment = self.toMemoryAssignment(jep.parametersAssignment(), false);

            List<AssignedLocation> assignment = new ArrayList<>();
            List<Class<?>> argumentTypes = new ArrayList<>();

            int i = 0;
            for (Adaptation a : adaptations) {
                Adaptation adaptation = a;
                if (adaptation == null) {
                    adaptation = NOOP;
                }

                assignment.addAll(adaptation.apply(originalAssignment[i]));
                argumentTypes.addAll(adaptation.apply(jep.handleType().parameterType(i)));
                ++i;
            }
            assert i == jep.handleType().parameterCount();

            return new Result.TypeAdaptation(assignment, MethodType.methodType(jep.handleType().returnType(), argumentTypes));
        }

        /**
         * Allow to define (and later apply) a transformation on a coordinate in arrays of parameter
         * types, parameter assignments and concrete arguments at the same time.
         *
         * E.g. given call with parameter types {@code [long, long, long]}, assignments
         * {@code [%rdi, %rsi, %rdx]} and concrete arguments {@code [ 0, 1, 2 ]}, one could define
         * the following adaptions {@code [ NOOP, drop(), check(long.class) ]}, which after
         * application would yield parameter types {@code [long, long]}, assignments
         * {@code [%rdi, %rdx]} and concrete arguments {@code [ 0, 2 ]}.
         *
         * No real restrictions are set on the actual transformations. The only invariant the
         * current implementation expects to hold is that all three methods of one object return the
         * same number of elements.
         */
        public abstract static class Adaptation {
            public abstract List<Class<?>> apply(Class<?> parameter);

            public abstract List<AssignedLocation> apply(AssignedLocation parameter);

            public abstract List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments, List<ValueNode> originalArguments, int originalArgumentIndex,
                            Consumer<Node> appendToGraph);
        }

        private static final Adaptation NOOP = new Adaptation() {
            @Override
            public List<Class<?>> apply(Class<?> parameter) {
                return List.of(parameter);
            }

            @Override
            public List<AssignedLocation> apply(AssignedLocation parameter) {
                return List.of(parameter);
            }

            @Override
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments, List<ValueNode> originalArguments, int originalArgumentIndex,
                            Consumer<Node> appendToGraph) {
                return List.of(parameter);
            }
        };

        public static Adaptation check(Class<?> type) {
            return new CheckType(Objects.requireNonNull(type));
        }

        public static Adaptation extract(Extracted as, Class<?> type) {
            return new ExtractSingle(as, type);
        }

        public static Adaptation extractSegmentPair(Extracted as) {
            return new ExtractSegmentPair(as);
        }

        public static Adaptation drop() {
            return Drop.SINGLETON;
        }

        public static Adaptation reinterpret(JavaKind to) {
            return new Reinterpret(to);
        }

        public static Adaptation computeAddressFromSegmentPair() {
            return ComputeAddressFromSegmentPair.SINGLETON;
        }

        private static final class CheckType extends Adaptation {
            private final Class<?> expected;

            private CheckType(Class<?> expected) {
                this.expected = expected;
            }

            @Override
            public List<Class<?>> apply(Class<?> parameter) {
                if (parameter != expected) {
                    throw new IllegalArgumentException("Expected type " + expected + ", got " + parameter);
                }
                return List.of(parameter);
            }

            @Override
            public List<AssignedLocation> apply(AssignedLocation parameter) {
                return List.of(parameter);
            }

            @Override
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments, List<ValueNode> originalArguments, int originalArgumentIndex,
                            Consumer<Node> appendToGraph) {
                return List.of(parameter);
            }
        }

        private static final class Reinterpret extends Adaptation {
            private final JavaKind to;

            private Reinterpret(JavaKind to) {
                this.to = to;
            }

            @Override
            public List<Class<?>> apply(Class<?> parameter) {
                return List.of(to.toJavaClass());
            }

            @Override
            public List<AssignedLocation> apply(AssignedLocation parameter) {
                return List.of(parameter);
            }

            @Override
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments, List<ValueNode> originalArguments, int originalArgumentIndex,
                            Consumer<Node> appendToGraph) {
                var reinterpreted = ReinterpretNode.reinterpret(to, parameter);
                appendToGraph.accept(reinterpreted);
                return List.of(reinterpreted);
            }
        }

        /**
         * This adaptation is used when a downcall uses Linker.Option.critical(true). <br>
         * When an argument whose layout is an AddressLayout is passed in a downcall, it must be
         * passed as a MemorySegment from the Java side. From these, the downcall stub extracts a
         * raw pointer to the data and calls the downcall with it. <br>
         * Usually, only {@link jdk.internal.foreign.NativeMemorySegmentImpl} can be passed
         * (segments allocated using an Arena), and these have a method `unsafeGetOffset` which
         * straightforwardly returns the raw pointer. <br>
         * However, when `allowHeapAccess` is true (the argument to Linker.Option.critical), one may
         * pass a {@link jdk.internal.foreign.HeapMemorySegmentImpl} as well. For reasons detailed
         * in its documentation, heap segments are represented as an Object + offset pair, where the
         * raw pointer should be derived from their sum. <br>
         * Hence, when `allowHeapAccess` is true,
         * {@link CallArranger.UnboxBindingCalculator#getBindings(Class, MemoryLayout)} passes two
         * arguments for every AddressLayout, the result of `unsafeGetBase` (of type Object) and
         * `unsafeGetOffset` (of type Long). <br>
         * Then, in the JVM, somewhere in the native implementation of
         * {@link NativeEntryPoint#makeDowncallStub(MethodType, ABIDescriptor, VMStorage[], VMStorage[], boolean, int, boolean)},
         * some code is generated which adds together the two values. Hence, when generating the
         * stub graph in SVM, make sure that the downcall performs the sum as well. <br>
         * <br>
         * Note that the only time when a VMStorage (such as in nep.parameterAssignments()) is
         * {@code null} is when Linker.Option.critical(true) is passed. See
         * {@link CallArranger.UnboxBindingCalculator#getBindings(Class, MemoryLayout)}.
         */
        @SuppressWarnings("javadoc")
        private static final class ComputeAddressFromSegmentPair extends Adaptation {
            private static final ComputeAddressFromSegmentPair SINGLETON = new ComputeAddressFromSegmentPair();

            private ComputeAddressFromSegmentPair() {
            }

            @Override
            public List<Class<?>> apply(Class<?> parameter) {
                return List.of(long.class);
            }

            @Override
            public List<AssignedLocation> apply(AssignedLocation parameter) {
                return List.of(parameter);
            }

            @Override
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments, List<ValueNode> originalArguments, int originalArgumentIndex,
                            Consumer<Node> appendToGraph) {
                return List.of(computeAbsolutePointerFromSegmentPair(parameter, originalArguments, originalArgumentIndex, appendToGraph));
            }

            static ValueNode computeAbsolutePointerFromSegmentPair(ValueNode parameter, List<ValueNode> originalArguments, int originalArgumentIndex, Consumer<Node> appendToGraph) {
                var offsetArg = originalArguments.get(originalArgumentIndex + 1);

                /*
                 * It would be most suitable to use OffsetAddressNode here (followed by
                 * WordCastNode.addressToWord) but NativeMemorySegmentImpls return null for
                 * `unsafeGetBase`,which seems to break the graph somewhere later.
                 */
                var basePointer = WordCastNode.objectToUntrackedPointer(parameter, ConfigurationValues.getWordKind());
                appendToGraph.accept(basePointer);
                var absolutePointer = AddNode.add(basePointer, offsetArg);
                appendToGraph.accept(absolutePointer);

                return absolutePointer;
            }
        }

        /**
         * Extract adaptations consume one or more stub parameters. In this case, "consuming" means
         * that the adapted parameter(s) won't be passed to the stub's target but will be used by
         * the stub itself. The result is usually one ValueNode that will be put into the
         * 'extractedArguments' table.
         */
        private abstract static class Extract extends Adaptation {
            final Extracted as;

            private Extract(Extracted as) {
                this.as = as;
            }

            @Override
            public final List<AssignedLocation> apply(AssignedLocation parameter) {
                return List.of();
            }
        }

        private static final class Drop extends Extract {
            private static final Drop SINGLETON = new Drop();

            private Drop() {
                super(null);
            }

            @Override
            public List<Class<?>> apply(Class<?> parameter) {
                return List.of();
            }

            @Override
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments, List<ValueNode> originalArguments, int originalArgumentIndex,
                            Consumer<Node> appendToGraph) {
                return List.of();
            }
        }

        private static final class ExtractSingle extends Extract {
            private final Class<?> type;

            private ExtractSingle(Extracted as, Class<?> type) {
                super(Objects.requireNonNull(as));
                this.type = Objects.requireNonNull(type);
            }

            @Override
            public List<Class<?>> apply(Class<?> parameter) {
                if (type != null && parameter != type) {
                    throw new IllegalArgumentException("Expected type " + type + ", got " + parameter);
                }
                return List.of();
            }

            @Override
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments, List<ValueNode> originalArguments, int originalArgumentIndex,
                            Consumer<Node> appendToGraph) {
                if (as != null) {
                    if (extractedArguments.containsKey(as)) {
                        throw new IllegalStateException("%s was already extracted (%s).".formatted(as, extractedArguments.get(as)));
                    }
                    extractedArguments.put(as, parameter);
                }
                return List.of();
            }
        }

        /**
         * Similar to {@link ComputeAddressFromSegmentPair}, consumes two parameters, i.e., an
         * Object + offset pair, and creates and AddNode that computes the absolute address.
         */
        private static final class ExtractSegmentPair extends Extract {

            private ExtractSegmentPair(Extracted as) {
                super(Objects.requireNonNull(as));
            }

            @Override
            public List<Class<?>> apply(Class<?> parameter) {
                if (parameter != Object.class) {
                    throw new IllegalArgumentException("Expected type " + Object.class + ", got " + parameter);
                }
                return List.of();
            }

            @Override
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments, List<ValueNode> originalArguments, int originalArgumentIndex,
                            Consumer<Node> appendToGraph) {
                assert as != null;
                if (extractedArguments.containsKey(as)) {
                    throw new IllegalStateException("%s was already extracted (%s).".formatted(as, extractedArguments.get(as)));
                }
                ValueNode extracted = ComputeAddressFromSegmentPair.computeAbsolutePointerFromSegmentPair(parameter, originalArguments, originalArgumentIndex, appendToGraph);
                extractedArguments.put(as, extracted);
                return List.of();
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static AbiUtils create() {
        return switch (CABI.current()) {
            case SYS_V -> new ABIs.SysV();
            case WIN_64 -> new ABIs.Win64();
            case MAC_OS_AARCH_64 -> new ABIs.MacOsAArch64();
            case LINUX_AARCH_64 -> new ABIs.LinuxAArch64();
            default -> new ABIs.Unsupported(CABI.current().name());
        };
    }

    @Fold
    public static AbiUtils singleton() {
        return ImageSingletons.lookup(AbiUtils.class);
    }

    /**
     * Specifies if a method handle invoked by an upcall stub needs to drop its return value in case
     * of an in-memory return type. See also: {@link java.lang.invoke.MethodHandles#dropReturn} and
     * {@code jdk.internal.foreign.abi.SharedUtils#adaptUpcallForIMR}
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract boolean dropReturn();

    /**
     * Calls method {@code isInMemoryReturn} of the appropriate {@code CallArranger}. This method
     * determines, if a given return type requires an in-memory return on the current platform.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout);

    protected abstract CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options);

    /**
     * This method re-implements a part of the logic from the JDK so that we can get the callee-type
     * (i.e. the ABI low-level type) of a function from its descriptor.
     */
    @Platforms(HOSTED_ONLY.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/java.base/share/classes/jdk/internal/foreign/abi/AbstractLinker.java#L99")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/java.base/share/classes/jdk/internal/foreign/abi/DowncallLinker.java#L71-L85")
    public final NativeEntryPointInfo makeNativeEntrypoint(FunctionDescriptor desc, LinkerOptions linkerOptions) {
        // From Linker.downcallHandle implemented in AbstractLinker.downcallHandle:
        // From AbstractLinker.downcallHandle0
        MethodType type = desc.toMethodType();

        // makeCallingSequence calls platform specific code
        var callingSequence = makeCallingSequence(type, desc, false, linkerOptions);

        // From DowncallLinker.getBoundMethodHandle
        var argMoveBindings = ABIs.Downcalls.argMoveBindingsStream(callingSequence).toArray(Binding.VMStore[]::new);
        var argMoves = ABIs.Downcalls.toStorageArray(argMoveBindings);
        var returnMoves = ABIs.Downcalls.toStorageArray(ABIs.Downcalls.retMoveBindings(callingSequence));
        var boundaryType = callingSequence.calleeMethodType();
        var needsReturnBuffer = callingSequence.needsReturnBuffer();

        // From NativeEntrypoint.make
        return NativeEntryPointInfo.make(argMoves, returnMoves, boundaryType, needsReturnBuffer, callingSequence.capturedStateMask(), callingSequence.needsTransition(),
                        linkerOptions.allowsHeapAccess());
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/foreign/abi/AbstractLinker.java#L126")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/java.base/share/classes/jdk/internal/foreign/abi/UpcallLinker.java#L62-L110")
    public final JavaEntryPointInfo makeJavaEntryPoint(FunctionDescriptor desc, LinkerOptions linkerOptions) {
        // Linker.upcallStub implemented in AbstractLinker.upcallStub
        MethodType type = desc.toMethodType();

        // From CallArranger.arrangeUpcall
        var callingSequence = makeCallingSequence(type, desc, true, linkerOptions);

        // From SharedUtil.arrangeUpcallHelper
        // From UpcallLinker.makeFactory
        Binding.VMLoad[] argMoves = ABIs.Upcalls.argMoveBindings(callingSequence);
        Binding.VMStore[] retMoves = ABIs.Upcalls.retMoveBindings(callingSequence);
        VMStorage[] args = Arrays.stream(argMoves).map(Binding.Move::storage).toArray(VMStorage[]::new);
        VMStorage[] rets = Arrays.stream(retMoves).map(Binding.Move::storage).toArray(VMStorage[]::new);
        Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs cr = new Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs(args, rets);

        return JavaEntryPointInfo.make(callingSequence.callerMethodType(), cr, callingSequence.needsReturnBuffer(), callingSequence.returnBufferSize());
    }

    /**
     * Generate a register allocation for SubstrateVM from the one generated by and for HotSpot.
     */
    public abstract AssignedLocation[] toMemoryAssignment(VMStorage[] moves, boolean forReturn);

    /**
     * Apply some ABI-specific transformations to an entrypoint (info) and arguments intended to be
     * used to call said entrypoint.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public final Adapter.Result.FullNativeAdaptation adapt(List<ValueNode> arguments, NativeEntryPointInfo nep) {
        return Adapter.adaptToNative(this, generateAdaptations(nep), arguments, nep);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final Adapter.Result.TypeAdaptation adapt(JavaEntryPointInfo jep) {
        return Adapter.adaptFromNative(this, generateAdaptations(jep), jep);
    }

    /**
     * Generate additional argument adaptations which are not done by HotSpot.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/java.base/share/classes/jdk/internal/foreign/abi/CallingSequenceBuilder.java#L103-L147")
    @Platforms(Platform.HOSTED_ONLY.class)
    protected List<Adapter.Adaptation> generateAdaptations(NativeEntryPointInfo nep) {
        List<Adapter.Adaptation> adaptations = new ArrayList<>(Collections.nCopies(nep.methodType().parameterCount(), null));
        int current = 0;
        if (nep.needsReturnBuffer()) {
            adaptations.set(current++, Adapter.check(long.class));
        }
        adaptations.set(current++, Adapter.extract(Adapter.Extracted.CallTarget, long.class));

        // Special handling in case Linker.Option.critical(true) is passed.
        // See the doc of class Adapter.ComputeAddressFromSegmentPair
        var storages = nep.parametersAssignment();

        /*
         * It is possible to combine linker options 'captureCallState(...)' and 'critical(true)'.
         * This means that one can pass a heap memory segment as capture buffer address.
         */
        if (nep.capturesCallState()) {
            if (nep.allowHeapAccess()) {
                VMError.guarantee(storages[current] != null && storages[current + 1] == null);
                // consumes two parameters (i.e. object + offset pair)
                handleCriticalWithHeapAccess(nep, current + 1, adaptations, Adapter.extractSegmentPair(Adapter.Extracted.CaptureBufferAddress));
                current += 2;
            } else {
                adaptations.set(current, Adapter.extract(Adapter.Extracted.CaptureBufferAddress, long.class));
                current++;
            }
        }

        for (int i = current; i < storages.length; ++i) {
            var storage = storages[i];
            if (storage == null) {
                handleCriticalWithHeapAccess(nep, i, adaptations, Adapter.computeAddressFromSegmentPair());
            }
        }

        return adaptations;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/java.base/share/classes/jdk/internal/foreign/abi/x64/sysv/CallArranger.java#L280-L290")
    @Platforms(Platform.HOSTED_ONLY.class)
    private static void handleCriticalWithHeapAccess(NativeEntryPointInfo nep, int i, List<Adaptation> adaptations, Adaptation adaptation) {
        VMError.guarantee(nep.allowHeapAccess(), "A storage may only be null when the Linker.Option.critical(true) option is passed.");
        VMError.guarantee(
                        JavaKind.fromJavaClass(nep.methodType().parameterArray()[i]) == JavaKind.Long &&
                                        JavaKind.fromJavaClass(nep.methodType().parameterArray()[i - 1]) == JavaKind.Object,
                        """
                                        Storage is null, but the other parameters are inconsistent.
                                        Storage may be null only if its kind is Long and previous kind is Object.
                                        See jdk/internal/foreign/abi/x64/sysv/CallArranger.java:286""");
        VMError.guarantee(
                        adaptations.get(i) == null && adaptations.get(i - 1) == null,
                        "This parameter already has an adaptation when it should not.");

        adaptations.set(i, Adapter.drop());
        adaptations.set(i - 1, adaptation);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected List<Adapter.Adaptation> generateAdaptations(JavaEntryPointInfo jep) {
        List<Adapter.Adaptation> adaptations = new ArrayList<>(Collections.nCopies(jep.handleType().parameterCount(), null));
        if (jep.buffersReturn()) {
            adaptations.set(0, Adapter.drop());
        }
        return adaptations;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract void checkLibrarySupport();

    /**
     * Backport the {@link Linker} method of the same name introduced in JDK22. TODO: replace by
     * said method once possible
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract Map<String, MemoryLayout> canonicalLayouts();

    public record Registers(Register methodHandle, Register isolate) {
    }

    public abstract Registers upcallSpecialArgumentsRegisters();

    public abstract int trampolineSize();

    public static class TrampolineTemplate {

        private final byte[] assemblyTemplate;

        /*
         * These fields will only be filled after the analysis, when an assembler is available.
         * Prevent optimizations that constant-fold these fields already during analysis.
         */

        @UnknownPrimitiveField(availability = AfterAnalysis.class) //
        private int isolateOffset;
        @UnknownPrimitiveField(availability = AfterAnalysis.class) //
        private int methodHandleOffset;
        @UnknownPrimitiveField(availability = AfterAnalysis.class) //
        private int stubOffset;

        public TrampolineTemplate(byte[] assemblyTemplate) {
            this.assemblyTemplate = assemblyTemplate;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public void setTemplate(byte[] code, int isolateOff, int methodHandleOff, int stubOff) {
            assert code.length == this.assemblyTemplate.length;
            System.arraycopy(code, 0, this.assemblyTemplate, 0, this.assemblyTemplate.length);
            this.isolateOffset = isolateOff;
            this.methodHandleOffset = methodHandleOff;
            this.stubOffset = stubOff;
        }

        public Pointer write(Pointer at, Isolate isolate, Word methodHandle, Word stubPointer) {
            for (int i = 0; i < assemblyTemplate.length; ++i) {
                at.writeByte(i, assemblyTemplate[i]);
            }

            at.writeWord(isolateOffset, isolate);
            at.writeWord(methodHandleOffset, methodHandle);
            at.writeWord(stubOffset, stubPointer);

            return at.add(assemblyTemplate.length);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    abstract void generateTrampolineTemplate(SubstrateBackendWithAssembler<?> backend, TrampolineTemplate template);
}

class ABIs {
    static final class Unsupported extends AbiUtils {
        private final String name;

        Unsupported(String name) {
            this.name = name;
        }

        private <Z> Z fail() {
            throw unsupportedFeature(name());
        }

        private String name() {
            return "Unsupported ABI: " + name;
        }

        @Override
        protected CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options) {
            return fail();
        }

        @Override
        public AssignedLocation[] toMemoryAssignment(VMStorage[] moves, boolean forReturn) {
            return fail();
        }

        @Override
        @Platforms(Platform.HOSTED_ONLY.class)
        protected List<Adapter.Adaptation> generateAdaptations(NativeEntryPointInfo nep) {
            return fail();
        }

        @Override
        public void checkLibrarySupport() {
        }

        @Override
        public Map<String, MemoryLayout> canonicalLayouts() {
            return fail();
        }

        @Override
        public Registers upcallSpecialArgumentsRegisters() {
            return fail();
        }

        @Override
        public int trampolineSize() {
            return fail();
        }

        @Override
        public void generateTrampolineTemplate(SubstrateBackendWithAssembler<?> backend, TrampolineTemplate template) {
            fail();
        }

        @Override
        public boolean dropReturn() {
            return fail();
        }

        @Override
        public boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
            return fail();
        }
    }

    @BasedOnJDKClass(AArch64Architecture.class)
    @BasedOnJDKClass(jdk.internal.foreign.abi.DowncallLinker.class)
    @BasedOnJDKClass(jdk.internal.foreign.abi.UpcallLinker.class)
    abstract static class ARM64 extends AbiUtils {

        @Platforms(Platform.HOSTED_ONLY.class) //
        private static final Method IS_IN_MEMORY_RETURN = ReflectionUtil.lookupMethod(jdk.internal.foreign.abi.aarch64.CallArranger.class, "isInMemoryReturn", Optional.class);

        @Override
        public Registers upcallSpecialArgumentsRegisters() {
            return new Registers(SubstrateAArch64MacroAssembler.scratch1, SubstrateAArch64MacroAssembler.scratch2);
        }

        @Override
        public AssignedLocation[] toMemoryAssignment(VMStorage[] argMoves, boolean forReturn) {
            AssignedLocation[] storages = new AssignedLocation[argMoves.length];
            int i = 0;
            for (VMStorage move : argMoves) {
                if (move == null) {
                    storages[i++] = AssignedLocation.placeholder();
                    continue;
                }
                storages[i++] = switch (move.type()) {
                    case AArch64Architecture.StorageType.PLACEHOLDER -> AssignedLocation.placeholder();
                    case AArch64Architecture.StorageType.INTEGER -> {
                        Register reg = AArch64.cpuRegisters.get(move.indexOrOffset());
                        assert reg.name.equals(move.debugName());
                        assert reg.getRegisterCategory().equals(AArch64.CPU);
                        yield AssignedLocation.forRegister(reg, JavaKind.Long);
                    }
                    case AArch64Architecture.StorageType.VECTOR -> {
                        Register reg = AArch64.simdRegisters.get(move.indexOrOffset());
                        assert reg.name.equals(move.debugName());
                        assert reg.getRegisterCategory().equals(AArch64.SIMD);
                        yield AssignedLocation.forRegister(reg, JavaKind.Double);
                    }
                    case AArch64Architecture.StorageType.STACK -> AssignedLocation.forStack(move.indexOrOffset());
                    default -> throw unsupportedFeature("Unhandled VMStorage: " + move);
                };
            }
            assert i == storages.length;

            return storages;
        }

        @Override
        public Map<String, MemoryLayout> canonicalLayouts() {
            return SharedUtils.canonicalLayouts(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
        }

        @Override
        public int trampolineSize() {
            return 64;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        @Override
        public void generateTrampolineTemplate(SubstrateBackendWithAssembler<?> backend, TrampolineTemplate template) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) backend.createAssemblerNoOptions();

            Register mhRegister = upcallSpecialArgumentsRegisters().methodHandle();
            Register isolateRegister = upcallSpecialArgumentsRegisters().isolate();

            Label loadIsolate = new Label();
            masm.jmp(loadIsolate);
            int posIsolate = masm.position();
            masm.emitLong(0x1111_2222_3333_4444L);
            int posMHArray = masm.position();
            masm.emitLong(0x5555_6666_7777_8888L);
            int posCallTarget = masm.position();
            masm.emitLong(0x9999_aaaa_bbbb_ccccL);

            masm.bind(loadIsolate);
            /* r10 contains the isolate address */
            masm.ldr(64, isolateRegister, AArch64Address.createPCLiteralAddress(64, posIsolate - masm.position()));

            masm.ldr(64, mhRegister, AArch64Address.createPCLiteralAddress(64, posMHArray - masm.position()));
            /* r9 contains the method handle */
            masm.ldr(64, mhRegister, AArch64Address.createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, mhRegister, 0));

            /*
             * NOTE: do not use r8, it's part of the CallArranger ABI ("indirect result register"),
             * also do not use scratch registers (r9/r10 on SVM).
             */
            Register scratch = AArch64.r11;
            assert !scratch.equals(mhRegister) && !scratch.equals(isolateRegister);
            masm.ldr(64, scratch, AArch64Address.createPCLiteralAddress(64, posCallTarget - masm.position()));
            /* deref it */
            masm.ldr(64, scratch, AArch64Address.createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, scratch, 0));

            /* jump into the target */
            masm.jmp(scratch);

            assert trampolineSize() >= masm.position();

            byte[] assembly = masm.closeAligned(true, trampolineSize());
            assert assembly.length == trampolineSize();

            template.setTemplate(assembly, posIsolate, posMHArray, posCallTarget);
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/foreign/abi/aarch64/CallArranger.java#L195")
        @Override
        public boolean dropReturn() {
            return true;
        }

        @Override
        public boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
            return ReflectionUtil.invokeMethod(IS_IN_MEMORY_RETURN, null, returnLayout);
        }
    }

    @BasedOnJDKClass(jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker.class)
    @BasedOnJDKClass(jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64CallArranger.class)
    static final class LinuxAArch64 extends ARM64 {

        @Override
        protected CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options) {
            return jdk.internal.foreign.abi.aarch64.CallArranger.LINUX.getBindings(type, desc, forUpcall, options).callingSequence();
        }

        @Override
        public void checkLibrarySupport() {
            String name = "Linux AArch64";
            VMError.guarantee(LibC.isSupported(), "Foreign functions feature requires LibC support on %s", name);
        }
    }

    @BasedOnJDKClass(jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker.class)
    @BasedOnJDKClass(jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64CallArranger.class)
    static final class MacOsAArch64 extends ARM64 {

        @Override
        protected CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options) {
            return jdk.internal.foreign.abi.aarch64.CallArranger.MACOS.getBindings(type, desc, forUpcall, options).callingSequence();
        }

        @Override
        public void checkLibrarySupport() {
            String name = "Darwin AArch64";
            VMError.guarantee(LibC.isSupported(), "Foreign functions feature requires LibC support on %s", name);
        }
    }

    @BasedOnJDKClass(X86_64Architecture.class)
    @BasedOnJDKClass(jdk.internal.foreign.abi.DowncallLinker.class)
    @BasedOnJDKClass(jdk.internal.foreign.abi.UpcallLinker.class)
    abstract static class X86_64 extends AbiUtils {

        @Override
        protected abstract CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options);

        @Override
        public AssignedLocation[] toMemoryAssignment(VMStorage[] argMoves, boolean forReturn) {
            for (VMStorage move : argMoves) {
                if (move == null) {
                    continue;
                }
                switch (move.type()) {
                    case X86_64Architecture.StorageType.X87 ->
                        throw unsupportedFeature("Unsupported register kind: X87");
                    case X86_64Architecture.StorageType.STACK -> {
                        if (forReturn) {
                            throw unsupportedFeature("Unsupported register kind for return: STACK");
                        }
                    }
                    default -> {
                    }
                }
            }

            AssignedLocation[] storages = new AssignedLocation[argMoves.length];
            int i = 0;
            for (VMStorage move : argMoves) {
                if (move == null) {
                    storages[i++] = AssignedLocation.placeholder();
                    continue;
                }
                storages[i++] = switch (move.type()) {
                    case X86_64Architecture.StorageType.PLACEHOLDER -> AssignedLocation.placeholder();
                    case X86_64Architecture.StorageType.INTEGER -> {
                        Register reg = AMD64.cpuRegisters.get(move.indexOrOffset());
                        assert reg.name.equals(move.debugName());
                        assert reg.getRegisterCategory().equals(AMD64.CPU);
                        yield AssignedLocation.forRegister(reg, JavaKind.Long);
                    }
                    case X86_64Architecture.StorageType.VECTOR -> {
                        /*
                         * Only the first four xmm registers should ever be used; in particular,
                         * this means we never need to index in xmmRegistersAVX512
                         */
                        Register reg = AMD64.xmmRegistersSSE.get(move.indexOrOffset());
                        assert reg.name.equals(move.debugName());
                        assert reg.getRegisterCategory().equals(AMD64.XMM);
                        yield AssignedLocation.forRegister(reg, JavaKind.Double);
                    }
                    case X86_64Architecture.StorageType.STACK -> AssignedLocation.forStack(move.indexOrOffset());
                    default -> throw unsupportedFeature("Unhandled VMStorage: " + move);
                };
            }
            assert i == storages.length;

            return storages;
        }

        @Override
        public Registers upcallSpecialArgumentsRegisters() {
            return new Registers(AMD64.r10, AMD64.r11);
        }

        @Override
        public int trampolineSize() {
            return 128;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        @Override
        public void generateTrampolineTemplate(SubstrateBackendWithAssembler<?> backend, TrampolineTemplate template) {
            // Generate the trampoline
            AMD64MacroAssembler asm = (AMD64MacroAssembler) backend.createAssemblerNoOptions();
            var odas = new ArrayList<AMD64BaseAssembler.OperandDataAnnotation>(3);
            // Collect the positions of the address in the movq instructions.
            asm.setCodePatchingAnnotationConsumer(ca -> {
                if (ca instanceof AMD64BaseAssembler.OperandDataAnnotation oda) {
                    odas.add(oda);
                }
            });

            Register mhRegister = upcallSpecialArgumentsRegisters().methodHandle();
            Register isolateRegister = upcallSpecialArgumentsRegisters().isolate();

            asm.maybeEmitIndirectTargetMarker();
            /* Store isolate in the assigned register */
            asm.movq(isolateRegister, 0L, true);
            /* r10 points in the mh array */
            asm.movq(mhRegister, 0L, true);
            /* r10 contains the method handle */
            asm.movq(mhRegister, new AMD64Address(mhRegister));
            /* rax contains the stub address */
            asm.movq(rax, 0L, true);
            /* executes the stub */
            if (SubstrateControlFlowIntegrity.useSoftwareCFI()) {
                asm.movq(rax, new AMD64Address(rax, 0));
                asm.jmp(rax);
            } else {
                asm.jmp(new AMD64Address(rax, 0));
            }

            assert trampolineSize() - asm.position() >= 0;

            byte[] assembly = asm.closeAligned(true, trampolineSize());
            assert assembly.length == trampolineSize();
            assert odas.size() == 3;
            assert odas.stream().allMatch(oda -> oda.operandSize == 8);

            template.setTemplate(assembly, odas.get(0).operandPosition, odas.get(1).operandPosition, odas.get(2).operandPosition);
        }
    }

    @BasedOnJDKClass(jdk.internal.foreign.abi.x64.sysv.SysVx64Linker.class)
    @BasedOnJDKClass(jdk.internal.foreign.abi.x64.sysv.CallArranger.class)
    static final class SysV extends X86_64 {

        @Platforms(Platform.HOSTED_ONLY.class) //
        private static final Method IS_IN_MEMORY_RETURN = ReflectionUtil.lookupMethod(jdk.internal.foreign.abi.x64.sysv.CallArranger.class, "isInMemoryReturn", Optional.class);

        @Override
        protected CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options) {
            return jdk.internal.foreign.abi.x64.sysv.CallArranger.getBindings(type, desc, forUpcall, options).callingSequence();
        }

        @Override
        @Platforms(Platform.HOSTED_ONLY.class)
        protected List<Adapter.Adaptation> generateAdaptations(NativeEntryPointInfo nep) {
            var adaptations = super.generateAdaptations(nep);
            var assignments = nep.parametersAssignment();

            if (assignments.length > 0) {
                final int last = assignments.length - 1;
                var lastAssignment = assignments[last];
                // assignments may be null when Linker.Option.critical(true) is used.
                // See docs from ComputeAddressFromSegmentPair.
                if (lastAssignment != null && lastAssignment.equals(X86_64Architecture.Regs.rax)) {
                    /*
                     * This branch is only taken when the function is variadic, that is when rax is
                     * passed as an additional pseudo-parameter, where it will contain the number of
                     * XMM registers passed as arguments. However, we need to remove the rax
                     * assignment since rax will already be assigned separately in
                     * SubstrateAMD64RegisterConfig.getCallingConvention and later used in
                     * SubstrateAMD64NodeLIRBuilder.visitInvokeArguments.
                     */
                    adaptations.set(last, Adapter.drop());
                }
            }
            return adaptations;
        }

        @Override
        @Platforms(Platform.HOSTED_ONLY.class)
        public void checkLibrarySupport() {
            String name = "SystemV (Linux AMD64)";
            VMError.guarantee(LibC.isSupported(), "Foreign functions feature requires LibC support on %s", name);
        }

        @Override
        public Map<String, MemoryLayout> canonicalLayouts() {
            return SharedUtils.canonicalLayouts(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/foreign/abi/x64/sysv/CallArranger.java#L147")
        @Override
        public boolean dropReturn() {
            return true;
        }

        @Override
        public boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
            return ReflectionUtil.invokeMethod(IS_IN_MEMORY_RETURN, null, returnLayout);
        }
    }

    @BasedOnJDKClass(jdk.internal.foreign.abi.x64.windows.Windowsx64Linker.class)
    @BasedOnJDKClass(jdk.internal.foreign.abi.x64.windows.CallArranger.class)
    static final class Win64 extends X86_64 {

        @Platforms(Platform.HOSTED_ONLY.class) //
        private static final Method IS_IN_MEMORY_RETURN = ReflectionUtil.lookupMethod(jdk.internal.foreign.abi.x64.windows.CallArranger.class, "isInMemoryReturn", Optional.class);

        @Override
        protected CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options) {
            return jdk.internal.foreign.abi.x64.windows.CallArranger.getBindings(type, desc, forUpcall, options).callingSequence();
        }

        /**
         * The Win64 ABI allows one mismatch between register and value type: When a variadic
         * floating-point argument is among the first four parameters of the function, the argument
         * should be passed in both the XMM and CPU register.
         * <p>
         * This method is slightly cheating: technically, we only ever want to adapt the
         * cpu-register-assigned copy of a "register assigned floating point vararg parameter" from
         * floating-point to long. This method assumes that this case will be the only source
         * assignments of float/double parameters to a cpu register.
         */
        @Override
        @Platforms(Platform.HOSTED_ONLY.class)
        protected List<Adapter.Adaptation> generateAdaptations(NativeEntryPointInfo nep) {
            List<Adapter.Adaptation> adaptations = super.generateAdaptations(nep);

            AMD64 target = (AMD64) ImageSingletons.lookup(SubstrateTargetDescription.class).arch;
            boolean previousMatched = false;
            PlatformKind previousKind = null;
            for (int i = adaptations.size() - 1; i >= 0; --i) {
                PlatformKind kind = target.getPlatformKind(JavaKind.fromJavaClass(nep.methodType().parameterType(i)));
                if ((kind.equals(target.getPlatformKind(JavaKind.Float)) || kind.equals(target.getPlatformKind(JavaKind.Double))) &&
                                nep.parametersAssignment()[i].type() == X86_64Architecture.StorageType.INTEGER) {
                    assert Objects.equals(previousKind, kind) && previousMatched;
                    assert adaptations.get(i) == null;
                    adaptations.set(i, Adapter.reinterpret(JavaKind.Long));
                    previousMatched = false;
                } else {
                    previousMatched = true;
                }
                previousKind = kind;
            }

            return adaptations;
        }

        @Override
        @Platforms(Platform.HOSTED_ONLY.class)
        public void checkLibrarySupport() {
            String name = "Win64 (Windows AMD64)";
            VMError.guarantee(LibC.isSupported(), "Foreign functions feature requires LibC support on %s", name);
            VMError.guarantee(WindowsAPIs.isSupported(), "Foreign functions feature requires Windows APIs support on %s", name);
        }

        @Override
        public Map<String, MemoryLayout> canonicalLayouts() {
            return SharedUtils.canonicalLayouts(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_CHAR);
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/foreign/abi/x64/windows/CallArranger.java#L139")
        @Override
        public boolean dropReturn() {
            return false;
        }

        @Override
        public boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
            return ReflectionUtil.invokeMethod(IS_IN_MEMORY_RETURN, null, returnLayout);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+11/src/java.base/share/classes/jdk/internal/foreign/abi/DowncallLinker.java#L122-L140")
    static class Downcalls {
        protected static Stream<Binding.VMStore> argMoveBindingsStream(CallingSequence callingSequence) {
            return callingSequence.argumentBindings()
                            .filter(Binding.VMStore.class::isInstance)
                            .map(Binding.VMStore.class::cast);
        }

        protected static Stream<Binding.VMLoad> retMoveBindingsStream(CallingSequence callingSequence) {
            return callingSequence.returnBindings().stream()
                            .filter(Binding.VMLoad.class::isInstance)
                            .map(Binding.VMLoad.class::cast);
        }

        protected static Binding.VMLoad[] retMoveBindings(CallingSequence callingSequence) {
            return retMoveBindingsStream(callingSequence).toArray(Binding.VMLoad[]::new);
        }

        static VMStorage[] toStorageArray(Binding.Move[] moves) {
            return Arrays.stream(moves).map(Binding.Move::storage).toArray(VMStorage[]::new);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+11/src/java.base/share/classes/jdk/internal/foreign/abi/UpcallLinker.java#L124-L134")
    static class Upcalls {
        static Binding.VMLoad[] argMoveBindings(CallingSequence callingSequence) {
            return callingSequence.argumentBindings()
                            .filter(Binding.VMLoad.class::isInstance)
                            .map(Binding.VMLoad.class::cast)
                            .toArray(Binding.VMLoad[]::new);
        }

        static Binding.VMStore[] retMoveBindings(CallingSequence callingSequence) {
            return callingSequence.returnBindings().stream()
                            .filter(Binding.VMStore.class::isInstance)
                            .map(Binding.VMStore.class::cast)
                            .toArray(Binding.VMStore[]::new);
        }
    }
}
