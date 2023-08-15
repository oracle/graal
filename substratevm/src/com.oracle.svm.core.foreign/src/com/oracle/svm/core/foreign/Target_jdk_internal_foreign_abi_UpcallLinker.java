package com.oracle.svm.core.foreign;

import java.lang.invoke.MethodHandle;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.foreign.abi.ABIDescriptor;
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