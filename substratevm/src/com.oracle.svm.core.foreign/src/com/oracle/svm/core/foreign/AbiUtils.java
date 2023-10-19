/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jdk.compiler.graal.api.replacements.Fold;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.ReinterpretNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.headers.WindowsAPIs;
import com.oracle.svm.core.util.VMError;

import jdk.internal.foreign.CABI;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.abi.x64.X86_64Architecture;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Utils for ABI specific functionalities in the context of the Java Foreign API. Provides methods
 * to transform JDK-internal data-structures into SubstrateVM ones.
 */
public abstract class AbiUtils {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class Adapter {
        private static boolean allEqual(int reference, int... values) {
            return Arrays.stream(values).allMatch(v -> v == reference);
        }

        private Adapter() {
        }

        public enum Extracted {
            CallTarget,
            CaptureBufferAddress
        }

        public record AdaptationResult(
                        Map<Extracted, ValueNode> extractedArguments,
                        List<ValueNode> arguments,
                        List<AssignedLocation> parametersAssignment,
                        List<AssignedLocation> returnsAssignment,
                        MethodType callType) {
            public ValueNode getArgument(Extracted id) {
                return extractedArguments.get(id);
            }
        }

        public static AdaptationResult adapt(AbiUtils self, List<Adaptation> adaptations, List<ValueNode> originalArguments, NativeEntryPointInfo nep) {
            AssignedLocation[] originalAssignment = self.toMemoryAssignment(nep.parametersAssignment(), false);
            VMError.guarantee(allEqual(adaptations.size(), originalArguments.size(), nep.methodType().parameterCount(), originalAssignment.length));

            Map<Extracted, ValueNode> extractedArguments = new EnumMap<>(Extracted.class);
            List<ValueNode> arguments = new ArrayList<>();
            List<AssignedLocation> assignment = new ArrayList<>();
            List<Class<?>> argumentTypes = new ArrayList<>();

            for (int i = 0; i < adaptations.size(); ++i) {
                Adaptation adaptation = adaptations.get(i);
                if (adaptation == null) {
                    adaptation = NOOP;
                }

                arguments.addAll(adaptation.apply(originalArguments.get(i), extractedArguments));
                assignment.addAll(adaptation.apply(originalAssignment[i]));
                argumentTypes.addAll(adaptation.apply(nep.methodType().parameterType(i)));

                VMError.guarantee(allEqual(arguments.size(), assignment.size(), argumentTypes.size()));
            }

            // Sanity checks
            VMError.guarantee(extractedArguments.containsKey(Extracted.CallTarget));
            VMError.guarantee(!nep.capturesCallState() || extractedArguments.containsKey(Extracted.CaptureBufferAddress));
            for (int i = 0; i < arguments.size(); ++i) {
                VMError.guarantee(arguments.get(i) != null);
                VMError.guarantee(!assignment.get(i).isPlaceholder() || (i == 0 && nep.needsReturnBuffer()));
            }

            return new AdaptationResult(extractedArguments, arguments, assignment, Arrays.stream(self.toMemoryAssignment(nep.returnsAssignment(), true)).toList(),
                            MethodType.methodType(nep.methodType().returnType(), argumentTypes));
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

            public abstract List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments);
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
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments) {
                return List.of(parameter);
            }
        };

        public static Adaptation check(Class<?> type) {
            return new CheckType(Objects.requireNonNull(type));
        }

        public static Adaptation extract(Extracted as, Class<?> type) {
            return new Extract(Objects.requireNonNull(as), Objects.requireNonNull(type));
        }

        public static Adaptation drop() {
            return Extract.DROP;
        }

        public static Adaptation reinterpret(JavaKind to) {
            return new Reinterpret(to);
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
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments) {
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
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments) {
                return List.of(ReinterpretNode.reinterpret(to, parameter));
            }
        }

        private static final class Extract extends Adaptation {
            private static Extract DROP = new Extract(null, null);
            private final Extracted as;
            private final Class<?> type;

            private Extract(Extracted as, Class<?> type) {
                this.as = as;
                this.type = type;
            }

            @Override
            public List<Class<?>> apply(Class<?> parameter) {
                if (type != null && parameter != type) {
                    throw new IllegalArgumentException("Expected type " + type + ", got " + parameter);
                }
                return List.of();
            }

            @Override
            public List<AssignedLocation> apply(AssignedLocation parameter) {
                return List.of();
            }

            @Override
            public List<ValueNode> apply(ValueNode parameter, Map<Extracted, ValueNode> extractedArguments) {
                if (as != null) {
                    if (extractedArguments.containsKey(as)) {
                        throw new IllegalStateException("%s was already extracted (%s).".formatted(as, extractedArguments.get(as)));
                    }
                    extractedArguments.put(as, parameter);
                }
                return List.of();
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static AbiUtils create() {
        return switch (CABI.current()) {
            case SYS_V -> new ABIs.SysV();
            case WIN_64 -> new ABIs.Win64();
            default -> new ABIs.Unsupported(CABI.current().name());
        };
    }

    @Fold
    public static AbiUtils singleton() {
        return ImageSingletons.lookup(AbiUtils.class);
    }

    /**
     * This method re-implements a part of the logic from the JDK so that we can get the callee-type
     * (i.e. the ABI low-level type) of a function from its descriptor.
     */
    public abstract NativeEntryPointInfo makeNativeEntrypoint(FunctionDescriptor desc, Linker.Option... options);

    /**
     * Generate a register allocation for SubstrateVM from the one generated by and for HotSpot.
     */
    public abstract AssignedLocation[] toMemoryAssignment(VMStorage[] moves, boolean forReturn);

    /**
     * Apply some ABI-specific transformations to an entrypoint (info) and arguments intended to be
     * used to call said entrypoint.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public final Adapter.AdaptationResult adapt(List<ValueNode> arguments, NativeEntryPointInfo nep) {
        return Adapter.adapt(this, generateAdaptations(nep), arguments, nep);
    }

    /**
     * Generate additional argument adaptations which are not done by HotSpot.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected List<Adapter.Adaptation> generateAdaptations(NativeEntryPointInfo nep) {
        List<Adapter.Adaptation> adaptations = new ArrayList<>(Collections.nCopies(nep.methodType().parameterCount(), null));
        int current = 0;
        if (nep.needsReturnBuffer()) {
            adaptations.set(current++, Adapter.check(long.class));
        }
        adaptations.set(current++, Adapter.extract(Adapter.Extracted.CallTarget, long.class));
        if (nep.capturesCallState()) {
            adaptations.set(current++, Adapter.extract(Adapter.Extracted.CaptureBufferAddress, long.class));
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
        public NativeEntryPointInfo makeNativeEntrypoint(FunctionDescriptor desc, Linker.Option... options) {
            return fail();
        }

        @Override
        public AssignedLocation[] toMemoryAssignment(VMStorage[] moves, boolean forReturn) {
            return fail();
        }

        @Override
        protected List<Adapter.Adaptation> generateAdaptations(NativeEntryPointInfo nep) {
            return fail();
        }

        @Override
        public void checkLibrarySupport() {
            fail();
        }

        @Override
        public Map<String, MemoryLayout> canonicalLayouts() {
            return fail();
        }
    }

    private abstract static class X86_64 extends AbiUtils {
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

            protected static VMStorage[] toStorageArray(Binding.Move[] moves) {
                return Arrays.stream(moves).map(Binding.Move::storage).toArray(VMStorage[]::new);
            }
        }

        protected abstract CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options);

        @Override
        public NativeEntryPointInfo makeNativeEntrypoint(FunctionDescriptor desc, Linker.Option... options) {
            // Linker.downcallHandle implemented in
            // AbstractLinker.downcallHandle

            // AbstractLinker.downcallHandle0
            LinkerOptions optionSet = LinkerOptions.forDowncall(desc, options);
            MethodType type = desc.toMethodType();

            /* OS SPECIFIC BEGINS */
            // AbstractLinker.arrangeDowncall implemented in
            // SysVx64Linker.arrangeDowncall or Windowsx64Linker.arrangeDowncall

            // CallArranger.arrangeDowncall
            var callingSequence = makeCallingSequence(type, desc, false, optionSet);
            /* OS SPECIFIC ENDS */

            // DowncallLinker.getBoundMethodHandle
            var argMoves = Downcalls.toStorageArray(Downcalls.argMoveBindingsStream(callingSequence).toArray(Binding.VMStore[]::new));
            var returnMoves = Downcalls.toStorageArray(Downcalls.retMoveBindings(callingSequence));
            var boundaryType = callingSequence.calleeMethodType();
            var needsReturnBuffer = callingSequence.needsReturnBuffer();

            // NativeEntrypoint.make
            return NativeEntryPointInfo.make(argMoves, returnMoves, boundaryType, needsReturnBuffer, callingSequence.capturedStateMask(), callingSequence.needsTransition());
        }

        @Override
        public AssignedLocation[] toMemoryAssignment(VMStorage[] argMoves, boolean forReturn) {
            for (VMStorage move : argMoves) {
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
                storages[i++] = switch (move.type()) {
                    case X86_64Architecture.StorageType.PLACEHOLDER -> AssignedLocation.placeholder();
                    case X86_64Architecture.StorageType.INTEGER -> {
                        Register reg = AMD64.cpuRegisters[move.indexOrOffset()];
                        assert reg.name.equals(move.debugName());
                        assert reg.getRegisterCategory().equals(AMD64.CPU);
                        yield AssignedLocation.forRegister(reg);
                    }
                    case X86_64Architecture.StorageType.VECTOR -> {
                        /*
                         * Only the first four xmm registers should ever be used; in particular,
                         * this means we never need to index in xmmRegistersAVX512
                         */
                        Register reg = AMD64.xmmRegistersSSE[move.indexOrOffset()];
                        assert reg.name.equals(move.debugName());
                        assert reg.getRegisterCategory().equals(AMD64.XMM);
                        yield AssignedLocation.forRegister(reg);
                    }
                    case X86_64Architecture.StorageType.STACK -> AssignedLocation.forStack(move.indexOrOffset());
                    default -> throw unsupportedFeature("Unhandled VMStorage: " + move);
                };
            }
            assert i == storages.length;

            return storages;
        }

        protected static Map<String, MemoryLayout> canonicalLayouts(ValueLayout longLayout, ValueLayout sizetLayout, ValueLayout wchartLayout) {
            return Map.ofEntries(
                            // specified canonical layouts
                            Map.entry("bool", ValueLayout.JAVA_BOOLEAN),
                            Map.entry("char", ValueLayout.JAVA_BYTE),
                            Map.entry("short", ValueLayout.JAVA_SHORT),
                            Map.entry("int", ValueLayout.JAVA_INT),
                            Map.entry("float", ValueLayout.JAVA_FLOAT),
                            Map.entry("long", longLayout),
                            Map.entry("long long", ValueLayout.JAVA_LONG),
                            Map.entry("double", ValueLayout.JAVA_DOUBLE),
                            Map.entry("void*", ValueLayout.ADDRESS),
                            Map.entry("size_t", sizetLayout),
                            Map.entry("wchar_t", wchartLayout),
                            // unspecified size-dependent layouts
                            Map.entry("int8_t", ValueLayout.JAVA_BYTE),
                            Map.entry("int16_t", ValueLayout.JAVA_SHORT),
                            Map.entry("int32_t", ValueLayout.JAVA_INT),
                            Map.entry("int64_t", ValueLayout.JAVA_LONG),
                            // unspecified JNI layouts
                            Map.entry("jboolean", ValueLayout.JAVA_BOOLEAN),
                            Map.entry("jchar", ValueLayout.JAVA_CHAR),
                            Map.entry("jbyte", ValueLayout.JAVA_BYTE),
                            Map.entry("jshort", ValueLayout.JAVA_SHORT),
                            Map.entry("jint", ValueLayout.JAVA_INT),
                            Map.entry("jlong", ValueLayout.JAVA_LONG),
                            Map.entry("jfloat", ValueLayout.JAVA_FLOAT),
                            Map.entry("jdouble", ValueLayout.JAVA_DOUBLE));
        }
    }

    static final class SysV extends X86_64 {
        @Override
        protected CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options) {
            return jdk.internal.foreign.abi.x64.sysv.CallArranger.getBindings(type, desc, forUpcall, options).callingSequence();
        }

        @Override
        protected List<Adapter.Adaptation> generateAdaptations(NativeEntryPointInfo nep) {
            var adaptations = super.generateAdaptations(nep);
            var assignments = nep.parametersAssignment();

            if (assignments.length > 0) {
                final int last = assignments.length - 1;
                if (assignments[last].equals(X86_64Architecture.Regs.rax)) {
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
            VMError.guarantee(LibC.isSupported(), "Foreign functions feature requires LibC support on " + name);
        }

        @Override
        public Map<String, MemoryLayout> canonicalLayouts() {
            return canonicalLayouts(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
        }
    };

    static final class Win64 extends X86_64 {
        @Override
        protected CallingSequence makeCallingSequence(MethodType type, FunctionDescriptor desc, boolean forUpcall, LinkerOptions options) {
            return jdk.internal.foreign.abi.x64.windows.CallArranger.getBindings(type, desc, forUpcall, options).callingSequence();
        }

        /**
         * The Win64 ABI allows one mismatch between register and value type: When a variadic
         * floating-point argument is among the first four parameters of the function, the argument
         * should be passed in both the XMM and CPU register.
         *
         * This method is slightly cheating: technically, we only ever want to adapt the
         * cpu-register-assigned copy of a "register assigned floating point vararg parameter" from
         * floating-point to long. This method assumes that this case will be the only source
         * assignments of float/double parameters to a cpu register.
         */
        @Override
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
            VMError.guarantee(LibC.isSupported(), "Foreign functions feature requires LibC support on" + name);
            VMError.guarantee(WindowsAPIs.isSupported(), "Foreign functions feature requires Windows APIs support on" + name);
        }

        @Override
        public Map<String, MemoryLayout> canonicalLayouts() {
            return canonicalLayouts(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_CHAR);
        }
    };
}
