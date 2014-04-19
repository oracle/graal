package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.CompilationResult.RawData;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.SPARCMove.LoadAddressOp;

public class SPARCConstDataOp extends SPARCLIRInstruction {

    @Def({REG}) private AllocatableValue dst;
    private byte[] val;

    public SPARCConstDataOp(AllocatableValue dst, byte[] val) {
        this.dst = dst;
        this.val = val;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        RawData data = new RawData(val, 16);
        new LoadAddressOp(dst, (SPARCAddress) crb.recordDataReferenceInCode(data)).emitCode(crb);
    }

}
