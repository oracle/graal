package com.oracle.svm.core.panama;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.abi.x64.X86_64Architecture;
import jdk.internal.foreign.abi.x64.sysv.CallArranger;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;
import static jdk.internal.foreign.abi.x64.sysv.CallArranger.getBindings;

@TargetClass(jdk.internal.foreign.abi.NativeEntryPoint.class)
@Substitute
public final class Target_jdk_internal_foreign_abi_NativeEntrypoint {
    private final MethodType methodType;
    private final SubstrateCallingConventionType.MemoryAssignment[] parameterAssignments;
    private final SubstrateCallingConventionType.MemoryAssignment[] returnBuffering;

    public Target_jdk_internal_foreign_abi_NativeEntrypoint(MethodType methodType, SubstrateCallingConventionType.MemoryAssignment[] cc, SubstrateCallingConventionType.MemoryAssignment[] returnBuffering) {
        this.methodType = methodType;
        this.parameterAssignments = cc;
        this.returnBuffering = returnBuffering;
    }

    private static void checkType(MethodType methodType, boolean needsReturnBuffer, int savedValueMask) {
        if (methodType.parameterType(0) != long.class) {
            throw new AssertionError("Address expected as first param: " + methodType);
        }
        int checkIdx = 1;
        if ((needsReturnBuffer && methodType.parameterType(checkIdx++) != long.class)
                || (savedValueMask != 0 && methodType.parameterType(checkIdx) != long.class)) {
            throw new AssertionError("return buffer and/or preserved value address expected: " + methodType);
        }
    }

    @Substitute
    public static Target_jdk_internal_foreign_abi_NativeEntrypoint make(ABIDescriptor abi,
                                                                        VMStorage[] argMoves, VMStorage[] returnMoves,
                                                                        MethodType methodType,
                                                                        boolean needsReturnBuffer,
                                                                        int capturedStateMask) {
        if (returnMoves.length > 1 != needsReturnBuffer) {
            throw new AssertionError("Multiple register return, but needsReturnBuffer was false");
        }

        checkType(methodType, needsReturnBuffer, capturedStateMask);
        var parametersAssignment = AbiUtils.getInstance().toMemoryAssignment(argMoves, false);
        var returnBuffering = needsReturnBuffer ? AbiUtils.getInstance().toMemoryAssignment(returnMoves, true) : null;
        return new Target_jdk_internal_foreign_abi_NativeEntrypoint(methodType, parametersAssignment, returnBuffering);
    }

    public static Target_jdk_internal_foreign_abi_NativeEntrypoint make(FunctionDescriptor desc, Linker.Option... options) {
        return AbiUtils.getInstance().makeEntryPoint(desc, options);
    }

    @Substitute
    private MethodType type() {
        return this.methodType;
    }

    public MethodType methodType() {
        return this.methodType;
    }

    public boolean needsReturnBuffer() {
        return this.returnBuffering != null;
    }

    public SubstrateCallingConventionType.MemoryAssignment[] parametersAssignment() {
        return parameterAssignments;
    }

    public SubstrateCallingConventionType.MemoryAssignment[] returnsAssignment() {
        return returnBuffering;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Target_jdk_internal_foreign_abi_NativeEntrypoint that = (Target_jdk_internal_foreign_abi_NativeEntrypoint) o;
        return methodType.equals(that.methodType) && Arrays.equals(parameterAssignments, that.parameterAssignments) && Arrays.equals(returnBuffering, that.returnBuffering);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(methodType);
        result = 31 * result + Arrays.hashCode(parameterAssignments);
        result = 31 * result + Arrays.hashCode(returnBuffering);
        return result;
    }
}

abstract class AbiUtils {
    private static abstract class X86_64 extends AbiUtils {

        @Override
        SubstrateCallingConventionType.MemoryAssignment[] toMemoryAssignment(VMStorage[] argMoves, boolean forReturn) {
            // See jdk.internal.foreign.abi.x64.X86_64Architecture
            int size = 0;
            for (VMStorage move: argMoves) {
                // Placeholders are ignored. They will be handled further down the line
                size += move.type() != X86_64Architecture.StorageType.PLACEHOLDER ? 1 : 0;
                if (move.type() == X86_64Architecture.StorageType.X87) {
                    throw unsupportedFeature("Unsupported register kind: X87");
                }
                if (move.type() == X86_64Architecture.StorageType.STACK && forReturn) {
                    throw unsupportedFeature("Unsupported register kind for return: STACK");
                }
            }

            SubstrateCallingConventionType.MemoryAssignment[] storages = new SubstrateCallingConventionType.MemoryAssignment[size];
            int i = 0;
            for (VMStorage move: argMoves) {
                if (move.type() != X86_64Architecture.StorageType.PLACEHOLDER) {
                    SubstrateCallingConventionType.MemoryAssignment.Kind kind = switch (move.type()) {
                        case X86_64Architecture.StorageType.INTEGER -> SubstrateCallingConventionType.MemoryAssignment.Kind.INTEGER;
                        case X86_64Architecture.StorageType.VECTOR -> SubstrateCallingConventionType.MemoryAssignment.Kind.FLOAT;
                        case X86_64Architecture.StorageType.STACK -> SubstrateCallingConventionType.MemoryAssignment.Kind.STACK;
                        default -> throw unsupportedFeature("Unhandled VMStorage: " + move);
                    };
                    storages[i++] = new SubstrateCallingConventionType.MemoryAssignment(kind, move.indexOrOffset());
                }
            }

            return storages;
        }
    };

    private static final AbiUtils SysV = new X86_64() {
        private static Stream<Binding.VMStore> argMoveBindingsStream(CallingSequence callingSequence) {
            return callingSequence.argumentBindings()
                    .filter(Binding.VMStore.class::isInstance)
                    .map(Binding.VMStore.class::cast);
        }

        private static Stream<Binding.VMLoad> retMoveBindingsStream(CallingSequence callingSequence) {
            return callingSequence.returnBindings().stream()
                    .filter(Binding.VMLoad.class::isInstance)
                    .map(Binding.VMLoad.class::cast);
        }

        private static Binding.VMLoad[] retMoveBindings(CallingSequence callingSequence) {
            return retMoveBindingsStream(callingSequence).toArray(Binding.VMLoad[]::new);
        }

        private VMStorage[] toStorageArray(Binding.Move[] moves) {
            return Arrays.stream(moves).map(Binding.Move::storage).toArray(VMStorage[]::new);
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        @Override
        Target_jdk_internal_foreign_abi_NativeEntrypoint makeEntryPoint(FunctionDescriptor desc, Linker.Option... options) {
            // From CallArranger.arrangeDowncall
            MethodType type = desc.toMethodType();
            CallArranger.Bindings bindings = getBindings(type, desc, false, LinkerOptions.forDowncall(desc, options));

            // From DowncallLinker.getBoundMethodHandle
            var argMoves = argMoveBindingsStream(bindings.callingSequence()).toArray(Binding.VMStore[]::new);
            var retMoves = retMoveBindings(bindings.callingSequence());

            return Target_jdk_internal_foreign_abi_NativeEntrypoint.make(
                    null, // The argument is unused, so no need to get it through reflection
                    toStorageArray(argMoves),
                    toStorageArray(retMoves),
                    bindings.callingSequence().calleeMethodType(),
                    bindings.callingSequence().needsReturnBuffer(),
                    bindings.callingSequence().capturedStateMask()
            );
        }
    };
    public static AbiUtils getInstance() {
//        switch (SubstrateUtil.getArchitectureName()) {
//            case "amd64": return X86_64;
//            default: throw unsupportedFeature("Unsupported architecture: " + SubstrateUtil.getArchitectureName());
//        }
        return SysV;
    }

    /**
     * Generate a register allocation for SubstrateVM from the one generated by and for Panama Foreign/HotSpot.
     */
    abstract SubstrateCallingConventionType.MemoryAssignment[] toMemoryAssignment(VMStorage[] moves, boolean forReturn);

    /**
     * This method re-implements a part of the logic from the JDK so that we can get the
     * callee-type (i.e. C type) of a function from its descriptor.
     * Note that this process is ABI (i.e. architecture and OS)  dependant.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    abstract Target_jdk_internal_foreign_abi_NativeEntrypoint makeEntryPoint(FunctionDescriptor desc, Linker.Option... options);
}