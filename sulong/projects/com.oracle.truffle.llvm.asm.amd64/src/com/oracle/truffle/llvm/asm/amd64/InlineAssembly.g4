/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
 * The parser and lexer need to be generated using 'mx create-asm-parser';
 */

grammar InlineAssembly;

@parser::header
{
// DO NOT MODIFY - generated from InlineAssembly.g4 using "mx create-asm-parser"

import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.runtime.types.Type;
import static com.oracle.truffle.llvm.runtime.types.Type.TypeArrayBuilder;
}

@lexer::header
{
// DO NOT MODIFY - generated from InlineAssembly.g4 using "mx create-asm-parser"
}

@parser::members
{
private AsmFactory factory;
private LLVMInlineAssemblyRootNode root;
private String snippet;

private static final class BailoutErrorListener extends BaseErrorListener {
    private final String snippet;
    BailoutErrorListener(String snippet) {
        this.snippet = snippet;
    }
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        String location = "-- line " + line + " col " + (charPositionInLine + 1) + ": ";
        throw new AsmParseException(String.format("ASM error in %s:\n%s%s", snippet, location, msg));
    }
}

public static LLVMInlineAssemblyRootNode parseInlineAssembly(LLVMLanguage language, String asmSnippet, String asmFlags, TypeArrayBuilder argTypes, Type retType, Type[] retTypes, long[] retOffsets) {
    InlineAssemblyLexer lexer = new InlineAssemblyLexer(CharStreams.fromString(asmSnippet));
    InlineAssemblyParser parser = new InlineAssemblyParser(new CommonTokenStream(lexer));
    lexer.removeErrorListeners();
    parser.removeErrorListeners();
    BailoutErrorListener listener = new BailoutErrorListener(asmSnippet);
    lexer.addErrorListener(listener);
    parser.addErrorListener(listener);
    parser.snippet = asmSnippet;
    parser.factory = new AsmFactory(language, argTypes, asmFlags, retType, retTypes, retOffsets);
    parser.inline_assembly();
    if (parser.root == null) {
        throw new IllegalStateException("no roots produced by inline assembly snippet");
    }
    return parser.root;
}
}

// parser

// note: this grammar is not using the "$x.text" shortcut to avoid findbugs warnings

inline_assembly :
  '"'
  ( ( prefix ( ';' )?
    |                                            { factory.setPrefix(null); }
    )
    assembly_instruction
    ( ( ';' | '\n' )
      ( prefix
      |                                          { factory.setPrefix(null); }
      )
      ( assembly_instruction )?
    )*
  )?
  '"'
                                                 { root = factory.finishInline(); }
  ;

////////////////////////////////////////////////////////////////////////////////
prefix :
  op=( 'rep'
  | 'repz'
  | 'repe'
  | 'repne'
  | 'repnz'
  | 'lock'
  )                                              { factory.setPrefix($op.getText()); }
  ;

assembly_instruction :
  ( directive
  | zero_op
  | unary_op8
  | unary_op16
  | unary_op32
  | unary_op64
  | unary_op
  | binary_op8
  | binary_op16
  | binary_op32
  | binary_op64
  | binary_op
  | imul_div
  | jump
  | int_value
  )
  ;

int_value :
  'int'
  immediate                                      { factory.createInt($immediate.op); }
  ;

jump :
  op=( 'call'
  | 'ja'
  | 'jae'
  | 'jb'
  | 'jbe'
  | 'jc'
  | 'jcxz'
  | 'je'
  | 'jecxz'
  | 'jg'
  | 'jge'
  | 'jl'
  | 'jle'
  | 'jmp'
  | 'jnae'
  | 'jnb'
  | 'jnbe'
  | 'jnc'
  | 'jne'
  | 'jng'
  | 'jnge'
  | 'jnl'
  | 'jnle'
  | 'jno'
  | 'jnp'
  | 'jns'
  | 'jnz'
  | 'jo'
  | 'jp'
  | 'jpe'
  | 'jpo'
  | 'js'
  | 'jz'
  | 'lcall'
  | 'loop'
  | 'loope'
  | 'loopne'
  | 'loopnz'
  | 'loopz'
  )
  bta=operand64
  ;

directive :
  op='.p2align' low_order_bits=number (',' padding_byte=number (',' max_bytes=number)?)? /* no-op */
  ;

zero_op :
  op=( 'clc'
  | 'cld'
  | 'cli'
  | 'cmc'
  | 'lahf'
  | 'popf'
  | 'popfw'
  | 'pushf'
  | 'pushfw'
  | 'sahf'
  | 'stc'
  | 'std'
  | 'sti'
  | 'nop'
  | 'rdtsc'
  | 'cpuid'
  | 'xgetbv'
  | 'ud2'
  | 'mfence'
  | 'lfence'
  | 'sfence'
  | 'hlt'
  | 'syscall'
  | 'stosb'
  | 'stosw'
  | 'stosd'
  | 'stosq'
  | 'pause'
  )                                              { factory.createOperation($op.getText()); }
  ;

imul_div :
  ( op1=( 'idivb' | 'imulb' )
    a1=operand8 ( { factory.createUnaryOperation($op1.getText(), $a1.op); } | ',' b1=operand8 ( { factory.createBinaryOperation($op1.getText(), $a1.op, $b1.op); } | ',' c1=operand8 { factory.createTernaryOperation($op1.getText(), $a1.op, $b1.op, $c1.op); } ) )
  | op2=( 'idivw' | 'imulw' )
    a2=operand16 ( { factory.createUnaryOperation($op2.getText(), $a2.op); } | ',' b2=operand16 ( { factory.createBinaryOperation($op2.getText(), $a2.op, $b2.op); } | ',' c2=operand16 { factory.createTernaryOperation($op2.getText(), $a2.op, $b2.op, $c2.op); } ) )
  | op3=( 'idivl' | 'imull' )
    a3=operand32 ( { factory.createUnaryOperation($op3.getText(), $a3.op); } | ',' b3=operand32 ( { factory.createBinaryOperation($op3.getText(), $a3.op, $b3.op); } | ',' c3=operand32 { factory.createTernaryOperation($op3.getText(), $a3.op, $b3.op, $c3.op); } ) )
  | op4=( 'idivq' | 'imulq' )
    a4=operand64 ( { factory.createUnaryOperation($op4.getText(), $a4.op); } | ',' b4=operand64 ( { factory.createBinaryOperation($op4.getText(), $a4.op, $b4.op); } | ',' c4=operand64 { factory.createTernaryOperation($op4.getText(), $a4.op, $b4.op, $c4.op); } ) )
  | op5=( 'idiv' | 'imul' )
    a5=operand ( { factory.createUnaryOperation($op5.getText(), $a5.op); } | ',' b5=operand ( { factory.createBinaryOperation($op5.getText(), $a5.op, $b5.op); } | ',' c5=operand { factory.createTernaryOperation($op5.getText(), $a5.op, $b5.op, $c5.op); } ) )
  )
  ;

unary_op8 :
  op=( 'incb'
  | 'decb'
  | 'negb'
  | 'notb'
  | 'divb'
  | 'mulb'
  )
  operand8                                       { factory.createUnaryOperation($op.getText(), $operand8.op); }
  ;

unary_op16 :
  op=( 'incw'
  | 'decw'
  | 'negw'
  | 'notw'
  | 'divw'
  | 'mulw'
  | 'pushw'
  | 'popw'
  )
  operand16                                      { factory.createUnaryOperation($op.getText(), $operand16.op); }
  ;

unary_op32 :
  op=( 'incl'
  | 'decl'
  | 'negl'
  | 'notl'
  | 'divl'
  | 'mull'
  | 'bswapl'
  | 'pushl'
  | 'popl'
  )
  operand32                                      { factory.createUnaryOperation($op.getText(), $operand32.op); }
  ;

unary_op64 :
  op=( 'incq'
  | 'decq'
  | 'negq'
  | 'notq'
  | 'divq'
  | 'mulq'
  | 'bswapq'
  | 'pushq'
  | 'popq'
  )
  operand64                                      { factory.createUnaryOperation($op.getText(), $operand64.op); }
  ;

unary_op :
  op=( 'inc'
  | 'dec'
  | 'neg'
  | 'not'
  | 'bswap'
  | 'rdrand'
  | 'rdseed'
  | 'seta'
  | 'setae'
  | 'setb'
  | 'setbe'
  | 'setc'
  | 'sete'
  | 'setg'
  | 'setge'
  | 'setl'
  | 'setle'
  | 'setna'
  | 'setnae'
  | 'setnb'
  | 'setnbe'
  | 'setnc'
  | 'setne'
  | 'setng'
  | 'setnge'
  | 'setnl'
  | 'setnle'
  | 'setno'
  | 'setnp'
  | 'setns'
  | 'setnz'
  | 'seto'
  | 'setp'
  | 'setpe'
  | 'setpo'
  | 'sets'
  | 'setz'
  | 'push'
  | 'pop'
  | 'cmpxchg8b'
  | 'cmpxchg16b'
  )
  operand                                        { factory.createUnaryOperationImplicitSize($op.getText(), $operand.op); }
  ;

binary_op8 :
  op=( 'movb'
  | 'xaddb'
  | 'xchgb'
  | 'adcb'
  | 'addb'
  | 'cmpb'
  | 'sbbb'
  | 'subb'
  | 'andb'
  | 'orb'
  | 'xorb'
  | 'rclb'
  | 'rcrb'
  | 'rolb'
  | 'rorb'
  | 'salb'
  | 'sarb'
  | 'shlb'
  | 'shrb'
  | 'testb'
  | 'cmpxchgb'
  )
  a=operand8 ',' b=operand8                      { factory.createBinaryOperation($op.getText(), $a.op, $b.op); }
  ;

binary_op16 :
  op1=( 'cmovaw'
  | 'cmovaew'
  | 'cmovbw'
  | 'cmovbew'
  | 'cmovcw'
  | 'cmovew'
  | 'cmovgw'
  | 'cmovgew'
  | 'cmovlw'
  | 'cmovlew'
  | 'cmovnaw'
  | 'cmovnaew'
  | 'cmovnbw'
  | 'cmovnbew'
  | 'cmovncw'
  | 'cmovnew'
  | 'cmovngw'
  | 'cmovngew'
  | 'cmovnlw'
  | 'cmovnlew'
  | 'cmovnow'
  | 'cmovnpw'
  | 'cmovnsw'
  | 'cmovnzw'
  | 'cmovow'
  | 'cmovpw'
  | 'cmovpew'
  | 'cmovpow'
  | 'cmovsw'
  | 'cmovzw'
  | 'cmpxchgw'
  | 'movw'
  | 'xaddw'
  | 'xchgw'
  | 'adcw'
  | 'addw'
  | 'cmpw'
  | 'sbbw'
  | 'subw'
  | 'andw'
  | 'orw'
  | 'xorw'
  | 'testw'
  | 'bsfw'
  | 'bsrw'
  | 'btw'
  | 'btcw'
  | 'btrw'
  | 'btsw'
  )
  a1=operand16 ',' b1=operand16                  { factory.createBinaryOperation($op1.getText(), $a1.op, $b1.op); }
  |
  op2=( 'rclw'
  | 'rcrw'
  | 'rolw'
  | 'rorw'
  | 'salw'
  | 'sarw'
  | 'shlw'
  | 'shrw'
  )
  a2=operand8 ',' b2=operand16                   { factory.createBinaryOperation($op2.getText(), $a2.op, $b2.op); }
  |
  op3=( 'movsbw'
  | 'movzbw'
  )
  a3=operand8 ',' b3=operand16                   { factory.createBinaryOperation($op3.getText(), $a3.op, $b3.op); }
  ;

binary_op32 :
  op1=( 'cmoval'
  | 'cmovael'
  | 'cmovbl'
  | 'cmovbel'
  | 'cmovcl'
  | 'cmovel'
  | 'cmovgl'
  | 'cmovgel'
  | 'cmovll'
  | 'cmovlel'
  | 'cmovnal'
  | 'cmovnael'
  | 'cmovnbl'
  | 'cmovnbel'
  | 'cmovncl'
  | 'cmovnel'
  | 'cmovngl'
  | 'cmovngel'
  | 'cmovnll'
  | 'cmovnlel'
  | 'cmovnol'
  | 'cmovnpl'
  | 'cmovnsl'
  | 'cmovnzl'
  | 'cmovol'
  | 'cmovpl'
  | 'cmovpel'
  | 'cmovpol'
  | 'cmovsl'
  | 'cmovzl'
  | 'cmpxchgl'
  | 'movl'
  | 'xaddl'
  | 'xchgl'
  | 'adcl'
  | 'addl'
  | 'cmpl'
  | 'sbbl'
  | 'subl'
  | 'andl'
  | 'orl'
  | 'xorl'
  | 'testl'
  | 'bsfl'
  | 'bsrl'
  | 'btl'
  | 'btcl'
  | 'btrl'
  | 'btsl'
  )
  a1=operand32 ',' b1=operand32                  { factory.createBinaryOperation($op1.getText(), $a1.op, $b1.op); }
  |
  op2=( 'rcll'
  | 'rcrl'
  | 'roll'
  | 'rorl'
  | 'sall'
  | 'sarl'
  | 'shll'
  | 'shrl'
  )
  a2=operand8 ',' b2=operand32                   { factory.createBinaryOperation($op2.getText(), $a2.op, $b2.op); }
  |
  op3=( 'movsbl'
  | 'movswl'
  )
  a3=operand8 ',' b3=operand32                   { factory.createBinaryOperation($op3.getText(), $a3.op, $b3.op); }
  |
  op4=( 'movzbl'
  | 'movzwl'
  )
  a4=operand16 ',' b4=operand32                  { factory.createBinaryOperation($op4.getText(), $a4.op, $b4.op); }
  ;

binary_op64 :
  op1=( 'cmovaq'
  | 'cmovaeq'
  | 'cmovbq'
  | 'cmovbeq'
  | 'cmovcq'
  | 'cmoveq'
  | 'cmovgq'
  | 'cmovgeq'
  | 'cmovlq'
  | 'cmovleq'
  | 'cmovnaq'
  | 'cmovnaeq'
  | 'cmovnbq'
  | 'cmovnbeq'
  | 'cmovncq'
  | 'cmovneq'
  | 'cmovngq'
  | 'cmovngeq'
  | 'cmovnlq'
  | 'cmovnleq'
  | 'cmovnoq'
  | 'cmovnpq'
  | 'cmovnsq'
  | 'cmovnzq'
  | 'cmovoq'
  | 'cmovpq'
  | 'cmovpeq'
  | 'cmovpoq'
  | 'cmovsq'
  | 'cmovzq'
  | 'cmpxchgq'
  | 'movq'
  | 'xaddq'
  | 'xchgq'
  | 'adcq'
  | 'addq'
  | 'cmpq'
  | 'sbbq'
  | 'subq'
  | 'andq'
  | 'orq'
  | 'xorq'
  | 'testq'
  | 'bsfq'
  | 'bsrq'
  | 'btq'
  | 'btcq'
  | 'btrq'
  | 'btsq'
  )
  a1=operand64 ',' b1=operand64                    { factory.createBinaryOperation($op1.getText(), $a1.op, $b1.op); }
  |
  op2=( 'rclq'
  | 'rcrq'
  | 'rolq'
  | 'rorq'
  | 'salq'
  | 'sarq'
  | 'shlq'
  | 'shrq'
  )
  a2=operand8 ',' b2=operand64                     { factory.createBinaryOperation($op2.getText(), $a2.op, $b2.op); }
  |
  op3=( 'movsbq'
  | 'movzbq'
  )
  a3=operand8 ',' b3=operand64                     { factory.createBinaryOperation($op3.getText(), $a3.op, $b3.op); }
  |
  op4=( 'movswq'
  | 'movzwq'
  )
  a4=operand16 ',' b4=operand64                    { factory.createBinaryOperation($op4.getText(), $a4.op, $b4.op); }
  |
  op5='movslq'
  a5=operand32 ',' b5=operand64                    { factory.createBinaryOperation($op5.getText(), $a5.op, $b5.op); }
  ;

binary_op :
  op=( 'cmova'
  | 'cmovae'
  | 'cmovb'
  | 'cmovbe'
  | 'cmovc'
  | 'cmove'
  | 'cmovg'
  | 'cmovge'
  | 'cmovl'
  | 'cmovle'
  | 'cmovna'
  | 'cmovnae'
  | 'cmovnb'
  | 'cmovnbe'
  | 'cmovnc'
  | 'cmovne'
  | 'cmovng'
  | 'cmovnge'
  | 'cmovnl'
  | 'cmovnle'
  | 'cmovno'
  | 'cmovnp'
  | 'cmovns'
  | 'cmovnz'
  | 'cmovo'
  | 'cmovp'
  | 'cmovpe'
  | 'cmovpo'
  | 'cmovs'
  | 'cmovz'
  | 'cmpxchg'
  | 'pmovmskb'
  | 'mov'
  | 'xadd'
  | 'xchg'
  | 'adc'
  | 'add'
  | 'cmp'
  | 'div'
  | 'mul'
  | 'sbb'
  | 'sub'
  | 'and'
  | 'or'
  | 'xor'
  | 'rcl'
  | 'rcr'
  | 'rol'
  | 'ror'
  | 'sal'
  | 'sar'
  | 'shl'
  | 'shr'
  | 'lea'
  | 'bsf'
  | 'bsr'
  )
  a=operand ',' b=operand                        { factory.createBinaryOperationImplicitSize($op.getText(), $a.op, $b.op); }
  ;

////////////////////////////////////////////////////////////////////////////////
operand8 returns [AsmOperand op] :
  ( register8                                    { $op = $register8.op; }
  | memory_reference                             { $op = $memory_reference.op; }
  | immediate                                    { $op = $immediate.op; }
  | argument                                     { $op = $argument.op; }
  )
  ;

operand16 returns [AsmOperand op] :
  ( register16                                   { $op = $register16.op; }
  | memory_reference                             { $op = $memory_reference.op; }
  | immediate                                    { $op = $immediate.op; }
  | argument                                     { $op = $argument.op; }
  )
  ;

operand32 returns [AsmOperand op] :
  ( register32                                   { $op = $register32.op; }
  | memory_reference                             { $op = $memory_reference.op; }
  | immediate                                    { $op = $immediate.op; }
  | argument                                     { $op = $argument.op; }
  )
  ;

operand64 returns [AsmOperand op] :
  ( register64                                   { $op = $register64.op; }
  | memory_reference                             { $op = $memory_reference.op; }
  | immediate                                    { $op = $immediate.op; }
  | argument                                     { $op = $argument.op; }
  )
  ;

operand returns [AsmOperand op] :
  ( register8                                    { $op = $register8.op; }
  | register16                                   { $op = $register16.op; }
  | register32                                   { $op = $register32.op; }
  | register64                                   { $op = $register64.op; }
  | registerXmm                                  { $op = $registerXmm.op; }
  | memory_reference                             { $op = $memory_reference.op; }
  | immediate                                    { $op = $immediate.op; }
  | argument                                     { $op = $argument.op; }
  )
  ;

memory_reference returns [AsmMemoryOperand op] :
                                                 { String displacement = null;
                                                   String segment = null;
                                                   AsmOperand base = null;
                                                   AsmOperand offset = null;
                                                   int scale = 1; }
  ( segment_register { segment = $segment_register.reg; } ':' )?
  ( ( i=IDENT                                    { displacement = $i.getText(); }
    | number                                     { displacement = String.valueOf($number.n); }
    )
    ( '('
      ( operand
      	                                         { base = $operand.op; }
      )?
      ( ',' operand                              { offset = $operand.op; }
        ( ',' number                             { scale = (int) $number.n; }
        )?
      )?
      ')'
    )?
  | '('
    ( operand
      	                                         { base = $operand.op; }
    )?
    ( ',' operand                                { offset = $operand.op; }
      ( ',' number                               { scale = (int) $number.n; }
      )?
    )?
    ')'
  )
                                                 { $op = new AsmMemoryOperand(segment, displacement, base, offset, scale); }
  ;

register8 returns [AsmRegisterOperand op] :
  r=( '%ah'
  | '%al'
  | '%bh'
  | '%bl'
  | '%ch'
  | '%cl'
  | '%dh'
  | '%dl'
  | '%r0l'
  | '%r1l'
  | '%r2l'
  | '%r3l'
  | '%r4l'
  | '%r5l'
  | '%r6l'
  | '%r7l'
  | '%r8l'
  | '%r9l'
  | '%r10l'
  | '%r11l'
  | '%r12l'
  | '%r13l'
  | '%r14l'
  | '%r15l'
  )                                              { $op = new AsmRegisterOperand($r.getText()); }
  ;

register16 returns [AsmRegisterOperand op] :
  r=( '%ax'
  | '%bx'
  | '%cx'
  | '%dx'
  | '%si'
  | '%di'
  | '%bp'
  | '%sp'
  | '%r0w'
  | '%r1w'
  | '%r2w'
  | '%r3w'
  | '%r4w'
  | '%r5w'
  | '%r6w'
  | '%r7w'
  | '%r8w'
  | '%r9w'
  | '%r10w'
  | '%r11w'
  | '%r12w'
  | '%r13w'
  | '%r14w'
  | '%r15w'
  )                                              { $op = new AsmRegisterOperand($r.getText()); }
  ;

register32 returns [AsmRegisterOperand op] :
  r=( '%eax'
  | '%ebx'
  | '%ecx'
  | '%edx'
  | '%esi'
  | '%edi'
  | '%ebp'
  | '%esp'
  | '%r0d'
  | '%r1d'
  | '%r2d'
  | '%r3d'
  | '%r4d'
  | '%r5d'
  | '%r6d'
  | '%r7d'
  | '%r8d'
  | '%r9d'
  | '%r10d'
  | '%r11d'
  | '%r12d'
  | '%r13d'
  | '%r14d'
  | '%r15d'
  )                                              { $op = new AsmRegisterOperand($r.getText()); }
  ;

register64 returns [AsmRegisterOperand op] :
  r=( '%rax'
  | '%rbx'
  | '%rcx'
  | '%rdx'
  | '%rsp'
  | '%rbp'
  | '%rsi'
  | '%rdi'
  | '%r0'
  | '%r1'
  | '%r2'
  | '%r3'
  | '%r4'
  | '%r5'
  | '%r6'
  | '%r7'
  | '%r8'
  | '%r9'
  | '%r10'
  | '%r11'
  | '%r12'
  | '%r13'
  | '%r14'
  | '%r15'
  )                                              { $op = new AsmRegisterOperand($r.getText()); }
  ;

registerXmm returns [AsmRegisterOperand op] :
  r=( '%xmm0'
  | '%xmm1'
  | '%xmm2'
  | '%xmm3'
  | '%xmm4'
  | '%xmm5'
  | '%xmm6'
  | '%xmm7'
  | '%xmm8'
  | '%xmm9'
  | '%xmm10'
  | '%xmm11'
  | '%xmm12'
  | '%xmm13'
  | '%xmm14'
  | '%xmm15'
  )                                              { $op = new AsmRegisterOperand($r.getText()); }
  ;

segment_register returns [String reg] :
  r=( '%cs'
  | '%ds'
  | '%es'
  | '%fs'
  | '%gs'
  | '%ss'
  )                                              { $reg = $r.getText(); }
  ;

number returns [long n] :
  ( num=NUMBER                                   { $n = Long.parseLong($num.getText(), 10); }
  | num=BIN_NUMBER                               { $n = Long.parseLong($num.getText().substring(2), 2); }
  | num=HEX_NUMBER                               { $n = Long.parseLong($num.getText().substring(2), 16); }
  )
  ;

immediate returns [AsmImmediateOperand op] :
  '$$'
  number                                         { $op = new AsmImmediateOperand($number.n); }
  ;

argument returns [AsmArgumentOperand op] :
  '$'
  ( n=number                                     { $op = new AsmArgumentOperand((int) $n.n); }
  | '{' n=number
    ':'                                          { int size = -1; int shift = 0; }
    ( 'b'                                        { size = 8; }
    | 'h'                                        { size = 8; shift = 8; }
    | 'w'                                        { size = 16; }
    | 'k'                                        { size = 32; }
    | 'q'                                        { size = 64; }
    )                                            { $op = new AsmArgumentOperand((int) $n.n, size, shift); }
    '}'
  )
  ;


fragment DIGIT : [0-9];
fragment BIN_DIGIT : [0-1];
fragment OCT_DIGIT : [0-7];
fragment HEX_DIGIT : [0-9] | [A-Z] | [a-z];
fragment LETTER : [a-z];

IDENT : LETTER ( LETTER | DIGIT | '_' )*;
BIN_NUMBER : '0b' BIN_DIGIT+;
HEX_NUMBER : '0x' HEX_DIGIT+;
NUMBER : '-'? DIGIT+;

WS : ( ' ' | '\t' )+ -> skip;
COMMENT : '/*' .*? '*/' -> skip;
LINE_COMMENT : '#' ~[\r\n]* -> skip;
