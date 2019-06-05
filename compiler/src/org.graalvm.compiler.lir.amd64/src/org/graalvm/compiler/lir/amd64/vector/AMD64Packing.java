package org.graalvm.compiler.lir.amd64.vector;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import java.nio.ByteBuffer;

import static jdk.vm.ci.code.ValueUtil.asRegister;

public class AMD64Packing {

    private AMD64Packing() {}

    public static final class PackConstantsOp extends AMD64LIRInstruction {

        public static final LIRInstructionClass<PackConstantsOp> TYPE = LIRInstructionClass.create(PackConstantsOp.class);

        private final ByteBuffer byteBuffer;

        @Def({OperandFlag.REG}) private final AllocatableValue result;

        public PackConstantsOp(AllocatableValue result, ByteBuffer byteBuffer) {
            this(TYPE, result, byteBuffer);
        }

        protected PackConstantsOp(LIRInstructionClass<? extends PackConstantsOp> c, AllocatableValue result, ByteBuffer byteBuffer) {
            super(TYPE);
            this.result = result;
            this.byteBuffer = byteBuffer;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            final PlatformKind pc = result.getPlatformKind();
            final int alignment = pc.getSizeInBytes() / pc.getVectorLength();
            final AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(byteBuffer.array(), alignment);
            masm.movdqu(asRegister(result), address);
        }
    }

}
