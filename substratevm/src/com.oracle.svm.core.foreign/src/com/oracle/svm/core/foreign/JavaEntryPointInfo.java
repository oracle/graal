package com.oracle.svm.core.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.svm.core.graal.code.AssignedLocation;

import jdk.internal.foreign.abi.ABIDescriptor;

public class JavaEntryPointInfo {
    private final MethodType methodType;
    private final AssignedLocation[] argumentsAssignment;
    private final AssignedLocation[] returnAssignment;
    private final int returnBufferSize;

    private JavaEntryPointInfo(MethodType methodType, AssignedLocation[] argumentsAssignment, AssignedLocation[] returnAssignment, long returnBufferSize) {
        this.methodType = Objects.requireNonNull(methodType);
        this.argumentsAssignment = Objects.requireNonNull(argumentsAssignment);
        this.returnAssignment = Objects.requireNonNull(returnAssignment);
        this.returnBufferSize = (int) returnBufferSize;
    }

    static JavaEntryPointInfo make(MethodType mt, Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs conv,
                    boolean needsReturnBuffer, long returnBufferSize) {
        AssignedLocation[] args = AbiUtils.singleton().toMemoryAssignment(conv.argRegs(), false);
        AssignedLocation[] ret = AbiUtils.singleton().toMemoryAssignment(conv.retRegs(), true);
        assert needsReturnBuffer == (ret.length >= 2);
        return new JavaEntryPointInfo(mt, args, ret, returnBufferSize);
    }

    static JavaEntryPointInfo make(MethodHandle mh, ABIDescriptor abi, Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs conv,
                    boolean needsReturnBuffer, long returnBufferSize) {
        return make(mh.type(), conv, needsReturnBuffer, returnBufferSize);
    }

    public MethodType cMethodType() {
        MethodType mt = methodType;
        if (buffersReturn()) {
            mt = mt.dropParameterTypes(0, 1);
        }
        return mt;
    }

    public MethodType handleType() {
        return methodType;
    }

    public MethodType javaMethodType() {
        return methodType.insertParameterTypes(0, MethodHandle.class);
    }

    public boolean buffersReturn() {
        return returnAssignment.length >= 2;
    }

    public AssignedLocation[] argumentsAssignment() {
        return argumentsAssignment;
    }

    public AssignedLocation[] returnAssignment() {
        return returnAssignment;
    }

    public int returnBufferSize() {
        assert buffersReturn();
        return returnBufferSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JavaEntryPointInfo that = (JavaEntryPointInfo) o;
        return returnBufferSize == that.returnBufferSize && Objects.equals(methodType, that.methodType) && Arrays.equals(argumentsAssignment, that.argumentsAssignment) &&
                        Arrays.equals(returnAssignment, that.returnAssignment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodType, returnBufferSize, Arrays.hashCode(argumentsAssignment), Arrays.hashCode(returnAssignment));
    }
}
