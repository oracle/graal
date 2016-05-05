/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * Copyright (c) 2016 University of Manchester
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.man.cs.llvm.ir.model;

import uk.ac.man.cs.llvm.ir.model.elements.AllocateInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.BinaryOperationInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.BranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CallInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CastInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CompareInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ConditionalBranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ExtractElementInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ExtractValueInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.GetElementPointerInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.IndirectBranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.InsertElementInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.InsertValueInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.LoadInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.PhiInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ReturnInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SelectInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ShuffleVectorInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.StoreInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SwitchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SwitchOldInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.UnreachableInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.VoidCallInstruction;

public interface InstructionVisitor {

    void visit(AllocateInstruction allocate);

    void visit(BinaryOperationInstruction operation);

    void visit(BranchInstruction branch);

    void visit(CallInstruction call);

    void visit(CastInstruction cast);

    void visit(CompareInstruction operation);

    void visit(ConditionalBranchInstruction branch);

    void visit(ExtractElementInstruction extract);

    void visit(ExtractValueInstruction extract);

    void visit(GetElementPointerInstruction gep);

    void visit(IndirectBranchInstruction branch);

    void visit(InsertElementInstruction insert);

    void visit(InsertValueInstruction insert);

    void visit(LoadInstruction load);

    void visit(PhiInstruction phi);

    void visit(ReturnInstruction ret);

    void visit(SelectInstruction select);

    void visit(ShuffleVectorInstruction shuffle);

    void visit(StoreInstruction store);

    void visit(SwitchInstruction select);

    void visit(SwitchOldInstruction select);

    void visit(UnreachableInstruction unreachable);

    void visit(VoidCallInstruction call);
}
