package com.oracle.svm.core.foreign;

import static com.oracle.svm.core.foreign.ABIs.X86_64.Downcalls.toStorageArray;
import static com.oracle.svm.core.foreign.ABIs.X86_64.Upcalls.argMoveBindings;
import static com.oracle.svm.core.foreign.ABIs.X86_64.Upcalls.retMoveBindings;
import static java.lang.invoke.MethodHandles.exactInvoker;
import static java.lang.invoke.MethodHandles.insertArguments;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.VMStorage;

@TargetClass(className = "jdk.internal.foreign.abi.UpcallLinker")
@SuppressWarnings("unused")
public final class Target_jdk_internal_foreign_abi_UpcallLinker {

    @Alias public static MethodHandle MH_invokeInterpBindings;

    @Substitute
    static long makeUpcallStub(MethodHandle mh, ABIDescriptor abi, Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs conv,
                    boolean needsReturnBuffer, long returnBufferSize) {
        var info = JavaEntryPointInfo.make(mh, abi, conv, needsReturnBuffer, returnBufferSize);
        return ForeignFunctionsRuntime.singleton().registerForUpcall(mh, info).rawValue();
    }

    @Alias
    private static native void checkPrimitive(MethodType type);

    @Substitute
    public static AbstractLinker.UpcallStubFactory makeFactory(MethodType targetType, ABIDescriptor abi, CallingSequence callingSequence) {
        assert callingSequence.forUpcall();
        Binding.VMLoad[] argMoves = argMoveBindings(callingSequence);
        Binding.VMStore[] retMoves = retMoveBindings(callingSequence);

        MethodType llType = callingSequence.callerMethodType();

        UnaryOperator<MethodHandle> doBindingsMaker;

        Map<VMStorage, Integer> argIndices = Util_jdk_internal_foreign_abi_UpcallLinker.indexMap(argMoves);
        Map<VMStorage, Integer> retIndices = Util_jdk_internal_foreign_abi_UpcallLinker.indexMap(retMoves);
        int spreaderCount = callingSequence.calleeMethodType().parameterCount();
        if (callingSequence.needsReturnBuffer()) {
            spreaderCount--; // return buffer is dropped from the argument list
        }
        final int finalSpreaderCount = spreaderCount;
        Target_jdk_internal_foreign_abi_UpcallLinker_InvocationData invData = new Target_jdk_internal_foreign_abi_UpcallLinker_InvocationData(argIndices, retIndices, callingSequence, retMoves, abi);
        MethodHandle doBindings = insertArguments(MH_invokeInterpBindings, 2, invData);
        doBindingsMaker = new UnaryOperator<MethodHandle>() {
            @Override
            public MethodHandle apply(MethodHandle target) {
                target = target.asSpreader(Object[].class, finalSpreaderCount);
                MethodHandle handle = insertArguments(doBindings, 0, target);
                handle = handle.asCollector(Object[].class, llType.parameterCount());
                return handle.asType(llType);
            }
        };

        VMStorage[] args = toStorageArray(argMoves);
        VMStorage[] rets = toStorageArray(retMoves);
        Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs conv = new Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs(args, rets);

        return new AbstractLinker.UpcallStubFactory() {
            @Override
            public MemorySegment makeStub(MethodHandle target, Arena scope) {
                /*
                 * It is my great pleasure to tell you that we do have to substitute this whole
                 * function JUST to disable this tiny check, which should use equals instead
                 */
                // assert target.type() == targetType;
                assert target.type().equals(targetType);
                MethodHandle bound = doBindingsMaker.apply(target);
                checkPrimitive(bound.type());
                bound = MethodHandles.insertArguments(exactInvoker(bound.type()), 0, bound);
                long entryPoint = makeUpcallStub(bound, abi, conv,
                                callingSequence.needsReturnBuffer(), callingSequence.returnBufferSize());
                return Target_jdk_internal_foreign_abi_UpcallStubs.makeUpcall(entryPoint, scope);
            }
        };
    }
}

final class Util_jdk_internal_foreign_abi_UpcallLinker {
    static Map<VMStorage, Integer> indexMap(Binding.Move[] moves) {
        return IntStream.range(0, moves.length)
                        .boxed()
                        .collect(Collectors.toMap(new Function<Integer, VMStorage>() {
                            @Override
                            public VMStorage apply(Integer i) {
                                return moves[i].storage();
                            }
                        }, new Function<Integer, Integer>() {
                            @Override
                            public Integer apply(Integer i) {
                                return i;
                            }
                        }));
    }
}

@TargetClass(className = "jdk.internal.foreign.abi.UpcallLinker", innerClass = "InvocationData")
final class Target_jdk_internal_foreign_abi_UpcallLinker_InvocationData {
    @Alias
    Target_jdk_internal_foreign_abi_UpcallLinker_InvocationData(Map<VMStorage, Integer> argIndexMap,
                    Map<VMStorage, Integer> retIndexMap,
                    CallingSequence callingSequence,
                    Binding.VMStore[] retMoves,
                    ABIDescriptor abi) {

    }
}

@TargetClass(className = "jdk.internal.foreign.abi.UpcallLinker", innerClass = "CallRegs")
final class Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs {
    @Alias private VMStorage[] argRegs;
    @Alias private VMStorage[] retRegs;

    @Substitute
    Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs(VMStorage[] argRegs, VMStorage[] retRegs) {
        this.argRegs = argRegs;
        this.retRegs = retRegs;
    }

    @Substitute
    public VMStorage[] argRegs() {
        return this.argRegs;
    }

    @Substitute
    public VMStorage[] retRegs() {
        return this.retRegs;
    }
}