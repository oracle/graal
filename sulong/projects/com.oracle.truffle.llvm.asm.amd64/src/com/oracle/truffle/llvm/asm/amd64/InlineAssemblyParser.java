/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
// Checkstyle: stop
//@formatter:off
package com.oracle.truffle.llvm.asm.amd64;

// DO NOT MODIFY - generated from InlineAssembly.g4 using "mx create-asm-parser"

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.Type;

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings("all")
public class InlineAssemblyParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24, 
		T__24=25, T__25=26, T__26=27, T__27=28, T__28=29, T__29=30, T__30=31, 
		T__31=32, T__32=33, T__33=34, T__34=35, T__35=36, T__36=37, T__37=38, 
		T__38=39, T__39=40, T__40=41, T__41=42, T__42=43, T__43=44, T__44=45, 
		T__45=46, T__46=47, T__47=48, T__48=49, T__49=50, T__50=51, T__51=52, 
		T__52=53, T__53=54, T__54=55, T__55=56, T__56=57, T__57=58, T__58=59, 
		T__59=60, T__60=61, T__61=62, T__62=63, T__63=64, T__64=65, T__65=66, 
		T__66=67, T__67=68, T__68=69, T__69=70, T__70=71, T__71=72, T__72=73, 
		T__73=74, T__74=75, T__75=76, T__76=77, T__77=78, T__78=79, T__79=80, 
		T__80=81, T__81=82, T__82=83, T__83=84, T__84=85, T__85=86, T__86=87, 
		T__87=88, T__88=89, T__89=90, T__90=91, T__91=92, T__92=93, T__93=94, 
		T__94=95, T__95=96, T__96=97, T__97=98, T__98=99, T__99=100, T__100=101, 
		T__101=102, T__102=103, T__103=104, T__104=105, T__105=106, T__106=107, 
		T__107=108, T__108=109, T__109=110, T__110=111, T__111=112, T__112=113, 
		T__113=114, T__114=115, T__115=116, T__116=117, T__117=118, T__118=119, 
		T__119=120, T__120=121, T__121=122, T__122=123, T__123=124, T__124=125, 
		T__125=126, T__126=127, T__127=128, T__128=129, T__129=130, T__130=131, 
		T__131=132, T__132=133, T__133=134, T__134=135, T__135=136, T__136=137, 
		T__137=138, T__138=139, T__139=140, T__140=141, T__141=142, T__142=143, 
		T__143=144, T__144=145, T__145=146, T__146=147, T__147=148, T__148=149, 
		T__149=150, T__150=151, T__151=152, T__152=153, T__153=154, T__154=155, 
		T__155=156, T__156=157, T__157=158, T__158=159, T__159=160, T__160=161, 
		T__161=162, T__162=163, T__163=164, T__164=165, T__165=166, T__166=167, 
		T__167=168, T__168=169, T__169=170, T__170=171, T__171=172, T__172=173, 
		T__173=174, T__174=175, T__175=176, T__176=177, T__177=178, T__178=179, 
		T__179=180, T__180=181, T__181=182, T__182=183, T__183=184, T__184=185, 
		T__185=186, T__186=187, T__187=188, T__188=189, T__189=190, T__190=191, 
		T__191=192, T__192=193, T__193=194, T__194=195, T__195=196, T__196=197, 
		T__197=198, T__198=199, T__199=200, T__200=201, T__201=202, T__202=203, 
		T__203=204, T__204=205, T__205=206, T__206=207, T__207=208, T__208=209, 
		T__209=210, T__210=211, T__211=212, T__212=213, T__213=214, T__214=215, 
		T__215=216, T__216=217, T__217=218, T__218=219, T__219=220, T__220=221, 
		T__221=222, T__222=223, T__223=224, T__224=225, T__225=226, T__226=227, 
		T__227=228, T__228=229, T__229=230, T__230=231, T__231=232, T__232=233, 
		T__233=234, T__234=235, T__235=236, T__236=237, T__237=238, T__238=239, 
		T__239=240, T__240=241, T__241=242, T__242=243, T__243=244, T__244=245, 
		T__245=246, T__246=247, T__247=248, T__248=249, T__249=250, T__250=251, 
		T__251=252, T__252=253, T__253=254, T__254=255, T__255=256, T__256=257, 
		T__257=258, T__258=259, T__259=260, T__260=261, T__261=262, T__262=263, 
		T__263=264, T__264=265, T__265=266, T__266=267, T__267=268, T__268=269, 
		T__269=270, T__270=271, T__271=272, T__272=273, T__273=274, T__274=275, 
		T__275=276, T__276=277, T__277=278, T__278=279, T__279=280, T__280=281, 
		T__281=282, T__282=283, T__283=284, T__284=285, T__285=286, T__286=287, 
		T__287=288, T__288=289, T__289=290, T__290=291, T__291=292, T__292=293, 
		T__293=294, T__294=295, T__295=296, T__296=297, T__297=298, T__298=299, 
		T__299=300, T__300=301, T__301=302, T__302=303, T__303=304, T__304=305, 
		T__305=306, T__306=307, T__307=308, T__308=309, T__309=310, T__310=311, 
		T__311=312, T__312=313, T__313=314, T__314=315, T__315=316, T__316=317, 
		T__317=318, T__318=319, T__319=320, T__320=321, T__321=322, T__322=323, 
		T__323=324, T__324=325, T__325=326, T__326=327, T__327=328, T__328=329, 
		T__329=330, T__330=331, T__331=332, T__332=333, T__333=334, T__334=335, 
		T__335=336, T__336=337, T__337=338, T__338=339, T__339=340, T__340=341, 
		T__341=342, T__342=343, T__343=344, T__344=345, T__345=346, T__346=347, 
		T__347=348, T__348=349, T__349=350, T__350=351, T__351=352, T__352=353, 
		T__353=354, T__354=355, T__355=356, T__356=357, T__357=358, T__358=359, 
		T__359=360, T__360=361, T__361=362, T__362=363, T__363=364, T__364=365, 
		T__365=366, T__366=367, T__367=368, T__368=369, T__369=370, T__370=371, 
		T__371=372, T__372=373, T__373=374, T__374=375, T__375=376, T__376=377, 
		T__377=378, T__378=379, T__379=380, T__380=381, T__381=382, T__382=383, 
		T__383=384, T__384=385, T__385=386, T__386=387, T__387=388, T__388=389, 
		T__389=390, T__390=391, T__391=392, T__392=393, T__393=394, T__394=395, 
		T__395=396, T__396=397, T__397=398, T__398=399, T__399=400, T__400=401, 
		T__401=402, T__402=403, T__403=404, T__404=405, T__405=406, T__406=407, 
		T__407=408, T__408=409, T__409=410, T__410=411, T__411=412, T__412=413, 
		T__413=414, T__414=415, T__415=416, T__416=417, T__417=418, T__418=419, 
		T__419=420, T__420=421, T__421=422, T__422=423, T__423=424, T__424=425, 
		T__425=426, T__426=427, T__427=428, T__428=429, T__429=430, T__430=431, 
		T__431=432, T__432=433, T__433=434, T__434=435, T__435=436, T__436=437, 
		T__437=438, T__438=439, T__439=440, T__440=441, T__441=442, T__442=443, 
		T__443=444, T__444=445, T__445=446, T__446=447, T__447=448, T__448=449, 
		T__449=450, T__450=451, T__451=452, T__452=453, T__453=454, T__454=455, 
		T__455=456, T__456=457, T__457=458, T__458=459, T__459=460, T__460=461, 
		T__461=462, T__462=463, T__463=464, T__464=465, T__465=466, T__466=467, 
		T__467=468, T__468=469, T__469=470, T__470=471, T__471=472, T__472=473, 
		T__473=474, T__474=475, T__475=476, T__476=477, T__477=478, T__478=479, 
		T__479=480, T__480=481, T__481=482, T__482=483, T__483=484, T__484=485, 
		T__485=486, T__486=487, T__487=488, T__488=489, T__489=490, T__490=491, 
		T__491=492, T__492=493, T__493=494, T__494=495, T__495=496, T__496=497, 
		T__497=498, T__498=499, T__499=500, T__500=501, T__501=502, T__502=503, 
		T__503=504, T__504=505, T__505=506, T__506=507, T__507=508, T__508=509, 
		T__509=510, T__510=511, T__511=512, T__512=513, T__513=514, T__514=515, 
		T__515=516, T__516=517, T__517=518, T__518=519, T__519=520, T__520=521, 
		T__521=522, T__522=523, T__523=524, T__524=525, T__525=526, T__526=527, 
		T__527=528, T__528=529, T__529=530, T__530=531, T__531=532, T__532=533, 
		T__533=534, T__534=535, T__535=536, T__536=537, T__537=538, T__538=539, 
		T__539=540, T__540=541, T__541=542, T__542=543, T__543=544, T__544=545, 
		T__545=546, T__546=547, T__547=548, T__548=549, T__549=550, IDENT=551, 
		BIN_NUMBER=552, HEX_NUMBER=553, NUMBER=554, WS=555;
	public static final int
		RULE_inline_assembly = 0, RULE_prefix = 1, RULE_assembly_instruction = 2, 
		RULE_int_value = 3, RULE_jump = 4, RULE_zero_op = 5, RULE_imul_div = 6, 
		RULE_unary_op8 = 7, RULE_unary_op16 = 8, RULE_unary_op32 = 9, RULE_unary_op64 = 10, 
		RULE_unary_op = 11, RULE_binary_op8 = 12, RULE_binary_op16 = 13, RULE_binary_op32 = 14, 
		RULE_binary_op64 = 15, RULE_binary_op = 16, RULE_operand8 = 17, RULE_operand16 = 18, 
		RULE_operand32 = 19, RULE_operand64 = 20, RULE_operand = 21, RULE_memory_reference = 22, 
		RULE_register8 = 23, RULE_register16 = 24, RULE_register32 = 25, RULE_register64 = 26, 
		RULE_registerXmm = 27, RULE_segment_register = 28, RULE_number = 29, RULE_immediate = 30, 
		RULE_argument = 31;
	public static final String[] ruleNames = {
		"inline_assembly", "prefix", "assembly_instruction", "int_value", "jump", 
		"zero_op", "imul_div", "unary_op8", "unary_op16", "unary_op32", "unary_op64", 
		"unary_op", "binary_op8", "binary_op16", "binary_op32", "binary_op64", 
		"binary_op", "operand8", "operand16", "operand32", "operand64", "operand", 
		"memory_reference", "register8", "register16", "register32", "register64", 
		"registerXmm", "segment_register", "number", "immediate", "argument"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'\"'", "';'", "'\n'", "'rep'", "'repz'", "'repe'", "'repne'", "'repnz'", 
		"'lock'", "'int'", "'call'", "'ja'", "'jae'", "'jb'", "'jbe'", "'jc'", 
		"'jcxz'", "'je'", "'jecxz'", "'jg'", "'jge'", "'jl'", "'jle'", "'jmp'", 
		"'jnae'", "'jnb'", "'jnbe'", "'jnc'", "'jne'", "'jng'", "'jnge'", "'jnl'", 
		"'jnle'", "'jno'", "'jnp'", "'jns'", "'jnz'", "'jo'", "'jp'", "'jpe'", 
		"'jpo'", "'js'", "'jz'", "'lcall'", "'loop'", "'loope'", "'loopne'", "'loopnz'", 
		"'loopz'", "'clc'", "'cld'", "'cli'", "'cmc'", "'lahf'", "'popf'", "'popfw'", 
		"'pushf'", "'pushfw'", "'sahf'", "'stc'", "'std'", "'sti'", "'nop'", "'rdtsc'", 
		"'cpuid'", "'xgetbv'", "'ud2'", "'mfence'", "'lfence'", "'sfence'", "'hlt'", 
		"'syscall'", "'stosb'", "'stosw'", "'stosd'", "'stosq'", "'pause'", "'idivb'", 
		"'imulb'", "','", "'idivw'", "'imulw'", "'idivl'", "'imull'", "'idivq'", 
		"'imulq'", "'idiv'", "'imul'", "'incb'", "'decb'", "'negb'", "'notb'", 
		"'divb'", "'mulb'", "'incw'", "'decw'", "'negw'", "'notw'", "'divw'", 
		"'mulw'", "'pushw'", "'popw'", "'incl'", "'decl'", "'negl'", "'notl'", 
		"'divl'", "'mull'", "'bswapl'", "'pushl'", "'popl'", "'incq'", "'decq'", 
		"'negq'", "'notq'", "'divq'", "'mulq'", "'bswapq'", "'pushq'", "'popq'", 
		"'inc'", "'dec'", "'neg'", "'not'", "'bswap'", "'rdrand'", "'rdseed'", 
		"'seta'", "'setae'", "'setb'", "'setbe'", "'setc'", "'sete'", "'setg'", 
		"'setge'", "'setl'", "'setle'", "'setna'", "'setnae'", "'setnb'", "'setnbe'", 
		"'setnc'", "'setne'", "'setng'", "'setnge'", "'setnl'", "'setnle'", "'setno'", 
		"'setnp'", "'setns'", "'setnz'", "'seto'", "'setp'", "'setpe'", "'setpo'", 
		"'sets'", "'setz'", "'push'", "'pop'", "'cmpxchg8b'", "'cmpxchg16b'", 
		"'movb'", "'xaddb'", "'xchgb'", "'adcb'", "'addb'", "'cmpb'", "'sbbb'", 
		"'subb'", "'andb'", "'orb'", "'xorb'", "'rclb'", "'rcrb'", "'rolb'", "'rorb'", 
		"'salb'", "'sarb'", "'shlb'", "'shrb'", "'testb'", "'cmpxchgb'", "'cmovaw'", 
		"'cmovaew'", "'cmovbw'", "'cmovbew'", "'cmovcw'", "'cmovew'", "'cmovgw'", 
		"'cmovgew'", "'cmovlw'", "'cmovlew'", "'cmovnaw'", "'cmovnaew'", "'cmovnbw'", 
		"'cmovnbew'", "'cmovncw'", "'cmovnew'", "'cmovngw'", "'cmovngew'", "'cmovnlw'", 
		"'cmovnlew'", "'cmovnow'", "'cmovnpw'", "'cmovnsw'", "'cmovnzw'", "'cmovow'", 
		"'cmovpw'", "'cmovpew'", "'cmovpow'", "'cmovsw'", "'cmovzw'", "'cmpxchgw'", 
		"'movw'", "'xaddw'", "'xchgw'", "'adcw'", "'addw'", "'cmpw'", "'sbbw'", 
		"'subw'", "'andw'", "'orw'", "'xorw'", "'testw'", "'bsfw'", "'bsrw'", 
		"'btw'", "'btcw'", "'btrw'", "'btsw'", "'rclw'", "'rcrw'", "'rolw'", "'rorw'", 
		"'salw'", "'sarw'", "'shlw'", "'shrw'", "'movsbw'", "'movzbw'", "'cmoval'", 
		"'cmovael'", "'cmovbl'", "'cmovbel'", "'cmovcl'", "'cmovel'", "'cmovgl'", 
		"'cmovgel'", "'cmovll'", "'cmovlel'", "'cmovnal'", "'cmovnael'", "'cmovnbl'", 
		"'cmovnbel'", "'cmovncl'", "'cmovnel'", "'cmovngl'", "'cmovngel'", "'cmovnll'", 
		"'cmovnlel'", "'cmovnol'", "'cmovnpl'", "'cmovnsl'", "'cmovnzl'", "'cmovol'", 
		"'cmovpl'", "'cmovpel'", "'cmovpol'", "'cmovsl'", "'cmovzl'", "'cmpxchgl'", 
		"'movl'", "'xaddl'", "'xchgl'", "'adcl'", "'addl'", "'cmpl'", "'sbbl'", 
		"'subl'", "'andl'", "'orl'", "'xorl'", "'testl'", "'bsfl'", "'bsrl'", 
		"'btl'", "'btcl'", "'btrl'", "'btsl'", "'rcll'", "'rcrl'", "'roll'", "'rorl'", 
		"'sall'", "'sarl'", "'shll'", "'shrl'", "'movsbl'", "'movswl'", "'movzbl'", 
		"'movzwl'", "'cmovaq'", "'cmovaeq'", "'cmovbq'", "'cmovbeq'", "'cmovcq'", 
		"'cmoveq'", "'cmovgq'", "'cmovgeq'", "'cmovlq'", "'cmovleq'", "'cmovnaq'", 
		"'cmovnaeq'", "'cmovnbq'", "'cmovnbeq'", "'cmovncq'", "'cmovneq'", "'cmovngq'", 
		"'cmovngeq'", "'cmovnlq'", "'cmovnleq'", "'cmovnoq'", "'cmovnpq'", "'cmovnsq'", 
		"'cmovnzq'", "'cmovoq'", "'cmovpq'", "'cmovpeq'", "'cmovpoq'", "'cmovsq'", 
		"'cmovzq'", "'cmpxchgq'", "'movq'", "'xaddq'", "'xchgq'", "'adcq'", "'addq'", 
		"'cmpq'", "'sbbq'", "'subq'", "'andq'", "'orq'", "'xorq'", "'testq'", 
		"'bsfq'", "'bsrq'", "'btq'", "'btcq'", "'btrq'", "'btsq'", "'rclq'", "'rcrq'", 
		"'rolq'", "'rorq'", "'salq'", "'sarq'", "'shlq'", "'shrq'", "'movsbq'", 
		"'movzbq'", "'movswq'", "'movzwq'", "'movslq'", "'cmova'", "'cmovae'", 
		"'cmovb'", "'cmovbe'", "'cmovc'", "'cmove'", "'cmovg'", "'cmovge'", "'cmovl'", 
		"'cmovle'", "'cmovna'", "'cmovnae'", "'cmovnb'", "'cmovnbe'", "'cmovnc'", 
		"'cmovne'", "'cmovng'", "'cmovnge'", "'cmovnl'", "'cmovnle'", "'cmovno'", 
		"'cmovnp'", "'cmovns'", "'cmovnz'", "'cmovo'", "'cmovp'", "'cmovpe'", 
		"'cmovpo'", "'cmovs'", "'cmovz'", "'cmpxchg'", "'pmovmskb'", "'mov'", 
		"'xadd'", "'xchg'", "'adc'", "'add'", "'cmp'", "'div'", "'mul'", "'sbb'", 
		"'sub'", "'and'", "'or'", "'xor'", "'rcl'", "'rcr'", "'rol'", "'ror'", 
		"'sal'", "'sar'", "'shl'", "'shr'", "'lea'", "'bsf'", "'bsr'", "':'", 
		"'('", "')'", "'%ah'", "'%al'", "'%bh'", "'%bl'", "'%ch'", "'%cl'", "'%dh'", 
		"'%dl'", "'%r0l'", "'%r1l'", "'%r2l'", "'%r3l'", "'%r4l'", "'%r5l'", "'%r6l'", 
		"'%r7l'", "'%r8l'", "'%r9l'", "'%r10l'", "'%r11l'", "'%r12l'", "'%r13l'", 
		"'%r14l'", "'%r15l'", "'%ax'", "'%bx'", "'%cx'", "'%dx'", "'%si'", "'%di'", 
		"'%bp'", "'%sp'", "'%r0w'", "'%r1w'", "'%r2w'", "'%r3w'", "'%r4w'", "'%r5w'", 
		"'%r6w'", "'%r7w'", "'%r8w'", "'%r9w'", "'%r10w'", "'%r11w'", "'%r12w'", 
		"'%r13w'", "'%r14w'", "'%r15w'", "'%eax'", "'%ebx'", "'%ecx'", "'%edx'", 
		"'%esi'", "'%edi'", "'%ebp'", "'%esp'", "'%r0d'", "'%r1d'", "'%r2d'", 
		"'%r3d'", "'%r4d'", "'%r5d'", "'%r6d'", "'%r7d'", "'%r8d'", "'%r9d'", 
		"'%r10d'", "'%r11d'", "'%r12d'", "'%r13d'", "'%r14d'", "'%r15d'", "'%rax'", 
		"'%rbx'", "'%rcx'", "'%rdx'", "'%rsp'", "'%rbp'", "'%rsi'", "'%rdi'", 
		"'%r0'", "'%r1'", "'%r2'", "'%r3'", "'%r4'", "'%r5'", "'%r6'", "'%r7'", 
		"'%r8'", "'%r9'", "'%r10'", "'%r11'", "'%r12'", "'%r13'", "'%r14'", "'%r15'", 
		"'%xmm0'", "'%xmm1'", "'%xmm2'", "'%xmm3'", "'%xmm4'", "'%xmm5'", "'%xmm6'", 
		"'%xmm7'", "'%xmm8'", "'%xmm9'", "'%xmm10'", "'%xmm11'", "'%xmm12'", "'%xmm13'", 
		"'%xmm14'", "'%xmm15'", "'%cs'", "'%ds'", "'%es'", "'%fs'", "'%gs'", "'%ss'", 
		"'$$'", "'$'", "'{'", "'b'", "'h'", "'w'", "'k'", "'q'", "'}'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, "IDENT", 
		"BIN_NUMBER", "HEX_NUMBER", "NUMBER", "WS"
	};
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "InlineAssembly.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }


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
	        throw new LLVMParserException(String.format("ASM error in %s:\n%s%s", snippet, location, msg));
	    }
	}

	public static LLVMInlineAssemblyRootNode parseInlineAssembly(LLVMLanguage language, LLVMSourceLocation sourceSection, String asmSnippet, String asmFlags, Type[] argTypes, Type retType, Type[] retTypes, int[] retOffsets) {
	    InlineAssemblyLexer lexer = new InlineAssemblyLexer(CharStreams.fromString(asmSnippet));
	    InlineAssemblyParser parser = new InlineAssemblyParser(new CommonTokenStream(lexer));
	    lexer.removeErrorListeners();
	    parser.removeErrorListeners();
	    BailoutErrorListener listener = new BailoutErrorListener(asmSnippet);
	    lexer.addErrorListener(listener);
	    parser.addErrorListener(listener);
	    parser.snippet = asmSnippet;
	    parser.factory = new AsmFactory(language, sourceSection, argTypes, asmFlags, retType, retTypes, retOffsets);
	    parser.inline_assembly();
	    if (parser.root == null) {
	        throw new IllegalStateException("no roots produced by inline assembly snippet");
	    }
	    return parser.root;
	}

	public InlineAssemblyParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}
	public static class Inline_assemblyContext extends ParserRuleContext {
		public List<Assembly_instructionContext> assembly_instruction() {
			return getRuleContexts(Assembly_instructionContext.class);
		}
		public Assembly_instructionContext assembly_instruction(int i) {
			return getRuleContext(Assembly_instructionContext.class,i);
		}
		public List<PrefixContext> prefix() {
			return getRuleContexts(PrefixContext.class);
		}
		public PrefixContext prefix(int i) {
			return getRuleContext(PrefixContext.class,i);
		}
		public Inline_assemblyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inline_assembly; }
	}

	public final Inline_assemblyContext inline_assembly() throws RecognitionException {
		Inline_assemblyContext _localctx = new Inline_assemblyContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_inline_assembly);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(64);
			match(T__0);
			setState(86);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__14) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25) | (1L << T__26) | (1L << T__27) | (1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37) | (1L << T__38) | (1L << T__39) | (1L << T__40) | (1L << T__41) | (1L << T__42) | (1L << T__43) | (1L << T__44) | (1L << T__45) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__74 - 64)) | (1L << (T__75 - 64)) | (1L << (T__76 - 64)) | (1L << (T__77 - 64)) | (1L << (T__78 - 64)) | (1L << (T__80 - 64)) | (1L << (T__81 - 64)) | (1L << (T__82 - 64)) | (1L << (T__83 - 64)) | (1L << (T__84 - 64)) | (1L << (T__85 - 64)) | (1L << (T__86 - 64)) | (1L << (T__87 - 64)) | (1L << (T__88 - 64)) | (1L << (T__89 - 64)) | (1L << (T__90 - 64)) | (1L << (T__91 - 64)) | (1L << (T__92 - 64)) | (1L << (T__93 - 64)) | (1L << (T__94 - 64)) | (1L << (T__95 - 64)) | (1L << (T__96 - 64)) | (1L << (T__97 - 64)) | (1L << (T__98 - 64)) | (1L << (T__99 - 64)) | (1L << (T__100 - 64)) | (1L << (T__101 - 64)) | (1L << (T__102 - 64)) | (1L << (T__103 - 64)) | (1L << (T__104 - 64)) | (1L << (T__105 - 64)) | (1L << (T__106 - 64)) | (1L << (T__107 - 64)) | (1L << (T__108 - 64)) | (1L << (T__109 - 64)) | (1L << (T__110 - 64)) | (1L << (T__111 - 64)) | (1L << (T__112 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__115 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__122 - 64)) | (1L << (T__123 - 64)) | (1L << (T__124 - 64)) | (1L << (T__125 - 64)) | (1L << (T__126 - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (T__127 - 128)) | (1L << (T__128 - 128)) | (1L << (T__129 - 128)) | (1L << (T__130 - 128)) | (1L << (T__131 - 128)) | (1L << (T__132 - 128)) | (1L << (T__133 - 128)) | (1L << (T__134 - 128)) | (1L << (T__135 - 128)) | (1L << (T__136 - 128)) | (1L << (T__137 - 128)) | (1L << (T__138 - 128)) | (1L << (T__139 - 128)) | (1L << (T__140 - 128)) | (1L << (T__141 - 128)) | (1L << (T__142 - 128)) | (1L << (T__143 - 128)) | (1L << (T__144 - 128)) | (1L << (T__145 - 128)) | (1L << (T__146 - 128)) | (1L << (T__147 - 128)) | (1L << (T__148 - 128)) | (1L << (T__149 - 128)) | (1L << (T__150 - 128)) | (1L << (T__151 - 128)) | (1L << (T__152 - 128)) | (1L << (T__153 - 128)) | (1L << (T__154 - 128)) | (1L << (T__155 - 128)) | (1L << (T__156 - 128)) | (1L << (T__157 - 128)) | (1L << (T__158 - 128)) | (1L << (T__159 - 128)) | (1L << (T__160 - 128)) | (1L << (T__161 - 128)) | (1L << (T__162 - 128)) | (1L << (T__163 - 128)) | (1L << (T__164 - 128)) | (1L << (T__165 - 128)) | (1L << (T__166 - 128)) | (1L << (T__167 - 128)) | (1L << (T__168 - 128)) | (1L << (T__169 - 128)) | (1L << (T__170 - 128)) | (1L << (T__171 - 128)) | (1L << (T__172 - 128)) | (1L << (T__173 - 128)) | (1L << (T__174 - 128)) | (1L << (T__175 - 128)) | (1L << (T__176 - 128)) | (1L << (T__177 - 128)) | (1L << (T__178 - 128)) | (1L << (T__179 - 128)) | (1L << (T__180 - 128)) | (1L << (T__181 - 128)) | (1L << (T__182 - 128)) | (1L << (T__183 - 128)) | (1L << (T__184 - 128)) | (1L << (T__185 - 128)) | (1L << (T__186 - 128)) | (1L << (T__187 - 128)) | (1L << (T__188 - 128)) | (1L << (T__189 - 128)) | (1L << (T__190 - 128)))) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & ((1L << (T__191 - 192)) | (1L << (T__192 - 192)) | (1L << (T__193 - 192)) | (1L << (T__194 - 192)) | (1L << (T__195 - 192)) | (1L << (T__196 - 192)) | (1L << (T__197 - 192)) | (1L << (T__198 - 192)) | (1L << (T__199 - 192)) | (1L << (T__200 - 192)) | (1L << (T__201 - 192)) | (1L << (T__202 - 192)) | (1L << (T__203 - 192)) | (1L << (T__204 - 192)) | (1L << (T__205 - 192)) | (1L << (T__206 - 192)) | (1L << (T__207 - 192)) | (1L << (T__208 - 192)) | (1L << (T__209 - 192)) | (1L << (T__210 - 192)) | (1L << (T__211 - 192)) | (1L << (T__212 - 192)) | (1L << (T__213 - 192)) | (1L << (T__214 - 192)) | (1L << (T__215 - 192)) | (1L << (T__216 - 192)) | (1L << (T__217 - 192)) | (1L << (T__218 - 192)) | (1L << (T__219 - 192)) | (1L << (T__220 - 192)) | (1L << (T__221 - 192)) | (1L << (T__222 - 192)) | (1L << (T__223 - 192)) | (1L << (T__224 - 192)) | (1L << (T__225 - 192)) | (1L << (T__226 - 192)) | (1L << (T__227 - 192)) | (1L << (T__228 - 192)) | (1L << (T__229 - 192)) | (1L << (T__230 - 192)) | (1L << (T__231 - 192)) | (1L << (T__232 - 192)) | (1L << (T__233 - 192)) | (1L << (T__234 - 192)) | (1L << (T__235 - 192)) | (1L << (T__236 - 192)) | (1L << (T__237 - 192)) | (1L << (T__238 - 192)) | (1L << (T__239 - 192)) | (1L << (T__240 - 192)) | (1L << (T__241 - 192)) | (1L << (T__242 - 192)) | (1L << (T__243 - 192)) | (1L << (T__244 - 192)) | (1L << (T__245 - 192)) | (1L << (T__246 - 192)) | (1L << (T__247 - 192)) | (1L << (T__248 - 192)) | (1L << (T__249 - 192)) | (1L << (T__250 - 192)) | (1L << (T__251 - 192)) | (1L << (T__252 - 192)) | (1L << (T__253 - 192)) | (1L << (T__254 - 192)))) != 0) || ((((_la - 256)) & ~0x3f) == 0 && ((1L << (_la - 256)) & ((1L << (T__255 - 256)) | (1L << (T__256 - 256)) | (1L << (T__257 - 256)) | (1L << (T__258 - 256)) | (1L << (T__259 - 256)) | (1L << (T__260 - 256)) | (1L << (T__261 - 256)) | (1L << (T__262 - 256)) | (1L << (T__263 - 256)) | (1L << (T__264 - 256)) | (1L << (T__265 - 256)) | (1L << (T__266 - 256)) | (1L << (T__267 - 256)) | (1L << (T__268 - 256)) | (1L << (T__269 - 256)) | (1L << (T__270 - 256)) | (1L << (T__271 - 256)) | (1L << (T__272 - 256)) | (1L << (T__273 - 256)) | (1L << (T__274 - 256)) | (1L << (T__275 - 256)) | (1L << (T__276 - 256)) | (1L << (T__277 - 256)) | (1L << (T__278 - 256)) | (1L << (T__279 - 256)) | (1L << (T__280 - 256)) | (1L << (T__281 - 256)) | (1L << (T__282 - 256)) | (1L << (T__283 - 256)) | (1L << (T__284 - 256)) | (1L << (T__285 - 256)) | (1L << (T__286 - 256)) | (1L << (T__287 - 256)) | (1L << (T__288 - 256)) | (1L << (T__289 - 256)) | (1L << (T__290 - 256)) | (1L << (T__291 - 256)) | (1L << (T__292 - 256)) | (1L << (T__293 - 256)) | (1L << (T__294 - 256)) | (1L << (T__295 - 256)) | (1L << (T__296 - 256)) | (1L << (T__297 - 256)) | (1L << (T__298 - 256)) | (1L << (T__299 - 256)) | (1L << (T__300 - 256)) | (1L << (T__301 - 256)) | (1L << (T__302 - 256)) | (1L << (T__303 - 256)) | (1L << (T__304 - 256)) | (1L << (T__305 - 256)) | (1L << (T__306 - 256)) | (1L << (T__307 - 256)) | (1L << (T__308 - 256)) | (1L << (T__309 - 256)) | (1L << (T__310 - 256)) | (1L << (T__311 - 256)) | (1L << (T__312 - 256)) | (1L << (T__313 - 256)) | (1L << (T__314 - 256)) | (1L << (T__315 - 256)) | (1L << (T__316 - 256)) | (1L << (T__317 - 256)) | (1L << (T__318 - 256)))) != 0) || ((((_la - 320)) & ~0x3f) == 0 && ((1L << (_la - 320)) & ((1L << (T__319 - 320)) | (1L << (T__320 - 320)) | (1L << (T__321 - 320)) | (1L << (T__322 - 320)) | (1L << (T__323 - 320)) | (1L << (T__324 - 320)) | (1L << (T__325 - 320)) | (1L << (T__326 - 320)) | (1L << (T__327 - 320)) | (1L << (T__328 - 320)) | (1L << (T__329 - 320)) | (1L << (T__330 - 320)) | (1L << (T__331 - 320)) | (1L << (T__332 - 320)) | (1L << (T__333 - 320)) | (1L << (T__334 - 320)) | (1L << (T__335 - 320)) | (1L << (T__336 - 320)) | (1L << (T__337 - 320)) | (1L << (T__338 - 320)) | (1L << (T__339 - 320)) | (1L << (T__340 - 320)) | (1L << (T__341 - 320)) | (1L << (T__342 - 320)) | (1L << (T__343 - 320)) | (1L << (T__344 - 320)) | (1L << (T__345 - 320)) | (1L << (T__346 - 320)) | (1L << (T__347 - 320)) | (1L << (T__348 - 320)) | (1L << (T__349 - 320)) | (1L << (T__350 - 320)) | (1L << (T__351 - 320)) | (1L << (T__352 - 320)) | (1L << (T__353 - 320)) | (1L << (T__354 - 320)) | (1L << (T__355 - 320)) | (1L << (T__356 - 320)) | (1L << (T__357 - 320)) | (1L << (T__358 - 320)) | (1L << (T__359 - 320)) | (1L << (T__360 - 320)) | (1L << (T__361 - 320)) | (1L << (T__362 - 320)) | (1L << (T__363 - 320)) | (1L << (T__364 - 320)) | (1L << (T__365 - 320)) | (1L << (T__366 - 320)) | (1L << (T__367 - 320)) | (1L << (T__368 - 320)) | (1L << (T__369 - 320)) | (1L << (T__370 - 320)) | (1L << (T__371 - 320)) | (1L << (T__372 - 320)) | (1L << (T__373 - 320)) | (1L << (T__374 - 320)) | (1L << (T__375 - 320)) | (1L << (T__376 - 320)) | (1L << (T__377 - 320)) | (1L << (T__378 - 320)) | (1L << (T__379 - 320)) | (1L << (T__380 - 320)) | (1L << (T__381 - 320)) | (1L << (T__382 - 320)))) != 0) || ((((_la - 384)) & ~0x3f) == 0 && ((1L << (_la - 384)) & ((1L << (T__383 - 384)) | (1L << (T__384 - 384)) | (1L << (T__385 - 384)) | (1L << (T__386 - 384)) | (1L << (T__387 - 384)) | (1L << (T__388 - 384)) | (1L << (T__389 - 384)) | (1L << (T__390 - 384)) | (1L << (T__391 - 384)) | (1L << (T__392 - 384)) | (1L << (T__393 - 384)) | (1L << (T__394 - 384)) | (1L << (T__395 - 384)) | (1L << (T__396 - 384)) | (1L << (T__397 - 384)) | (1L << (T__398 - 384)) | (1L << (T__399 - 384)) | (1L << (T__400 - 384)) | (1L << (T__401 - 384)) | (1L << (T__402 - 384)) | (1L << (T__403 - 384)) | (1L << (T__404 - 384)) | (1L << (T__405 - 384)) | (1L << (T__406 - 384)) | (1L << (T__407 - 384)) | (1L << (T__408 - 384)) | (1L << (T__409 - 384)) | (1L << (T__410 - 384)) | (1L << (T__411 - 384)) | (1L << (T__412 - 384)) | (1L << (T__413 - 384)) | (1L << (T__414 - 384)) | (1L << (T__415 - 384)) | (1L << (T__416 - 384)) | (1L << (T__417 - 384)) | (1L << (T__418 - 384)) | (1L << (T__419 - 384)))) != 0)) {
				{
				setState(70);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__3:
				case T__4:
				case T__5:
				case T__6:
				case T__7:
				case T__8:
					{
					setState(65);
					prefix();
					setState(67);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__1) {
						{
						setState(66);
						match(T__1);
						}
					}

					}
					break;
				case T__9:
				case T__10:
				case T__11:
				case T__12:
				case T__13:
				case T__14:
				case T__15:
				case T__16:
				case T__17:
				case T__18:
				case T__19:
				case T__20:
				case T__21:
				case T__22:
				case T__23:
				case T__24:
				case T__25:
				case T__26:
				case T__27:
				case T__28:
				case T__29:
				case T__30:
				case T__31:
				case T__32:
				case T__33:
				case T__34:
				case T__35:
				case T__36:
				case T__37:
				case T__38:
				case T__39:
				case T__40:
				case T__41:
				case T__42:
				case T__43:
				case T__44:
				case T__45:
				case T__46:
				case T__47:
				case T__48:
				case T__49:
				case T__50:
				case T__51:
				case T__52:
				case T__53:
				case T__54:
				case T__55:
				case T__56:
				case T__57:
				case T__58:
				case T__59:
				case T__60:
				case T__61:
				case T__62:
				case T__63:
				case T__64:
				case T__65:
				case T__66:
				case T__67:
				case T__68:
				case T__69:
				case T__70:
				case T__71:
				case T__72:
				case T__73:
				case T__74:
				case T__75:
				case T__76:
				case T__77:
				case T__78:
				case T__80:
				case T__81:
				case T__82:
				case T__83:
				case T__84:
				case T__85:
				case T__86:
				case T__87:
				case T__88:
				case T__89:
				case T__90:
				case T__91:
				case T__92:
				case T__93:
				case T__94:
				case T__95:
				case T__96:
				case T__97:
				case T__98:
				case T__99:
				case T__100:
				case T__101:
				case T__102:
				case T__103:
				case T__104:
				case T__105:
				case T__106:
				case T__107:
				case T__108:
				case T__109:
				case T__110:
				case T__111:
				case T__112:
				case T__113:
				case T__114:
				case T__115:
				case T__116:
				case T__117:
				case T__118:
				case T__119:
				case T__120:
				case T__121:
				case T__122:
				case T__123:
				case T__124:
				case T__125:
				case T__126:
				case T__127:
				case T__128:
				case T__129:
				case T__130:
				case T__131:
				case T__132:
				case T__133:
				case T__134:
				case T__135:
				case T__136:
				case T__137:
				case T__138:
				case T__139:
				case T__140:
				case T__141:
				case T__142:
				case T__143:
				case T__144:
				case T__145:
				case T__146:
				case T__147:
				case T__148:
				case T__149:
				case T__150:
				case T__151:
				case T__152:
				case T__153:
				case T__154:
				case T__155:
				case T__156:
				case T__157:
				case T__158:
				case T__159:
				case T__160:
				case T__161:
				case T__162:
				case T__163:
				case T__164:
				case T__165:
				case T__166:
				case T__167:
				case T__168:
				case T__169:
				case T__170:
				case T__171:
				case T__172:
				case T__173:
				case T__174:
				case T__175:
				case T__176:
				case T__177:
				case T__178:
				case T__179:
				case T__180:
				case T__181:
				case T__182:
				case T__183:
				case T__184:
				case T__185:
				case T__186:
				case T__187:
				case T__188:
				case T__189:
				case T__190:
				case T__191:
				case T__192:
				case T__193:
				case T__194:
				case T__195:
				case T__196:
				case T__197:
				case T__198:
				case T__199:
				case T__200:
				case T__201:
				case T__202:
				case T__203:
				case T__204:
				case T__205:
				case T__206:
				case T__207:
				case T__208:
				case T__209:
				case T__210:
				case T__211:
				case T__212:
				case T__213:
				case T__214:
				case T__215:
				case T__216:
				case T__217:
				case T__218:
				case T__219:
				case T__220:
				case T__221:
				case T__222:
				case T__223:
				case T__224:
				case T__225:
				case T__226:
				case T__227:
				case T__228:
				case T__229:
				case T__230:
				case T__231:
				case T__232:
				case T__233:
				case T__234:
				case T__235:
				case T__236:
				case T__237:
				case T__238:
				case T__239:
				case T__240:
				case T__241:
				case T__242:
				case T__243:
				case T__244:
				case T__245:
				case T__246:
				case T__247:
				case T__248:
				case T__249:
				case T__250:
				case T__251:
				case T__252:
				case T__253:
				case T__254:
				case T__255:
				case T__256:
				case T__257:
				case T__258:
				case T__259:
				case T__260:
				case T__261:
				case T__262:
				case T__263:
				case T__264:
				case T__265:
				case T__266:
				case T__267:
				case T__268:
				case T__269:
				case T__270:
				case T__271:
				case T__272:
				case T__273:
				case T__274:
				case T__275:
				case T__276:
				case T__277:
				case T__278:
				case T__279:
				case T__280:
				case T__281:
				case T__282:
				case T__283:
				case T__284:
				case T__285:
				case T__286:
				case T__287:
				case T__288:
				case T__289:
				case T__290:
				case T__291:
				case T__292:
				case T__293:
				case T__294:
				case T__295:
				case T__296:
				case T__297:
				case T__298:
				case T__299:
				case T__300:
				case T__301:
				case T__302:
				case T__303:
				case T__304:
				case T__305:
				case T__306:
				case T__307:
				case T__308:
				case T__309:
				case T__310:
				case T__311:
				case T__312:
				case T__313:
				case T__314:
				case T__315:
				case T__316:
				case T__317:
				case T__318:
				case T__319:
				case T__320:
				case T__321:
				case T__322:
				case T__323:
				case T__324:
				case T__325:
				case T__326:
				case T__327:
				case T__328:
				case T__329:
				case T__330:
				case T__331:
				case T__332:
				case T__333:
				case T__334:
				case T__335:
				case T__336:
				case T__337:
				case T__338:
				case T__339:
				case T__340:
				case T__341:
				case T__342:
				case T__343:
				case T__344:
				case T__345:
				case T__346:
				case T__347:
				case T__348:
				case T__349:
				case T__350:
				case T__351:
				case T__352:
				case T__353:
				case T__354:
				case T__355:
				case T__356:
				case T__357:
				case T__358:
				case T__359:
				case T__360:
				case T__361:
				case T__362:
				case T__363:
				case T__364:
				case T__365:
				case T__366:
				case T__367:
				case T__368:
				case T__369:
				case T__370:
				case T__371:
				case T__372:
				case T__373:
				case T__374:
				case T__375:
				case T__376:
				case T__377:
				case T__378:
				case T__379:
				case T__380:
				case T__381:
				case T__382:
				case T__383:
				case T__384:
				case T__385:
				case T__386:
				case T__387:
				case T__388:
				case T__389:
				case T__390:
				case T__391:
				case T__392:
				case T__393:
				case T__394:
				case T__395:
				case T__396:
				case T__397:
				case T__398:
				case T__399:
				case T__400:
				case T__401:
				case T__402:
				case T__403:
				case T__404:
				case T__405:
				case T__406:
				case T__407:
				case T__408:
				case T__409:
				case T__410:
				case T__411:
				case T__412:
				case T__413:
				case T__414:
				case T__415:
				case T__416:
				case T__417:
				case T__418:
				case T__419:
					{
					 factory.setPrefix(null); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(72);
				assembly_instruction();
				setState(83);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__1 || _la==T__2) {
					{
					{
					setState(73);
					_la = _input.LA(1);
					if ( !(_la==T__1 || _la==T__2) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(76);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__3:
					case T__4:
					case T__5:
					case T__6:
					case T__7:
					case T__8:
						{
						setState(74);
						prefix();
						}
						break;
					case T__0:
					case T__1:
					case T__2:
					case T__9:
					case T__10:
					case T__11:
					case T__12:
					case T__13:
					case T__14:
					case T__15:
					case T__16:
					case T__17:
					case T__18:
					case T__19:
					case T__20:
					case T__21:
					case T__22:
					case T__23:
					case T__24:
					case T__25:
					case T__26:
					case T__27:
					case T__28:
					case T__29:
					case T__30:
					case T__31:
					case T__32:
					case T__33:
					case T__34:
					case T__35:
					case T__36:
					case T__37:
					case T__38:
					case T__39:
					case T__40:
					case T__41:
					case T__42:
					case T__43:
					case T__44:
					case T__45:
					case T__46:
					case T__47:
					case T__48:
					case T__49:
					case T__50:
					case T__51:
					case T__52:
					case T__53:
					case T__54:
					case T__55:
					case T__56:
					case T__57:
					case T__58:
					case T__59:
					case T__60:
					case T__61:
					case T__62:
					case T__63:
					case T__64:
					case T__65:
					case T__66:
					case T__67:
					case T__68:
					case T__69:
					case T__70:
					case T__71:
					case T__72:
					case T__73:
					case T__74:
					case T__75:
					case T__76:
					case T__77:
					case T__78:
					case T__80:
					case T__81:
					case T__82:
					case T__83:
					case T__84:
					case T__85:
					case T__86:
					case T__87:
					case T__88:
					case T__89:
					case T__90:
					case T__91:
					case T__92:
					case T__93:
					case T__94:
					case T__95:
					case T__96:
					case T__97:
					case T__98:
					case T__99:
					case T__100:
					case T__101:
					case T__102:
					case T__103:
					case T__104:
					case T__105:
					case T__106:
					case T__107:
					case T__108:
					case T__109:
					case T__110:
					case T__111:
					case T__112:
					case T__113:
					case T__114:
					case T__115:
					case T__116:
					case T__117:
					case T__118:
					case T__119:
					case T__120:
					case T__121:
					case T__122:
					case T__123:
					case T__124:
					case T__125:
					case T__126:
					case T__127:
					case T__128:
					case T__129:
					case T__130:
					case T__131:
					case T__132:
					case T__133:
					case T__134:
					case T__135:
					case T__136:
					case T__137:
					case T__138:
					case T__139:
					case T__140:
					case T__141:
					case T__142:
					case T__143:
					case T__144:
					case T__145:
					case T__146:
					case T__147:
					case T__148:
					case T__149:
					case T__150:
					case T__151:
					case T__152:
					case T__153:
					case T__154:
					case T__155:
					case T__156:
					case T__157:
					case T__158:
					case T__159:
					case T__160:
					case T__161:
					case T__162:
					case T__163:
					case T__164:
					case T__165:
					case T__166:
					case T__167:
					case T__168:
					case T__169:
					case T__170:
					case T__171:
					case T__172:
					case T__173:
					case T__174:
					case T__175:
					case T__176:
					case T__177:
					case T__178:
					case T__179:
					case T__180:
					case T__181:
					case T__182:
					case T__183:
					case T__184:
					case T__185:
					case T__186:
					case T__187:
					case T__188:
					case T__189:
					case T__190:
					case T__191:
					case T__192:
					case T__193:
					case T__194:
					case T__195:
					case T__196:
					case T__197:
					case T__198:
					case T__199:
					case T__200:
					case T__201:
					case T__202:
					case T__203:
					case T__204:
					case T__205:
					case T__206:
					case T__207:
					case T__208:
					case T__209:
					case T__210:
					case T__211:
					case T__212:
					case T__213:
					case T__214:
					case T__215:
					case T__216:
					case T__217:
					case T__218:
					case T__219:
					case T__220:
					case T__221:
					case T__222:
					case T__223:
					case T__224:
					case T__225:
					case T__226:
					case T__227:
					case T__228:
					case T__229:
					case T__230:
					case T__231:
					case T__232:
					case T__233:
					case T__234:
					case T__235:
					case T__236:
					case T__237:
					case T__238:
					case T__239:
					case T__240:
					case T__241:
					case T__242:
					case T__243:
					case T__244:
					case T__245:
					case T__246:
					case T__247:
					case T__248:
					case T__249:
					case T__250:
					case T__251:
					case T__252:
					case T__253:
					case T__254:
					case T__255:
					case T__256:
					case T__257:
					case T__258:
					case T__259:
					case T__260:
					case T__261:
					case T__262:
					case T__263:
					case T__264:
					case T__265:
					case T__266:
					case T__267:
					case T__268:
					case T__269:
					case T__270:
					case T__271:
					case T__272:
					case T__273:
					case T__274:
					case T__275:
					case T__276:
					case T__277:
					case T__278:
					case T__279:
					case T__280:
					case T__281:
					case T__282:
					case T__283:
					case T__284:
					case T__285:
					case T__286:
					case T__287:
					case T__288:
					case T__289:
					case T__290:
					case T__291:
					case T__292:
					case T__293:
					case T__294:
					case T__295:
					case T__296:
					case T__297:
					case T__298:
					case T__299:
					case T__300:
					case T__301:
					case T__302:
					case T__303:
					case T__304:
					case T__305:
					case T__306:
					case T__307:
					case T__308:
					case T__309:
					case T__310:
					case T__311:
					case T__312:
					case T__313:
					case T__314:
					case T__315:
					case T__316:
					case T__317:
					case T__318:
					case T__319:
					case T__320:
					case T__321:
					case T__322:
					case T__323:
					case T__324:
					case T__325:
					case T__326:
					case T__327:
					case T__328:
					case T__329:
					case T__330:
					case T__331:
					case T__332:
					case T__333:
					case T__334:
					case T__335:
					case T__336:
					case T__337:
					case T__338:
					case T__339:
					case T__340:
					case T__341:
					case T__342:
					case T__343:
					case T__344:
					case T__345:
					case T__346:
					case T__347:
					case T__348:
					case T__349:
					case T__350:
					case T__351:
					case T__352:
					case T__353:
					case T__354:
					case T__355:
					case T__356:
					case T__357:
					case T__358:
					case T__359:
					case T__360:
					case T__361:
					case T__362:
					case T__363:
					case T__364:
					case T__365:
					case T__366:
					case T__367:
					case T__368:
					case T__369:
					case T__370:
					case T__371:
					case T__372:
					case T__373:
					case T__374:
					case T__375:
					case T__376:
					case T__377:
					case T__378:
					case T__379:
					case T__380:
					case T__381:
					case T__382:
					case T__383:
					case T__384:
					case T__385:
					case T__386:
					case T__387:
					case T__388:
					case T__389:
					case T__390:
					case T__391:
					case T__392:
					case T__393:
					case T__394:
					case T__395:
					case T__396:
					case T__397:
					case T__398:
					case T__399:
					case T__400:
					case T__401:
					case T__402:
					case T__403:
					case T__404:
					case T__405:
					case T__406:
					case T__407:
					case T__408:
					case T__409:
					case T__410:
					case T__411:
					case T__412:
					case T__413:
					case T__414:
					case T__415:
					case T__416:
					case T__417:
					case T__418:
					case T__419:
						{
						 factory.setPrefix(null); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(79);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__14) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25) | (1L << T__26) | (1L << T__27) | (1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37) | (1L << T__38) | (1L << T__39) | (1L << T__40) | (1L << T__41) | (1L << T__42) | (1L << T__43) | (1L << T__44) | (1L << T__45) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__74 - 64)) | (1L << (T__75 - 64)) | (1L << (T__76 - 64)) | (1L << (T__77 - 64)) | (1L << (T__78 - 64)) | (1L << (T__80 - 64)) | (1L << (T__81 - 64)) | (1L << (T__82 - 64)) | (1L << (T__83 - 64)) | (1L << (T__84 - 64)) | (1L << (T__85 - 64)) | (1L << (T__86 - 64)) | (1L << (T__87 - 64)) | (1L << (T__88 - 64)) | (1L << (T__89 - 64)) | (1L << (T__90 - 64)) | (1L << (T__91 - 64)) | (1L << (T__92 - 64)) | (1L << (T__93 - 64)) | (1L << (T__94 - 64)) | (1L << (T__95 - 64)) | (1L << (T__96 - 64)) | (1L << (T__97 - 64)) | (1L << (T__98 - 64)) | (1L << (T__99 - 64)) | (1L << (T__100 - 64)) | (1L << (T__101 - 64)) | (1L << (T__102 - 64)) | (1L << (T__103 - 64)) | (1L << (T__104 - 64)) | (1L << (T__105 - 64)) | (1L << (T__106 - 64)) | (1L << (T__107 - 64)) | (1L << (T__108 - 64)) | (1L << (T__109 - 64)) | (1L << (T__110 - 64)) | (1L << (T__111 - 64)) | (1L << (T__112 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__115 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__122 - 64)) | (1L << (T__123 - 64)) | (1L << (T__124 - 64)) | (1L << (T__125 - 64)) | (1L << (T__126 - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (T__127 - 128)) | (1L << (T__128 - 128)) | (1L << (T__129 - 128)) | (1L << (T__130 - 128)) | (1L << (T__131 - 128)) | (1L << (T__132 - 128)) | (1L << (T__133 - 128)) | (1L << (T__134 - 128)) | (1L << (T__135 - 128)) | (1L << (T__136 - 128)) | (1L << (T__137 - 128)) | (1L << (T__138 - 128)) | (1L << (T__139 - 128)) | (1L << (T__140 - 128)) | (1L << (T__141 - 128)) | (1L << (T__142 - 128)) | (1L << (T__143 - 128)) | (1L << (T__144 - 128)) | (1L << (T__145 - 128)) | (1L << (T__146 - 128)) | (1L << (T__147 - 128)) | (1L << (T__148 - 128)) | (1L << (T__149 - 128)) | (1L << (T__150 - 128)) | (1L << (T__151 - 128)) | (1L << (T__152 - 128)) | (1L << (T__153 - 128)) | (1L << (T__154 - 128)) | (1L << (T__155 - 128)) | (1L << (T__156 - 128)) | (1L << (T__157 - 128)) | (1L << (T__158 - 128)) | (1L << (T__159 - 128)) | (1L << (T__160 - 128)) | (1L << (T__161 - 128)) | (1L << (T__162 - 128)) | (1L << (T__163 - 128)) | (1L << (T__164 - 128)) | (1L << (T__165 - 128)) | (1L << (T__166 - 128)) | (1L << (T__167 - 128)) | (1L << (T__168 - 128)) | (1L << (T__169 - 128)) | (1L << (T__170 - 128)) | (1L << (T__171 - 128)) | (1L << (T__172 - 128)) | (1L << (T__173 - 128)) | (1L << (T__174 - 128)) | (1L << (T__175 - 128)) | (1L << (T__176 - 128)) | (1L << (T__177 - 128)) | (1L << (T__178 - 128)) | (1L << (T__179 - 128)) | (1L << (T__180 - 128)) | (1L << (T__181 - 128)) | (1L << (T__182 - 128)) | (1L << (T__183 - 128)) | (1L << (T__184 - 128)) | (1L << (T__185 - 128)) | (1L << (T__186 - 128)) | (1L << (T__187 - 128)) | (1L << (T__188 - 128)) | (1L << (T__189 - 128)) | (1L << (T__190 - 128)))) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & ((1L << (T__191 - 192)) | (1L << (T__192 - 192)) | (1L << (T__193 - 192)) | (1L << (T__194 - 192)) | (1L << (T__195 - 192)) | (1L << (T__196 - 192)) | (1L << (T__197 - 192)) | (1L << (T__198 - 192)) | (1L << (T__199 - 192)) | (1L << (T__200 - 192)) | (1L << (T__201 - 192)) | (1L << (T__202 - 192)) | (1L << (T__203 - 192)) | (1L << (T__204 - 192)) | (1L << (T__205 - 192)) | (1L << (T__206 - 192)) | (1L << (T__207 - 192)) | (1L << (T__208 - 192)) | (1L << (T__209 - 192)) | (1L << (T__210 - 192)) | (1L << (T__211 - 192)) | (1L << (T__212 - 192)) | (1L << (T__213 - 192)) | (1L << (T__214 - 192)) | (1L << (T__215 - 192)) | (1L << (T__216 - 192)) | (1L << (T__217 - 192)) | (1L << (T__218 - 192)) | (1L << (T__219 - 192)) | (1L << (T__220 - 192)) | (1L << (T__221 - 192)) | (1L << (T__222 - 192)) | (1L << (T__223 - 192)) | (1L << (T__224 - 192)) | (1L << (T__225 - 192)) | (1L << (T__226 - 192)) | (1L << (T__227 - 192)) | (1L << (T__228 - 192)) | (1L << (T__229 - 192)) | (1L << (T__230 - 192)) | (1L << (T__231 - 192)) | (1L << (T__232 - 192)) | (1L << (T__233 - 192)) | (1L << (T__234 - 192)) | (1L << (T__235 - 192)) | (1L << (T__236 - 192)) | (1L << (T__237 - 192)) | (1L << (T__238 - 192)) | (1L << (T__239 - 192)) | (1L << (T__240 - 192)) | (1L << (T__241 - 192)) | (1L << (T__242 - 192)) | (1L << (T__243 - 192)) | (1L << (T__244 - 192)) | (1L << (T__245 - 192)) | (1L << (T__246 - 192)) | (1L << (T__247 - 192)) | (1L << (T__248 - 192)) | (1L << (T__249 - 192)) | (1L << (T__250 - 192)) | (1L << (T__251 - 192)) | (1L << (T__252 - 192)) | (1L << (T__253 - 192)) | (1L << (T__254 - 192)))) != 0) || ((((_la - 256)) & ~0x3f) == 0 && ((1L << (_la - 256)) & ((1L << (T__255 - 256)) | (1L << (T__256 - 256)) | (1L << (T__257 - 256)) | (1L << (T__258 - 256)) | (1L << (T__259 - 256)) | (1L << (T__260 - 256)) | (1L << (T__261 - 256)) | (1L << (T__262 - 256)) | (1L << (T__263 - 256)) | (1L << (T__264 - 256)) | (1L << (T__265 - 256)) | (1L << (T__266 - 256)) | (1L << (T__267 - 256)) | (1L << (T__268 - 256)) | (1L << (T__269 - 256)) | (1L << (T__270 - 256)) | (1L << (T__271 - 256)) | (1L << (T__272 - 256)) | (1L << (T__273 - 256)) | (1L << (T__274 - 256)) | (1L << (T__275 - 256)) | (1L << (T__276 - 256)) | (1L << (T__277 - 256)) | (1L << (T__278 - 256)) | (1L << (T__279 - 256)) | (1L << (T__280 - 256)) | (1L << (T__281 - 256)) | (1L << (T__282 - 256)) | (1L << (T__283 - 256)) | (1L << (T__284 - 256)) | (1L << (T__285 - 256)) | (1L << (T__286 - 256)) | (1L << (T__287 - 256)) | (1L << (T__288 - 256)) | (1L << (T__289 - 256)) | (1L << (T__290 - 256)) | (1L << (T__291 - 256)) | (1L << (T__292 - 256)) | (1L << (T__293 - 256)) | (1L << (T__294 - 256)) | (1L << (T__295 - 256)) | (1L << (T__296 - 256)) | (1L << (T__297 - 256)) | (1L << (T__298 - 256)) | (1L << (T__299 - 256)) | (1L << (T__300 - 256)) | (1L << (T__301 - 256)) | (1L << (T__302 - 256)) | (1L << (T__303 - 256)) | (1L << (T__304 - 256)) | (1L << (T__305 - 256)) | (1L << (T__306 - 256)) | (1L << (T__307 - 256)) | (1L << (T__308 - 256)) | (1L << (T__309 - 256)) | (1L << (T__310 - 256)) | (1L << (T__311 - 256)) | (1L << (T__312 - 256)) | (1L << (T__313 - 256)) | (1L << (T__314 - 256)) | (1L << (T__315 - 256)) | (1L << (T__316 - 256)) | (1L << (T__317 - 256)) | (1L << (T__318 - 256)))) != 0) || ((((_la - 320)) & ~0x3f) == 0 && ((1L << (_la - 320)) & ((1L << (T__319 - 320)) | (1L << (T__320 - 320)) | (1L << (T__321 - 320)) | (1L << (T__322 - 320)) | (1L << (T__323 - 320)) | (1L << (T__324 - 320)) | (1L << (T__325 - 320)) | (1L << (T__326 - 320)) | (1L << (T__327 - 320)) | (1L << (T__328 - 320)) | (1L << (T__329 - 320)) | (1L << (T__330 - 320)) | (1L << (T__331 - 320)) | (1L << (T__332 - 320)) | (1L << (T__333 - 320)) | (1L << (T__334 - 320)) | (1L << (T__335 - 320)) | (1L << (T__336 - 320)) | (1L << (T__337 - 320)) | (1L << (T__338 - 320)) | (1L << (T__339 - 320)) | (1L << (T__340 - 320)) | (1L << (T__341 - 320)) | (1L << (T__342 - 320)) | (1L << (T__343 - 320)) | (1L << (T__344 - 320)) | (1L << (T__345 - 320)) | (1L << (T__346 - 320)) | (1L << (T__347 - 320)) | (1L << (T__348 - 320)) | (1L << (T__349 - 320)) | (1L << (T__350 - 320)) | (1L << (T__351 - 320)) | (1L << (T__352 - 320)) | (1L << (T__353 - 320)) | (1L << (T__354 - 320)) | (1L << (T__355 - 320)) | (1L << (T__356 - 320)) | (1L << (T__357 - 320)) | (1L << (T__358 - 320)) | (1L << (T__359 - 320)) | (1L << (T__360 - 320)) | (1L << (T__361 - 320)) | (1L << (T__362 - 320)) | (1L << (T__363 - 320)) | (1L << (T__364 - 320)) | (1L << (T__365 - 320)) | (1L << (T__366 - 320)) | (1L << (T__367 - 320)) | (1L << (T__368 - 320)) | (1L << (T__369 - 320)) | (1L << (T__370 - 320)) | (1L << (T__371 - 320)) | (1L << (T__372 - 320)) | (1L << (T__373 - 320)) | (1L << (T__374 - 320)) | (1L << (T__375 - 320)) | (1L << (T__376 - 320)) | (1L << (T__377 - 320)) | (1L << (T__378 - 320)) | (1L << (T__379 - 320)) | (1L << (T__380 - 320)) | (1L << (T__381 - 320)) | (1L << (T__382 - 320)))) != 0) || ((((_la - 384)) & ~0x3f) == 0 && ((1L << (_la - 384)) & ((1L << (T__383 - 384)) | (1L << (T__384 - 384)) | (1L << (T__385 - 384)) | (1L << (T__386 - 384)) | (1L << (T__387 - 384)) | (1L << (T__388 - 384)) | (1L << (T__389 - 384)) | (1L << (T__390 - 384)) | (1L << (T__391 - 384)) | (1L << (T__392 - 384)) | (1L << (T__393 - 384)) | (1L << (T__394 - 384)) | (1L << (T__395 - 384)) | (1L << (T__396 - 384)) | (1L << (T__397 - 384)) | (1L << (T__398 - 384)) | (1L << (T__399 - 384)) | (1L << (T__400 - 384)) | (1L << (T__401 - 384)) | (1L << (T__402 - 384)) | (1L << (T__403 - 384)) | (1L << (T__404 - 384)) | (1L << (T__405 - 384)) | (1L << (T__406 - 384)) | (1L << (T__407 - 384)) | (1L << (T__408 - 384)) | (1L << (T__409 - 384)) | (1L << (T__410 - 384)) | (1L << (T__411 - 384)) | (1L << (T__412 - 384)) | (1L << (T__413 - 384)) | (1L << (T__414 - 384)) | (1L << (T__415 - 384)) | (1L << (T__416 - 384)) | (1L << (T__417 - 384)) | (1L << (T__418 - 384)) | (1L << (T__419 - 384)))) != 0)) {
						{
						setState(78);
						assembly_instruction();
						}
					}

					}
					}
					setState(85);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(88);
			match(T__0);
			 root = factory.finishInline(); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PrefixContext extends ParserRuleContext {
		public Token op;
		public PrefixContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_prefix; }
	}

	public final PrefixContext prefix() throws RecognitionException {
		PrefixContext _localctx = new PrefixContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_prefix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(91);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8))) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 factory.setPrefix(_localctx.op.getText()); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Assembly_instructionContext extends ParserRuleContext {
		public Zero_opContext zero_op() {
			return getRuleContext(Zero_opContext.class,0);
		}
		public Unary_op8Context unary_op8() {
			return getRuleContext(Unary_op8Context.class,0);
		}
		public Unary_op16Context unary_op16() {
			return getRuleContext(Unary_op16Context.class,0);
		}
		public Unary_op32Context unary_op32() {
			return getRuleContext(Unary_op32Context.class,0);
		}
		public Unary_op64Context unary_op64() {
			return getRuleContext(Unary_op64Context.class,0);
		}
		public Unary_opContext unary_op() {
			return getRuleContext(Unary_opContext.class,0);
		}
		public Binary_op8Context binary_op8() {
			return getRuleContext(Binary_op8Context.class,0);
		}
		public Binary_op16Context binary_op16() {
			return getRuleContext(Binary_op16Context.class,0);
		}
		public Binary_op32Context binary_op32() {
			return getRuleContext(Binary_op32Context.class,0);
		}
		public Binary_op64Context binary_op64() {
			return getRuleContext(Binary_op64Context.class,0);
		}
		public Binary_opContext binary_op() {
			return getRuleContext(Binary_opContext.class,0);
		}
		public Imul_divContext imul_div() {
			return getRuleContext(Imul_divContext.class,0);
		}
		public JumpContext jump() {
			return getRuleContext(JumpContext.class,0);
		}
		public Int_valueContext int_value() {
			return getRuleContext(Int_valueContext.class,0);
		}
		public Assembly_instructionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assembly_instruction; }
	}

	public final Assembly_instructionContext assembly_instruction() throws RecognitionException {
		Assembly_instructionContext _localctx = new Assembly_instructionContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_assembly_instruction);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(108);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__49:
			case T__50:
			case T__51:
			case T__52:
			case T__53:
			case T__54:
			case T__55:
			case T__56:
			case T__57:
			case T__58:
			case T__59:
			case T__60:
			case T__61:
			case T__62:
			case T__63:
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
			case T__70:
			case T__71:
			case T__72:
			case T__73:
			case T__74:
			case T__75:
			case T__76:
				{
				setState(94);
				zero_op();
				}
				break;
			case T__88:
			case T__89:
			case T__90:
			case T__91:
			case T__92:
			case T__93:
				{
				setState(95);
				unary_op8();
				}
				break;
			case T__94:
			case T__95:
			case T__96:
			case T__97:
			case T__98:
			case T__99:
			case T__100:
			case T__101:
				{
				setState(96);
				unary_op16();
				}
				break;
			case T__102:
			case T__103:
			case T__104:
			case T__105:
			case T__106:
			case T__107:
			case T__108:
			case T__109:
			case T__110:
				{
				setState(97);
				unary_op32();
				}
				break;
			case T__111:
			case T__112:
			case T__113:
			case T__114:
			case T__115:
			case T__116:
			case T__117:
			case T__118:
			case T__119:
				{
				setState(98);
				unary_op64();
				}
				break;
			case T__120:
			case T__121:
			case T__122:
			case T__123:
			case T__124:
			case T__125:
			case T__126:
			case T__127:
			case T__128:
			case T__129:
			case T__130:
			case T__131:
			case T__132:
			case T__133:
			case T__134:
			case T__135:
			case T__136:
			case T__137:
			case T__138:
			case T__139:
			case T__140:
			case T__141:
			case T__142:
			case T__143:
			case T__144:
			case T__145:
			case T__146:
			case T__147:
			case T__148:
			case T__149:
			case T__150:
			case T__151:
			case T__152:
			case T__153:
			case T__154:
			case T__155:
			case T__156:
			case T__157:
			case T__158:
			case T__159:
			case T__160:
				{
				setState(99);
				unary_op();
				}
				break;
			case T__161:
			case T__162:
			case T__163:
			case T__164:
			case T__165:
			case T__166:
			case T__167:
			case T__168:
			case T__169:
			case T__170:
			case T__171:
			case T__172:
			case T__173:
			case T__174:
			case T__175:
			case T__176:
			case T__177:
			case T__178:
			case T__179:
			case T__180:
			case T__181:
				{
				setState(100);
				binary_op8();
				}
				break;
			case T__182:
			case T__183:
			case T__184:
			case T__185:
			case T__186:
			case T__187:
			case T__188:
			case T__189:
			case T__190:
			case T__191:
			case T__192:
			case T__193:
			case T__194:
			case T__195:
			case T__196:
			case T__197:
			case T__198:
			case T__199:
			case T__200:
			case T__201:
			case T__202:
			case T__203:
			case T__204:
			case T__205:
			case T__206:
			case T__207:
			case T__208:
			case T__209:
			case T__210:
			case T__211:
			case T__212:
			case T__213:
			case T__214:
			case T__215:
			case T__216:
			case T__217:
			case T__218:
			case T__219:
			case T__220:
			case T__221:
			case T__222:
			case T__223:
			case T__224:
			case T__225:
			case T__226:
			case T__227:
			case T__228:
			case T__229:
			case T__230:
			case T__231:
			case T__232:
			case T__233:
			case T__234:
			case T__235:
			case T__236:
			case T__237:
			case T__238:
			case T__239:
			case T__240:
				{
				setState(101);
				binary_op16();
				}
				break;
			case T__241:
			case T__242:
			case T__243:
			case T__244:
			case T__245:
			case T__246:
			case T__247:
			case T__248:
			case T__249:
			case T__250:
			case T__251:
			case T__252:
			case T__253:
			case T__254:
			case T__255:
			case T__256:
			case T__257:
			case T__258:
			case T__259:
			case T__260:
			case T__261:
			case T__262:
			case T__263:
			case T__264:
			case T__265:
			case T__266:
			case T__267:
			case T__268:
			case T__269:
			case T__270:
			case T__271:
			case T__272:
			case T__273:
			case T__274:
			case T__275:
			case T__276:
			case T__277:
			case T__278:
			case T__279:
			case T__280:
			case T__281:
			case T__282:
			case T__283:
			case T__284:
			case T__285:
			case T__286:
			case T__287:
			case T__288:
			case T__289:
			case T__290:
			case T__291:
			case T__292:
			case T__293:
			case T__294:
			case T__295:
			case T__296:
			case T__297:
			case T__298:
			case T__299:
			case T__300:
			case T__301:
				{
				setState(102);
				binary_op32();
				}
				break;
			case T__302:
			case T__303:
			case T__304:
			case T__305:
			case T__306:
			case T__307:
			case T__308:
			case T__309:
			case T__310:
			case T__311:
			case T__312:
			case T__313:
			case T__314:
			case T__315:
			case T__316:
			case T__317:
			case T__318:
			case T__319:
			case T__320:
			case T__321:
			case T__322:
			case T__323:
			case T__324:
			case T__325:
			case T__326:
			case T__327:
			case T__328:
			case T__329:
			case T__330:
			case T__331:
			case T__332:
			case T__333:
			case T__334:
			case T__335:
			case T__336:
			case T__337:
			case T__338:
			case T__339:
			case T__340:
			case T__341:
			case T__342:
			case T__343:
			case T__344:
			case T__345:
			case T__346:
			case T__347:
			case T__348:
			case T__349:
			case T__350:
			case T__351:
			case T__352:
			case T__353:
			case T__354:
			case T__355:
			case T__356:
			case T__357:
			case T__358:
			case T__359:
			case T__360:
			case T__361:
			case T__362:
			case T__363:
				{
				setState(103);
				binary_op64();
				}
				break;
			case T__364:
			case T__365:
			case T__366:
			case T__367:
			case T__368:
			case T__369:
			case T__370:
			case T__371:
			case T__372:
			case T__373:
			case T__374:
			case T__375:
			case T__376:
			case T__377:
			case T__378:
			case T__379:
			case T__380:
			case T__381:
			case T__382:
			case T__383:
			case T__384:
			case T__385:
			case T__386:
			case T__387:
			case T__388:
			case T__389:
			case T__390:
			case T__391:
			case T__392:
			case T__393:
			case T__394:
			case T__395:
			case T__396:
			case T__397:
			case T__398:
			case T__399:
			case T__400:
			case T__401:
			case T__402:
			case T__403:
			case T__404:
			case T__405:
			case T__406:
			case T__407:
			case T__408:
			case T__409:
			case T__410:
			case T__411:
			case T__412:
			case T__413:
			case T__414:
			case T__415:
			case T__416:
			case T__417:
			case T__418:
			case T__419:
				{
				setState(104);
				binary_op();
				}
				break;
			case T__77:
			case T__78:
			case T__80:
			case T__81:
			case T__82:
			case T__83:
			case T__84:
			case T__85:
			case T__86:
			case T__87:
				{
				setState(105);
				imul_div();
				}
				break;
			case T__10:
			case T__11:
			case T__12:
			case T__13:
			case T__14:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__24:
			case T__25:
			case T__26:
			case T__27:
			case T__28:
			case T__29:
			case T__30:
			case T__31:
			case T__32:
			case T__33:
			case T__34:
			case T__35:
			case T__36:
			case T__37:
			case T__38:
			case T__39:
			case T__40:
			case T__41:
			case T__42:
			case T__43:
			case T__44:
			case T__45:
			case T__46:
			case T__47:
			case T__48:
				{
				setState(106);
				jump();
				}
				break;
			case T__9:
				{
				setState(107);
				int_value();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Int_valueContext extends ParserRuleContext {
		public ImmediateContext immediate;
		public ImmediateContext immediate() {
			return getRuleContext(ImmediateContext.class,0);
		}
		public Int_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_int_value; }
	}

	public final Int_valueContext int_value() throws RecognitionException {
		Int_valueContext _localctx = new Int_valueContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_int_value);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(110);
			match(T__9);
			setState(111);
			_localctx.immediate = immediate();
			 factory.createInt(_localctx.immediate.op); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class JumpContext extends ParserRuleContext {
		public Token op;
		public Operand64Context bta;
		public Operand64Context operand64() {
			return getRuleContext(Operand64Context.class,0);
		}
		public JumpContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_jump; }
	}

	public final JumpContext jump() throws RecognitionException {
		JumpContext _localctx = new JumpContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_jump);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(114);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__14) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25) | (1L << T__26) | (1L << T__27) | (1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37) | (1L << T__38) | (1L << T__39) | (1L << T__40) | (1L << T__41) | (1L << T__42) | (1L << T__43) | (1L << T__44) | (1L << T__45) | (1L << T__46) | (1L << T__47) | (1L << T__48))) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(115);
			_localctx.bta = operand64();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Zero_opContext extends ParserRuleContext {
		public Token op;
		public Zero_opContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_zero_op; }
	}

	public final Zero_opContext zero_op() throws RecognitionException {
		Zero_opContext _localctx = new Zero_opContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_zero_op);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(117);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 50)) & ~0x3f) == 0 && ((1L << (_la - 50)) & ((1L << (T__49 - 50)) | (1L << (T__50 - 50)) | (1L << (T__51 - 50)) | (1L << (T__52 - 50)) | (1L << (T__53 - 50)) | (1L << (T__54 - 50)) | (1L << (T__55 - 50)) | (1L << (T__56 - 50)) | (1L << (T__57 - 50)) | (1L << (T__58 - 50)) | (1L << (T__59 - 50)) | (1L << (T__60 - 50)) | (1L << (T__61 - 50)) | (1L << (T__62 - 50)) | (1L << (T__63 - 50)) | (1L << (T__64 - 50)) | (1L << (T__65 - 50)) | (1L << (T__66 - 50)) | (1L << (T__67 - 50)) | (1L << (T__68 - 50)) | (1L << (T__69 - 50)) | (1L << (T__70 - 50)) | (1L << (T__71 - 50)) | (1L << (T__72 - 50)) | (1L << (T__73 - 50)) | (1L << (T__74 - 50)) | (1L << (T__75 - 50)) | (1L << (T__76 - 50)))) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 factory.createOperation(_localctx.op.getText()); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Imul_divContext extends ParserRuleContext {
		public Token op1;
		public Operand8Context a1;
		public Operand8Context b1;
		public Operand8Context c1;
		public Token op2;
		public Operand16Context a2;
		public Operand16Context b2;
		public Operand16Context c2;
		public Token op3;
		public Operand32Context a3;
		public Operand32Context b3;
		public Operand32Context c3;
		public Token op4;
		public Operand64Context a4;
		public Operand64Context b4;
		public Operand64Context c4;
		public Token op5;
		public OperandContext a5;
		public OperandContext b5;
		public OperandContext c5;
		public List<Operand8Context> operand8() {
			return getRuleContexts(Operand8Context.class);
		}
		public Operand8Context operand8(int i) {
			return getRuleContext(Operand8Context.class,i);
		}
		public List<Operand16Context> operand16() {
			return getRuleContexts(Operand16Context.class);
		}
		public Operand16Context operand16(int i) {
			return getRuleContext(Operand16Context.class,i);
		}
		public List<Operand32Context> operand32() {
			return getRuleContexts(Operand32Context.class);
		}
		public Operand32Context operand32(int i) {
			return getRuleContext(Operand32Context.class,i);
		}
		public List<Operand64Context> operand64() {
			return getRuleContexts(Operand64Context.class);
		}
		public Operand64Context operand64(int i) {
			return getRuleContext(Operand64Context.class,i);
		}
		public List<OperandContext> operand() {
			return getRuleContexts(OperandContext.class);
		}
		public OperandContext operand(int i) {
			return getRuleContext(OperandContext.class,i);
		}
		public Imul_divContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_imul_div; }
	}

	public final Imul_divContext imul_div() throws RecognitionException {
		Imul_divContext _localctx = new Imul_divContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_imul_div);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(190);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__77:
			case T__78:
				{
				setState(120);
				_localctx.op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__77 || _la==T__78) ) {
					_localctx.op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(121);
				_localctx.a1 = operand8();
				setState(132);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op1.getText(), _localctx.a1.op); 
					}
					break;
				case T__79:
					{
					setState(123);
					match(T__79);
					setState(124);
					_localctx.b1 = operand8();
					setState(130);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op1.getText(), _localctx.a1.op, _localctx.b1.op); 
						}
						break;
					case T__79:
						{
						setState(126);
						match(T__79);
						setState(127);
						_localctx.c1 = operand8();
						 factory.createTernaryOperation(_localctx.op1.getText(), _localctx.a1.op, _localctx.b1.op, _localctx.c1.op); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case T__80:
			case T__81:
				{
				setState(134);
				_localctx.op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__80 || _la==T__81) ) {
					_localctx.op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(135);
				_localctx.a2 = operand16();
				setState(146);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op2.getText(), _localctx.a2.op); 
					}
					break;
				case T__79:
					{
					setState(137);
					match(T__79);
					setState(138);
					_localctx.b2 = operand16();
					setState(144);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op2.getText(), _localctx.a2.op, _localctx.b2.op); 
						}
						break;
					case T__79:
						{
						setState(140);
						match(T__79);
						setState(141);
						_localctx.c2 = operand16();
						 factory.createTernaryOperation(_localctx.op2.getText(), _localctx.a2.op, _localctx.b2.op, _localctx.c2.op); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case T__82:
			case T__83:
				{
				setState(148);
				_localctx.op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__82 || _la==T__83) ) {
					_localctx.op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(149);
				_localctx.a3 = operand32();
				setState(160);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op3.getText(), _localctx.a3.op); 
					}
					break;
				case T__79:
					{
					setState(151);
					match(T__79);
					setState(152);
					_localctx.b3 = operand32();
					setState(158);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op3.getText(), _localctx.a3.op, _localctx.b3.op); 
						}
						break;
					case T__79:
						{
						setState(154);
						match(T__79);
						setState(155);
						_localctx.c3 = operand32();
						 factory.createTernaryOperation(_localctx.op3.getText(), _localctx.a3.op, _localctx.b3.op, _localctx.c3.op); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case T__84:
			case T__85:
				{
				setState(162);
				_localctx.op4 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__84 || _la==T__85) ) {
					_localctx.op4 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(163);
				_localctx.a4 = operand64();
				setState(174);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op4.getText(), _localctx.a4.op); 
					}
					break;
				case T__79:
					{
					setState(165);
					match(T__79);
					setState(166);
					_localctx.b4 = operand64();
					setState(172);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op4.getText(), _localctx.a4.op, _localctx.b4.op); 
						}
						break;
					case T__79:
						{
						setState(168);
						match(T__79);
						setState(169);
						_localctx.c4 = operand64();
						 factory.createTernaryOperation(_localctx.op4.getText(), _localctx.a4.op, _localctx.b4.op, _localctx.c4.op); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case T__86:
			case T__87:
				{
				setState(176);
				_localctx.op5 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__86 || _la==T__87) ) {
					_localctx.op5 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(177);
				_localctx.a5 = operand();
				setState(188);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op5.getText(), _localctx.a5.op); 
					}
					break;
				case T__79:
					{
					setState(179);
					match(T__79);
					setState(180);
					_localctx.b5 = operand();
					setState(186);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op5.getText(), _localctx.a5.op, _localctx.b5.op); 
						}
						break;
					case T__79:
						{
						setState(182);
						match(T__79);
						setState(183);
						_localctx.c5 = operand();
						 factory.createTernaryOperation(_localctx.op5.getText(), _localctx.a5.op, _localctx.b5.op, _localctx.c5.op); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Unary_op8Context extends ParserRuleContext {
		public Token op;
		public Operand8Context operand8;
		public Operand8Context operand8() {
			return getRuleContext(Operand8Context.class,0);
		}
		public Unary_op8Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unary_op8; }
	}

	public final Unary_op8Context unary_op8() throws RecognitionException {
		Unary_op8Context _localctx = new Unary_op8Context(_ctx, getState());
		enterRule(_localctx, 14, RULE_unary_op8);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(192);
			((Unary_op8Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 89)) & ~0x3f) == 0 && ((1L << (_la - 89)) & ((1L << (T__88 - 89)) | (1L << (T__89 - 89)) | (1L << (T__90 - 89)) | (1L << (T__91 - 89)) | (1L << (T__92 - 89)) | (1L << (T__93 - 89)))) != 0)) ) {
				((Unary_op8Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(193);
			((Unary_op8Context)_localctx).operand8 = operand8();
			 factory.createUnaryOperation(((Unary_op8Context)_localctx).op.getText(), ((Unary_op8Context)_localctx).operand8.op); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Unary_op16Context extends ParserRuleContext {
		public Token op;
		public Operand16Context operand16;
		public Operand16Context operand16() {
			return getRuleContext(Operand16Context.class,0);
		}
		public Unary_op16Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unary_op16; }
	}

	public final Unary_op16Context unary_op16() throws RecognitionException {
		Unary_op16Context _localctx = new Unary_op16Context(_ctx, getState());
		enterRule(_localctx, 16, RULE_unary_op16);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(196);
			((Unary_op16Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 95)) & ~0x3f) == 0 && ((1L << (_la - 95)) & ((1L << (T__94 - 95)) | (1L << (T__95 - 95)) | (1L << (T__96 - 95)) | (1L << (T__97 - 95)) | (1L << (T__98 - 95)) | (1L << (T__99 - 95)) | (1L << (T__100 - 95)) | (1L << (T__101 - 95)))) != 0)) ) {
				((Unary_op16Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(197);
			((Unary_op16Context)_localctx).operand16 = operand16();
			 factory.createUnaryOperation(((Unary_op16Context)_localctx).op.getText(), ((Unary_op16Context)_localctx).operand16.op); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Unary_op32Context extends ParserRuleContext {
		public Token op;
		public Operand32Context operand32;
		public Operand32Context operand32() {
			return getRuleContext(Operand32Context.class,0);
		}
		public Unary_op32Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unary_op32; }
	}

	public final Unary_op32Context unary_op32() throws RecognitionException {
		Unary_op32Context _localctx = new Unary_op32Context(_ctx, getState());
		enterRule(_localctx, 18, RULE_unary_op32);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(200);
			((Unary_op32Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 103)) & ~0x3f) == 0 && ((1L << (_la - 103)) & ((1L << (T__102 - 103)) | (1L << (T__103 - 103)) | (1L << (T__104 - 103)) | (1L << (T__105 - 103)) | (1L << (T__106 - 103)) | (1L << (T__107 - 103)) | (1L << (T__108 - 103)) | (1L << (T__109 - 103)) | (1L << (T__110 - 103)))) != 0)) ) {
				((Unary_op32Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(201);
			((Unary_op32Context)_localctx).operand32 = operand32();
			 factory.createUnaryOperation(((Unary_op32Context)_localctx).op.getText(), ((Unary_op32Context)_localctx).operand32.op); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Unary_op64Context extends ParserRuleContext {
		public Token op;
		public Operand64Context operand64;
		public Operand64Context operand64() {
			return getRuleContext(Operand64Context.class,0);
		}
		public Unary_op64Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unary_op64; }
	}

	public final Unary_op64Context unary_op64() throws RecognitionException {
		Unary_op64Context _localctx = new Unary_op64Context(_ctx, getState());
		enterRule(_localctx, 20, RULE_unary_op64);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(204);
			((Unary_op64Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 112)) & ~0x3f) == 0 && ((1L << (_la - 112)) & ((1L << (T__111 - 112)) | (1L << (T__112 - 112)) | (1L << (T__113 - 112)) | (1L << (T__114 - 112)) | (1L << (T__115 - 112)) | (1L << (T__116 - 112)) | (1L << (T__117 - 112)) | (1L << (T__118 - 112)) | (1L << (T__119 - 112)))) != 0)) ) {
				((Unary_op64Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(205);
			((Unary_op64Context)_localctx).operand64 = operand64();
			 factory.createUnaryOperation(((Unary_op64Context)_localctx).op.getText(), ((Unary_op64Context)_localctx).operand64.op); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Unary_opContext extends ParserRuleContext {
		public Token op;
		public OperandContext operand;
		public OperandContext operand() {
			return getRuleContext(OperandContext.class,0);
		}
		public Unary_opContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unary_op; }
	}

	public final Unary_opContext unary_op() throws RecognitionException {
		Unary_opContext _localctx = new Unary_opContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_unary_op);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(208);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 121)) & ~0x3f) == 0 && ((1L << (_la - 121)) & ((1L << (T__120 - 121)) | (1L << (T__121 - 121)) | (1L << (T__122 - 121)) | (1L << (T__123 - 121)) | (1L << (T__124 - 121)) | (1L << (T__125 - 121)) | (1L << (T__126 - 121)) | (1L << (T__127 - 121)) | (1L << (T__128 - 121)) | (1L << (T__129 - 121)) | (1L << (T__130 - 121)) | (1L << (T__131 - 121)) | (1L << (T__132 - 121)) | (1L << (T__133 - 121)) | (1L << (T__134 - 121)) | (1L << (T__135 - 121)) | (1L << (T__136 - 121)) | (1L << (T__137 - 121)) | (1L << (T__138 - 121)) | (1L << (T__139 - 121)) | (1L << (T__140 - 121)) | (1L << (T__141 - 121)) | (1L << (T__142 - 121)) | (1L << (T__143 - 121)) | (1L << (T__144 - 121)) | (1L << (T__145 - 121)) | (1L << (T__146 - 121)) | (1L << (T__147 - 121)) | (1L << (T__148 - 121)) | (1L << (T__149 - 121)) | (1L << (T__150 - 121)) | (1L << (T__151 - 121)) | (1L << (T__152 - 121)) | (1L << (T__153 - 121)) | (1L << (T__154 - 121)) | (1L << (T__155 - 121)) | (1L << (T__156 - 121)) | (1L << (T__157 - 121)) | (1L << (T__158 - 121)) | (1L << (T__159 - 121)) | (1L << (T__160 - 121)))) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(209);
			_localctx.operand = operand();
			 factory.createUnaryOperationImplicitSize(_localctx.op.getText(), _localctx.operand.op); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Binary_op8Context extends ParserRuleContext {
		public Token op;
		public Operand8Context a;
		public Operand8Context b;
		public List<Operand8Context> operand8() {
			return getRuleContexts(Operand8Context.class);
		}
		public Operand8Context operand8(int i) {
			return getRuleContext(Operand8Context.class,i);
		}
		public Binary_op8Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_binary_op8; }
	}

	public final Binary_op8Context binary_op8() throws RecognitionException {
		Binary_op8Context _localctx = new Binary_op8Context(_ctx, getState());
		enterRule(_localctx, 24, RULE_binary_op8);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(212);
			((Binary_op8Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 162)) & ~0x3f) == 0 && ((1L << (_la - 162)) & ((1L << (T__161 - 162)) | (1L << (T__162 - 162)) | (1L << (T__163 - 162)) | (1L << (T__164 - 162)) | (1L << (T__165 - 162)) | (1L << (T__166 - 162)) | (1L << (T__167 - 162)) | (1L << (T__168 - 162)) | (1L << (T__169 - 162)) | (1L << (T__170 - 162)) | (1L << (T__171 - 162)) | (1L << (T__172 - 162)) | (1L << (T__173 - 162)) | (1L << (T__174 - 162)) | (1L << (T__175 - 162)) | (1L << (T__176 - 162)) | (1L << (T__177 - 162)) | (1L << (T__178 - 162)) | (1L << (T__179 - 162)) | (1L << (T__180 - 162)) | (1L << (T__181 - 162)))) != 0)) ) {
				((Binary_op8Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(213);
			((Binary_op8Context)_localctx).a = operand8();
			setState(214);
			match(T__79);
			setState(215);
			((Binary_op8Context)_localctx).b = operand8();
			 factory.createBinaryOperation(((Binary_op8Context)_localctx).op.getText(), ((Binary_op8Context)_localctx).a.op, ((Binary_op8Context)_localctx).b.op); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Binary_op16Context extends ParserRuleContext {
		public Token op1;
		public Operand16Context a1;
		public Operand16Context b1;
		public Token op2;
		public Operand8Context a2;
		public Operand16Context b2;
		public Token op3;
		public Operand8Context a3;
		public Operand16Context b3;
		public List<Operand16Context> operand16() {
			return getRuleContexts(Operand16Context.class);
		}
		public Operand16Context operand16(int i) {
			return getRuleContext(Operand16Context.class,i);
		}
		public Operand8Context operand8() {
			return getRuleContext(Operand8Context.class,0);
		}
		public Binary_op16Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_binary_op16; }
	}

	public final Binary_op16Context binary_op16() throws RecognitionException {
		Binary_op16Context _localctx = new Binary_op16Context(_ctx, getState());
		enterRule(_localctx, 26, RULE_binary_op16);
		int _la;
		try {
			setState(236);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__182:
			case T__183:
			case T__184:
			case T__185:
			case T__186:
			case T__187:
			case T__188:
			case T__189:
			case T__190:
			case T__191:
			case T__192:
			case T__193:
			case T__194:
			case T__195:
			case T__196:
			case T__197:
			case T__198:
			case T__199:
			case T__200:
			case T__201:
			case T__202:
			case T__203:
			case T__204:
			case T__205:
			case T__206:
			case T__207:
			case T__208:
			case T__209:
			case T__210:
			case T__211:
			case T__212:
			case T__213:
			case T__214:
			case T__215:
			case T__216:
			case T__217:
			case T__218:
			case T__219:
			case T__220:
			case T__221:
			case T__222:
			case T__223:
			case T__224:
			case T__225:
			case T__226:
			case T__227:
			case T__228:
			case T__229:
			case T__230:
				enterOuterAlt(_localctx, 1);
				{
				setState(218);
				((Binary_op16Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 183)) & ~0x3f) == 0 && ((1L << (_la - 183)) & ((1L << (T__182 - 183)) | (1L << (T__183 - 183)) | (1L << (T__184 - 183)) | (1L << (T__185 - 183)) | (1L << (T__186 - 183)) | (1L << (T__187 - 183)) | (1L << (T__188 - 183)) | (1L << (T__189 - 183)) | (1L << (T__190 - 183)) | (1L << (T__191 - 183)) | (1L << (T__192 - 183)) | (1L << (T__193 - 183)) | (1L << (T__194 - 183)) | (1L << (T__195 - 183)) | (1L << (T__196 - 183)) | (1L << (T__197 - 183)) | (1L << (T__198 - 183)) | (1L << (T__199 - 183)) | (1L << (T__200 - 183)) | (1L << (T__201 - 183)) | (1L << (T__202 - 183)) | (1L << (T__203 - 183)) | (1L << (T__204 - 183)) | (1L << (T__205 - 183)) | (1L << (T__206 - 183)) | (1L << (T__207 - 183)) | (1L << (T__208 - 183)) | (1L << (T__209 - 183)) | (1L << (T__210 - 183)) | (1L << (T__211 - 183)) | (1L << (T__212 - 183)) | (1L << (T__213 - 183)) | (1L << (T__214 - 183)) | (1L << (T__215 - 183)) | (1L << (T__216 - 183)) | (1L << (T__217 - 183)) | (1L << (T__218 - 183)) | (1L << (T__219 - 183)) | (1L << (T__220 - 183)) | (1L << (T__221 - 183)) | (1L << (T__222 - 183)) | (1L << (T__223 - 183)) | (1L << (T__224 - 183)) | (1L << (T__225 - 183)) | (1L << (T__226 - 183)) | (1L << (T__227 - 183)) | (1L << (T__228 - 183)) | (1L << (T__229 - 183)) | (1L << (T__230 - 183)))) != 0)) ) {
					((Binary_op16Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(219);
				((Binary_op16Context)_localctx).a1 = operand16();
				setState(220);
				match(T__79);
				setState(221);
				((Binary_op16Context)_localctx).b1 = operand16();
				 factory.createBinaryOperation(((Binary_op16Context)_localctx).op1.getText(), ((Binary_op16Context)_localctx).a1.op, ((Binary_op16Context)_localctx).b1.op); 
				}
				break;
			case T__231:
			case T__232:
			case T__233:
			case T__234:
			case T__235:
			case T__236:
			case T__237:
			case T__238:
				enterOuterAlt(_localctx, 2);
				{
				setState(224);
				((Binary_op16Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 232)) & ~0x3f) == 0 && ((1L << (_la - 232)) & ((1L << (T__231 - 232)) | (1L << (T__232 - 232)) | (1L << (T__233 - 232)) | (1L << (T__234 - 232)) | (1L << (T__235 - 232)) | (1L << (T__236 - 232)) | (1L << (T__237 - 232)) | (1L << (T__238 - 232)))) != 0)) ) {
					((Binary_op16Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(225);
				((Binary_op16Context)_localctx).a2 = operand8();
				setState(226);
				match(T__79);
				setState(227);
				((Binary_op16Context)_localctx).b2 = operand16();
				 factory.createBinaryOperation(((Binary_op16Context)_localctx).op2.getText(), ((Binary_op16Context)_localctx).a2.op, ((Binary_op16Context)_localctx).b2.op); 
				}
				break;
			case T__239:
			case T__240:
				enterOuterAlt(_localctx, 3);
				{
				setState(230);
				((Binary_op16Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__239 || _la==T__240) ) {
					((Binary_op16Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(231);
				((Binary_op16Context)_localctx).a3 = operand8();
				setState(232);
				match(T__79);
				setState(233);
				((Binary_op16Context)_localctx).b3 = operand16();
				 factory.createBinaryOperation(((Binary_op16Context)_localctx).op3.getText(), ((Binary_op16Context)_localctx).a3.op, ((Binary_op16Context)_localctx).b3.op); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Binary_op32Context extends ParserRuleContext {
		public Token op1;
		public Operand32Context a1;
		public Operand32Context b1;
		public Token op2;
		public Operand8Context a2;
		public Operand32Context b2;
		public Token op3;
		public Operand8Context a3;
		public Operand32Context b3;
		public Token op4;
		public Operand16Context a4;
		public Operand32Context b4;
		public List<Operand32Context> operand32() {
			return getRuleContexts(Operand32Context.class);
		}
		public Operand32Context operand32(int i) {
			return getRuleContext(Operand32Context.class,i);
		}
		public Operand8Context operand8() {
			return getRuleContext(Operand8Context.class,0);
		}
		public Operand16Context operand16() {
			return getRuleContext(Operand16Context.class,0);
		}
		public Binary_op32Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_binary_op32; }
	}

	public final Binary_op32Context binary_op32() throws RecognitionException {
		Binary_op32Context _localctx = new Binary_op32Context(_ctx, getState());
		enterRule(_localctx, 28, RULE_binary_op32);
		int _la;
		try {
			setState(262);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__241:
			case T__242:
			case T__243:
			case T__244:
			case T__245:
			case T__246:
			case T__247:
			case T__248:
			case T__249:
			case T__250:
			case T__251:
			case T__252:
			case T__253:
			case T__254:
			case T__255:
			case T__256:
			case T__257:
			case T__258:
			case T__259:
			case T__260:
			case T__261:
			case T__262:
			case T__263:
			case T__264:
			case T__265:
			case T__266:
			case T__267:
			case T__268:
			case T__269:
			case T__270:
			case T__271:
			case T__272:
			case T__273:
			case T__274:
			case T__275:
			case T__276:
			case T__277:
			case T__278:
			case T__279:
			case T__280:
			case T__281:
			case T__282:
			case T__283:
			case T__284:
			case T__285:
			case T__286:
			case T__287:
			case T__288:
			case T__289:
				enterOuterAlt(_localctx, 1);
				{
				setState(238);
				((Binary_op32Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 242)) & ~0x3f) == 0 && ((1L << (_la - 242)) & ((1L << (T__241 - 242)) | (1L << (T__242 - 242)) | (1L << (T__243 - 242)) | (1L << (T__244 - 242)) | (1L << (T__245 - 242)) | (1L << (T__246 - 242)) | (1L << (T__247 - 242)) | (1L << (T__248 - 242)) | (1L << (T__249 - 242)) | (1L << (T__250 - 242)) | (1L << (T__251 - 242)) | (1L << (T__252 - 242)) | (1L << (T__253 - 242)) | (1L << (T__254 - 242)) | (1L << (T__255 - 242)) | (1L << (T__256 - 242)) | (1L << (T__257 - 242)) | (1L << (T__258 - 242)) | (1L << (T__259 - 242)) | (1L << (T__260 - 242)) | (1L << (T__261 - 242)) | (1L << (T__262 - 242)) | (1L << (T__263 - 242)) | (1L << (T__264 - 242)) | (1L << (T__265 - 242)) | (1L << (T__266 - 242)) | (1L << (T__267 - 242)) | (1L << (T__268 - 242)) | (1L << (T__269 - 242)) | (1L << (T__270 - 242)) | (1L << (T__271 - 242)) | (1L << (T__272 - 242)) | (1L << (T__273 - 242)) | (1L << (T__274 - 242)) | (1L << (T__275 - 242)) | (1L << (T__276 - 242)) | (1L << (T__277 - 242)) | (1L << (T__278 - 242)) | (1L << (T__279 - 242)) | (1L << (T__280 - 242)) | (1L << (T__281 - 242)) | (1L << (T__282 - 242)) | (1L << (T__283 - 242)) | (1L << (T__284 - 242)) | (1L << (T__285 - 242)) | (1L << (T__286 - 242)) | (1L << (T__287 - 242)) | (1L << (T__288 - 242)) | (1L << (T__289 - 242)))) != 0)) ) {
					((Binary_op32Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(239);
				((Binary_op32Context)_localctx).a1 = operand32();
				setState(240);
				match(T__79);
				setState(241);
				((Binary_op32Context)_localctx).b1 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op1.getText(), ((Binary_op32Context)_localctx).a1.op, ((Binary_op32Context)_localctx).b1.op); 
				}
				break;
			case T__290:
			case T__291:
			case T__292:
			case T__293:
			case T__294:
			case T__295:
			case T__296:
			case T__297:
				enterOuterAlt(_localctx, 2);
				{
				setState(244);
				((Binary_op32Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 291)) & ~0x3f) == 0 && ((1L << (_la - 291)) & ((1L << (T__290 - 291)) | (1L << (T__291 - 291)) | (1L << (T__292 - 291)) | (1L << (T__293 - 291)) | (1L << (T__294 - 291)) | (1L << (T__295 - 291)) | (1L << (T__296 - 291)) | (1L << (T__297 - 291)))) != 0)) ) {
					((Binary_op32Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(245);
				((Binary_op32Context)_localctx).a2 = operand8();
				setState(246);
				match(T__79);
				setState(247);
				((Binary_op32Context)_localctx).b2 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op2.getText(), ((Binary_op32Context)_localctx).a2.op, ((Binary_op32Context)_localctx).b2.op); 
				}
				break;
			case T__298:
			case T__299:
				enterOuterAlt(_localctx, 3);
				{
				setState(250);
				((Binary_op32Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__298 || _la==T__299) ) {
					((Binary_op32Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(251);
				((Binary_op32Context)_localctx).a3 = operand8();
				setState(252);
				match(T__79);
				setState(253);
				((Binary_op32Context)_localctx).b3 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op3.getText(), ((Binary_op32Context)_localctx).a3.op, ((Binary_op32Context)_localctx).b3.op); 
				}
				break;
			case T__300:
			case T__301:
				enterOuterAlt(_localctx, 4);
				{
				setState(256);
				((Binary_op32Context)_localctx).op4 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__300 || _la==T__301) ) {
					((Binary_op32Context)_localctx).op4 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(257);
				((Binary_op32Context)_localctx).a4 = operand16();
				setState(258);
				match(T__79);
				setState(259);
				((Binary_op32Context)_localctx).b4 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op4.getText(), ((Binary_op32Context)_localctx).a4.op, ((Binary_op32Context)_localctx).b4.op); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Binary_op64Context extends ParserRuleContext {
		public Token op1;
		public Operand64Context a1;
		public Operand64Context b1;
		public Token op2;
		public Operand8Context a2;
		public Operand64Context b2;
		public Token op3;
		public Operand8Context a3;
		public Operand64Context b3;
		public Token op4;
		public Operand16Context a4;
		public Operand64Context b4;
		public Token op5;
		public Operand32Context a5;
		public Operand64Context b5;
		public List<Operand64Context> operand64() {
			return getRuleContexts(Operand64Context.class);
		}
		public Operand64Context operand64(int i) {
			return getRuleContext(Operand64Context.class,i);
		}
		public Operand8Context operand8() {
			return getRuleContext(Operand8Context.class,0);
		}
		public Operand16Context operand16() {
			return getRuleContext(Operand16Context.class,0);
		}
		public Operand32Context operand32() {
			return getRuleContext(Operand32Context.class,0);
		}
		public Binary_op64Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_binary_op64; }
	}

	public final Binary_op64Context binary_op64() throws RecognitionException {
		Binary_op64Context _localctx = new Binary_op64Context(_ctx, getState());
		enterRule(_localctx, 30, RULE_binary_op64);
		int _la;
		try {
			setState(294);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__302:
			case T__303:
			case T__304:
			case T__305:
			case T__306:
			case T__307:
			case T__308:
			case T__309:
			case T__310:
			case T__311:
			case T__312:
			case T__313:
			case T__314:
			case T__315:
			case T__316:
			case T__317:
			case T__318:
			case T__319:
			case T__320:
			case T__321:
			case T__322:
			case T__323:
			case T__324:
			case T__325:
			case T__326:
			case T__327:
			case T__328:
			case T__329:
			case T__330:
			case T__331:
			case T__332:
			case T__333:
			case T__334:
			case T__335:
			case T__336:
			case T__337:
			case T__338:
			case T__339:
			case T__340:
			case T__341:
			case T__342:
			case T__343:
			case T__344:
			case T__345:
			case T__346:
			case T__347:
			case T__348:
			case T__349:
			case T__350:
				enterOuterAlt(_localctx, 1);
				{
				setState(264);
				((Binary_op64Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 303)) & ~0x3f) == 0 && ((1L << (_la - 303)) & ((1L << (T__302 - 303)) | (1L << (T__303 - 303)) | (1L << (T__304 - 303)) | (1L << (T__305 - 303)) | (1L << (T__306 - 303)) | (1L << (T__307 - 303)) | (1L << (T__308 - 303)) | (1L << (T__309 - 303)) | (1L << (T__310 - 303)) | (1L << (T__311 - 303)) | (1L << (T__312 - 303)) | (1L << (T__313 - 303)) | (1L << (T__314 - 303)) | (1L << (T__315 - 303)) | (1L << (T__316 - 303)) | (1L << (T__317 - 303)) | (1L << (T__318 - 303)) | (1L << (T__319 - 303)) | (1L << (T__320 - 303)) | (1L << (T__321 - 303)) | (1L << (T__322 - 303)) | (1L << (T__323 - 303)) | (1L << (T__324 - 303)) | (1L << (T__325 - 303)) | (1L << (T__326 - 303)) | (1L << (T__327 - 303)) | (1L << (T__328 - 303)) | (1L << (T__329 - 303)) | (1L << (T__330 - 303)) | (1L << (T__331 - 303)) | (1L << (T__332 - 303)) | (1L << (T__333 - 303)) | (1L << (T__334 - 303)) | (1L << (T__335 - 303)) | (1L << (T__336 - 303)) | (1L << (T__337 - 303)) | (1L << (T__338 - 303)) | (1L << (T__339 - 303)) | (1L << (T__340 - 303)) | (1L << (T__341 - 303)) | (1L << (T__342 - 303)) | (1L << (T__343 - 303)) | (1L << (T__344 - 303)) | (1L << (T__345 - 303)) | (1L << (T__346 - 303)) | (1L << (T__347 - 303)) | (1L << (T__348 - 303)) | (1L << (T__349 - 303)) | (1L << (T__350 - 303)))) != 0)) ) {
					((Binary_op64Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(265);
				((Binary_op64Context)_localctx).a1 = operand64();
				setState(266);
				match(T__79);
				setState(267);
				((Binary_op64Context)_localctx).b1 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op1.getText(), ((Binary_op64Context)_localctx).a1.op, ((Binary_op64Context)_localctx).b1.op); 
				}
				break;
			case T__351:
			case T__352:
			case T__353:
			case T__354:
			case T__355:
			case T__356:
			case T__357:
			case T__358:
				enterOuterAlt(_localctx, 2);
				{
				setState(270);
				((Binary_op64Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 352)) & ~0x3f) == 0 && ((1L << (_la - 352)) & ((1L << (T__351 - 352)) | (1L << (T__352 - 352)) | (1L << (T__353 - 352)) | (1L << (T__354 - 352)) | (1L << (T__355 - 352)) | (1L << (T__356 - 352)) | (1L << (T__357 - 352)) | (1L << (T__358 - 352)))) != 0)) ) {
					((Binary_op64Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(271);
				((Binary_op64Context)_localctx).a2 = operand8();
				setState(272);
				match(T__79);
				setState(273);
				((Binary_op64Context)_localctx).b2 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op2.getText(), ((Binary_op64Context)_localctx).a2.op, ((Binary_op64Context)_localctx).b2.op); 
				}
				break;
			case T__359:
			case T__360:
				enterOuterAlt(_localctx, 3);
				{
				setState(276);
				((Binary_op64Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__359 || _la==T__360) ) {
					((Binary_op64Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(277);
				((Binary_op64Context)_localctx).a3 = operand8();
				setState(278);
				match(T__79);
				setState(279);
				((Binary_op64Context)_localctx).b3 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op3.getText(), ((Binary_op64Context)_localctx).a3.op, ((Binary_op64Context)_localctx).b3.op); 
				}
				break;
			case T__361:
			case T__362:
				enterOuterAlt(_localctx, 4);
				{
				setState(282);
				((Binary_op64Context)_localctx).op4 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__361 || _la==T__362) ) {
					((Binary_op64Context)_localctx).op4 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(283);
				((Binary_op64Context)_localctx).a4 = operand16();
				setState(284);
				match(T__79);
				setState(285);
				((Binary_op64Context)_localctx).b4 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op4.getText(), ((Binary_op64Context)_localctx).a4.op, ((Binary_op64Context)_localctx).b4.op); 
				}
				break;
			case T__363:
				enterOuterAlt(_localctx, 5);
				{
				setState(288);
				((Binary_op64Context)_localctx).op5 = match(T__363);
				setState(289);
				((Binary_op64Context)_localctx).a5 = operand32();
				setState(290);
				match(T__79);
				setState(291);
				((Binary_op64Context)_localctx).b5 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op5.getText(), ((Binary_op64Context)_localctx).a5.op, ((Binary_op64Context)_localctx).b5.op); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Binary_opContext extends ParserRuleContext {
		public Token op;
		public OperandContext a;
		public OperandContext b;
		public List<OperandContext> operand() {
			return getRuleContexts(OperandContext.class);
		}
		public OperandContext operand(int i) {
			return getRuleContext(OperandContext.class,i);
		}
		public Binary_opContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_binary_op; }
	}

	public final Binary_opContext binary_op() throws RecognitionException {
		Binary_opContext _localctx = new Binary_opContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_binary_op);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(296);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 365)) & ~0x3f) == 0 && ((1L << (_la - 365)) & ((1L << (T__364 - 365)) | (1L << (T__365 - 365)) | (1L << (T__366 - 365)) | (1L << (T__367 - 365)) | (1L << (T__368 - 365)) | (1L << (T__369 - 365)) | (1L << (T__370 - 365)) | (1L << (T__371 - 365)) | (1L << (T__372 - 365)) | (1L << (T__373 - 365)) | (1L << (T__374 - 365)) | (1L << (T__375 - 365)) | (1L << (T__376 - 365)) | (1L << (T__377 - 365)) | (1L << (T__378 - 365)) | (1L << (T__379 - 365)) | (1L << (T__380 - 365)) | (1L << (T__381 - 365)) | (1L << (T__382 - 365)) | (1L << (T__383 - 365)) | (1L << (T__384 - 365)) | (1L << (T__385 - 365)) | (1L << (T__386 - 365)) | (1L << (T__387 - 365)) | (1L << (T__388 - 365)) | (1L << (T__389 - 365)) | (1L << (T__390 - 365)) | (1L << (T__391 - 365)) | (1L << (T__392 - 365)) | (1L << (T__393 - 365)) | (1L << (T__394 - 365)) | (1L << (T__395 - 365)) | (1L << (T__396 - 365)) | (1L << (T__397 - 365)) | (1L << (T__398 - 365)) | (1L << (T__399 - 365)) | (1L << (T__400 - 365)) | (1L << (T__401 - 365)) | (1L << (T__402 - 365)) | (1L << (T__403 - 365)) | (1L << (T__404 - 365)) | (1L << (T__405 - 365)) | (1L << (T__406 - 365)) | (1L << (T__407 - 365)) | (1L << (T__408 - 365)) | (1L << (T__409 - 365)) | (1L << (T__410 - 365)) | (1L << (T__411 - 365)) | (1L << (T__412 - 365)) | (1L << (T__413 - 365)) | (1L << (T__414 - 365)) | (1L << (T__415 - 365)) | (1L << (T__416 - 365)) | (1L << (T__417 - 365)) | (1L << (T__418 - 365)) | (1L << (T__419 - 365)))) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(297);
			_localctx.a = operand();
			setState(298);
			match(T__79);
			setState(299);
			_localctx.b = operand();
			 factory.createBinaryOperationImplicitSize(_localctx.op.getText(), _localctx.a.op, _localctx.b.op); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Operand8Context extends ParserRuleContext {
		public AsmOperand op;
		public Register8Context register8;
		public Memory_referenceContext memory_reference;
		public ImmediateContext immediate;
		public ArgumentContext argument;
		public Register8Context register8() {
			return getRuleContext(Register8Context.class,0);
		}
		public Memory_referenceContext memory_reference() {
			return getRuleContext(Memory_referenceContext.class,0);
		}
		public ImmediateContext immediate() {
			return getRuleContext(ImmediateContext.class,0);
		}
		public ArgumentContext argument() {
			return getRuleContext(ArgumentContext.class,0);
		}
		public Operand8Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand8; }
	}

	public final Operand8Context operand8() throws RecognitionException {
		Operand8Context _localctx = new Operand8Context(_ctx, getState());
		enterRule(_localctx, 34, RULE_operand8);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(314);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__423:
			case T__424:
			case T__425:
			case T__426:
			case T__427:
			case T__428:
			case T__429:
			case T__430:
			case T__431:
			case T__432:
			case T__433:
			case T__434:
			case T__435:
			case T__436:
			case T__437:
			case T__438:
			case T__439:
			case T__440:
			case T__441:
			case T__442:
			case T__443:
			case T__444:
			case T__445:
			case T__446:
				{
				setState(302);
				((Operand8Context)_localctx).register8 = register8();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).register8.op; 
				}
				break;
			case T__421:
			case T__535:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(305);
				((Operand8Context)_localctx).memory_reference = memory_reference();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).memory_reference.op; 
				}
				break;
			case T__541:
				{
				setState(308);
				((Operand8Context)_localctx).immediate = immediate();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).immediate.op; 
				}
				break;
			case T__542:
				{
				setState(311);
				((Operand8Context)_localctx).argument = argument();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).argument.op; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Operand16Context extends ParserRuleContext {
		public AsmOperand op;
		public Register16Context register16;
		public Memory_referenceContext memory_reference;
		public ImmediateContext immediate;
		public ArgumentContext argument;
		public Register16Context register16() {
			return getRuleContext(Register16Context.class,0);
		}
		public Memory_referenceContext memory_reference() {
			return getRuleContext(Memory_referenceContext.class,0);
		}
		public ImmediateContext immediate() {
			return getRuleContext(ImmediateContext.class,0);
		}
		public ArgumentContext argument() {
			return getRuleContext(ArgumentContext.class,0);
		}
		public Operand16Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand16; }
	}

	public final Operand16Context operand16() throws RecognitionException {
		Operand16Context _localctx = new Operand16Context(_ctx, getState());
		enterRule(_localctx, 36, RULE_operand16);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(328);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__447:
			case T__448:
			case T__449:
			case T__450:
			case T__451:
			case T__452:
			case T__453:
			case T__454:
			case T__455:
			case T__456:
			case T__457:
			case T__458:
			case T__459:
			case T__460:
			case T__461:
			case T__462:
			case T__463:
			case T__464:
			case T__465:
			case T__466:
			case T__467:
			case T__468:
			case T__469:
			case T__470:
				{
				setState(316);
				((Operand16Context)_localctx).register16 = register16();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).register16.op; 
				}
				break;
			case T__421:
			case T__535:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(319);
				((Operand16Context)_localctx).memory_reference = memory_reference();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).memory_reference.op; 
				}
				break;
			case T__541:
				{
				setState(322);
				((Operand16Context)_localctx).immediate = immediate();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).immediate.op; 
				}
				break;
			case T__542:
				{
				setState(325);
				((Operand16Context)_localctx).argument = argument();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).argument.op; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Operand32Context extends ParserRuleContext {
		public AsmOperand op;
		public Register32Context register32;
		public Memory_referenceContext memory_reference;
		public ImmediateContext immediate;
		public ArgumentContext argument;
		public Register32Context register32() {
			return getRuleContext(Register32Context.class,0);
		}
		public Memory_referenceContext memory_reference() {
			return getRuleContext(Memory_referenceContext.class,0);
		}
		public ImmediateContext immediate() {
			return getRuleContext(ImmediateContext.class,0);
		}
		public ArgumentContext argument() {
			return getRuleContext(ArgumentContext.class,0);
		}
		public Operand32Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand32; }
	}

	public final Operand32Context operand32() throws RecognitionException {
		Operand32Context _localctx = new Operand32Context(_ctx, getState());
		enterRule(_localctx, 38, RULE_operand32);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(342);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__471:
			case T__472:
			case T__473:
			case T__474:
			case T__475:
			case T__476:
			case T__477:
			case T__478:
			case T__479:
			case T__480:
			case T__481:
			case T__482:
			case T__483:
			case T__484:
			case T__485:
			case T__486:
			case T__487:
			case T__488:
			case T__489:
			case T__490:
			case T__491:
			case T__492:
			case T__493:
			case T__494:
				{
				setState(330);
				((Operand32Context)_localctx).register32 = register32();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).register32.op; 
				}
				break;
			case T__421:
			case T__535:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(333);
				((Operand32Context)_localctx).memory_reference = memory_reference();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).memory_reference.op; 
				}
				break;
			case T__541:
				{
				setState(336);
				((Operand32Context)_localctx).immediate = immediate();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).immediate.op; 
				}
				break;
			case T__542:
				{
				setState(339);
				((Operand32Context)_localctx).argument = argument();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).argument.op; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Operand64Context extends ParserRuleContext {
		public AsmOperand op;
		public Register64Context register64;
		public Memory_referenceContext memory_reference;
		public ImmediateContext immediate;
		public ArgumentContext argument;
		public Register64Context register64() {
			return getRuleContext(Register64Context.class,0);
		}
		public Memory_referenceContext memory_reference() {
			return getRuleContext(Memory_referenceContext.class,0);
		}
		public ImmediateContext immediate() {
			return getRuleContext(ImmediateContext.class,0);
		}
		public ArgumentContext argument() {
			return getRuleContext(ArgumentContext.class,0);
		}
		public Operand64Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand64; }
	}

	public final Operand64Context operand64() throws RecognitionException {
		Operand64Context _localctx = new Operand64Context(_ctx, getState());
		enterRule(_localctx, 40, RULE_operand64);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(356);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__495:
			case T__496:
			case T__497:
			case T__498:
			case T__499:
			case T__500:
			case T__501:
			case T__502:
			case T__503:
			case T__504:
			case T__505:
			case T__506:
			case T__507:
			case T__508:
			case T__509:
			case T__510:
			case T__511:
			case T__512:
			case T__513:
			case T__514:
			case T__515:
			case T__516:
			case T__517:
			case T__518:
				{
				setState(344);
				((Operand64Context)_localctx).register64 = register64();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).register64.op; 
				}
				break;
			case T__421:
			case T__535:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(347);
				((Operand64Context)_localctx).memory_reference = memory_reference();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).memory_reference.op; 
				}
				break;
			case T__541:
				{
				setState(350);
				((Operand64Context)_localctx).immediate = immediate();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).immediate.op; 
				}
				break;
			case T__542:
				{
				setState(353);
				((Operand64Context)_localctx).argument = argument();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).argument.op; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class OperandContext extends ParserRuleContext {
		public AsmOperand op;
		public Register8Context register8;
		public Register16Context register16;
		public Register32Context register32;
		public Register64Context register64;
		public RegisterXmmContext registerXmm;
		public Memory_referenceContext memory_reference;
		public ImmediateContext immediate;
		public ArgumentContext argument;
		public Register8Context register8() {
			return getRuleContext(Register8Context.class,0);
		}
		public Register16Context register16() {
			return getRuleContext(Register16Context.class,0);
		}
		public Register32Context register32() {
			return getRuleContext(Register32Context.class,0);
		}
		public Register64Context register64() {
			return getRuleContext(Register64Context.class,0);
		}
		public RegisterXmmContext registerXmm() {
			return getRuleContext(RegisterXmmContext.class,0);
		}
		public Memory_referenceContext memory_reference() {
			return getRuleContext(Memory_referenceContext.class,0);
		}
		public ImmediateContext immediate() {
			return getRuleContext(ImmediateContext.class,0);
		}
		public ArgumentContext argument() {
			return getRuleContext(ArgumentContext.class,0);
		}
		public OperandContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand; }
	}

	public final OperandContext operand() throws RecognitionException {
		OperandContext _localctx = new OperandContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_operand);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(382);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__423:
			case T__424:
			case T__425:
			case T__426:
			case T__427:
			case T__428:
			case T__429:
			case T__430:
			case T__431:
			case T__432:
			case T__433:
			case T__434:
			case T__435:
			case T__436:
			case T__437:
			case T__438:
			case T__439:
			case T__440:
			case T__441:
			case T__442:
			case T__443:
			case T__444:
			case T__445:
			case T__446:
				{
				setState(358);
				_localctx.register8 = register8();
				 _localctx.op =  _localctx.register8.op; 
				}
				break;
			case T__447:
			case T__448:
			case T__449:
			case T__450:
			case T__451:
			case T__452:
			case T__453:
			case T__454:
			case T__455:
			case T__456:
			case T__457:
			case T__458:
			case T__459:
			case T__460:
			case T__461:
			case T__462:
			case T__463:
			case T__464:
			case T__465:
			case T__466:
			case T__467:
			case T__468:
			case T__469:
			case T__470:
				{
				setState(361);
				_localctx.register16 = register16();
				 _localctx.op =  _localctx.register16.op; 
				}
				break;
			case T__471:
			case T__472:
			case T__473:
			case T__474:
			case T__475:
			case T__476:
			case T__477:
			case T__478:
			case T__479:
			case T__480:
			case T__481:
			case T__482:
			case T__483:
			case T__484:
			case T__485:
			case T__486:
			case T__487:
			case T__488:
			case T__489:
			case T__490:
			case T__491:
			case T__492:
			case T__493:
			case T__494:
				{
				setState(364);
				_localctx.register32 = register32();
				 _localctx.op =  _localctx.register32.op; 
				}
				break;
			case T__495:
			case T__496:
			case T__497:
			case T__498:
			case T__499:
			case T__500:
			case T__501:
			case T__502:
			case T__503:
			case T__504:
			case T__505:
			case T__506:
			case T__507:
			case T__508:
			case T__509:
			case T__510:
			case T__511:
			case T__512:
			case T__513:
			case T__514:
			case T__515:
			case T__516:
			case T__517:
			case T__518:
				{
				setState(367);
				_localctx.register64 = register64();
				 _localctx.op =  _localctx.register64.op; 
				}
				break;
			case T__519:
			case T__520:
			case T__521:
			case T__522:
			case T__523:
			case T__524:
			case T__525:
			case T__526:
			case T__527:
			case T__528:
			case T__529:
			case T__530:
			case T__531:
			case T__532:
			case T__533:
			case T__534:
				{
				setState(370);
				_localctx.registerXmm = registerXmm();
				 _localctx.op =  _localctx.registerXmm.op; 
				}
				break;
			case T__421:
			case T__535:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(373);
				_localctx.memory_reference = memory_reference();
				 _localctx.op =  _localctx.memory_reference.op; 
				}
				break;
			case T__541:
				{
				setState(376);
				_localctx.immediate = immediate();
				 _localctx.op =  _localctx.immediate.op; 
				}
				break;
			case T__542:
				{
				setState(379);
				_localctx.argument = argument();
				 _localctx.op =  _localctx.argument.op; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Memory_referenceContext extends ParserRuleContext {
		public AsmMemoryOperand op;
		public Segment_registerContext segment_register;
		public Token i;
		public NumberContext number;
		public OperandContext operand;
		public Segment_registerContext segment_register() {
			return getRuleContext(Segment_registerContext.class,0);
		}
		public List<NumberContext> number() {
			return getRuleContexts(NumberContext.class);
		}
		public NumberContext number(int i) {
			return getRuleContext(NumberContext.class,i);
		}
		public TerminalNode IDENT() { return getToken(InlineAssemblyParser.IDENT, 0); }
		public List<OperandContext> operand() {
			return getRuleContexts(OperandContext.class);
		}
		public OperandContext operand(int i) {
			return getRuleContext(OperandContext.class,i);
		}
		public Memory_referenceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_memory_reference; }
	}

	public final Memory_referenceContext memory_reference() throws RecognitionException {
		Memory_referenceContext _localctx = new Memory_referenceContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_memory_reference);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 String displacement = null;
			                                                   String segment = null;
			                                                   AsmOperand base = null;
			                                                   AsmOperand offset = null;
			                                                   int scale = 1; 
			setState(389);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 536)) & ~0x3f) == 0 && ((1L << (_la - 536)) & ((1L << (T__535 - 536)) | (1L << (T__536 - 536)) | (1L << (T__537 - 536)) | (1L << (T__538 - 536)) | (1L << (T__539 - 536)) | (1L << (T__540 - 536)))) != 0)) {
				{
				setState(385);
				_localctx.segment_register = segment_register();
				 segment = _localctx.segment_register.reg; 
				setState(387);
				match(T__420);
				}
			}

			setState(436);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(396);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case IDENT:
					{
					setState(391);
					_localctx.i = match(IDENT);
					 displacement = _localctx.i.getText(); 
					}
					break;
				case BIN_NUMBER:
				case HEX_NUMBER:
				case NUMBER:
					{
					setState(393);
					_localctx.number = number();
					 displacement = String.valueOf(_localctx.number.n); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(416);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__421) {
					{
					setState(398);
					match(T__421);
					setState(402);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (((((_la - 422)) & ~0x3f) == 0 && ((1L << (_la - 422)) & ((1L << (T__421 - 422)) | (1L << (T__423 - 422)) | (1L << (T__424 - 422)) | (1L << (T__425 - 422)) | (1L << (T__426 - 422)) | (1L << (T__427 - 422)) | (1L << (T__428 - 422)) | (1L << (T__429 - 422)) | (1L << (T__430 - 422)) | (1L << (T__431 - 422)) | (1L << (T__432 - 422)) | (1L << (T__433 - 422)) | (1L << (T__434 - 422)) | (1L << (T__435 - 422)) | (1L << (T__436 - 422)) | (1L << (T__437 - 422)) | (1L << (T__438 - 422)) | (1L << (T__439 - 422)) | (1L << (T__440 - 422)) | (1L << (T__441 - 422)) | (1L << (T__442 - 422)) | (1L << (T__443 - 422)) | (1L << (T__444 - 422)) | (1L << (T__445 - 422)) | (1L << (T__446 - 422)) | (1L << (T__447 - 422)) | (1L << (T__448 - 422)) | (1L << (T__449 - 422)) | (1L << (T__450 - 422)) | (1L << (T__451 - 422)) | (1L << (T__452 - 422)) | (1L << (T__453 - 422)) | (1L << (T__454 - 422)) | (1L << (T__455 - 422)) | (1L << (T__456 - 422)) | (1L << (T__457 - 422)) | (1L << (T__458 - 422)) | (1L << (T__459 - 422)) | (1L << (T__460 - 422)) | (1L << (T__461 - 422)) | (1L << (T__462 - 422)) | (1L << (T__463 - 422)) | (1L << (T__464 - 422)) | (1L << (T__465 - 422)) | (1L << (T__466 - 422)) | (1L << (T__467 - 422)) | (1L << (T__468 - 422)) | (1L << (T__469 - 422)) | (1L << (T__470 - 422)) | (1L << (T__471 - 422)) | (1L << (T__472 - 422)) | (1L << (T__473 - 422)) | (1L << (T__474 - 422)) | (1L << (T__475 - 422)) | (1L << (T__476 - 422)) | (1L << (T__477 - 422)) | (1L << (T__478 - 422)) | (1L << (T__479 - 422)) | (1L << (T__480 - 422)) | (1L << (T__481 - 422)) | (1L << (T__482 - 422)) | (1L << (T__483 - 422)) | (1L << (T__484 - 422)))) != 0) || ((((_la - 486)) & ~0x3f) == 0 && ((1L << (_la - 486)) & ((1L << (T__485 - 486)) | (1L << (T__486 - 486)) | (1L << (T__487 - 486)) | (1L << (T__488 - 486)) | (1L << (T__489 - 486)) | (1L << (T__490 - 486)) | (1L << (T__491 - 486)) | (1L << (T__492 - 486)) | (1L << (T__493 - 486)) | (1L << (T__494 - 486)) | (1L << (T__495 - 486)) | (1L << (T__496 - 486)) | (1L << (T__497 - 486)) | (1L << (T__498 - 486)) | (1L << (T__499 - 486)) | (1L << (T__500 - 486)) | (1L << (T__501 - 486)) | (1L << (T__502 - 486)) | (1L << (T__503 - 486)) | (1L << (T__504 - 486)) | (1L << (T__505 - 486)) | (1L << (T__506 - 486)) | (1L << (T__507 - 486)) | (1L << (T__508 - 486)) | (1L << (T__509 - 486)) | (1L << (T__510 - 486)) | (1L << (T__511 - 486)) | (1L << (T__512 - 486)) | (1L << (T__513 - 486)) | (1L << (T__514 - 486)) | (1L << (T__515 - 486)) | (1L << (T__516 - 486)) | (1L << (T__517 - 486)) | (1L << (T__518 - 486)) | (1L << (T__519 - 486)) | (1L << (T__520 - 486)) | (1L << (T__521 - 486)) | (1L << (T__522 - 486)) | (1L << (T__523 - 486)) | (1L << (T__524 - 486)) | (1L << (T__525 - 486)) | (1L << (T__526 - 486)) | (1L << (T__527 - 486)) | (1L << (T__528 - 486)) | (1L << (T__529 - 486)) | (1L << (T__530 - 486)) | (1L << (T__531 - 486)) | (1L << (T__532 - 486)) | (1L << (T__533 - 486)) | (1L << (T__534 - 486)) | (1L << (T__535 - 486)) | (1L << (T__536 - 486)) | (1L << (T__537 - 486)) | (1L << (T__538 - 486)) | (1L << (T__539 - 486)) | (1L << (T__540 - 486)) | (1L << (T__541 - 486)) | (1L << (T__542 - 486)))) != 0) || ((((_la - 551)) & ~0x3f) == 0 && ((1L << (_la - 551)) & ((1L << (IDENT - 551)) | (1L << (BIN_NUMBER - 551)) | (1L << (HEX_NUMBER - 551)) | (1L << (NUMBER - 551)))) != 0)) {
						{
						setState(399);
						_localctx.operand = operand();
						 base = _localctx.operand.op; 
						}
					}

					setState(413);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__79) {
						{
						setState(404);
						match(T__79);
						setState(405);
						_localctx.operand = operand();
						 offset = _localctx.operand.op; 
						setState(411);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==T__79) {
							{
							setState(407);
							match(T__79);
							setState(408);
							_localctx.number = number();
							 scale = (int) _localctx.number.n; 
							}
						}

						}
					}

					setState(415);
					match(T__422);
					}
				}

				}
				break;
			case T__421:
				{
				setState(418);
				match(T__421);
				setState(422);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 422)) & ~0x3f) == 0 && ((1L << (_la - 422)) & ((1L << (T__421 - 422)) | (1L << (T__423 - 422)) | (1L << (T__424 - 422)) | (1L << (T__425 - 422)) | (1L << (T__426 - 422)) | (1L << (T__427 - 422)) | (1L << (T__428 - 422)) | (1L << (T__429 - 422)) | (1L << (T__430 - 422)) | (1L << (T__431 - 422)) | (1L << (T__432 - 422)) | (1L << (T__433 - 422)) | (1L << (T__434 - 422)) | (1L << (T__435 - 422)) | (1L << (T__436 - 422)) | (1L << (T__437 - 422)) | (1L << (T__438 - 422)) | (1L << (T__439 - 422)) | (1L << (T__440 - 422)) | (1L << (T__441 - 422)) | (1L << (T__442 - 422)) | (1L << (T__443 - 422)) | (1L << (T__444 - 422)) | (1L << (T__445 - 422)) | (1L << (T__446 - 422)) | (1L << (T__447 - 422)) | (1L << (T__448 - 422)) | (1L << (T__449 - 422)) | (1L << (T__450 - 422)) | (1L << (T__451 - 422)) | (1L << (T__452 - 422)) | (1L << (T__453 - 422)) | (1L << (T__454 - 422)) | (1L << (T__455 - 422)) | (1L << (T__456 - 422)) | (1L << (T__457 - 422)) | (1L << (T__458 - 422)) | (1L << (T__459 - 422)) | (1L << (T__460 - 422)) | (1L << (T__461 - 422)) | (1L << (T__462 - 422)) | (1L << (T__463 - 422)) | (1L << (T__464 - 422)) | (1L << (T__465 - 422)) | (1L << (T__466 - 422)) | (1L << (T__467 - 422)) | (1L << (T__468 - 422)) | (1L << (T__469 - 422)) | (1L << (T__470 - 422)) | (1L << (T__471 - 422)) | (1L << (T__472 - 422)) | (1L << (T__473 - 422)) | (1L << (T__474 - 422)) | (1L << (T__475 - 422)) | (1L << (T__476 - 422)) | (1L << (T__477 - 422)) | (1L << (T__478 - 422)) | (1L << (T__479 - 422)) | (1L << (T__480 - 422)) | (1L << (T__481 - 422)) | (1L << (T__482 - 422)) | (1L << (T__483 - 422)) | (1L << (T__484 - 422)))) != 0) || ((((_la - 486)) & ~0x3f) == 0 && ((1L << (_la - 486)) & ((1L << (T__485 - 486)) | (1L << (T__486 - 486)) | (1L << (T__487 - 486)) | (1L << (T__488 - 486)) | (1L << (T__489 - 486)) | (1L << (T__490 - 486)) | (1L << (T__491 - 486)) | (1L << (T__492 - 486)) | (1L << (T__493 - 486)) | (1L << (T__494 - 486)) | (1L << (T__495 - 486)) | (1L << (T__496 - 486)) | (1L << (T__497 - 486)) | (1L << (T__498 - 486)) | (1L << (T__499 - 486)) | (1L << (T__500 - 486)) | (1L << (T__501 - 486)) | (1L << (T__502 - 486)) | (1L << (T__503 - 486)) | (1L << (T__504 - 486)) | (1L << (T__505 - 486)) | (1L << (T__506 - 486)) | (1L << (T__507 - 486)) | (1L << (T__508 - 486)) | (1L << (T__509 - 486)) | (1L << (T__510 - 486)) | (1L << (T__511 - 486)) | (1L << (T__512 - 486)) | (1L << (T__513 - 486)) | (1L << (T__514 - 486)) | (1L << (T__515 - 486)) | (1L << (T__516 - 486)) | (1L << (T__517 - 486)) | (1L << (T__518 - 486)) | (1L << (T__519 - 486)) | (1L << (T__520 - 486)) | (1L << (T__521 - 486)) | (1L << (T__522 - 486)) | (1L << (T__523 - 486)) | (1L << (T__524 - 486)) | (1L << (T__525 - 486)) | (1L << (T__526 - 486)) | (1L << (T__527 - 486)) | (1L << (T__528 - 486)) | (1L << (T__529 - 486)) | (1L << (T__530 - 486)) | (1L << (T__531 - 486)) | (1L << (T__532 - 486)) | (1L << (T__533 - 486)) | (1L << (T__534 - 486)) | (1L << (T__535 - 486)) | (1L << (T__536 - 486)) | (1L << (T__537 - 486)) | (1L << (T__538 - 486)) | (1L << (T__539 - 486)) | (1L << (T__540 - 486)) | (1L << (T__541 - 486)) | (1L << (T__542 - 486)))) != 0) || ((((_la - 551)) & ~0x3f) == 0 && ((1L << (_la - 551)) & ((1L << (IDENT - 551)) | (1L << (BIN_NUMBER - 551)) | (1L << (HEX_NUMBER - 551)) | (1L << (NUMBER - 551)))) != 0)) {
					{
					setState(419);
					_localctx.operand = operand();
					 base = _localctx.operand.op; 
					}
				}

				setState(433);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__79) {
					{
					setState(424);
					match(T__79);
					setState(425);
					_localctx.operand = operand();
					 offset = _localctx.operand.op; 
					setState(431);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__79) {
						{
						setState(427);
						match(T__79);
						setState(428);
						_localctx.number = number();
						 scale = (int) _localctx.number.n; 
						}
					}

					}
				}

				setState(435);
				match(T__422);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			 _localctx.op =  new AsmMemoryOperand(segment, displacement, base, offset, scale); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Register8Context extends ParserRuleContext {
		public AsmRegisterOperand op;
		public Token r;
		public Register8Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_register8; }
	}

	public final Register8Context register8() throws RecognitionException {
		Register8Context _localctx = new Register8Context(_ctx, getState());
		enterRule(_localctx, 46, RULE_register8);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(440);
			((Register8Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 424)) & ~0x3f) == 0 && ((1L << (_la - 424)) & ((1L << (T__423 - 424)) | (1L << (T__424 - 424)) | (1L << (T__425 - 424)) | (1L << (T__426 - 424)) | (1L << (T__427 - 424)) | (1L << (T__428 - 424)) | (1L << (T__429 - 424)) | (1L << (T__430 - 424)) | (1L << (T__431 - 424)) | (1L << (T__432 - 424)) | (1L << (T__433 - 424)) | (1L << (T__434 - 424)) | (1L << (T__435 - 424)) | (1L << (T__436 - 424)) | (1L << (T__437 - 424)) | (1L << (T__438 - 424)) | (1L << (T__439 - 424)) | (1L << (T__440 - 424)) | (1L << (T__441 - 424)) | (1L << (T__442 - 424)) | (1L << (T__443 - 424)) | (1L << (T__444 - 424)) | (1L << (T__445 - 424)) | (1L << (T__446 - 424)))) != 0)) ) {
				((Register8Context)_localctx).r = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 ((Register8Context)_localctx).op =  new AsmRegisterOperand(((Register8Context)_localctx).r.getText()); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Register16Context extends ParserRuleContext {
		public AsmRegisterOperand op;
		public Token r;
		public Register16Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_register16; }
	}

	public final Register16Context register16() throws RecognitionException {
		Register16Context _localctx = new Register16Context(_ctx, getState());
		enterRule(_localctx, 48, RULE_register16);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(443);
			((Register16Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 448)) & ~0x3f) == 0 && ((1L << (_la - 448)) & ((1L << (T__447 - 448)) | (1L << (T__448 - 448)) | (1L << (T__449 - 448)) | (1L << (T__450 - 448)) | (1L << (T__451 - 448)) | (1L << (T__452 - 448)) | (1L << (T__453 - 448)) | (1L << (T__454 - 448)) | (1L << (T__455 - 448)) | (1L << (T__456 - 448)) | (1L << (T__457 - 448)) | (1L << (T__458 - 448)) | (1L << (T__459 - 448)) | (1L << (T__460 - 448)) | (1L << (T__461 - 448)) | (1L << (T__462 - 448)) | (1L << (T__463 - 448)) | (1L << (T__464 - 448)) | (1L << (T__465 - 448)) | (1L << (T__466 - 448)) | (1L << (T__467 - 448)) | (1L << (T__468 - 448)) | (1L << (T__469 - 448)) | (1L << (T__470 - 448)))) != 0)) ) {
				((Register16Context)_localctx).r = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 ((Register16Context)_localctx).op =  new AsmRegisterOperand(((Register16Context)_localctx).r.getText()); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Register32Context extends ParserRuleContext {
		public AsmRegisterOperand op;
		public Token r;
		public Register32Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_register32; }
	}

	public final Register32Context register32() throws RecognitionException {
		Register32Context _localctx = new Register32Context(_ctx, getState());
		enterRule(_localctx, 50, RULE_register32);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(446);
			((Register32Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 472)) & ~0x3f) == 0 && ((1L << (_la - 472)) & ((1L << (T__471 - 472)) | (1L << (T__472 - 472)) | (1L << (T__473 - 472)) | (1L << (T__474 - 472)) | (1L << (T__475 - 472)) | (1L << (T__476 - 472)) | (1L << (T__477 - 472)) | (1L << (T__478 - 472)) | (1L << (T__479 - 472)) | (1L << (T__480 - 472)) | (1L << (T__481 - 472)) | (1L << (T__482 - 472)) | (1L << (T__483 - 472)) | (1L << (T__484 - 472)) | (1L << (T__485 - 472)) | (1L << (T__486 - 472)) | (1L << (T__487 - 472)) | (1L << (T__488 - 472)) | (1L << (T__489 - 472)) | (1L << (T__490 - 472)) | (1L << (T__491 - 472)) | (1L << (T__492 - 472)) | (1L << (T__493 - 472)) | (1L << (T__494 - 472)))) != 0)) ) {
				((Register32Context)_localctx).r = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 ((Register32Context)_localctx).op =  new AsmRegisterOperand(((Register32Context)_localctx).r.getText()); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Register64Context extends ParserRuleContext {
		public AsmRegisterOperand op;
		public Token r;
		public Register64Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_register64; }
	}

	public final Register64Context register64() throws RecognitionException {
		Register64Context _localctx = new Register64Context(_ctx, getState());
		enterRule(_localctx, 52, RULE_register64);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(449);
			((Register64Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 496)) & ~0x3f) == 0 && ((1L << (_la - 496)) & ((1L << (T__495 - 496)) | (1L << (T__496 - 496)) | (1L << (T__497 - 496)) | (1L << (T__498 - 496)) | (1L << (T__499 - 496)) | (1L << (T__500 - 496)) | (1L << (T__501 - 496)) | (1L << (T__502 - 496)) | (1L << (T__503 - 496)) | (1L << (T__504 - 496)) | (1L << (T__505 - 496)) | (1L << (T__506 - 496)) | (1L << (T__507 - 496)) | (1L << (T__508 - 496)) | (1L << (T__509 - 496)) | (1L << (T__510 - 496)) | (1L << (T__511 - 496)) | (1L << (T__512 - 496)) | (1L << (T__513 - 496)) | (1L << (T__514 - 496)) | (1L << (T__515 - 496)) | (1L << (T__516 - 496)) | (1L << (T__517 - 496)) | (1L << (T__518 - 496)))) != 0)) ) {
				((Register64Context)_localctx).r = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 ((Register64Context)_localctx).op =  new AsmRegisterOperand(((Register64Context)_localctx).r.getText()); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RegisterXmmContext extends ParserRuleContext {
		public AsmRegisterOperand op;
		public Token r;
		public RegisterXmmContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_registerXmm; }
	}

	public final RegisterXmmContext registerXmm() throws RecognitionException {
		RegisterXmmContext _localctx = new RegisterXmmContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_registerXmm);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(452);
			_localctx.r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 520)) & ~0x3f) == 0 && ((1L << (_la - 520)) & ((1L << (T__519 - 520)) | (1L << (T__520 - 520)) | (1L << (T__521 - 520)) | (1L << (T__522 - 520)) | (1L << (T__523 - 520)) | (1L << (T__524 - 520)) | (1L << (T__525 - 520)) | (1L << (T__526 - 520)) | (1L << (T__527 - 520)) | (1L << (T__528 - 520)) | (1L << (T__529 - 520)) | (1L << (T__530 - 520)) | (1L << (T__531 - 520)) | (1L << (T__532 - 520)) | (1L << (T__533 - 520)) | (1L << (T__534 - 520)))) != 0)) ) {
				_localctx.r = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 _localctx.op =  new AsmRegisterOperand(_localctx.r.getText()); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Segment_registerContext extends ParserRuleContext {
		public String reg;
		public Token r;
		public Segment_registerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_segment_register; }
	}

	public final Segment_registerContext segment_register() throws RecognitionException {
		Segment_registerContext _localctx = new Segment_registerContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_segment_register);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(455);
			_localctx.r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 536)) & ~0x3f) == 0 && ((1L << (_la - 536)) & ((1L << (T__535 - 536)) | (1L << (T__536 - 536)) | (1L << (T__537 - 536)) | (1L << (T__538 - 536)) | (1L << (T__539 - 536)) | (1L << (T__540 - 536)))) != 0)) ) {
				_localctx.r = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			 _localctx.reg =  _localctx.r.getText(); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NumberContext extends ParserRuleContext {
		public long n;
		public Token num;
		public TerminalNode NUMBER() { return getToken(InlineAssemblyParser.NUMBER, 0); }
		public TerminalNode BIN_NUMBER() { return getToken(InlineAssemblyParser.BIN_NUMBER, 0); }
		public TerminalNode HEX_NUMBER() { return getToken(InlineAssemblyParser.HEX_NUMBER, 0); }
		public NumberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_number; }
	}

	public final NumberContext number() throws RecognitionException {
		NumberContext _localctx = new NumberContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_number);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(464);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER:
				{
				setState(458);
				_localctx.num = match(NUMBER);
				 _localctx.n =  Long.parseLong(_localctx.num.getText(), 10); 
				}
				break;
			case BIN_NUMBER:
				{
				setState(460);
				_localctx.num = match(BIN_NUMBER);
				 _localctx.n =  Long.parseLong(_localctx.num.getText().substring(2), 2); 
				}
				break;
			case HEX_NUMBER:
				{
				setState(462);
				_localctx.num = match(HEX_NUMBER);
				 _localctx.n =  Long.parseLong(_localctx.num.getText().substring(2), 16); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ImmediateContext extends ParserRuleContext {
		public AsmImmediateOperand op;
		public NumberContext number;
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public ImmediateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_immediate; }
	}

	public final ImmediateContext immediate() throws RecognitionException {
		ImmediateContext _localctx = new ImmediateContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_immediate);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(466);
			match(T__541);
			setState(467);
			_localctx.number = number();
			 _localctx.op =  new AsmImmediateOperand(_localctx.number.n); 
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArgumentContext extends ParserRuleContext {
		public AsmArgumentOperand op;
		public NumberContext n;
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public ArgumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argument; }
	}

	public final ArgumentContext argument() throws RecognitionException {
		ArgumentContext _localctx = new ArgumentContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_argument);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(470);
			match(T__542);
			setState(493);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(471);
				_localctx.n = number();
				 _localctx.op =  new AsmArgumentOperand((int) _localctx.n.n); 
				}
				break;
			case T__543:
				{
				setState(474);
				match(T__543);
				setState(475);
				_localctx.n = number();
				setState(476);
				match(T__420);
				 int size = -1; int shift = 0; 
				setState(488);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__544:
					{
					setState(478);
					match(T__544);
					 size = 8; 
					}
					break;
				case T__545:
					{
					setState(480);
					match(T__545);
					 size = 8; shift = 8; 
					}
					break;
				case T__546:
					{
					setState(482);
					match(T__546);
					 size = 16; 
					}
					break;
				case T__547:
					{
					setState(484);
					match(T__547);
					 size = 32; 
					}
					break;
				case T__548:
					{
					setState(486);
					match(T__548);
					 size = 64; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 _localctx.op =  new AsmArgumentOperand((int) _localctx.n.n, size, shift); 
				setState(491);
				match(T__549);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\u022d\u01f2\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\3\2\3\2\3\2\5\2F\n\2\3\2\5\2I\n\2\3\2\3\2\3\2\3\2\5\2O\n\2\3\2\5\2"+
		"R\n\2\7\2T\n\2\f\2\16\2W\13\2\5\2Y\n\2\3\2\3\2\3\2\3\3\3\3\3\3\3\4\3\4"+
		"\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\5\4o\n\4\3\5\3\5\3\5"+
		"\3\5\3\6\3\6\3\6\3\7\3\7\3\7\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\5"+
		"\b\u0085\n\b\5\b\u0087\n\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\5\b"+
		"\u0093\n\b\5\b\u0095\n\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\5\b\u00a1"+
		"\n\b\5\b\u00a3\n\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\5\b\u00af\n"+
		"\b\5\b\u00b1\n\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\5\b\u00bd\n\b"+
		"\5\b\u00bf\n\b\5\b\u00c1\n\b\3\t\3\t\3\t\3\t\3\n\3\n\3\n\3\n\3\13\3\13"+
		"\3\13\3\13\3\f\3\f\3\f\3\f\3\r\3\r\3\r\3\r\3\16\3\16\3\16\3\16\3\16\3"+
		"\16\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3"+
		"\17\3\17\3\17\3\17\3\17\5\17\u00ef\n\17\3\20\3\20\3\20\3\20\3\20\3\20"+
		"\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20"+
		"\3\20\3\20\3\20\3\20\5\20\u0109\n\20\3\21\3\21\3\21\3\21\3\21\3\21\3\21"+
		"\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21"+
		"\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\5\21\u0129\n\21\3\22\3\22"+
		"\3\22\3\22\3\22\3\22\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23"+
		"\3\23\3\23\5\23\u013d\n\23\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\3\24\5\24\u014b\n\24\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\3\25\5\25\u0159\n\25\3\26\3\26\3\26\3\26\3\26\3\26\3\26"+
		"\3\26\3\26\3\26\3\26\3\26\5\26\u0167\n\26\3\27\3\27\3\27\3\27\3\27\3\27"+
		"\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27"+
		"\3\27\3\27\3\27\3\27\5\27\u0181\n\27\3\30\3\30\3\30\3\30\3\30\5\30\u0188"+
		"\n\30\3\30\3\30\3\30\3\30\3\30\5\30\u018f\n\30\3\30\3\30\3\30\3\30\5\30"+
		"\u0195\n\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\5\30\u019e\n\30\5\30\u01a0"+
		"\n\30\3\30\5\30\u01a3\n\30\3\30\3\30\3\30\3\30\5\30\u01a9\n\30\3\30\3"+
		"\30\3\30\3\30\3\30\3\30\3\30\5\30\u01b2\n\30\5\30\u01b4\n\30\3\30\5\30"+
		"\u01b7\n\30\3\30\3\30\3\31\3\31\3\31\3\32\3\32\3\32\3\33\3\33\3\33\3\34"+
		"\3\34\3\34\3\35\3\35\3\35\3\36\3\36\3\36\3\37\3\37\3\37\3\37\3\37\3\37"+
		"\5\37\u01d3\n\37\3 \3 \3 \3 \3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\3"+
		"!\3!\3!\3!\3!\5!\u01eb\n!\3!\3!\3!\5!\u01f0\n!\3!\2\2\"\2\4\6\b\n\f\16"+
		"\20\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@\2#\3\2\4\5\3\2\6\13"+
		"\3\2\r\63\3\2\64O\3\2PQ\3\2ST\3\2UV\3\2WX\3\2YZ\3\2[`\3\2ah\3\2iq\3\2"+
		"rz\3\2{\u00a3\3\2\u00a4\u00b8\3\2\u00b9\u00e9\3\2\u00ea\u00f1\3\2\u00f2"+
		"\u00f3\3\2\u00f4\u0124\3\2\u0125\u012c\3\2\u012d\u012e\3\2\u012f\u0130"+
		"\3\2\u0131\u0161\3\2\u0162\u0169\3\2\u016a\u016b\3\2\u016c\u016d\3\2\u016f"+
		"\u01a6\3\2\u01aa\u01c1\3\2\u01c2\u01d9\3\2\u01da\u01f1\3\2\u01f2\u0209"+
		"\3\2\u020a\u0219\3\2\u021a\u021f\2\u021f\2B\3\2\2\2\4]\3\2\2\2\6n\3\2"+
		"\2\2\bp\3\2\2\2\nt\3\2\2\2\fw\3\2\2\2\16\u00c0\3\2\2\2\20\u00c2\3\2\2"+
		"\2\22\u00c6\3\2\2\2\24\u00ca\3\2\2\2\26\u00ce\3\2\2\2\30\u00d2\3\2\2\2"+
		"\32\u00d6\3\2\2\2\34\u00ee\3\2\2\2\36\u0108\3\2\2\2 \u0128\3\2\2\2\"\u012a"+
		"\3\2\2\2$\u013c\3\2\2\2&\u014a\3\2\2\2(\u0158\3\2\2\2*\u0166\3\2\2\2,"+
		"\u0180\3\2\2\2.\u0182\3\2\2\2\60\u01ba\3\2\2\2\62\u01bd\3\2\2\2\64\u01c0"+
		"\3\2\2\2\66\u01c3\3\2\2\28\u01c6\3\2\2\2:\u01c9\3\2\2\2<\u01d2\3\2\2\2"+
		">\u01d4\3\2\2\2@\u01d8\3\2\2\2BX\7\3\2\2CE\5\4\3\2DF\7\4\2\2ED\3\2\2\2"+
		"EF\3\2\2\2FI\3\2\2\2GI\b\2\1\2HC\3\2\2\2HG\3\2\2\2IJ\3\2\2\2JU\5\6\4\2"+
		"KN\t\2\2\2LO\5\4\3\2MO\b\2\1\2NL\3\2\2\2NM\3\2\2\2OQ\3\2\2\2PR\5\6\4\2"+
		"QP\3\2\2\2QR\3\2\2\2RT\3\2\2\2SK\3\2\2\2TW\3\2\2\2US\3\2\2\2UV\3\2\2\2"+
		"VY\3\2\2\2WU\3\2\2\2XH\3\2\2\2XY\3\2\2\2YZ\3\2\2\2Z[\7\3\2\2[\\\b\2\1"+
		"\2\\\3\3\2\2\2]^\t\3\2\2^_\b\3\1\2_\5\3\2\2\2`o\5\f\7\2ao\5\20\t\2bo\5"+
		"\22\n\2co\5\24\13\2do\5\26\f\2eo\5\30\r\2fo\5\32\16\2go\5\34\17\2ho\5"+
		"\36\20\2io\5 \21\2jo\5\"\22\2ko\5\16\b\2lo\5\n\6\2mo\5\b\5\2n`\3\2\2\2"+
		"na\3\2\2\2nb\3\2\2\2nc\3\2\2\2nd\3\2\2\2ne\3\2\2\2nf\3\2\2\2ng\3\2\2\2"+
		"nh\3\2\2\2ni\3\2\2\2nj\3\2\2\2nk\3\2\2\2nl\3\2\2\2nm\3\2\2\2o\7\3\2\2"+
		"\2pq\7\f\2\2qr\5> \2rs\b\5\1\2s\t\3\2\2\2tu\t\4\2\2uv\5*\26\2v\13\3\2"+
		"\2\2wx\t\5\2\2xy\b\7\1\2y\r\3\2\2\2z{\t\6\2\2{\u0086\5$\23\2|\u0087\b"+
		"\b\1\2}~\7R\2\2~\u0084\5$\23\2\177\u0085\b\b\1\2\u0080\u0081\7R\2\2\u0081"+
		"\u0082\5$\23\2\u0082\u0083\b\b\1\2\u0083\u0085\3\2\2\2\u0084\177\3\2\2"+
		"\2\u0084\u0080\3\2\2\2\u0085\u0087\3\2\2\2\u0086|\3\2\2\2\u0086}\3\2\2"+
		"\2\u0087\u00c1\3\2\2\2\u0088\u0089\t\7\2\2\u0089\u0094\5&\24\2\u008a\u0095"+
		"\b\b\1\2\u008b\u008c\7R\2\2\u008c\u0092\5&\24\2\u008d\u0093\b\b\1\2\u008e"+
		"\u008f\7R\2\2\u008f\u0090\5&\24\2\u0090\u0091\b\b\1\2\u0091\u0093\3\2"+
		"\2\2\u0092\u008d\3\2\2\2\u0092\u008e\3\2\2\2\u0093\u0095\3\2\2\2\u0094"+
		"\u008a\3\2\2\2\u0094\u008b\3\2\2\2\u0095\u00c1\3\2\2\2\u0096\u0097\t\b"+
		"\2\2\u0097\u00a2\5(\25\2\u0098\u00a3\b\b\1\2\u0099\u009a\7R\2\2\u009a"+
		"\u00a0\5(\25\2\u009b\u00a1\b\b\1\2\u009c\u009d\7R\2\2\u009d\u009e\5(\25"+
		"\2\u009e\u009f\b\b\1\2\u009f\u00a1\3\2\2\2\u00a0\u009b\3\2\2\2\u00a0\u009c"+
		"\3\2\2\2\u00a1\u00a3\3\2\2\2\u00a2\u0098\3\2\2\2\u00a2\u0099\3\2\2\2\u00a3"+
		"\u00c1\3\2\2\2\u00a4\u00a5\t\t\2\2\u00a5\u00b0\5*\26\2\u00a6\u00b1\b\b"+
		"\1\2\u00a7\u00a8\7R\2\2\u00a8\u00ae\5*\26\2\u00a9\u00af\b\b\1\2\u00aa"+
		"\u00ab\7R\2\2\u00ab\u00ac\5*\26\2\u00ac\u00ad\b\b\1\2\u00ad\u00af\3\2"+
		"\2\2\u00ae\u00a9\3\2\2\2\u00ae\u00aa\3\2\2\2\u00af\u00b1\3\2\2\2\u00b0"+
		"\u00a6\3\2\2\2\u00b0\u00a7\3\2\2\2\u00b1\u00c1\3\2\2\2\u00b2\u00b3\t\n"+
		"\2\2\u00b3\u00be\5,\27\2\u00b4\u00bf\b\b\1\2\u00b5\u00b6\7R\2\2\u00b6"+
		"\u00bc\5,\27\2\u00b7\u00bd\b\b\1\2\u00b8\u00b9\7R\2\2\u00b9\u00ba\5,\27"+
		"\2\u00ba\u00bb\b\b\1\2\u00bb\u00bd\3\2\2\2\u00bc\u00b7\3\2\2\2\u00bc\u00b8"+
		"\3\2\2\2\u00bd\u00bf\3\2\2\2\u00be\u00b4\3\2\2\2\u00be\u00b5\3\2\2\2\u00bf"+
		"\u00c1\3\2\2\2\u00c0z\3\2\2\2\u00c0\u0088\3\2\2\2\u00c0\u0096\3\2\2\2"+
		"\u00c0\u00a4\3\2\2\2\u00c0\u00b2\3\2\2\2\u00c1\17\3\2\2\2\u00c2\u00c3"+
		"\t\13\2\2\u00c3\u00c4\5$\23\2\u00c4\u00c5\b\t\1\2\u00c5\21\3\2\2\2\u00c6"+
		"\u00c7\t\f\2\2\u00c7\u00c8\5&\24\2\u00c8\u00c9\b\n\1\2\u00c9\23\3\2\2"+
		"\2\u00ca\u00cb\t\r\2\2\u00cb\u00cc\5(\25\2\u00cc\u00cd\b\13\1\2\u00cd"+
		"\25\3\2\2\2\u00ce\u00cf\t\16\2\2\u00cf\u00d0\5*\26\2\u00d0\u00d1\b\f\1"+
		"\2\u00d1\27\3\2\2\2\u00d2\u00d3\t\17\2\2\u00d3\u00d4\5,\27\2\u00d4\u00d5"+
		"\b\r\1\2\u00d5\31\3\2\2\2\u00d6\u00d7\t\20\2\2\u00d7\u00d8\5$\23\2\u00d8"+
		"\u00d9\7R\2\2\u00d9\u00da\5$\23\2\u00da\u00db\b\16\1\2\u00db\33\3\2\2"+
		"\2\u00dc\u00dd\t\21\2\2\u00dd\u00de\5&\24\2\u00de\u00df\7R\2\2\u00df\u00e0"+
		"\5&\24\2\u00e0\u00e1\b\17\1\2\u00e1\u00ef\3\2\2\2\u00e2\u00e3\t\22\2\2"+
		"\u00e3\u00e4\5$\23\2\u00e4\u00e5\7R\2\2\u00e5\u00e6\5&\24\2\u00e6\u00e7"+
		"\b\17\1\2\u00e7\u00ef\3\2\2\2\u00e8\u00e9\t\23\2\2\u00e9\u00ea\5$\23\2"+
		"\u00ea\u00eb\7R\2\2\u00eb\u00ec\5&\24\2\u00ec\u00ed\b\17\1\2\u00ed\u00ef"+
		"\3\2\2\2\u00ee\u00dc\3\2\2\2\u00ee\u00e2\3\2\2\2\u00ee\u00e8\3\2\2\2\u00ef"+
		"\35\3\2\2\2\u00f0\u00f1\t\24\2\2\u00f1\u00f2\5(\25\2\u00f2\u00f3\7R\2"+
		"\2\u00f3\u00f4\5(\25\2\u00f4\u00f5\b\20\1\2\u00f5\u0109\3\2\2\2\u00f6"+
		"\u00f7\t\25\2\2\u00f7\u00f8\5$\23\2\u00f8\u00f9\7R\2\2\u00f9\u00fa\5("+
		"\25\2\u00fa\u00fb\b\20\1\2\u00fb\u0109\3\2\2\2\u00fc\u00fd\t\26\2\2\u00fd"+
		"\u00fe\5$\23\2\u00fe\u00ff\7R\2\2\u00ff\u0100\5(\25\2\u0100\u0101\b\20"+
		"\1\2\u0101\u0109\3\2\2\2\u0102\u0103\t\27\2\2\u0103\u0104\5&\24\2\u0104"+
		"\u0105\7R\2\2\u0105\u0106\5(\25\2\u0106\u0107\b\20\1\2\u0107\u0109\3\2"+
		"\2\2\u0108\u00f0\3\2\2\2\u0108\u00f6\3\2\2\2\u0108\u00fc\3\2\2\2\u0108"+
		"\u0102\3\2\2\2\u0109\37\3\2\2\2\u010a\u010b\t\30\2\2\u010b\u010c\5*\26"+
		"\2\u010c\u010d\7R\2\2\u010d\u010e\5*\26\2\u010e\u010f\b\21\1\2\u010f\u0129"+
		"\3\2\2\2\u0110\u0111\t\31\2\2\u0111\u0112\5$\23\2\u0112\u0113\7R\2\2\u0113"+
		"\u0114\5*\26\2\u0114\u0115\b\21\1\2\u0115\u0129\3\2\2\2\u0116\u0117\t"+
		"\32\2\2\u0117\u0118\5$\23\2\u0118\u0119\7R\2\2\u0119\u011a\5*\26\2\u011a"+
		"\u011b\b\21\1\2\u011b\u0129\3\2\2\2\u011c\u011d\t\33\2\2\u011d\u011e\5"+
		"&\24\2\u011e\u011f\7R\2\2\u011f\u0120\5*\26\2\u0120\u0121\b\21\1\2\u0121"+
		"\u0129\3\2\2\2\u0122\u0123\7\u016e\2\2\u0123\u0124\5(\25\2\u0124\u0125"+
		"\7R\2\2\u0125\u0126\5*\26\2\u0126\u0127\b\21\1\2\u0127\u0129\3\2\2\2\u0128"+
		"\u010a\3\2\2\2\u0128\u0110\3\2\2\2\u0128\u0116\3\2\2\2\u0128\u011c\3\2"+
		"\2\2\u0128\u0122\3\2\2\2\u0129!\3\2\2\2\u012a\u012b\t\34\2\2\u012b\u012c"+
		"\5,\27\2\u012c\u012d\7R\2\2\u012d\u012e\5,\27\2\u012e\u012f\b\22\1\2\u012f"+
		"#\3\2\2\2\u0130\u0131\5\60\31\2\u0131\u0132\b\23\1\2\u0132\u013d\3\2\2"+
		"\2\u0133\u0134\5.\30\2\u0134\u0135\b\23\1\2\u0135\u013d\3\2\2\2\u0136"+
		"\u0137\5> \2\u0137\u0138\b\23\1\2\u0138\u013d\3\2\2\2\u0139\u013a\5@!"+
		"\2\u013a\u013b\b\23\1\2\u013b\u013d\3\2\2\2\u013c\u0130\3\2\2\2\u013c"+
		"\u0133\3\2\2\2\u013c\u0136\3\2\2\2\u013c\u0139\3\2\2\2\u013d%\3\2\2\2"+
		"\u013e\u013f\5\62\32\2\u013f\u0140\b\24\1\2\u0140\u014b\3\2\2\2\u0141"+
		"\u0142\5.\30\2\u0142\u0143\b\24\1\2\u0143\u014b\3\2\2\2\u0144\u0145\5"+
		"> \2\u0145\u0146\b\24\1\2\u0146\u014b\3\2\2\2\u0147\u0148\5@!\2\u0148"+
		"\u0149\b\24\1\2\u0149\u014b\3\2\2\2\u014a\u013e\3\2\2\2\u014a\u0141\3"+
		"\2\2\2\u014a\u0144\3\2\2\2\u014a\u0147\3\2\2\2\u014b\'\3\2\2\2\u014c\u014d"+
		"\5\64\33\2\u014d\u014e\b\25\1\2\u014e\u0159\3\2\2\2\u014f\u0150\5.\30"+
		"\2\u0150\u0151\b\25\1\2\u0151\u0159\3\2\2\2\u0152\u0153\5> \2\u0153\u0154"+
		"\b\25\1\2\u0154\u0159\3\2\2\2\u0155\u0156\5@!\2\u0156\u0157\b\25\1\2\u0157"+
		"\u0159\3\2\2\2\u0158\u014c\3\2\2\2\u0158\u014f\3\2\2\2\u0158\u0152\3\2"+
		"\2\2\u0158\u0155\3\2\2\2\u0159)\3\2\2\2\u015a\u015b\5\66\34\2\u015b\u015c"+
		"\b\26\1\2\u015c\u0167\3\2\2\2\u015d\u015e\5.\30\2\u015e\u015f\b\26\1\2"+
		"\u015f\u0167\3\2\2\2\u0160\u0161\5> \2\u0161\u0162\b\26\1\2\u0162\u0167"+
		"\3\2\2\2\u0163\u0164\5@!\2\u0164\u0165\b\26\1\2\u0165\u0167\3\2\2\2\u0166"+
		"\u015a\3\2\2\2\u0166\u015d\3\2\2\2\u0166\u0160\3\2\2\2\u0166\u0163\3\2"+
		"\2\2\u0167+\3\2\2\2\u0168\u0169\5\60\31\2\u0169\u016a\b\27\1\2\u016a\u0181"+
		"\3\2\2\2\u016b\u016c\5\62\32\2\u016c\u016d\b\27\1\2\u016d\u0181\3\2\2"+
		"\2\u016e\u016f\5\64\33\2\u016f\u0170\b\27\1\2\u0170\u0181\3\2\2\2\u0171"+
		"\u0172\5\66\34\2\u0172\u0173\b\27\1\2\u0173\u0181\3\2\2\2\u0174\u0175"+
		"\58\35\2\u0175\u0176\b\27\1\2\u0176\u0181\3\2\2\2\u0177\u0178\5.\30\2"+
		"\u0178\u0179\b\27\1\2\u0179\u0181\3\2\2\2\u017a\u017b\5> \2\u017b\u017c"+
		"\b\27\1\2\u017c\u0181\3\2\2\2\u017d\u017e\5@!\2\u017e\u017f\b\27\1\2\u017f"+
		"\u0181\3\2\2\2\u0180\u0168\3\2\2\2\u0180\u016b\3\2\2\2\u0180\u016e\3\2"+
		"\2\2\u0180\u0171\3\2\2\2\u0180\u0174\3\2\2\2\u0180\u0177\3\2\2\2\u0180"+
		"\u017a\3\2\2\2\u0180\u017d\3\2\2\2\u0181-\3\2\2\2\u0182\u0187\b\30\1\2"+
		"\u0183\u0184\5:\36\2\u0184\u0185\b\30\1\2\u0185\u0186\7\u01a7\2\2\u0186"+
		"\u0188\3\2\2\2\u0187\u0183\3\2\2\2\u0187\u0188\3\2\2\2\u0188\u01b6\3\2"+
		"\2\2\u0189\u018a\7\u0229\2\2\u018a\u018f\b\30\1\2\u018b\u018c\5<\37\2"+
		"\u018c\u018d\b\30\1\2\u018d\u018f\3\2\2\2\u018e\u0189\3\2\2\2\u018e\u018b"+
		"\3\2\2\2\u018f\u01a2\3\2\2\2\u0190\u0194\7\u01a8\2\2\u0191\u0192\5,\27"+
		"\2\u0192\u0193\b\30\1\2\u0193\u0195\3\2\2\2\u0194\u0191\3\2\2\2\u0194"+
		"\u0195\3\2\2\2\u0195\u019f\3\2\2\2\u0196\u0197\7R\2\2\u0197\u0198\5,\27"+
		"\2\u0198\u019d\b\30\1\2\u0199\u019a\7R\2\2\u019a\u019b\5<\37\2\u019b\u019c"+
		"\b\30\1\2\u019c\u019e\3\2\2\2\u019d\u0199\3\2\2\2\u019d\u019e\3\2\2\2"+
		"\u019e\u01a0\3\2\2\2\u019f\u0196\3\2\2\2\u019f\u01a0\3\2\2\2\u01a0\u01a1"+
		"\3\2\2\2\u01a1\u01a3\7\u01a9\2\2\u01a2\u0190\3\2\2\2\u01a2\u01a3\3\2\2"+
		"\2\u01a3\u01b7\3\2\2\2\u01a4\u01a8\7\u01a8\2\2\u01a5\u01a6\5,\27\2\u01a6"+
		"\u01a7\b\30\1\2\u01a7\u01a9\3\2\2\2\u01a8\u01a5\3\2\2\2\u01a8\u01a9\3"+
		"\2\2\2\u01a9\u01b3\3\2\2\2\u01aa\u01ab\7R\2\2\u01ab\u01ac\5,\27\2\u01ac"+
		"\u01b1\b\30\1\2\u01ad\u01ae\7R\2\2\u01ae\u01af\5<\37\2\u01af\u01b0\b\30"+
		"\1\2\u01b0\u01b2\3\2\2\2\u01b1\u01ad\3\2\2\2\u01b1\u01b2\3\2\2\2\u01b2"+
		"\u01b4\3\2\2\2\u01b3\u01aa\3\2\2\2\u01b3\u01b4\3\2\2\2\u01b4\u01b5\3\2"+
		"\2\2\u01b5\u01b7\7\u01a9\2\2\u01b6\u018e\3\2\2\2\u01b6\u01a4\3\2\2\2\u01b7"+
		"\u01b8\3\2\2\2\u01b8\u01b9\b\30\1\2\u01b9/\3\2\2\2\u01ba\u01bb\t\35\2"+
		"\2\u01bb\u01bc\b\31\1\2\u01bc\61\3\2\2\2\u01bd\u01be\t\36\2\2\u01be\u01bf"+
		"\b\32\1\2\u01bf\63\3\2\2\2\u01c0\u01c1\t\37\2\2\u01c1\u01c2\b\33\1\2\u01c2"+
		"\65\3\2\2\2\u01c3\u01c4\t \2\2\u01c4\u01c5\b\34\1\2\u01c5\67\3\2\2\2\u01c6"+
		"\u01c7\t!\2\2\u01c7\u01c8\b\35\1\2\u01c89\3\2\2\2\u01c9\u01ca\t\"\2\2"+
		"\u01ca\u01cb\b\36\1\2\u01cb;\3\2\2\2\u01cc\u01cd\7\u022c\2\2\u01cd\u01d3"+
		"\b\37\1\2\u01ce\u01cf\7\u022a\2\2\u01cf\u01d3\b\37\1\2\u01d0\u01d1\7\u022b"+
		"\2\2\u01d1\u01d3\b\37\1\2\u01d2\u01cc\3\2\2\2\u01d2\u01ce\3\2\2\2\u01d2"+
		"\u01d0\3\2\2\2\u01d3=\3\2\2\2\u01d4\u01d5\7\u0220\2\2\u01d5\u01d6\5<\37"+
		"\2\u01d6\u01d7\b \1\2\u01d7?\3\2\2\2\u01d8\u01ef\7\u0221\2\2\u01d9\u01da"+
		"\5<\37\2\u01da\u01db\b!\1\2\u01db\u01f0\3\2\2\2\u01dc\u01dd\7\u0222\2"+
		"\2\u01dd\u01de\5<\37\2\u01de\u01df\7\u01a7\2\2\u01df\u01ea\b!\1\2\u01e0"+
		"\u01e1\7\u0223\2\2\u01e1\u01eb\b!\1\2\u01e2\u01e3\7\u0224\2\2\u01e3\u01eb"+
		"\b!\1\2\u01e4\u01e5\7\u0225\2\2\u01e5\u01eb\b!\1\2\u01e6\u01e7\7\u0226"+
		"\2\2\u01e7\u01eb\b!\1\2\u01e8\u01e9\7\u0227\2\2\u01e9\u01eb\b!\1\2\u01ea"+
		"\u01e0\3\2\2\2\u01ea\u01e2\3\2\2\2\u01ea\u01e4\3\2\2\2\u01ea\u01e6\3\2"+
		"\2\2\u01ea\u01e8\3\2\2\2\u01eb\u01ec\3\2\2\2\u01ec\u01ed\b!\1\2\u01ed"+
		"\u01ee\7\u0228\2\2\u01ee\u01f0\3\2\2\2\u01ef\u01d9\3\2\2\2\u01ef\u01dc"+
		"\3\2\2\2\u01f0A\3\2\2\2)EHNQUXn\u0084\u0086\u0092\u0094\u00a0\u00a2\u00ae"+
		"\u00b0\u00bc\u00be\u00c0\u00ee\u0108\u0128\u013c\u014a\u0158\u0166\u0180"+
		"\u0187\u018e\u0194\u019d\u019f\u01a2\u01a8\u01b1\u01b3\u01b6\u01d2\u01ea"+
		"\u01ef";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
