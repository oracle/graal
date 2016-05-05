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
package uk.ac.man.cs.llvm.ir.module;

import java.util.ArrayList;
import java.util.List;
import uk.ac.man.cs.llvm.bc.ParserListener;
import uk.ac.man.cs.llvm.bc.blocks.Block;
import uk.ac.man.cs.llvm.ir.FunctionGenerator;
import uk.ac.man.cs.llvm.ir.ModuleGenerator;
import uk.ac.man.cs.llvm.ir.module.records.ModuleRecord;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class Module implements ParserListener {

    protected final ModuleGenerator generator;

    protected final ModuleVersion version;

    protected int mode = 1;

    protected final Types types;

    protected final List<FunctionType> methods = new ArrayList<>();

    protected final List<Type> symbols = new ArrayList<>();

    public Module(ModuleVersion version, ModuleGenerator generator) {
        this.version = version;
        this.generator = generator;
        types = new Types(generator);
    }

    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case MODULE:
                return this; // Entering from root

            case CONSTANTS:
                return version.createConstants(types, symbols, generator);

            case FUNCTION: {
                FunctionType method = methods.remove(0);

                FunctionGenerator gen = generator.generateFunction();

                List<Type> sym = new ArrayList<>(symbols);

                for (Type arg : method.getArgumentTypes()) {
                    gen.createParameter(arg);
                    sym.add(arg);
                }

                return version.createMethod(types, sym, gen, mode);
            }
            case IDENTIFICATION:
                return new Identification();

            case TYPE:
                return types;

            case VALUE_SYMTAB:
                return new ValueSymbolTable(generator);

            default:
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        generator.exitModule();
    }

    @Override
    public void record(long id, long[] args) {
        ModuleRecord record = ModuleRecord.decode(id);

        switch (record) {
            case VERSION:
                mode = (int) args[0];
                break;

            case TARGET_TRIPLE:


            case GLOBAL_VARIABLE:
                createGlobalVariable(args);
                break;

            case FUNCTION:
                createFunction(args);
                break;

            default:
                break;
        }
    }

    protected void createFunction(long[] args) {
        FunctionType type = (FunctionType) types.get(args[0]);
        boolean isPrototype = args[2] != 0;

        generator.createFunction(type, isPrototype);
        symbols.add(type);
        if (!isPrototype) {
            methods.add(type);
        }
    }

    protected void createGlobalVariable(long[] args) {
        int i = 0;
        Type type = new PointerType(types.get(args[i++]));
        boolean isConstant = (args[i++] & 1) == 1;
        int initialiser = (int) args[i++];
        i++; // Unused parameter
        int align = (int) args[i++];

        generator.createVariable(type, isConstant, initialiser, align);
        symbols.add(type);
    }
}
