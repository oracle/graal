package jdk.graal.compiler.lir.alloc.verifier;

import jdk.vm.ci.code.Register;

@SuppressWarnings("serial")
public class InvalidRegisterUsedException extends RAVException {

    public InvalidRegisterUsedException(Register register) {
        super(getErrorMessage(register));
    }

    static String getErrorMessage(Register register) {
        return "Register " + register + " is not allowed to be used by RegisterAllocatorConfig";
    }
}
