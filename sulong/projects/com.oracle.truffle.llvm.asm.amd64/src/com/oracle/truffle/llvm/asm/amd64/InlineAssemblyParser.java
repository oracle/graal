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
// Checkstyle: stop
//@formatter:off
package com.oracle.truffle.llvm.asm.amd64;

// DO NOT MODIFY - generated from InlineAssembly.g4 using "mx create-asm-parser"

import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.runtime.types.Type;
import static com.oracle.truffle.llvm.runtime.types.Type.TypeArrayBuilder;

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
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

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
		T__545=546, T__546=547, T__547=548, T__548=549, T__549=550, T__550=551, 
		IDENT=552, BIN_NUMBER=553, HEX_NUMBER=554, NUMBER=555, WS=556, COMMENT=557, 
		LINE_COMMENT=558;
	public static final int
		RULE_inline_assembly = 0, RULE_prefix = 1, RULE_assembly_instruction = 2, 
		RULE_int_value = 3, RULE_jump = 4, RULE_directive = 5, RULE_zero_op = 6, 
		RULE_imul_div = 7, RULE_unary_op8 = 8, RULE_unary_op16 = 9, RULE_unary_op32 = 10, 
		RULE_unary_op64 = 11, RULE_unary_op = 12, RULE_binary_op8 = 13, RULE_binary_op16 = 14, 
		RULE_binary_op32 = 15, RULE_binary_op64 = 16, RULE_binary_op = 17, RULE_operand8 = 18, 
		RULE_operand16 = 19, RULE_operand32 = 20, RULE_operand64 = 21, RULE_operand = 22, 
		RULE_memory_reference = 23, RULE_register8 = 24, RULE_register16 = 25, 
		RULE_register32 = 26, RULE_register64 = 27, RULE_registerXmm = 28, RULE_segment_register = 29, 
		RULE_number = 30, RULE_immediate = 31, RULE_argument = 32;
	private static String[] makeRuleNames() {
		return new String[] {
			"inline_assembly", "prefix", "assembly_instruction", "int_value", "jump", 
			"directive", "zero_op", "imul_div", "unary_op8", "unary_op16", "unary_op32", 
			"unary_op64", "unary_op", "binary_op8", "binary_op16", "binary_op32", 
			"binary_op64", "binary_op", "operand8", "operand16", "operand32", "operand64", 
			"operand", "memory_reference", "register8", "register16", "register32", 
			"register64", "registerXmm", "segment_register", "number", "immediate", 
			"argument"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'\"'", "';'", "'\n'", "'rep'", "'repz'", "'repe'", "'repne'", 
			"'repnz'", "'lock'", "'int'", "'call'", "'ja'", "'jae'", "'jb'", "'jbe'", 
			"'jc'", "'jcxz'", "'je'", "'jecxz'", "'jg'", "'jge'", "'jl'", "'jle'", 
			"'jmp'", "'jnae'", "'jnb'", "'jnbe'", "'jnc'", "'jne'", "'jng'", "'jnge'", 
			"'jnl'", "'jnle'", "'jno'", "'jnp'", "'jns'", "'jnz'", "'jo'", "'jp'", 
			"'jpe'", "'jpo'", "'js'", "'jz'", "'lcall'", "'loop'", "'loope'", "'loopne'", 
			"'loopnz'", "'loopz'", "'.p2align'", "','", "'clc'", "'cld'", "'cli'", 
			"'cmc'", "'lahf'", "'popf'", "'popfw'", "'pushf'", "'pushfw'", "'sahf'", 
			"'stc'", "'std'", "'sti'", "'nop'", "'rdtsc'", "'cpuid'", "'xgetbv'", 
			"'ud2'", "'mfence'", "'lfence'", "'sfence'", "'hlt'", "'syscall'", "'stosb'", 
			"'stosw'", "'stosd'", "'stosq'", "'pause'", "'idivb'", "'imulb'", "'idivw'", 
			"'imulw'", "'idivl'", "'imull'", "'idivq'", "'imulq'", "'idiv'", "'imul'", 
			"'incb'", "'decb'", "'negb'", "'notb'", "'divb'", "'mulb'", "'incw'", 
			"'decw'", "'negw'", "'notw'", "'divw'", "'mulw'", "'pushw'", "'popw'", 
			"'incl'", "'decl'", "'negl'", "'notl'", "'divl'", "'mull'", "'bswapl'", 
			"'pushl'", "'popl'", "'incq'", "'decq'", "'negq'", "'notq'", "'divq'", 
			"'mulq'", "'bswapq'", "'pushq'", "'popq'", "'inc'", "'dec'", "'neg'", 
			"'not'", "'bswap'", "'rdrand'", "'rdseed'", "'seta'", "'setae'", "'setb'", 
			"'setbe'", "'setc'", "'sete'", "'setg'", "'setge'", "'setl'", "'setle'", 
			"'setna'", "'setnae'", "'setnb'", "'setnbe'", "'setnc'", "'setne'", "'setng'", 
			"'setnge'", "'setnl'", "'setnle'", "'setno'", "'setnp'", "'setns'", "'setnz'", 
			"'seto'", "'setp'", "'setpe'", "'setpo'", "'sets'", "'setz'", "'push'", 
			"'pop'", "'cmpxchg8b'", "'cmpxchg16b'", "'movb'", "'xaddb'", "'xchgb'", 
			"'adcb'", "'addb'", "'cmpb'", "'sbbb'", "'subb'", "'andb'", "'orb'", 
			"'xorb'", "'rclb'", "'rcrb'", "'rolb'", "'rorb'", "'salb'", "'sarb'", 
			"'shlb'", "'shrb'", "'testb'", "'cmpxchgb'", "'cmovaw'", "'cmovaew'", 
			"'cmovbw'", "'cmovbew'", "'cmovcw'", "'cmovew'", "'cmovgw'", "'cmovgew'", 
			"'cmovlw'", "'cmovlew'", "'cmovnaw'", "'cmovnaew'", "'cmovnbw'", "'cmovnbew'", 
			"'cmovncw'", "'cmovnew'", "'cmovngw'", "'cmovngew'", "'cmovnlw'", "'cmovnlew'", 
			"'cmovnow'", "'cmovnpw'", "'cmovnsw'", "'cmovnzw'", "'cmovow'", "'cmovpw'", 
			"'cmovpew'", "'cmovpow'", "'cmovsw'", "'cmovzw'", "'cmpxchgw'", "'movw'", 
			"'xaddw'", "'xchgw'", "'adcw'", "'addw'", "'cmpw'", "'sbbw'", "'subw'", 
			"'andw'", "'orw'", "'xorw'", "'testw'", "'bsfw'", "'bsrw'", "'btw'", 
			"'btcw'", "'btrw'", "'btsw'", "'rclw'", "'rcrw'", "'rolw'", "'rorw'", 
			"'salw'", "'sarw'", "'shlw'", "'shrw'", "'movsbw'", "'movzbw'", "'cmoval'", 
			"'cmovael'", "'cmovbl'", "'cmovbel'", "'cmovcl'", "'cmovel'", "'cmovgl'", 
			"'cmovgel'", "'cmovll'", "'cmovlel'", "'cmovnal'", "'cmovnael'", "'cmovnbl'", 
			"'cmovnbel'", "'cmovncl'", "'cmovnel'", "'cmovngl'", "'cmovngel'", "'cmovnll'", 
			"'cmovnlel'", "'cmovnol'", "'cmovnpl'", "'cmovnsl'", "'cmovnzl'", "'cmovol'", 
			"'cmovpl'", "'cmovpel'", "'cmovpol'", "'cmovsl'", "'cmovzl'", "'cmpxchgl'", 
			"'movl'", "'xaddl'", "'xchgl'", "'adcl'", "'addl'", "'cmpl'", "'sbbl'", 
			"'subl'", "'andl'", "'orl'", "'xorl'", "'testl'", "'bsfl'", "'bsrl'", 
			"'btl'", "'btcl'", "'btrl'", "'btsl'", "'rcll'", "'rcrl'", "'roll'", 
			"'rorl'", "'sall'", "'sarl'", "'shll'", "'shrl'", "'movsbl'", "'movswl'", 
			"'movzbl'", "'movzwl'", "'cmovaq'", "'cmovaeq'", "'cmovbq'", "'cmovbeq'", 
			"'cmovcq'", "'cmoveq'", "'cmovgq'", "'cmovgeq'", "'cmovlq'", "'cmovleq'", 
			"'cmovnaq'", "'cmovnaeq'", "'cmovnbq'", "'cmovnbeq'", "'cmovncq'", "'cmovneq'", 
			"'cmovngq'", "'cmovngeq'", "'cmovnlq'", "'cmovnleq'", "'cmovnoq'", "'cmovnpq'", 
			"'cmovnsq'", "'cmovnzq'", "'cmovoq'", "'cmovpq'", "'cmovpeq'", "'cmovpoq'", 
			"'cmovsq'", "'cmovzq'", "'cmpxchgq'", "'movq'", "'xaddq'", "'xchgq'", 
			"'adcq'", "'addq'", "'cmpq'", "'sbbq'", "'subq'", "'andq'", "'orq'", 
			"'xorq'", "'testq'", "'bsfq'", "'bsrq'", "'btq'", "'btcq'", "'btrq'", 
			"'btsq'", "'rclq'", "'rcrq'", "'rolq'", "'rorq'", "'salq'", "'sarq'", 
			"'shlq'", "'shrq'", "'movsbq'", "'movzbq'", "'movswq'", "'movzwq'", "'movslq'", 
			"'cmova'", "'cmovae'", "'cmovb'", "'cmovbe'", "'cmovc'", "'cmove'", "'cmovg'", 
			"'cmovge'", "'cmovl'", "'cmovle'", "'cmovna'", "'cmovnae'", "'cmovnb'", 
			"'cmovnbe'", "'cmovnc'", "'cmovne'", "'cmovng'", "'cmovnge'", "'cmovnl'", 
			"'cmovnle'", "'cmovno'", "'cmovnp'", "'cmovns'", "'cmovnz'", "'cmovo'", 
			"'cmovp'", "'cmovpe'", "'cmovpo'", "'cmovs'", "'cmovz'", "'cmpxchg'", 
			"'pmovmskb'", "'mov'", "'xadd'", "'xchg'", "'adc'", "'add'", "'cmp'", 
			"'div'", "'mul'", "'sbb'", "'sub'", "'and'", "'or'", "'xor'", "'rcl'", 
			"'rcr'", "'rol'", "'ror'", "'sal'", "'sar'", "'shl'", "'shr'", "'lea'", 
			"'bsf'", "'bsr'", "':'", "'('", "')'", "'%ah'", "'%al'", "'%bh'", "'%bl'", 
			"'%ch'", "'%cl'", "'%dh'", "'%dl'", "'%r0l'", "'%r1l'", "'%r2l'", "'%r3l'", 
			"'%r4l'", "'%r5l'", "'%r6l'", "'%r7l'", "'%r8l'", "'%r9l'", "'%r10l'", 
			"'%r11l'", "'%r12l'", "'%r13l'", "'%r14l'", "'%r15l'", "'%ax'", "'%bx'", 
			"'%cx'", "'%dx'", "'%si'", "'%di'", "'%bp'", "'%sp'", "'%r0w'", "'%r1w'", 
			"'%r2w'", "'%r3w'", "'%r4w'", "'%r5w'", "'%r6w'", "'%r7w'", "'%r8w'", 
			"'%r9w'", "'%r10w'", "'%r11w'", "'%r12w'", "'%r13w'", "'%r14w'", "'%r15w'", 
			"'%eax'", "'%ebx'", "'%ecx'", "'%edx'", "'%esi'", "'%edi'", "'%ebp'", 
			"'%esp'", "'%r0d'", "'%r1d'", "'%r2d'", "'%r3d'", "'%r4d'", "'%r5d'", 
			"'%r6d'", "'%r7d'", "'%r8d'", "'%r9d'", "'%r10d'", "'%r11d'", "'%r12d'", 
			"'%r13d'", "'%r14d'", "'%r15d'", "'%rax'", "'%rbx'", "'%rcx'", "'%rdx'", 
			"'%rsp'", "'%rbp'", "'%rsi'", "'%rdi'", "'%r0'", "'%r1'", "'%r2'", "'%r3'", 
			"'%r4'", "'%r5'", "'%r6'", "'%r7'", "'%r8'", "'%r9'", "'%r10'", "'%r11'", 
			"'%r12'", "'%r13'", "'%r14'", "'%r15'", "'%xmm0'", "'%xmm1'", "'%xmm2'", 
			"'%xmm3'", "'%xmm4'", "'%xmm5'", "'%xmm6'", "'%xmm7'", "'%xmm8'", "'%xmm9'", 
			"'%xmm10'", "'%xmm11'", "'%xmm12'", "'%xmm13'", "'%xmm14'", "'%xmm15'", 
			"'%cs'", "'%ds'", "'%es'", "'%fs'", "'%gs'", "'%ss'", "'$$'", "'$'", 
			"'{'", "'b'", "'h'", "'w'", "'k'", "'q'", "'}'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
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
			null, null, null, null, null, null, null, null, null, null, null, null, 
			"IDENT", "BIN_NUMBER", "HEX_NUMBER", "NUMBER", "WS", "COMMENT", "LINE_COMMENT"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
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
			setState(66);
			match(T__0);
			setState(88);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__14) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25) | (1L << T__26) | (1L << T__27) | (1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37) | (1L << T__38) | (1L << T__39) | (1L << T__40) | (1L << T__41) | (1L << T__42) | (1L << T__43) | (1L << T__44) | (1L << T__45) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__74 - 64)) | (1L << (T__75 - 64)) | (1L << (T__76 - 64)) | (1L << (T__77 - 64)) | (1L << (T__78 - 64)) | (1L << (T__79 - 64)) | (1L << (T__80 - 64)) | (1L << (T__81 - 64)) | (1L << (T__82 - 64)) | (1L << (T__83 - 64)) | (1L << (T__84 - 64)) | (1L << (T__85 - 64)) | (1L << (T__86 - 64)) | (1L << (T__87 - 64)) | (1L << (T__88 - 64)) | (1L << (T__89 - 64)) | (1L << (T__90 - 64)) | (1L << (T__91 - 64)) | (1L << (T__92 - 64)) | (1L << (T__93 - 64)) | (1L << (T__94 - 64)) | (1L << (T__95 - 64)) | (1L << (T__96 - 64)) | (1L << (T__97 - 64)) | (1L << (T__98 - 64)) | (1L << (T__99 - 64)) | (1L << (T__100 - 64)) | (1L << (T__101 - 64)) | (1L << (T__102 - 64)) | (1L << (T__103 - 64)) | (1L << (T__104 - 64)) | (1L << (T__105 - 64)) | (1L << (T__106 - 64)) | (1L << (T__107 - 64)) | (1L << (T__108 - 64)) | (1L << (T__109 - 64)) | (1L << (T__110 - 64)) | (1L << (T__111 - 64)) | (1L << (T__112 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__115 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__122 - 64)) | (1L << (T__123 - 64)) | (1L << (T__124 - 64)) | (1L << (T__125 - 64)) | (1L << (T__126 - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (T__127 - 128)) | (1L << (T__128 - 128)) | (1L << (T__129 - 128)) | (1L << (T__130 - 128)) | (1L << (T__131 - 128)) | (1L << (T__132 - 128)) | (1L << (T__133 - 128)) | (1L << (T__134 - 128)) | (1L << (T__135 - 128)) | (1L << (T__136 - 128)) | (1L << (T__137 - 128)) | (1L << (T__138 - 128)) | (1L << (T__139 - 128)) | (1L << (T__140 - 128)) | (1L << (T__141 - 128)) | (1L << (T__142 - 128)) | (1L << (T__143 - 128)) | (1L << (T__144 - 128)) | (1L << (T__145 - 128)) | (1L << (T__146 - 128)) | (1L << (T__147 - 128)) | (1L << (T__148 - 128)) | (1L << (T__149 - 128)) | (1L << (T__150 - 128)) | (1L << (T__151 - 128)) | (1L << (T__152 - 128)) | (1L << (T__153 - 128)) | (1L << (T__154 - 128)) | (1L << (T__155 - 128)) | (1L << (T__156 - 128)) | (1L << (T__157 - 128)) | (1L << (T__158 - 128)) | (1L << (T__159 - 128)) | (1L << (T__160 - 128)) | (1L << (T__161 - 128)) | (1L << (T__162 - 128)) | (1L << (T__163 - 128)) | (1L << (T__164 - 128)) | (1L << (T__165 - 128)) | (1L << (T__166 - 128)) | (1L << (T__167 - 128)) | (1L << (T__168 - 128)) | (1L << (T__169 - 128)) | (1L << (T__170 - 128)) | (1L << (T__171 - 128)) | (1L << (T__172 - 128)) | (1L << (T__173 - 128)) | (1L << (T__174 - 128)) | (1L << (T__175 - 128)) | (1L << (T__176 - 128)) | (1L << (T__177 - 128)) | (1L << (T__178 - 128)) | (1L << (T__179 - 128)) | (1L << (T__180 - 128)) | (1L << (T__181 - 128)) | (1L << (T__182 - 128)) | (1L << (T__183 - 128)) | (1L << (T__184 - 128)) | (1L << (T__185 - 128)) | (1L << (T__186 - 128)) | (1L << (T__187 - 128)) | (1L << (T__188 - 128)) | (1L << (T__189 - 128)) | (1L << (T__190 - 128)))) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & ((1L << (T__191 - 192)) | (1L << (T__192 - 192)) | (1L << (T__193 - 192)) | (1L << (T__194 - 192)) | (1L << (T__195 - 192)) | (1L << (T__196 - 192)) | (1L << (T__197 - 192)) | (1L << (T__198 - 192)) | (1L << (T__199 - 192)) | (1L << (T__200 - 192)) | (1L << (T__201 - 192)) | (1L << (T__202 - 192)) | (1L << (T__203 - 192)) | (1L << (T__204 - 192)) | (1L << (T__205 - 192)) | (1L << (T__206 - 192)) | (1L << (T__207 - 192)) | (1L << (T__208 - 192)) | (1L << (T__209 - 192)) | (1L << (T__210 - 192)) | (1L << (T__211 - 192)) | (1L << (T__212 - 192)) | (1L << (T__213 - 192)) | (1L << (T__214 - 192)) | (1L << (T__215 - 192)) | (1L << (T__216 - 192)) | (1L << (T__217 - 192)) | (1L << (T__218 - 192)) | (1L << (T__219 - 192)) | (1L << (T__220 - 192)) | (1L << (T__221 - 192)) | (1L << (T__222 - 192)) | (1L << (T__223 - 192)) | (1L << (T__224 - 192)) | (1L << (T__225 - 192)) | (1L << (T__226 - 192)) | (1L << (T__227 - 192)) | (1L << (T__228 - 192)) | (1L << (T__229 - 192)) | (1L << (T__230 - 192)) | (1L << (T__231 - 192)) | (1L << (T__232 - 192)) | (1L << (T__233 - 192)) | (1L << (T__234 - 192)) | (1L << (T__235 - 192)) | (1L << (T__236 - 192)) | (1L << (T__237 - 192)) | (1L << (T__238 - 192)) | (1L << (T__239 - 192)) | (1L << (T__240 - 192)) | (1L << (T__241 - 192)) | (1L << (T__242 - 192)) | (1L << (T__243 - 192)) | (1L << (T__244 - 192)) | (1L << (T__245 - 192)) | (1L << (T__246 - 192)) | (1L << (T__247 - 192)) | (1L << (T__248 - 192)) | (1L << (T__249 - 192)) | (1L << (T__250 - 192)) | (1L << (T__251 - 192)) | (1L << (T__252 - 192)) | (1L << (T__253 - 192)) | (1L << (T__254 - 192)))) != 0) || ((((_la - 256)) & ~0x3f) == 0 && ((1L << (_la - 256)) & ((1L << (T__255 - 256)) | (1L << (T__256 - 256)) | (1L << (T__257 - 256)) | (1L << (T__258 - 256)) | (1L << (T__259 - 256)) | (1L << (T__260 - 256)) | (1L << (T__261 - 256)) | (1L << (T__262 - 256)) | (1L << (T__263 - 256)) | (1L << (T__264 - 256)) | (1L << (T__265 - 256)) | (1L << (T__266 - 256)) | (1L << (T__267 - 256)) | (1L << (T__268 - 256)) | (1L << (T__269 - 256)) | (1L << (T__270 - 256)) | (1L << (T__271 - 256)) | (1L << (T__272 - 256)) | (1L << (T__273 - 256)) | (1L << (T__274 - 256)) | (1L << (T__275 - 256)) | (1L << (T__276 - 256)) | (1L << (T__277 - 256)) | (1L << (T__278 - 256)) | (1L << (T__279 - 256)) | (1L << (T__280 - 256)) | (1L << (T__281 - 256)) | (1L << (T__282 - 256)) | (1L << (T__283 - 256)) | (1L << (T__284 - 256)) | (1L << (T__285 - 256)) | (1L << (T__286 - 256)) | (1L << (T__287 - 256)) | (1L << (T__288 - 256)) | (1L << (T__289 - 256)) | (1L << (T__290 - 256)) | (1L << (T__291 - 256)) | (1L << (T__292 - 256)) | (1L << (T__293 - 256)) | (1L << (T__294 - 256)) | (1L << (T__295 - 256)) | (1L << (T__296 - 256)) | (1L << (T__297 - 256)) | (1L << (T__298 - 256)) | (1L << (T__299 - 256)) | (1L << (T__300 - 256)) | (1L << (T__301 - 256)) | (1L << (T__302 - 256)) | (1L << (T__303 - 256)) | (1L << (T__304 - 256)) | (1L << (T__305 - 256)) | (1L << (T__306 - 256)) | (1L << (T__307 - 256)) | (1L << (T__308 - 256)) | (1L << (T__309 - 256)) | (1L << (T__310 - 256)) | (1L << (T__311 - 256)) | (1L << (T__312 - 256)) | (1L << (T__313 - 256)) | (1L << (T__314 - 256)) | (1L << (T__315 - 256)) | (1L << (T__316 - 256)) | (1L << (T__317 - 256)) | (1L << (T__318 - 256)))) != 0) || ((((_la - 320)) & ~0x3f) == 0 && ((1L << (_la - 320)) & ((1L << (T__319 - 320)) | (1L << (T__320 - 320)) | (1L << (T__321 - 320)) | (1L << (T__322 - 320)) | (1L << (T__323 - 320)) | (1L << (T__324 - 320)) | (1L << (T__325 - 320)) | (1L << (T__326 - 320)) | (1L << (T__327 - 320)) | (1L << (T__328 - 320)) | (1L << (T__329 - 320)) | (1L << (T__330 - 320)) | (1L << (T__331 - 320)) | (1L << (T__332 - 320)) | (1L << (T__333 - 320)) | (1L << (T__334 - 320)) | (1L << (T__335 - 320)) | (1L << (T__336 - 320)) | (1L << (T__337 - 320)) | (1L << (T__338 - 320)) | (1L << (T__339 - 320)) | (1L << (T__340 - 320)) | (1L << (T__341 - 320)) | (1L << (T__342 - 320)) | (1L << (T__343 - 320)) | (1L << (T__344 - 320)) | (1L << (T__345 - 320)) | (1L << (T__346 - 320)) | (1L << (T__347 - 320)) | (1L << (T__348 - 320)) | (1L << (T__349 - 320)) | (1L << (T__350 - 320)) | (1L << (T__351 - 320)) | (1L << (T__352 - 320)) | (1L << (T__353 - 320)) | (1L << (T__354 - 320)) | (1L << (T__355 - 320)) | (1L << (T__356 - 320)) | (1L << (T__357 - 320)) | (1L << (T__358 - 320)) | (1L << (T__359 - 320)) | (1L << (T__360 - 320)) | (1L << (T__361 - 320)) | (1L << (T__362 - 320)) | (1L << (T__363 - 320)) | (1L << (T__364 - 320)) | (1L << (T__365 - 320)) | (1L << (T__366 - 320)) | (1L << (T__367 - 320)) | (1L << (T__368 - 320)) | (1L << (T__369 - 320)) | (1L << (T__370 - 320)) | (1L << (T__371 - 320)) | (1L << (T__372 - 320)) | (1L << (T__373 - 320)) | (1L << (T__374 - 320)) | (1L << (T__375 - 320)) | (1L << (T__376 - 320)) | (1L << (T__377 - 320)) | (1L << (T__378 - 320)) | (1L << (T__379 - 320)) | (1L << (T__380 - 320)) | (1L << (T__381 - 320)) | (1L << (T__382 - 320)))) != 0) || ((((_la - 384)) & ~0x3f) == 0 && ((1L << (_la - 384)) & ((1L << (T__383 - 384)) | (1L << (T__384 - 384)) | (1L << (T__385 - 384)) | (1L << (T__386 - 384)) | (1L << (T__387 - 384)) | (1L << (T__388 - 384)) | (1L << (T__389 - 384)) | (1L << (T__390 - 384)) | (1L << (T__391 - 384)) | (1L << (T__392 - 384)) | (1L << (T__393 - 384)) | (1L << (T__394 - 384)) | (1L << (T__395 - 384)) | (1L << (T__396 - 384)) | (1L << (T__397 - 384)) | (1L << (T__398 - 384)) | (1L << (T__399 - 384)) | (1L << (T__400 - 384)) | (1L << (T__401 - 384)) | (1L << (T__402 - 384)) | (1L << (T__403 - 384)) | (1L << (T__404 - 384)) | (1L << (T__405 - 384)) | (1L << (T__406 - 384)) | (1L << (T__407 - 384)) | (1L << (T__408 - 384)) | (1L << (T__409 - 384)) | (1L << (T__410 - 384)) | (1L << (T__411 - 384)) | (1L << (T__412 - 384)) | (1L << (T__413 - 384)) | (1L << (T__414 - 384)) | (1L << (T__415 - 384)) | (1L << (T__416 - 384)) | (1L << (T__417 - 384)) | (1L << (T__418 - 384)) | (1L << (T__419 - 384)) | (1L << (T__420 - 384)))) != 0)) {
				{
				setState(72);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__3:
				case T__4:
				case T__5:
				case T__6:
				case T__7:
				case T__8:
					{
					setState(67);
					prefix();
					setState(69);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__1) {
						{
						setState(68);
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
				case T__79:
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
				case T__420:
					{
					 factory.setPrefix(null); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(74);
				assembly_instruction();
				setState(85);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__1 || _la==T__2) {
					{
					{
					setState(75);
					_la = _input.LA(1);
					if ( !(_la==T__1 || _la==T__2) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(78);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__3:
					case T__4:
					case T__5:
					case T__6:
					case T__7:
					case T__8:
						{
						setState(76);
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
					case T__79:
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
					case T__420:
						{
						 factory.setPrefix(null); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(81);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__14) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25) | (1L << T__26) | (1L << T__27) | (1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37) | (1L << T__38) | (1L << T__39) | (1L << T__40) | (1L << T__41) | (1L << T__42) | (1L << T__43) | (1L << T__44) | (1L << T__45) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__74 - 64)) | (1L << (T__75 - 64)) | (1L << (T__76 - 64)) | (1L << (T__77 - 64)) | (1L << (T__78 - 64)) | (1L << (T__79 - 64)) | (1L << (T__80 - 64)) | (1L << (T__81 - 64)) | (1L << (T__82 - 64)) | (1L << (T__83 - 64)) | (1L << (T__84 - 64)) | (1L << (T__85 - 64)) | (1L << (T__86 - 64)) | (1L << (T__87 - 64)) | (1L << (T__88 - 64)) | (1L << (T__89 - 64)) | (1L << (T__90 - 64)) | (1L << (T__91 - 64)) | (1L << (T__92 - 64)) | (1L << (T__93 - 64)) | (1L << (T__94 - 64)) | (1L << (T__95 - 64)) | (1L << (T__96 - 64)) | (1L << (T__97 - 64)) | (1L << (T__98 - 64)) | (1L << (T__99 - 64)) | (1L << (T__100 - 64)) | (1L << (T__101 - 64)) | (1L << (T__102 - 64)) | (1L << (T__103 - 64)) | (1L << (T__104 - 64)) | (1L << (T__105 - 64)) | (1L << (T__106 - 64)) | (1L << (T__107 - 64)) | (1L << (T__108 - 64)) | (1L << (T__109 - 64)) | (1L << (T__110 - 64)) | (1L << (T__111 - 64)) | (1L << (T__112 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__115 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__122 - 64)) | (1L << (T__123 - 64)) | (1L << (T__124 - 64)) | (1L << (T__125 - 64)) | (1L << (T__126 - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (T__127 - 128)) | (1L << (T__128 - 128)) | (1L << (T__129 - 128)) | (1L << (T__130 - 128)) | (1L << (T__131 - 128)) | (1L << (T__132 - 128)) | (1L << (T__133 - 128)) | (1L << (T__134 - 128)) | (1L << (T__135 - 128)) | (1L << (T__136 - 128)) | (1L << (T__137 - 128)) | (1L << (T__138 - 128)) | (1L << (T__139 - 128)) | (1L << (T__140 - 128)) | (1L << (T__141 - 128)) | (1L << (T__142 - 128)) | (1L << (T__143 - 128)) | (1L << (T__144 - 128)) | (1L << (T__145 - 128)) | (1L << (T__146 - 128)) | (1L << (T__147 - 128)) | (1L << (T__148 - 128)) | (1L << (T__149 - 128)) | (1L << (T__150 - 128)) | (1L << (T__151 - 128)) | (1L << (T__152 - 128)) | (1L << (T__153 - 128)) | (1L << (T__154 - 128)) | (1L << (T__155 - 128)) | (1L << (T__156 - 128)) | (1L << (T__157 - 128)) | (1L << (T__158 - 128)) | (1L << (T__159 - 128)) | (1L << (T__160 - 128)) | (1L << (T__161 - 128)) | (1L << (T__162 - 128)) | (1L << (T__163 - 128)) | (1L << (T__164 - 128)) | (1L << (T__165 - 128)) | (1L << (T__166 - 128)) | (1L << (T__167 - 128)) | (1L << (T__168 - 128)) | (1L << (T__169 - 128)) | (1L << (T__170 - 128)) | (1L << (T__171 - 128)) | (1L << (T__172 - 128)) | (1L << (T__173 - 128)) | (1L << (T__174 - 128)) | (1L << (T__175 - 128)) | (1L << (T__176 - 128)) | (1L << (T__177 - 128)) | (1L << (T__178 - 128)) | (1L << (T__179 - 128)) | (1L << (T__180 - 128)) | (1L << (T__181 - 128)) | (1L << (T__182 - 128)) | (1L << (T__183 - 128)) | (1L << (T__184 - 128)) | (1L << (T__185 - 128)) | (1L << (T__186 - 128)) | (1L << (T__187 - 128)) | (1L << (T__188 - 128)) | (1L << (T__189 - 128)) | (1L << (T__190 - 128)))) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & ((1L << (T__191 - 192)) | (1L << (T__192 - 192)) | (1L << (T__193 - 192)) | (1L << (T__194 - 192)) | (1L << (T__195 - 192)) | (1L << (T__196 - 192)) | (1L << (T__197 - 192)) | (1L << (T__198 - 192)) | (1L << (T__199 - 192)) | (1L << (T__200 - 192)) | (1L << (T__201 - 192)) | (1L << (T__202 - 192)) | (1L << (T__203 - 192)) | (1L << (T__204 - 192)) | (1L << (T__205 - 192)) | (1L << (T__206 - 192)) | (1L << (T__207 - 192)) | (1L << (T__208 - 192)) | (1L << (T__209 - 192)) | (1L << (T__210 - 192)) | (1L << (T__211 - 192)) | (1L << (T__212 - 192)) | (1L << (T__213 - 192)) | (1L << (T__214 - 192)) | (1L << (T__215 - 192)) | (1L << (T__216 - 192)) | (1L << (T__217 - 192)) | (1L << (T__218 - 192)) | (1L << (T__219 - 192)) | (1L << (T__220 - 192)) | (1L << (T__221 - 192)) | (1L << (T__222 - 192)) | (1L << (T__223 - 192)) | (1L << (T__224 - 192)) | (1L << (T__225 - 192)) | (1L << (T__226 - 192)) | (1L << (T__227 - 192)) | (1L << (T__228 - 192)) | (1L << (T__229 - 192)) | (1L << (T__230 - 192)) | (1L << (T__231 - 192)) | (1L << (T__232 - 192)) | (1L << (T__233 - 192)) | (1L << (T__234 - 192)) | (1L << (T__235 - 192)) | (1L << (T__236 - 192)) | (1L << (T__237 - 192)) | (1L << (T__238 - 192)) | (1L << (T__239 - 192)) | (1L << (T__240 - 192)) | (1L << (T__241 - 192)) | (1L << (T__242 - 192)) | (1L << (T__243 - 192)) | (1L << (T__244 - 192)) | (1L << (T__245 - 192)) | (1L << (T__246 - 192)) | (1L << (T__247 - 192)) | (1L << (T__248 - 192)) | (1L << (T__249 - 192)) | (1L << (T__250 - 192)) | (1L << (T__251 - 192)) | (1L << (T__252 - 192)) | (1L << (T__253 - 192)) | (1L << (T__254 - 192)))) != 0) || ((((_la - 256)) & ~0x3f) == 0 && ((1L << (_la - 256)) & ((1L << (T__255 - 256)) | (1L << (T__256 - 256)) | (1L << (T__257 - 256)) | (1L << (T__258 - 256)) | (1L << (T__259 - 256)) | (1L << (T__260 - 256)) | (1L << (T__261 - 256)) | (1L << (T__262 - 256)) | (1L << (T__263 - 256)) | (1L << (T__264 - 256)) | (1L << (T__265 - 256)) | (1L << (T__266 - 256)) | (1L << (T__267 - 256)) | (1L << (T__268 - 256)) | (1L << (T__269 - 256)) | (1L << (T__270 - 256)) | (1L << (T__271 - 256)) | (1L << (T__272 - 256)) | (1L << (T__273 - 256)) | (1L << (T__274 - 256)) | (1L << (T__275 - 256)) | (1L << (T__276 - 256)) | (1L << (T__277 - 256)) | (1L << (T__278 - 256)) | (1L << (T__279 - 256)) | (1L << (T__280 - 256)) | (1L << (T__281 - 256)) | (1L << (T__282 - 256)) | (1L << (T__283 - 256)) | (1L << (T__284 - 256)) | (1L << (T__285 - 256)) | (1L << (T__286 - 256)) | (1L << (T__287 - 256)) | (1L << (T__288 - 256)) | (1L << (T__289 - 256)) | (1L << (T__290 - 256)) | (1L << (T__291 - 256)) | (1L << (T__292 - 256)) | (1L << (T__293 - 256)) | (1L << (T__294 - 256)) | (1L << (T__295 - 256)) | (1L << (T__296 - 256)) | (1L << (T__297 - 256)) | (1L << (T__298 - 256)) | (1L << (T__299 - 256)) | (1L << (T__300 - 256)) | (1L << (T__301 - 256)) | (1L << (T__302 - 256)) | (1L << (T__303 - 256)) | (1L << (T__304 - 256)) | (1L << (T__305 - 256)) | (1L << (T__306 - 256)) | (1L << (T__307 - 256)) | (1L << (T__308 - 256)) | (1L << (T__309 - 256)) | (1L << (T__310 - 256)) | (1L << (T__311 - 256)) | (1L << (T__312 - 256)) | (1L << (T__313 - 256)) | (1L << (T__314 - 256)) | (1L << (T__315 - 256)) | (1L << (T__316 - 256)) | (1L << (T__317 - 256)) | (1L << (T__318 - 256)))) != 0) || ((((_la - 320)) & ~0x3f) == 0 && ((1L << (_la - 320)) & ((1L << (T__319 - 320)) | (1L << (T__320 - 320)) | (1L << (T__321 - 320)) | (1L << (T__322 - 320)) | (1L << (T__323 - 320)) | (1L << (T__324 - 320)) | (1L << (T__325 - 320)) | (1L << (T__326 - 320)) | (1L << (T__327 - 320)) | (1L << (T__328 - 320)) | (1L << (T__329 - 320)) | (1L << (T__330 - 320)) | (1L << (T__331 - 320)) | (1L << (T__332 - 320)) | (1L << (T__333 - 320)) | (1L << (T__334 - 320)) | (1L << (T__335 - 320)) | (1L << (T__336 - 320)) | (1L << (T__337 - 320)) | (1L << (T__338 - 320)) | (1L << (T__339 - 320)) | (1L << (T__340 - 320)) | (1L << (T__341 - 320)) | (1L << (T__342 - 320)) | (1L << (T__343 - 320)) | (1L << (T__344 - 320)) | (1L << (T__345 - 320)) | (1L << (T__346 - 320)) | (1L << (T__347 - 320)) | (1L << (T__348 - 320)) | (1L << (T__349 - 320)) | (1L << (T__350 - 320)) | (1L << (T__351 - 320)) | (1L << (T__352 - 320)) | (1L << (T__353 - 320)) | (1L << (T__354 - 320)) | (1L << (T__355 - 320)) | (1L << (T__356 - 320)) | (1L << (T__357 - 320)) | (1L << (T__358 - 320)) | (1L << (T__359 - 320)) | (1L << (T__360 - 320)) | (1L << (T__361 - 320)) | (1L << (T__362 - 320)) | (1L << (T__363 - 320)) | (1L << (T__364 - 320)) | (1L << (T__365 - 320)) | (1L << (T__366 - 320)) | (1L << (T__367 - 320)) | (1L << (T__368 - 320)) | (1L << (T__369 - 320)) | (1L << (T__370 - 320)) | (1L << (T__371 - 320)) | (1L << (T__372 - 320)) | (1L << (T__373 - 320)) | (1L << (T__374 - 320)) | (1L << (T__375 - 320)) | (1L << (T__376 - 320)) | (1L << (T__377 - 320)) | (1L << (T__378 - 320)) | (1L << (T__379 - 320)) | (1L << (T__380 - 320)) | (1L << (T__381 - 320)) | (1L << (T__382 - 320)))) != 0) || ((((_la - 384)) & ~0x3f) == 0 && ((1L << (_la - 384)) & ((1L << (T__383 - 384)) | (1L << (T__384 - 384)) | (1L << (T__385 - 384)) | (1L << (T__386 - 384)) | (1L << (T__387 - 384)) | (1L << (T__388 - 384)) | (1L << (T__389 - 384)) | (1L << (T__390 - 384)) | (1L << (T__391 - 384)) | (1L << (T__392 - 384)) | (1L << (T__393 - 384)) | (1L << (T__394 - 384)) | (1L << (T__395 - 384)) | (1L << (T__396 - 384)) | (1L << (T__397 - 384)) | (1L << (T__398 - 384)) | (1L << (T__399 - 384)) | (1L << (T__400 - 384)) | (1L << (T__401 - 384)) | (1L << (T__402 - 384)) | (1L << (T__403 - 384)) | (1L << (T__404 - 384)) | (1L << (T__405 - 384)) | (1L << (T__406 - 384)) | (1L << (T__407 - 384)) | (1L << (T__408 - 384)) | (1L << (T__409 - 384)) | (1L << (T__410 - 384)) | (1L << (T__411 - 384)) | (1L << (T__412 - 384)) | (1L << (T__413 - 384)) | (1L << (T__414 - 384)) | (1L << (T__415 - 384)) | (1L << (T__416 - 384)) | (1L << (T__417 - 384)) | (1L << (T__418 - 384)) | (1L << (T__419 - 384)) | (1L << (T__420 - 384)))) != 0)) {
						{
						setState(80);
						assembly_instruction();
						}
					}

					}
					}
					setState(87);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(90);
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
			setState(93);
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
		public DirectiveContext directive() {
			return getRuleContext(DirectiveContext.class,0);
		}
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
			setState(111);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__49:
				{
				setState(96);
				directive();
				}
				break;
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
				{
				setState(97);
				zero_op();
				}
				break;
			case T__89:
			case T__90:
			case T__91:
			case T__92:
			case T__93:
			case T__94:
				{
				setState(98);
				unary_op8();
				}
				break;
			case T__95:
			case T__96:
			case T__97:
			case T__98:
			case T__99:
			case T__100:
			case T__101:
			case T__102:
				{
				setState(99);
				unary_op16();
				}
				break;
			case T__103:
			case T__104:
			case T__105:
			case T__106:
			case T__107:
			case T__108:
			case T__109:
			case T__110:
			case T__111:
				{
				setState(100);
				unary_op32();
				}
				break;
			case T__112:
			case T__113:
			case T__114:
			case T__115:
			case T__116:
			case T__117:
			case T__118:
			case T__119:
			case T__120:
				{
				setState(101);
				unary_op64();
				}
				break;
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
				{
				setState(102);
				unary_op();
				}
				break;
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
				{
				setState(103);
				binary_op8();
				}
				break;
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
				{
				setState(104);
				binary_op16();
				}
				break;
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
				{
				setState(105);
				binary_op32();
				}
				break;
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
				{
				setState(106);
				binary_op64();
				}
				break;
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
			case T__420:
				{
				setState(107);
				binary_op();
				}
				break;
			case T__79:
			case T__80:
			case T__81:
			case T__82:
			case T__83:
			case T__84:
			case T__85:
			case T__86:
			case T__87:
			case T__88:
				{
				setState(108);
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
				setState(109);
				jump();
				}
				break;
			case T__9:
				{
				setState(110);
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
			setState(113);
			match(T__9);
			setState(114);
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
			setState(117);
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
			setState(118);
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

	public static class DirectiveContext extends ParserRuleContext {
		public Token op;
		public NumberContext low_order_bits;
		public NumberContext padding_byte;
		public NumberContext max_bytes;
		public List<NumberContext> number() {
			return getRuleContexts(NumberContext.class);
		}
		public NumberContext number(int i) {
			return getRuleContext(NumberContext.class,i);
		}
		public DirectiveContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directive; }
	}

	public final DirectiveContext directive() throws RecognitionException {
		DirectiveContext _localctx = new DirectiveContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_directive);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(120);
			_localctx.op = match(T__49);
			setState(121);
			_localctx.low_order_bits = number();
			setState(128);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__50) {
				{
				setState(122);
				match(T__50);
				setState(123);
				_localctx.padding_byte = number();
				setState(126);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__50) {
					{
					setState(124);
					match(T__50);
					setState(125);
					_localctx.max_bytes = number();
					}
				}

				}
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

	public static class Zero_opContext extends ParserRuleContext {
		public Token op;
		public Zero_opContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_zero_op; }
	}

	public final Zero_opContext zero_op() throws RecognitionException {
		Zero_opContext _localctx = new Zero_opContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_zero_op);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(130);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 52)) & ~0x3f) == 0 && ((1L << (_la - 52)) & ((1L << (T__51 - 52)) | (1L << (T__52 - 52)) | (1L << (T__53 - 52)) | (1L << (T__54 - 52)) | (1L << (T__55 - 52)) | (1L << (T__56 - 52)) | (1L << (T__57 - 52)) | (1L << (T__58 - 52)) | (1L << (T__59 - 52)) | (1L << (T__60 - 52)) | (1L << (T__61 - 52)) | (1L << (T__62 - 52)) | (1L << (T__63 - 52)) | (1L << (T__64 - 52)) | (1L << (T__65 - 52)) | (1L << (T__66 - 52)) | (1L << (T__67 - 52)) | (1L << (T__68 - 52)) | (1L << (T__69 - 52)) | (1L << (T__70 - 52)) | (1L << (T__71 - 52)) | (1L << (T__72 - 52)) | (1L << (T__73 - 52)) | (1L << (T__74 - 52)) | (1L << (T__75 - 52)) | (1L << (T__76 - 52)) | (1L << (T__77 - 52)) | (1L << (T__78 - 52)))) != 0)) ) {
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
		enterRule(_localctx, 14, RULE_imul_div);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(203);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__79:
			case T__80:
				{
				setState(133);
				_localctx.op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__79 || _la==T__80) ) {
					_localctx.op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(134);
				_localctx.a1 = operand8();
				setState(145);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op1.getText(), _localctx.a1.op); 
					}
					break;
				case T__50:
					{
					setState(136);
					match(T__50);
					setState(137);
					_localctx.b1 = operand8();
					setState(143);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op1.getText(), _localctx.a1.op, _localctx.b1.op); 
						}
						break;
					case T__50:
						{
						setState(139);
						match(T__50);
						setState(140);
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
			case T__81:
			case T__82:
				{
				setState(147);
				_localctx.op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__81 || _la==T__82) ) {
					_localctx.op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(148);
				_localctx.a2 = operand16();
				setState(159);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op2.getText(), _localctx.a2.op); 
					}
					break;
				case T__50:
					{
					setState(150);
					match(T__50);
					setState(151);
					_localctx.b2 = operand16();
					setState(157);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op2.getText(), _localctx.a2.op, _localctx.b2.op); 
						}
						break;
					case T__50:
						{
						setState(153);
						match(T__50);
						setState(154);
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
			case T__83:
			case T__84:
				{
				setState(161);
				_localctx.op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__83 || _la==T__84) ) {
					_localctx.op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(162);
				_localctx.a3 = operand32();
				setState(173);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op3.getText(), _localctx.a3.op); 
					}
					break;
				case T__50:
					{
					setState(164);
					match(T__50);
					setState(165);
					_localctx.b3 = operand32();
					setState(171);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op3.getText(), _localctx.a3.op, _localctx.b3.op); 
						}
						break;
					case T__50:
						{
						setState(167);
						match(T__50);
						setState(168);
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
			case T__85:
			case T__86:
				{
				setState(175);
				_localctx.op4 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__85 || _la==T__86) ) {
					_localctx.op4 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(176);
				_localctx.a4 = operand64();
				setState(187);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op4.getText(), _localctx.a4.op); 
					}
					break;
				case T__50:
					{
					setState(178);
					match(T__50);
					setState(179);
					_localctx.b4 = operand64();
					setState(185);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op4.getText(), _localctx.a4.op, _localctx.b4.op); 
						}
						break;
					case T__50:
						{
						setState(181);
						match(T__50);
						setState(182);
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
			case T__87:
			case T__88:
				{
				setState(189);
				_localctx.op5 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__87 || _la==T__88) ) {
					_localctx.op5 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(190);
				_localctx.a5 = operand();
				setState(201);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__1:
				case T__2:
					{
					 factory.createUnaryOperation(_localctx.op5.getText(), _localctx.a5.op); 
					}
					break;
				case T__50:
					{
					setState(192);
					match(T__50);
					setState(193);
					_localctx.b5 = operand();
					setState(199);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__0:
					case T__1:
					case T__2:
						{
						 factory.createBinaryOperation(_localctx.op5.getText(), _localctx.a5.op, _localctx.b5.op); 
						}
						break;
					case T__50:
						{
						setState(195);
						match(T__50);
						setState(196);
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
		enterRule(_localctx, 16, RULE_unary_op8);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(205);
			((Unary_op8Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 90)) & ~0x3f) == 0 && ((1L << (_la - 90)) & ((1L << (T__89 - 90)) | (1L << (T__90 - 90)) | (1L << (T__91 - 90)) | (1L << (T__92 - 90)) | (1L << (T__93 - 90)) | (1L << (T__94 - 90)))) != 0)) ) {
				((Unary_op8Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(206);
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
		enterRule(_localctx, 18, RULE_unary_op16);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(209);
			((Unary_op16Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 96)) & ~0x3f) == 0 && ((1L << (_la - 96)) & ((1L << (T__95 - 96)) | (1L << (T__96 - 96)) | (1L << (T__97 - 96)) | (1L << (T__98 - 96)) | (1L << (T__99 - 96)) | (1L << (T__100 - 96)) | (1L << (T__101 - 96)) | (1L << (T__102 - 96)))) != 0)) ) {
				((Unary_op16Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(210);
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
		enterRule(_localctx, 20, RULE_unary_op32);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(213);
			((Unary_op32Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 104)) & ~0x3f) == 0 && ((1L << (_la - 104)) & ((1L << (T__103 - 104)) | (1L << (T__104 - 104)) | (1L << (T__105 - 104)) | (1L << (T__106 - 104)) | (1L << (T__107 - 104)) | (1L << (T__108 - 104)) | (1L << (T__109 - 104)) | (1L << (T__110 - 104)) | (1L << (T__111 - 104)))) != 0)) ) {
				((Unary_op32Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(214);
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
		enterRule(_localctx, 22, RULE_unary_op64);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(217);
			((Unary_op64Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 113)) & ~0x3f) == 0 && ((1L << (_la - 113)) & ((1L << (T__112 - 113)) | (1L << (T__113 - 113)) | (1L << (T__114 - 113)) | (1L << (T__115 - 113)) | (1L << (T__116 - 113)) | (1L << (T__117 - 113)) | (1L << (T__118 - 113)) | (1L << (T__119 - 113)) | (1L << (T__120 - 113)))) != 0)) ) {
				((Unary_op64Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(218);
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
		enterRule(_localctx, 24, RULE_unary_op);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(221);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 122)) & ~0x3f) == 0 && ((1L << (_la - 122)) & ((1L << (T__121 - 122)) | (1L << (T__122 - 122)) | (1L << (T__123 - 122)) | (1L << (T__124 - 122)) | (1L << (T__125 - 122)) | (1L << (T__126 - 122)) | (1L << (T__127 - 122)) | (1L << (T__128 - 122)) | (1L << (T__129 - 122)) | (1L << (T__130 - 122)) | (1L << (T__131 - 122)) | (1L << (T__132 - 122)) | (1L << (T__133 - 122)) | (1L << (T__134 - 122)) | (1L << (T__135 - 122)) | (1L << (T__136 - 122)) | (1L << (T__137 - 122)) | (1L << (T__138 - 122)) | (1L << (T__139 - 122)) | (1L << (T__140 - 122)) | (1L << (T__141 - 122)) | (1L << (T__142 - 122)) | (1L << (T__143 - 122)) | (1L << (T__144 - 122)) | (1L << (T__145 - 122)) | (1L << (T__146 - 122)) | (1L << (T__147 - 122)) | (1L << (T__148 - 122)) | (1L << (T__149 - 122)) | (1L << (T__150 - 122)) | (1L << (T__151 - 122)) | (1L << (T__152 - 122)) | (1L << (T__153 - 122)) | (1L << (T__154 - 122)) | (1L << (T__155 - 122)) | (1L << (T__156 - 122)) | (1L << (T__157 - 122)) | (1L << (T__158 - 122)) | (1L << (T__159 - 122)) | (1L << (T__160 - 122)) | (1L << (T__161 - 122)))) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(222);
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
		enterRule(_localctx, 26, RULE_binary_op8);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(225);
			((Binary_op8Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 163)) & ~0x3f) == 0 && ((1L << (_la - 163)) & ((1L << (T__162 - 163)) | (1L << (T__163 - 163)) | (1L << (T__164 - 163)) | (1L << (T__165 - 163)) | (1L << (T__166 - 163)) | (1L << (T__167 - 163)) | (1L << (T__168 - 163)) | (1L << (T__169 - 163)) | (1L << (T__170 - 163)) | (1L << (T__171 - 163)) | (1L << (T__172 - 163)) | (1L << (T__173 - 163)) | (1L << (T__174 - 163)) | (1L << (T__175 - 163)) | (1L << (T__176 - 163)) | (1L << (T__177 - 163)) | (1L << (T__178 - 163)) | (1L << (T__179 - 163)) | (1L << (T__180 - 163)) | (1L << (T__181 - 163)) | (1L << (T__182 - 163)))) != 0)) ) {
				((Binary_op8Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(226);
			((Binary_op8Context)_localctx).a = operand8();
			setState(227);
			match(T__50);
			setState(228);
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
		enterRule(_localctx, 28, RULE_binary_op16);
		int _la;
		try {
			setState(249);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
				enterOuterAlt(_localctx, 1);
				{
				setState(231);
				((Binary_op16Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 184)) & ~0x3f) == 0 && ((1L << (_la - 184)) & ((1L << (T__183 - 184)) | (1L << (T__184 - 184)) | (1L << (T__185 - 184)) | (1L << (T__186 - 184)) | (1L << (T__187 - 184)) | (1L << (T__188 - 184)) | (1L << (T__189 - 184)) | (1L << (T__190 - 184)) | (1L << (T__191 - 184)) | (1L << (T__192 - 184)) | (1L << (T__193 - 184)) | (1L << (T__194 - 184)) | (1L << (T__195 - 184)) | (1L << (T__196 - 184)) | (1L << (T__197 - 184)) | (1L << (T__198 - 184)) | (1L << (T__199 - 184)) | (1L << (T__200 - 184)) | (1L << (T__201 - 184)) | (1L << (T__202 - 184)) | (1L << (T__203 - 184)) | (1L << (T__204 - 184)) | (1L << (T__205 - 184)) | (1L << (T__206 - 184)) | (1L << (T__207 - 184)) | (1L << (T__208 - 184)) | (1L << (T__209 - 184)) | (1L << (T__210 - 184)) | (1L << (T__211 - 184)) | (1L << (T__212 - 184)) | (1L << (T__213 - 184)) | (1L << (T__214 - 184)) | (1L << (T__215 - 184)) | (1L << (T__216 - 184)) | (1L << (T__217 - 184)) | (1L << (T__218 - 184)) | (1L << (T__219 - 184)) | (1L << (T__220 - 184)) | (1L << (T__221 - 184)) | (1L << (T__222 - 184)) | (1L << (T__223 - 184)) | (1L << (T__224 - 184)) | (1L << (T__225 - 184)) | (1L << (T__226 - 184)) | (1L << (T__227 - 184)) | (1L << (T__228 - 184)) | (1L << (T__229 - 184)) | (1L << (T__230 - 184)) | (1L << (T__231 - 184)))) != 0)) ) {
					((Binary_op16Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(232);
				((Binary_op16Context)_localctx).a1 = operand16();
				setState(233);
				match(T__50);
				setState(234);
				((Binary_op16Context)_localctx).b1 = operand16();
				 factory.createBinaryOperation(((Binary_op16Context)_localctx).op1.getText(), ((Binary_op16Context)_localctx).a1.op, ((Binary_op16Context)_localctx).b1.op); 
				}
				break;
			case T__232:
			case T__233:
			case T__234:
			case T__235:
			case T__236:
			case T__237:
			case T__238:
			case T__239:
				enterOuterAlt(_localctx, 2);
				{
				setState(237);
				((Binary_op16Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 233)) & ~0x3f) == 0 && ((1L << (_la - 233)) & ((1L << (T__232 - 233)) | (1L << (T__233 - 233)) | (1L << (T__234 - 233)) | (1L << (T__235 - 233)) | (1L << (T__236 - 233)) | (1L << (T__237 - 233)) | (1L << (T__238 - 233)) | (1L << (T__239 - 233)))) != 0)) ) {
					((Binary_op16Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(238);
				((Binary_op16Context)_localctx).a2 = operand8();
				setState(239);
				match(T__50);
				setState(240);
				((Binary_op16Context)_localctx).b2 = operand16();
				 factory.createBinaryOperation(((Binary_op16Context)_localctx).op2.getText(), ((Binary_op16Context)_localctx).a2.op, ((Binary_op16Context)_localctx).b2.op); 
				}
				break;
			case T__240:
			case T__241:
				enterOuterAlt(_localctx, 3);
				{
				setState(243);
				((Binary_op16Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__240 || _la==T__241) ) {
					((Binary_op16Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(244);
				((Binary_op16Context)_localctx).a3 = operand8();
				setState(245);
				match(T__50);
				setState(246);
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
		enterRule(_localctx, 30, RULE_binary_op32);
		int _la;
		try {
			setState(275);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
				enterOuterAlt(_localctx, 1);
				{
				setState(251);
				((Binary_op32Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 243)) & ~0x3f) == 0 && ((1L << (_la - 243)) & ((1L << (T__242 - 243)) | (1L << (T__243 - 243)) | (1L << (T__244 - 243)) | (1L << (T__245 - 243)) | (1L << (T__246 - 243)) | (1L << (T__247 - 243)) | (1L << (T__248 - 243)) | (1L << (T__249 - 243)) | (1L << (T__250 - 243)) | (1L << (T__251 - 243)) | (1L << (T__252 - 243)) | (1L << (T__253 - 243)) | (1L << (T__254 - 243)) | (1L << (T__255 - 243)) | (1L << (T__256 - 243)) | (1L << (T__257 - 243)) | (1L << (T__258 - 243)) | (1L << (T__259 - 243)) | (1L << (T__260 - 243)) | (1L << (T__261 - 243)) | (1L << (T__262 - 243)) | (1L << (T__263 - 243)) | (1L << (T__264 - 243)) | (1L << (T__265 - 243)) | (1L << (T__266 - 243)) | (1L << (T__267 - 243)) | (1L << (T__268 - 243)) | (1L << (T__269 - 243)) | (1L << (T__270 - 243)) | (1L << (T__271 - 243)) | (1L << (T__272 - 243)) | (1L << (T__273 - 243)) | (1L << (T__274 - 243)) | (1L << (T__275 - 243)) | (1L << (T__276 - 243)) | (1L << (T__277 - 243)) | (1L << (T__278 - 243)) | (1L << (T__279 - 243)) | (1L << (T__280 - 243)) | (1L << (T__281 - 243)) | (1L << (T__282 - 243)) | (1L << (T__283 - 243)) | (1L << (T__284 - 243)) | (1L << (T__285 - 243)) | (1L << (T__286 - 243)) | (1L << (T__287 - 243)) | (1L << (T__288 - 243)) | (1L << (T__289 - 243)) | (1L << (T__290 - 243)))) != 0)) ) {
					((Binary_op32Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(252);
				((Binary_op32Context)_localctx).a1 = operand32();
				setState(253);
				match(T__50);
				setState(254);
				((Binary_op32Context)_localctx).b1 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op1.getText(), ((Binary_op32Context)_localctx).a1.op, ((Binary_op32Context)_localctx).b1.op); 
				}
				break;
			case T__291:
			case T__292:
			case T__293:
			case T__294:
			case T__295:
			case T__296:
			case T__297:
			case T__298:
				enterOuterAlt(_localctx, 2);
				{
				setState(257);
				((Binary_op32Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 292)) & ~0x3f) == 0 && ((1L << (_la - 292)) & ((1L << (T__291 - 292)) | (1L << (T__292 - 292)) | (1L << (T__293 - 292)) | (1L << (T__294 - 292)) | (1L << (T__295 - 292)) | (1L << (T__296 - 292)) | (1L << (T__297 - 292)) | (1L << (T__298 - 292)))) != 0)) ) {
					((Binary_op32Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(258);
				((Binary_op32Context)_localctx).a2 = operand8();
				setState(259);
				match(T__50);
				setState(260);
				((Binary_op32Context)_localctx).b2 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op2.getText(), ((Binary_op32Context)_localctx).a2.op, ((Binary_op32Context)_localctx).b2.op); 
				}
				break;
			case T__299:
			case T__300:
				enterOuterAlt(_localctx, 3);
				{
				setState(263);
				((Binary_op32Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__299 || _la==T__300) ) {
					((Binary_op32Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(264);
				((Binary_op32Context)_localctx).a3 = operand8();
				setState(265);
				match(T__50);
				setState(266);
				((Binary_op32Context)_localctx).b3 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op3.getText(), ((Binary_op32Context)_localctx).a3.op, ((Binary_op32Context)_localctx).b3.op); 
				}
				break;
			case T__301:
			case T__302:
				enterOuterAlt(_localctx, 4);
				{
				setState(269);
				((Binary_op32Context)_localctx).op4 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__301 || _la==T__302) ) {
					((Binary_op32Context)_localctx).op4 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(270);
				((Binary_op32Context)_localctx).a4 = operand16();
				setState(271);
				match(T__50);
				setState(272);
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
		enterRule(_localctx, 32, RULE_binary_op64);
		int _la;
		try {
			setState(307);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
				enterOuterAlt(_localctx, 1);
				{
				setState(277);
				((Binary_op64Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 304)) & ~0x3f) == 0 && ((1L << (_la - 304)) & ((1L << (T__303 - 304)) | (1L << (T__304 - 304)) | (1L << (T__305 - 304)) | (1L << (T__306 - 304)) | (1L << (T__307 - 304)) | (1L << (T__308 - 304)) | (1L << (T__309 - 304)) | (1L << (T__310 - 304)) | (1L << (T__311 - 304)) | (1L << (T__312 - 304)) | (1L << (T__313 - 304)) | (1L << (T__314 - 304)) | (1L << (T__315 - 304)) | (1L << (T__316 - 304)) | (1L << (T__317 - 304)) | (1L << (T__318 - 304)) | (1L << (T__319 - 304)) | (1L << (T__320 - 304)) | (1L << (T__321 - 304)) | (1L << (T__322 - 304)) | (1L << (T__323 - 304)) | (1L << (T__324 - 304)) | (1L << (T__325 - 304)) | (1L << (T__326 - 304)) | (1L << (T__327 - 304)) | (1L << (T__328 - 304)) | (1L << (T__329 - 304)) | (1L << (T__330 - 304)) | (1L << (T__331 - 304)) | (1L << (T__332 - 304)) | (1L << (T__333 - 304)) | (1L << (T__334 - 304)) | (1L << (T__335 - 304)) | (1L << (T__336 - 304)) | (1L << (T__337 - 304)) | (1L << (T__338 - 304)) | (1L << (T__339 - 304)) | (1L << (T__340 - 304)) | (1L << (T__341 - 304)) | (1L << (T__342 - 304)) | (1L << (T__343 - 304)) | (1L << (T__344 - 304)) | (1L << (T__345 - 304)) | (1L << (T__346 - 304)) | (1L << (T__347 - 304)) | (1L << (T__348 - 304)) | (1L << (T__349 - 304)) | (1L << (T__350 - 304)) | (1L << (T__351 - 304)))) != 0)) ) {
					((Binary_op64Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(278);
				((Binary_op64Context)_localctx).a1 = operand64();
				setState(279);
				match(T__50);
				setState(280);
				((Binary_op64Context)_localctx).b1 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op1.getText(), ((Binary_op64Context)_localctx).a1.op, ((Binary_op64Context)_localctx).b1.op); 
				}
				break;
			case T__352:
			case T__353:
			case T__354:
			case T__355:
			case T__356:
			case T__357:
			case T__358:
			case T__359:
				enterOuterAlt(_localctx, 2);
				{
				setState(283);
				((Binary_op64Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 353)) & ~0x3f) == 0 && ((1L << (_la - 353)) & ((1L << (T__352 - 353)) | (1L << (T__353 - 353)) | (1L << (T__354 - 353)) | (1L << (T__355 - 353)) | (1L << (T__356 - 353)) | (1L << (T__357 - 353)) | (1L << (T__358 - 353)) | (1L << (T__359 - 353)))) != 0)) ) {
					((Binary_op64Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(284);
				((Binary_op64Context)_localctx).a2 = operand8();
				setState(285);
				match(T__50);
				setState(286);
				((Binary_op64Context)_localctx).b2 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op2.getText(), ((Binary_op64Context)_localctx).a2.op, ((Binary_op64Context)_localctx).b2.op); 
				}
				break;
			case T__360:
			case T__361:
				enterOuterAlt(_localctx, 3);
				{
				setState(289);
				((Binary_op64Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__360 || _la==T__361) ) {
					((Binary_op64Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(290);
				((Binary_op64Context)_localctx).a3 = operand8();
				setState(291);
				match(T__50);
				setState(292);
				((Binary_op64Context)_localctx).b3 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op3.getText(), ((Binary_op64Context)_localctx).a3.op, ((Binary_op64Context)_localctx).b3.op); 
				}
				break;
			case T__362:
			case T__363:
				enterOuterAlt(_localctx, 4);
				{
				setState(295);
				((Binary_op64Context)_localctx).op4 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__362 || _la==T__363) ) {
					((Binary_op64Context)_localctx).op4 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(296);
				((Binary_op64Context)_localctx).a4 = operand16();
				setState(297);
				match(T__50);
				setState(298);
				((Binary_op64Context)_localctx).b4 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op4.getText(), ((Binary_op64Context)_localctx).a4.op, ((Binary_op64Context)_localctx).b4.op); 
				}
				break;
			case T__364:
				enterOuterAlt(_localctx, 5);
				{
				setState(301);
				((Binary_op64Context)_localctx).op5 = match(T__364);
				setState(302);
				((Binary_op64Context)_localctx).a5 = operand32();
				setState(303);
				match(T__50);
				setState(304);
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
		enterRule(_localctx, 34, RULE_binary_op);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(309);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 366)) & ~0x3f) == 0 && ((1L << (_la - 366)) & ((1L << (T__365 - 366)) | (1L << (T__366 - 366)) | (1L << (T__367 - 366)) | (1L << (T__368 - 366)) | (1L << (T__369 - 366)) | (1L << (T__370 - 366)) | (1L << (T__371 - 366)) | (1L << (T__372 - 366)) | (1L << (T__373 - 366)) | (1L << (T__374 - 366)) | (1L << (T__375 - 366)) | (1L << (T__376 - 366)) | (1L << (T__377 - 366)) | (1L << (T__378 - 366)) | (1L << (T__379 - 366)) | (1L << (T__380 - 366)) | (1L << (T__381 - 366)) | (1L << (T__382 - 366)) | (1L << (T__383 - 366)) | (1L << (T__384 - 366)) | (1L << (T__385 - 366)) | (1L << (T__386 - 366)) | (1L << (T__387 - 366)) | (1L << (T__388 - 366)) | (1L << (T__389 - 366)) | (1L << (T__390 - 366)) | (1L << (T__391 - 366)) | (1L << (T__392 - 366)) | (1L << (T__393 - 366)) | (1L << (T__394 - 366)) | (1L << (T__395 - 366)) | (1L << (T__396 - 366)) | (1L << (T__397 - 366)) | (1L << (T__398 - 366)) | (1L << (T__399 - 366)) | (1L << (T__400 - 366)) | (1L << (T__401 - 366)) | (1L << (T__402 - 366)) | (1L << (T__403 - 366)) | (1L << (T__404 - 366)) | (1L << (T__405 - 366)) | (1L << (T__406 - 366)) | (1L << (T__407 - 366)) | (1L << (T__408 - 366)) | (1L << (T__409 - 366)) | (1L << (T__410 - 366)) | (1L << (T__411 - 366)) | (1L << (T__412 - 366)) | (1L << (T__413 - 366)) | (1L << (T__414 - 366)) | (1L << (T__415 - 366)) | (1L << (T__416 - 366)) | (1L << (T__417 - 366)) | (1L << (T__418 - 366)) | (1L << (T__419 - 366)) | (1L << (T__420 - 366)))) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(310);
			_localctx.a = operand();
			setState(311);
			match(T__50);
			setState(312);
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
		enterRule(_localctx, 36, RULE_operand8);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(327);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__447:
				{
				setState(315);
				((Operand8Context)_localctx).register8 = register8();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).register8.op; 
				}
				break;
			case T__422:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(318);
				((Operand8Context)_localctx).memory_reference = memory_reference();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).memory_reference.op; 
				}
				break;
			case T__542:
				{
				setState(321);
				((Operand8Context)_localctx).immediate = immediate();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).immediate.op; 
				}
				break;
			case T__543:
				{
				setState(324);
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
		enterRule(_localctx, 38, RULE_operand16);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(341);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__471:
				{
				setState(329);
				((Operand16Context)_localctx).register16 = register16();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).register16.op; 
				}
				break;
			case T__422:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(332);
				((Operand16Context)_localctx).memory_reference = memory_reference();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).memory_reference.op; 
				}
				break;
			case T__542:
				{
				setState(335);
				((Operand16Context)_localctx).immediate = immediate();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).immediate.op; 
				}
				break;
			case T__543:
				{
				setState(338);
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
		enterRule(_localctx, 40, RULE_operand32);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(355);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__495:
				{
				setState(343);
				((Operand32Context)_localctx).register32 = register32();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).register32.op; 
				}
				break;
			case T__422:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(346);
				((Operand32Context)_localctx).memory_reference = memory_reference();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).memory_reference.op; 
				}
				break;
			case T__542:
				{
				setState(349);
				((Operand32Context)_localctx).immediate = immediate();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).immediate.op; 
				}
				break;
			case T__543:
				{
				setState(352);
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
		enterRule(_localctx, 42, RULE_operand64);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(369);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__519:
				{
				setState(357);
				((Operand64Context)_localctx).register64 = register64();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).register64.op; 
				}
				break;
			case T__422:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(360);
				((Operand64Context)_localctx).memory_reference = memory_reference();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).memory_reference.op; 
				}
				break;
			case T__542:
				{
				setState(363);
				((Operand64Context)_localctx).immediate = immediate();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).immediate.op; 
				}
				break;
			case T__543:
				{
				setState(366);
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
		enterRule(_localctx, 44, RULE_operand);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(395);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__447:
				{
				setState(371);
				_localctx.register8 = register8();
				 _localctx.op =  _localctx.register8.op; 
				}
				break;
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
			case T__471:
				{
				setState(374);
				_localctx.register16 = register16();
				 _localctx.op =  _localctx.register16.op; 
				}
				break;
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
			case T__495:
				{
				setState(377);
				_localctx.register32 = register32();
				 _localctx.op =  _localctx.register32.op; 
				}
				break;
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
			case T__519:
				{
				setState(380);
				_localctx.register64 = register64();
				 _localctx.op =  _localctx.register64.op; 
				}
				break;
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
			case T__535:
				{
				setState(383);
				_localctx.registerXmm = registerXmm();
				 _localctx.op =  _localctx.registerXmm.op; 
				}
				break;
			case T__422:
			case T__536:
			case T__537:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(386);
				_localctx.memory_reference = memory_reference();
				 _localctx.op =  _localctx.memory_reference.op; 
				}
				break;
			case T__542:
				{
				setState(389);
				_localctx.immediate = immediate();
				 _localctx.op =  _localctx.immediate.op; 
				}
				break;
			case T__543:
				{
				setState(392);
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
		enterRule(_localctx, 46, RULE_memory_reference);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 String displacement = null;
			                                                   String segment = null;
			                                                   AsmOperand base = null;
			                                                   AsmOperand offset = null;
			                                                   int scale = 1; 
			setState(402);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 537)) & ~0x3f) == 0 && ((1L << (_la - 537)) & ((1L << (T__536 - 537)) | (1L << (T__537 - 537)) | (1L << (T__538 - 537)) | (1L << (T__539 - 537)) | (1L << (T__540 - 537)) | (1L << (T__541 - 537)))) != 0)) {
				{
				setState(398);
				_localctx.segment_register = segment_register();
				 segment = _localctx.segment_register.reg; 
				setState(400);
				match(T__421);
				}
			}

			setState(449);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(409);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case IDENT:
					{
					setState(404);
					_localctx.i = match(IDENT);
					 displacement = _localctx.i.getText(); 
					}
					break;
				case BIN_NUMBER:
				case HEX_NUMBER:
				case NUMBER:
					{
					setState(406);
					_localctx.number = number();
					 displacement = String.valueOf(_localctx.number.n); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(429);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__422) {
					{
					setState(411);
					match(T__422);
					setState(415);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (((((_la - 423)) & ~0x3f) == 0 && ((1L << (_la - 423)) & ((1L << (T__422 - 423)) | (1L << (T__424 - 423)) | (1L << (T__425 - 423)) | (1L << (T__426 - 423)) | (1L << (T__427 - 423)) | (1L << (T__428 - 423)) | (1L << (T__429 - 423)) | (1L << (T__430 - 423)) | (1L << (T__431 - 423)) | (1L << (T__432 - 423)) | (1L << (T__433 - 423)) | (1L << (T__434 - 423)) | (1L << (T__435 - 423)) | (1L << (T__436 - 423)) | (1L << (T__437 - 423)) | (1L << (T__438 - 423)) | (1L << (T__439 - 423)) | (1L << (T__440 - 423)) | (1L << (T__441 - 423)) | (1L << (T__442 - 423)) | (1L << (T__443 - 423)) | (1L << (T__444 - 423)) | (1L << (T__445 - 423)) | (1L << (T__446 - 423)) | (1L << (T__447 - 423)) | (1L << (T__448 - 423)) | (1L << (T__449 - 423)) | (1L << (T__450 - 423)) | (1L << (T__451 - 423)) | (1L << (T__452 - 423)) | (1L << (T__453 - 423)) | (1L << (T__454 - 423)) | (1L << (T__455 - 423)) | (1L << (T__456 - 423)) | (1L << (T__457 - 423)) | (1L << (T__458 - 423)) | (1L << (T__459 - 423)) | (1L << (T__460 - 423)) | (1L << (T__461 - 423)) | (1L << (T__462 - 423)) | (1L << (T__463 - 423)) | (1L << (T__464 - 423)) | (1L << (T__465 - 423)) | (1L << (T__466 - 423)) | (1L << (T__467 - 423)) | (1L << (T__468 - 423)) | (1L << (T__469 - 423)) | (1L << (T__470 - 423)) | (1L << (T__471 - 423)) | (1L << (T__472 - 423)) | (1L << (T__473 - 423)) | (1L << (T__474 - 423)) | (1L << (T__475 - 423)) | (1L << (T__476 - 423)) | (1L << (T__477 - 423)) | (1L << (T__478 - 423)) | (1L << (T__479 - 423)) | (1L << (T__480 - 423)) | (1L << (T__481 - 423)) | (1L << (T__482 - 423)) | (1L << (T__483 - 423)) | (1L << (T__484 - 423)) | (1L << (T__485 - 423)))) != 0) || ((((_la - 487)) & ~0x3f) == 0 && ((1L << (_la - 487)) & ((1L << (T__486 - 487)) | (1L << (T__487 - 487)) | (1L << (T__488 - 487)) | (1L << (T__489 - 487)) | (1L << (T__490 - 487)) | (1L << (T__491 - 487)) | (1L << (T__492 - 487)) | (1L << (T__493 - 487)) | (1L << (T__494 - 487)) | (1L << (T__495 - 487)) | (1L << (T__496 - 487)) | (1L << (T__497 - 487)) | (1L << (T__498 - 487)) | (1L << (T__499 - 487)) | (1L << (T__500 - 487)) | (1L << (T__501 - 487)) | (1L << (T__502 - 487)) | (1L << (T__503 - 487)) | (1L << (T__504 - 487)) | (1L << (T__505 - 487)) | (1L << (T__506 - 487)) | (1L << (T__507 - 487)) | (1L << (T__508 - 487)) | (1L << (T__509 - 487)) | (1L << (T__510 - 487)) | (1L << (T__511 - 487)) | (1L << (T__512 - 487)) | (1L << (T__513 - 487)) | (1L << (T__514 - 487)) | (1L << (T__515 - 487)) | (1L << (T__516 - 487)) | (1L << (T__517 - 487)) | (1L << (T__518 - 487)) | (1L << (T__519 - 487)) | (1L << (T__520 - 487)) | (1L << (T__521 - 487)) | (1L << (T__522 - 487)) | (1L << (T__523 - 487)) | (1L << (T__524 - 487)) | (1L << (T__525 - 487)) | (1L << (T__526 - 487)) | (1L << (T__527 - 487)) | (1L << (T__528 - 487)) | (1L << (T__529 - 487)) | (1L << (T__530 - 487)) | (1L << (T__531 - 487)) | (1L << (T__532 - 487)) | (1L << (T__533 - 487)) | (1L << (T__534 - 487)) | (1L << (T__535 - 487)) | (1L << (T__536 - 487)) | (1L << (T__537 - 487)) | (1L << (T__538 - 487)) | (1L << (T__539 - 487)) | (1L << (T__540 - 487)) | (1L << (T__541 - 487)) | (1L << (T__542 - 487)) | (1L << (T__543 - 487)))) != 0) || ((((_la - 552)) & ~0x3f) == 0 && ((1L << (_la - 552)) & ((1L << (IDENT - 552)) | (1L << (BIN_NUMBER - 552)) | (1L << (HEX_NUMBER - 552)) | (1L << (NUMBER - 552)))) != 0)) {
						{
						setState(412);
						_localctx.operand = operand();
						 base = _localctx.operand.op; 
						}
					}

					setState(426);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__50) {
						{
						setState(417);
						match(T__50);
						setState(418);
						_localctx.operand = operand();
						 offset = _localctx.operand.op; 
						setState(424);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==T__50) {
							{
							setState(420);
							match(T__50);
							setState(421);
							_localctx.number = number();
							 scale = (int) _localctx.number.n; 
							}
						}

						}
					}

					setState(428);
					match(T__423);
					}
				}

				}
				break;
			case T__422:
				{
				setState(431);
				match(T__422);
				setState(435);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 423)) & ~0x3f) == 0 && ((1L << (_la - 423)) & ((1L << (T__422 - 423)) | (1L << (T__424 - 423)) | (1L << (T__425 - 423)) | (1L << (T__426 - 423)) | (1L << (T__427 - 423)) | (1L << (T__428 - 423)) | (1L << (T__429 - 423)) | (1L << (T__430 - 423)) | (1L << (T__431 - 423)) | (1L << (T__432 - 423)) | (1L << (T__433 - 423)) | (1L << (T__434 - 423)) | (1L << (T__435 - 423)) | (1L << (T__436 - 423)) | (1L << (T__437 - 423)) | (1L << (T__438 - 423)) | (1L << (T__439 - 423)) | (1L << (T__440 - 423)) | (1L << (T__441 - 423)) | (1L << (T__442 - 423)) | (1L << (T__443 - 423)) | (1L << (T__444 - 423)) | (1L << (T__445 - 423)) | (1L << (T__446 - 423)) | (1L << (T__447 - 423)) | (1L << (T__448 - 423)) | (1L << (T__449 - 423)) | (1L << (T__450 - 423)) | (1L << (T__451 - 423)) | (1L << (T__452 - 423)) | (1L << (T__453 - 423)) | (1L << (T__454 - 423)) | (1L << (T__455 - 423)) | (1L << (T__456 - 423)) | (1L << (T__457 - 423)) | (1L << (T__458 - 423)) | (1L << (T__459 - 423)) | (1L << (T__460 - 423)) | (1L << (T__461 - 423)) | (1L << (T__462 - 423)) | (1L << (T__463 - 423)) | (1L << (T__464 - 423)) | (1L << (T__465 - 423)) | (1L << (T__466 - 423)) | (1L << (T__467 - 423)) | (1L << (T__468 - 423)) | (1L << (T__469 - 423)) | (1L << (T__470 - 423)) | (1L << (T__471 - 423)) | (1L << (T__472 - 423)) | (1L << (T__473 - 423)) | (1L << (T__474 - 423)) | (1L << (T__475 - 423)) | (1L << (T__476 - 423)) | (1L << (T__477 - 423)) | (1L << (T__478 - 423)) | (1L << (T__479 - 423)) | (1L << (T__480 - 423)) | (1L << (T__481 - 423)) | (1L << (T__482 - 423)) | (1L << (T__483 - 423)) | (1L << (T__484 - 423)) | (1L << (T__485 - 423)))) != 0) || ((((_la - 487)) & ~0x3f) == 0 && ((1L << (_la - 487)) & ((1L << (T__486 - 487)) | (1L << (T__487 - 487)) | (1L << (T__488 - 487)) | (1L << (T__489 - 487)) | (1L << (T__490 - 487)) | (1L << (T__491 - 487)) | (1L << (T__492 - 487)) | (1L << (T__493 - 487)) | (1L << (T__494 - 487)) | (1L << (T__495 - 487)) | (1L << (T__496 - 487)) | (1L << (T__497 - 487)) | (1L << (T__498 - 487)) | (1L << (T__499 - 487)) | (1L << (T__500 - 487)) | (1L << (T__501 - 487)) | (1L << (T__502 - 487)) | (1L << (T__503 - 487)) | (1L << (T__504 - 487)) | (1L << (T__505 - 487)) | (1L << (T__506 - 487)) | (1L << (T__507 - 487)) | (1L << (T__508 - 487)) | (1L << (T__509 - 487)) | (1L << (T__510 - 487)) | (1L << (T__511 - 487)) | (1L << (T__512 - 487)) | (1L << (T__513 - 487)) | (1L << (T__514 - 487)) | (1L << (T__515 - 487)) | (1L << (T__516 - 487)) | (1L << (T__517 - 487)) | (1L << (T__518 - 487)) | (1L << (T__519 - 487)) | (1L << (T__520 - 487)) | (1L << (T__521 - 487)) | (1L << (T__522 - 487)) | (1L << (T__523 - 487)) | (1L << (T__524 - 487)) | (1L << (T__525 - 487)) | (1L << (T__526 - 487)) | (1L << (T__527 - 487)) | (1L << (T__528 - 487)) | (1L << (T__529 - 487)) | (1L << (T__530 - 487)) | (1L << (T__531 - 487)) | (1L << (T__532 - 487)) | (1L << (T__533 - 487)) | (1L << (T__534 - 487)) | (1L << (T__535 - 487)) | (1L << (T__536 - 487)) | (1L << (T__537 - 487)) | (1L << (T__538 - 487)) | (1L << (T__539 - 487)) | (1L << (T__540 - 487)) | (1L << (T__541 - 487)) | (1L << (T__542 - 487)) | (1L << (T__543 - 487)))) != 0) || ((((_la - 552)) & ~0x3f) == 0 && ((1L << (_la - 552)) & ((1L << (IDENT - 552)) | (1L << (BIN_NUMBER - 552)) | (1L << (HEX_NUMBER - 552)) | (1L << (NUMBER - 552)))) != 0)) {
					{
					setState(432);
					_localctx.operand = operand();
					 base = _localctx.operand.op; 
					}
				}

				setState(446);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__50) {
					{
					setState(437);
					match(T__50);
					setState(438);
					_localctx.operand = operand();
					 offset = _localctx.operand.op; 
					setState(444);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__50) {
						{
						setState(440);
						match(T__50);
						setState(441);
						_localctx.number = number();
						 scale = (int) _localctx.number.n; 
						}
					}

					}
				}

				setState(448);
				match(T__423);
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
		enterRule(_localctx, 48, RULE_register8);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(453);
			((Register8Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 425)) & ~0x3f) == 0 && ((1L << (_la - 425)) & ((1L << (T__424 - 425)) | (1L << (T__425 - 425)) | (1L << (T__426 - 425)) | (1L << (T__427 - 425)) | (1L << (T__428 - 425)) | (1L << (T__429 - 425)) | (1L << (T__430 - 425)) | (1L << (T__431 - 425)) | (1L << (T__432 - 425)) | (1L << (T__433 - 425)) | (1L << (T__434 - 425)) | (1L << (T__435 - 425)) | (1L << (T__436 - 425)) | (1L << (T__437 - 425)) | (1L << (T__438 - 425)) | (1L << (T__439 - 425)) | (1L << (T__440 - 425)) | (1L << (T__441 - 425)) | (1L << (T__442 - 425)) | (1L << (T__443 - 425)) | (1L << (T__444 - 425)) | (1L << (T__445 - 425)) | (1L << (T__446 - 425)) | (1L << (T__447 - 425)))) != 0)) ) {
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
		enterRule(_localctx, 50, RULE_register16);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(456);
			((Register16Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 449)) & ~0x3f) == 0 && ((1L << (_la - 449)) & ((1L << (T__448 - 449)) | (1L << (T__449 - 449)) | (1L << (T__450 - 449)) | (1L << (T__451 - 449)) | (1L << (T__452 - 449)) | (1L << (T__453 - 449)) | (1L << (T__454 - 449)) | (1L << (T__455 - 449)) | (1L << (T__456 - 449)) | (1L << (T__457 - 449)) | (1L << (T__458 - 449)) | (1L << (T__459 - 449)) | (1L << (T__460 - 449)) | (1L << (T__461 - 449)) | (1L << (T__462 - 449)) | (1L << (T__463 - 449)) | (1L << (T__464 - 449)) | (1L << (T__465 - 449)) | (1L << (T__466 - 449)) | (1L << (T__467 - 449)) | (1L << (T__468 - 449)) | (1L << (T__469 - 449)) | (1L << (T__470 - 449)) | (1L << (T__471 - 449)))) != 0)) ) {
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
		enterRule(_localctx, 52, RULE_register32);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(459);
			((Register32Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 473)) & ~0x3f) == 0 && ((1L << (_la - 473)) & ((1L << (T__472 - 473)) | (1L << (T__473 - 473)) | (1L << (T__474 - 473)) | (1L << (T__475 - 473)) | (1L << (T__476 - 473)) | (1L << (T__477 - 473)) | (1L << (T__478 - 473)) | (1L << (T__479 - 473)) | (1L << (T__480 - 473)) | (1L << (T__481 - 473)) | (1L << (T__482 - 473)) | (1L << (T__483 - 473)) | (1L << (T__484 - 473)) | (1L << (T__485 - 473)) | (1L << (T__486 - 473)) | (1L << (T__487 - 473)) | (1L << (T__488 - 473)) | (1L << (T__489 - 473)) | (1L << (T__490 - 473)) | (1L << (T__491 - 473)) | (1L << (T__492 - 473)) | (1L << (T__493 - 473)) | (1L << (T__494 - 473)) | (1L << (T__495 - 473)))) != 0)) ) {
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
		enterRule(_localctx, 54, RULE_register64);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(462);
			((Register64Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 497)) & ~0x3f) == 0 && ((1L << (_la - 497)) & ((1L << (T__496 - 497)) | (1L << (T__497 - 497)) | (1L << (T__498 - 497)) | (1L << (T__499 - 497)) | (1L << (T__500 - 497)) | (1L << (T__501 - 497)) | (1L << (T__502 - 497)) | (1L << (T__503 - 497)) | (1L << (T__504 - 497)) | (1L << (T__505 - 497)) | (1L << (T__506 - 497)) | (1L << (T__507 - 497)) | (1L << (T__508 - 497)) | (1L << (T__509 - 497)) | (1L << (T__510 - 497)) | (1L << (T__511 - 497)) | (1L << (T__512 - 497)) | (1L << (T__513 - 497)) | (1L << (T__514 - 497)) | (1L << (T__515 - 497)) | (1L << (T__516 - 497)) | (1L << (T__517 - 497)) | (1L << (T__518 - 497)) | (1L << (T__519 - 497)))) != 0)) ) {
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
		enterRule(_localctx, 56, RULE_registerXmm);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(465);
			_localctx.r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 521)) & ~0x3f) == 0 && ((1L << (_la - 521)) & ((1L << (T__520 - 521)) | (1L << (T__521 - 521)) | (1L << (T__522 - 521)) | (1L << (T__523 - 521)) | (1L << (T__524 - 521)) | (1L << (T__525 - 521)) | (1L << (T__526 - 521)) | (1L << (T__527 - 521)) | (1L << (T__528 - 521)) | (1L << (T__529 - 521)) | (1L << (T__530 - 521)) | (1L << (T__531 - 521)) | (1L << (T__532 - 521)) | (1L << (T__533 - 521)) | (1L << (T__534 - 521)) | (1L << (T__535 - 521)))) != 0)) ) {
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
		enterRule(_localctx, 58, RULE_segment_register);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(468);
			_localctx.r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 537)) & ~0x3f) == 0 && ((1L << (_la - 537)) & ((1L << (T__536 - 537)) | (1L << (T__537 - 537)) | (1L << (T__538 - 537)) | (1L << (T__539 - 537)) | (1L << (T__540 - 537)) | (1L << (T__541 - 537)))) != 0)) ) {
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
		enterRule(_localctx, 60, RULE_number);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(477);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER:
				{
				setState(471);
				_localctx.num = match(NUMBER);
				 _localctx.n =  Long.parseLong(_localctx.num.getText(), 10); 
				}
				break;
			case BIN_NUMBER:
				{
				setState(473);
				_localctx.num = match(BIN_NUMBER);
				 _localctx.n =  Long.parseLong(_localctx.num.getText().substring(2), 2); 
				}
				break;
			case HEX_NUMBER:
				{
				setState(475);
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
		enterRule(_localctx, 62, RULE_immediate);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(479);
			match(T__542);
			setState(480);
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
		enterRule(_localctx, 64, RULE_argument);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(483);
			match(T__543);
			setState(506);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(484);
				_localctx.n = number();
				 _localctx.op =  new AsmArgumentOperand((int) _localctx.n.n); 
				}
				break;
			case T__544:
				{
				setState(487);
				match(T__544);
				setState(488);
				_localctx.n = number();
				setState(489);
				match(T__421);
				 int size = -1; int shift = 0; 
				setState(501);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__545:
					{
					setState(491);
					match(T__545);
					 size = 8; 
					}
					break;
				case T__546:
					{
					setState(493);
					match(T__546);
					 size = 8; shift = 8; 
					}
					break;
				case T__547:
					{
					setState(495);
					match(T__547);
					 size = 16; 
					}
					break;
				case T__548:
					{
					setState(497);
					match(T__548);
					 size = 32; 
					}
					break;
				case T__549:
					{
					setState(499);
					match(T__549);
					 size = 64; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 _localctx.op =  new AsmArgumentOperand((int) _localctx.n.n, size, shift); 
				setState(504);
				match(T__550);
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\u0230\u01ff\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\3\2\3\2\3\2\5\2H\n\2\3\2\5\2K\n\2\3\2\3\2\3\2\3\2\5\2Q\n\2"+
		"\3\2\5\2T\n\2\7\2V\n\2\f\2\16\2Y\13\2\5\2[\n\2\3\2\3\2\3\2\3\3\3\3\3\3"+
		"\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\5\4r\n\4"+
		"\3\5\3\5\3\5\3\5\3\6\3\6\3\6\3\7\3\7\3\7\3\7\3\7\3\7\5\7\u0081\n\7\5\7"+
		"\u0083\n\7\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\5\t\u0092"+
		"\n\t\5\t\u0094\n\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\5\t\u00a0\n"+
		"\t\5\t\u00a2\n\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\5\t\u00ae\n\t"+
		"\5\t\u00b0\n\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\5\t\u00bc\n\t\5"+
		"\t\u00be\n\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\5\t\u00ca\n\t\5\t"+
		"\u00cc\n\t\5\t\u00ce\n\t\3\n\3\n\3\n\3\n\3\13\3\13\3\13\3\13\3\f\3\f\3"+
		"\f\3\f\3\r\3\r\3\r\3\r\3\16\3\16\3\16\3\16\3\17\3\17\3\17\3\17\3\17\3"+
		"\17\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3"+
		"\20\3\20\3\20\3\20\3\20\5\20\u00fc\n\20\3\21\3\21\3\21\3\21\3\21\3\21"+
		"\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21"+
		"\3\21\3\21\3\21\3\21\5\21\u0116\n\21\3\22\3\22\3\22\3\22\3\22\3\22\3\22"+
		"\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22"+
		"\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\3\22\5\22\u0136\n\22\3\23\3\23"+
		"\3\23\3\23\3\23\3\23\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\5\24\u014a\n\24\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\5\25\u0158\n\25\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26"+
		"\3\26\3\26\3\26\3\26\5\26\u0166\n\26\3\27\3\27\3\27\3\27\3\27\3\27\3\27"+
		"\3\27\3\27\3\27\3\27\3\27\5\27\u0174\n\27\3\30\3\30\3\30\3\30\3\30\3\30"+
		"\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30"+
		"\3\30\3\30\3\30\3\30\5\30\u018e\n\30\3\31\3\31\3\31\3\31\3\31\5\31\u0195"+
		"\n\31\3\31\3\31\3\31\3\31\3\31\5\31\u019c\n\31\3\31\3\31\3\31\3\31\5\31"+
		"\u01a2\n\31\3\31\3\31\3\31\3\31\3\31\3\31\3\31\5\31\u01ab\n\31\5\31\u01ad"+
		"\n\31\3\31\5\31\u01b0\n\31\3\31\3\31\3\31\3\31\5\31\u01b6\n\31\3\31\3"+
		"\31\3\31\3\31\3\31\3\31\3\31\5\31\u01bf\n\31\5\31\u01c1\n\31\3\31\5\31"+
		"\u01c4\n\31\3\31\3\31\3\32\3\32\3\32\3\33\3\33\3\33\3\34\3\34\3\34\3\35"+
		"\3\35\3\35\3\36\3\36\3\36\3\37\3\37\3\37\3 \3 \3 \3 \3 \3 \5 \u01e0\n"+
		" \3!\3!\3!\3!\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\""+
		"\3\"\3\"\3\"\3\"\5\"\u01f8\n\"\3\"\3\"\3\"\5\"\u01fd\n\"\3\"\2\2#\2\4"+
		"\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@B\2#\3\2"+
		"\4\5\3\2\6\13\3\2\r\63\3\2\66Q\3\2RS\3\2TU\3\2VW\3\2XY\3\2Z[\3\2\\a\3"+
		"\2bi\3\2jr\3\2s{\3\2|\u00a4\3\2\u00a5\u00b9\3\2\u00ba\u00ea\3\2\u00eb"+
		"\u00f2\3\2\u00f3\u00f4\3\2\u00f5\u0125\3\2\u0126\u012d\3\2\u012e\u012f"+
		"\3\2\u0130\u0131\3\2\u0132\u0162\3\2\u0163\u016a\3\2\u016b\u016c\3\2\u016d"+
		"\u016e\3\2\u0170\u01a7\3\2\u01ab\u01c2\3\2\u01c3\u01da\3\2\u01db\u01f2"+
		"\3\2\u01f3\u020a\3\2\u020b\u021a\3\2\u021b\u0220\2\u022e\2D\3\2\2\2\4"+
		"_\3\2\2\2\6q\3\2\2\2\bs\3\2\2\2\nw\3\2\2\2\fz\3\2\2\2\16\u0084\3\2\2\2"+
		"\20\u00cd\3\2\2\2\22\u00cf\3\2\2\2\24\u00d3\3\2\2\2\26\u00d7\3\2\2\2\30"+
		"\u00db\3\2\2\2\32\u00df\3\2\2\2\34\u00e3\3\2\2\2\36\u00fb\3\2\2\2 \u0115"+
		"\3\2\2\2\"\u0135\3\2\2\2$\u0137\3\2\2\2&\u0149\3\2\2\2(\u0157\3\2\2\2"+
		"*\u0165\3\2\2\2,\u0173\3\2\2\2.\u018d\3\2\2\2\60\u018f\3\2\2\2\62\u01c7"+
		"\3\2\2\2\64\u01ca\3\2\2\2\66\u01cd\3\2\2\28\u01d0\3\2\2\2:\u01d3\3\2\2"+
		"\2<\u01d6\3\2\2\2>\u01df\3\2\2\2@\u01e1\3\2\2\2B\u01e5\3\2\2\2DZ\7\3\2"+
		"\2EG\5\4\3\2FH\7\4\2\2GF\3\2\2\2GH\3\2\2\2HK\3\2\2\2IK\b\2\1\2JE\3\2\2"+
		"\2JI\3\2\2\2KL\3\2\2\2LW\5\6\4\2MP\t\2\2\2NQ\5\4\3\2OQ\b\2\1\2PN\3\2\2"+
		"\2PO\3\2\2\2QS\3\2\2\2RT\5\6\4\2SR\3\2\2\2ST\3\2\2\2TV\3\2\2\2UM\3\2\2"+
		"\2VY\3\2\2\2WU\3\2\2\2WX\3\2\2\2X[\3\2\2\2YW\3\2\2\2ZJ\3\2\2\2Z[\3\2\2"+
		"\2[\\\3\2\2\2\\]\7\3\2\2]^\b\2\1\2^\3\3\2\2\2_`\t\3\2\2`a\b\3\1\2a\5\3"+
		"\2\2\2br\5\f\7\2cr\5\16\b\2dr\5\22\n\2er\5\24\13\2fr\5\26\f\2gr\5\30\r"+
		"\2hr\5\32\16\2ir\5\34\17\2jr\5\36\20\2kr\5 \21\2lr\5\"\22\2mr\5$\23\2"+
		"nr\5\20\t\2or\5\n\6\2pr\5\b\5\2qb\3\2\2\2qc\3\2\2\2qd\3\2\2\2qe\3\2\2"+
		"\2qf\3\2\2\2qg\3\2\2\2qh\3\2\2\2qi\3\2\2\2qj\3\2\2\2qk\3\2\2\2ql\3\2\2"+
		"\2qm\3\2\2\2qn\3\2\2\2qo\3\2\2\2qp\3\2\2\2r\7\3\2\2\2st\7\f\2\2tu\5@!"+
		"\2uv\b\5\1\2v\t\3\2\2\2wx\t\4\2\2xy\5,\27\2y\13\3\2\2\2z{\7\64\2\2{\u0082"+
		"\5> \2|}\7\65\2\2}\u0080\5> \2~\177\7\65\2\2\177\u0081\5> \2\u0080~\3"+
		"\2\2\2\u0080\u0081\3\2\2\2\u0081\u0083\3\2\2\2\u0082|\3\2\2\2\u0082\u0083"+
		"\3\2\2\2\u0083\r\3\2\2\2\u0084\u0085\t\5\2\2\u0085\u0086\b\b\1\2\u0086"+
		"\17\3\2\2\2\u0087\u0088\t\6\2\2\u0088\u0093\5&\24\2\u0089\u0094\b\t\1"+
		"\2\u008a\u008b\7\65\2\2\u008b\u0091\5&\24\2\u008c\u0092\b\t\1\2\u008d"+
		"\u008e\7\65\2\2\u008e\u008f\5&\24\2\u008f\u0090\b\t\1\2\u0090\u0092\3"+
		"\2\2\2\u0091\u008c\3\2\2\2\u0091\u008d\3\2\2\2\u0092\u0094\3\2\2\2\u0093"+
		"\u0089\3\2\2\2\u0093\u008a\3\2\2\2\u0094\u00ce\3\2\2\2\u0095\u0096\t\7"+
		"\2\2\u0096\u00a1\5(\25\2\u0097\u00a2\b\t\1\2\u0098\u0099\7\65\2\2\u0099"+
		"\u009f\5(\25\2\u009a\u00a0\b\t\1\2\u009b\u009c\7\65\2\2\u009c\u009d\5"+
		"(\25\2\u009d\u009e\b\t\1\2\u009e\u00a0\3\2\2\2\u009f\u009a\3\2\2\2\u009f"+
		"\u009b\3\2\2\2\u00a0\u00a2\3\2\2\2\u00a1\u0097\3\2\2\2\u00a1\u0098\3\2"+
		"\2\2\u00a2\u00ce\3\2\2\2\u00a3\u00a4\t\b\2\2\u00a4\u00af\5*\26\2\u00a5"+
		"\u00b0\b\t\1\2\u00a6\u00a7\7\65\2\2\u00a7\u00ad\5*\26\2\u00a8\u00ae\b"+
		"\t\1\2\u00a9\u00aa\7\65\2\2\u00aa\u00ab\5*\26\2\u00ab\u00ac\b\t\1\2\u00ac"+
		"\u00ae\3\2\2\2\u00ad\u00a8\3\2\2\2\u00ad\u00a9\3\2\2\2\u00ae\u00b0\3\2"+
		"\2\2\u00af\u00a5\3\2\2\2\u00af\u00a6\3\2\2\2\u00b0\u00ce\3\2\2\2\u00b1"+
		"\u00b2\t\t\2\2\u00b2\u00bd\5,\27\2\u00b3\u00be\b\t\1\2\u00b4\u00b5\7\65"+
		"\2\2\u00b5\u00bb\5,\27\2\u00b6\u00bc\b\t\1\2\u00b7\u00b8\7\65\2\2\u00b8"+
		"\u00b9\5,\27\2\u00b9\u00ba\b\t\1\2\u00ba\u00bc\3\2\2\2\u00bb\u00b6\3\2"+
		"\2\2\u00bb\u00b7\3\2\2\2\u00bc\u00be\3\2\2\2\u00bd\u00b3\3\2\2\2\u00bd"+
		"\u00b4\3\2\2\2\u00be\u00ce\3\2\2\2\u00bf\u00c0\t\n\2\2\u00c0\u00cb\5."+
		"\30\2\u00c1\u00cc\b\t\1\2\u00c2\u00c3\7\65\2\2\u00c3\u00c9\5.\30\2\u00c4"+
		"\u00ca\b\t\1\2\u00c5\u00c6\7\65\2\2\u00c6\u00c7\5.\30\2\u00c7\u00c8\b"+
		"\t\1\2\u00c8\u00ca\3\2\2\2\u00c9\u00c4\3\2\2\2\u00c9\u00c5\3\2\2\2\u00ca"+
		"\u00cc\3\2\2\2\u00cb\u00c1\3\2\2\2\u00cb\u00c2\3\2\2\2\u00cc\u00ce\3\2"+
		"\2\2\u00cd\u0087\3\2\2\2\u00cd\u0095\3\2\2\2\u00cd\u00a3\3\2\2\2\u00cd"+
		"\u00b1\3\2\2\2\u00cd\u00bf\3\2\2\2\u00ce\21\3\2\2\2\u00cf\u00d0\t\13\2"+
		"\2\u00d0\u00d1\5&\24\2\u00d1\u00d2\b\n\1\2\u00d2\23\3\2\2\2\u00d3\u00d4"+
		"\t\f\2\2\u00d4\u00d5\5(\25\2\u00d5\u00d6\b\13\1\2\u00d6\25\3\2\2\2\u00d7"+
		"\u00d8\t\r\2\2\u00d8\u00d9\5*\26\2\u00d9\u00da\b\f\1\2\u00da\27\3\2\2"+
		"\2\u00db\u00dc\t\16\2\2\u00dc\u00dd\5,\27\2\u00dd\u00de\b\r\1\2\u00de"+
		"\31\3\2\2\2\u00df\u00e0\t\17\2\2\u00e0\u00e1\5.\30\2\u00e1\u00e2\b\16"+
		"\1\2\u00e2\33\3\2\2\2\u00e3\u00e4\t\20\2\2\u00e4\u00e5\5&\24\2\u00e5\u00e6"+
		"\7\65\2\2\u00e6\u00e7\5&\24\2\u00e7\u00e8\b\17\1\2\u00e8\35\3\2\2\2\u00e9"+
		"\u00ea\t\21\2\2\u00ea\u00eb\5(\25\2\u00eb\u00ec\7\65\2\2\u00ec\u00ed\5"+
		"(\25\2\u00ed\u00ee\b\20\1\2\u00ee\u00fc\3\2\2\2\u00ef\u00f0\t\22\2\2\u00f0"+
		"\u00f1\5&\24\2\u00f1\u00f2\7\65\2\2\u00f2\u00f3\5(\25\2\u00f3\u00f4\b"+
		"\20\1\2\u00f4\u00fc\3\2\2\2\u00f5\u00f6\t\23\2\2\u00f6\u00f7\5&\24\2\u00f7"+
		"\u00f8\7\65\2\2\u00f8\u00f9\5(\25\2\u00f9\u00fa\b\20\1\2\u00fa\u00fc\3"+
		"\2\2\2\u00fb\u00e9\3\2\2\2\u00fb\u00ef\3\2\2\2\u00fb\u00f5\3\2\2\2\u00fc"+
		"\37\3\2\2\2\u00fd\u00fe\t\24\2\2\u00fe\u00ff\5*\26\2\u00ff\u0100\7\65"+
		"\2\2\u0100\u0101\5*\26\2\u0101\u0102\b\21\1\2\u0102\u0116\3\2\2\2\u0103"+
		"\u0104\t\25\2\2\u0104\u0105\5&\24\2\u0105\u0106\7\65\2\2\u0106\u0107\5"+
		"*\26\2\u0107\u0108\b\21\1\2\u0108\u0116\3\2\2\2\u0109\u010a\t\26\2\2\u010a"+
		"\u010b\5&\24\2\u010b\u010c\7\65\2\2\u010c\u010d\5*\26\2\u010d\u010e\b"+
		"\21\1\2\u010e\u0116\3\2\2\2\u010f\u0110\t\27\2\2\u0110\u0111\5(\25\2\u0111"+
		"\u0112\7\65\2\2\u0112\u0113\5*\26\2\u0113\u0114\b\21\1\2\u0114\u0116\3"+
		"\2\2\2\u0115\u00fd\3\2\2\2\u0115\u0103\3\2\2\2\u0115\u0109\3\2\2\2\u0115"+
		"\u010f\3\2\2\2\u0116!\3\2\2\2\u0117\u0118\t\30\2\2\u0118\u0119\5,\27\2"+
		"\u0119\u011a\7\65\2\2\u011a\u011b\5,\27\2\u011b\u011c\b\22\1\2\u011c\u0136"+
		"\3\2\2\2\u011d\u011e\t\31\2\2\u011e\u011f\5&\24\2\u011f\u0120\7\65\2\2"+
		"\u0120\u0121\5,\27\2\u0121\u0122\b\22\1\2\u0122\u0136\3\2\2\2\u0123\u0124"+
		"\t\32\2\2\u0124\u0125\5&\24\2\u0125\u0126\7\65\2\2\u0126\u0127\5,\27\2"+
		"\u0127\u0128\b\22\1\2\u0128\u0136\3\2\2\2\u0129\u012a\t\33\2\2\u012a\u012b"+
		"\5(\25\2\u012b\u012c\7\65\2\2\u012c\u012d\5,\27\2\u012d\u012e\b\22\1\2"+
		"\u012e\u0136\3\2\2\2\u012f\u0130\7\u016f\2\2\u0130\u0131\5*\26\2\u0131"+
		"\u0132\7\65\2\2\u0132\u0133\5,\27\2\u0133\u0134\b\22\1\2\u0134\u0136\3"+
		"\2\2\2\u0135\u0117\3\2\2\2\u0135\u011d\3\2\2\2\u0135\u0123\3\2\2\2\u0135"+
		"\u0129\3\2\2\2\u0135\u012f\3\2\2\2\u0136#\3\2\2\2\u0137\u0138\t\34\2\2"+
		"\u0138\u0139\5.\30\2\u0139\u013a\7\65\2\2\u013a\u013b\5.\30\2\u013b\u013c"+
		"\b\23\1\2\u013c%\3\2\2\2\u013d\u013e\5\62\32\2\u013e\u013f\b\24\1\2\u013f"+
		"\u014a\3\2\2\2\u0140\u0141\5\60\31\2\u0141\u0142\b\24\1\2\u0142\u014a"+
		"\3\2\2\2\u0143\u0144\5@!\2\u0144\u0145\b\24\1\2\u0145\u014a\3\2\2\2\u0146"+
		"\u0147\5B\"\2\u0147\u0148\b\24\1\2\u0148\u014a\3\2\2\2\u0149\u013d\3\2"+
		"\2\2\u0149\u0140\3\2\2\2\u0149\u0143\3\2\2\2\u0149\u0146\3\2\2\2\u014a"+
		"\'\3\2\2\2\u014b\u014c\5\64\33\2\u014c\u014d\b\25\1\2\u014d\u0158\3\2"+
		"\2\2\u014e\u014f\5\60\31\2\u014f\u0150\b\25\1\2\u0150\u0158\3\2\2\2\u0151"+
		"\u0152\5@!\2\u0152\u0153\b\25\1\2\u0153\u0158\3\2\2\2\u0154\u0155\5B\""+
		"\2\u0155\u0156\b\25\1\2\u0156\u0158\3\2\2\2\u0157\u014b\3\2\2\2\u0157"+
		"\u014e\3\2\2\2\u0157\u0151\3\2\2\2\u0157\u0154\3\2\2\2\u0158)\3\2\2\2"+
		"\u0159\u015a\5\66\34\2\u015a\u015b\b\26\1\2\u015b\u0166\3\2\2\2\u015c"+
		"\u015d\5\60\31\2\u015d\u015e\b\26\1\2\u015e\u0166\3\2\2\2\u015f\u0160"+
		"\5@!\2\u0160\u0161\b\26\1\2\u0161\u0166\3\2\2\2\u0162\u0163\5B\"\2\u0163"+
		"\u0164\b\26\1\2\u0164\u0166\3\2\2\2\u0165\u0159\3\2\2\2\u0165\u015c\3"+
		"\2\2\2\u0165\u015f\3\2\2\2\u0165\u0162\3\2\2\2\u0166+\3\2\2\2\u0167\u0168"+
		"\58\35\2\u0168\u0169\b\27\1\2\u0169\u0174\3\2\2\2\u016a\u016b\5\60\31"+
		"\2\u016b\u016c\b\27\1\2\u016c\u0174\3\2\2\2\u016d\u016e\5@!\2\u016e\u016f"+
		"\b\27\1\2\u016f\u0174\3\2\2\2\u0170\u0171\5B\"\2\u0171\u0172\b\27\1\2"+
		"\u0172\u0174\3\2\2\2\u0173\u0167\3\2\2\2\u0173\u016a\3\2\2\2\u0173\u016d"+
		"\3\2\2\2\u0173\u0170\3\2\2\2\u0174-\3\2\2\2\u0175\u0176\5\62\32\2\u0176"+
		"\u0177\b\30\1\2\u0177\u018e\3\2\2\2\u0178\u0179\5\64\33\2\u0179\u017a"+
		"\b\30\1\2\u017a\u018e\3\2\2\2\u017b\u017c\5\66\34\2\u017c\u017d\b\30\1"+
		"\2\u017d\u018e\3\2\2\2\u017e\u017f\58\35\2\u017f\u0180\b\30\1\2\u0180"+
		"\u018e\3\2\2\2\u0181\u0182\5:\36\2\u0182\u0183\b\30\1\2\u0183\u018e\3"+
		"\2\2\2\u0184\u0185\5\60\31\2\u0185\u0186\b\30\1\2\u0186\u018e\3\2\2\2"+
		"\u0187\u0188\5@!\2\u0188\u0189\b\30\1\2\u0189\u018e\3\2\2\2\u018a\u018b"+
		"\5B\"\2\u018b\u018c\b\30\1\2\u018c\u018e\3\2\2\2\u018d\u0175\3\2\2\2\u018d"+
		"\u0178\3\2\2\2\u018d\u017b\3\2\2\2\u018d\u017e\3\2\2\2\u018d\u0181\3\2"+
		"\2\2\u018d\u0184\3\2\2\2\u018d\u0187\3\2\2\2\u018d\u018a\3\2\2\2\u018e"+
		"/\3\2\2\2\u018f\u0194\b\31\1\2\u0190\u0191\5<\37\2\u0191\u0192\b\31\1"+
		"\2\u0192\u0193\7\u01a8\2\2\u0193\u0195\3\2\2\2\u0194\u0190\3\2\2\2\u0194"+
		"\u0195\3\2\2\2\u0195\u01c3\3\2\2\2\u0196\u0197\7\u022a\2\2\u0197\u019c"+
		"\b\31\1\2\u0198\u0199\5> \2\u0199\u019a\b\31\1\2\u019a\u019c\3\2\2\2\u019b"+
		"\u0196\3\2\2\2\u019b\u0198\3\2\2\2\u019c\u01af\3\2\2\2\u019d\u01a1\7\u01a9"+
		"\2\2\u019e\u019f\5.\30\2\u019f\u01a0\b\31\1\2\u01a0\u01a2\3\2\2\2\u01a1"+
		"\u019e\3\2\2\2\u01a1\u01a2\3\2\2\2\u01a2\u01ac\3\2\2\2\u01a3\u01a4\7\65"+
		"\2\2\u01a4\u01a5\5.\30\2\u01a5\u01aa\b\31\1\2\u01a6\u01a7\7\65\2\2\u01a7"+
		"\u01a8\5> \2\u01a8\u01a9\b\31\1\2\u01a9\u01ab\3\2\2\2\u01aa\u01a6\3\2"+
		"\2\2\u01aa\u01ab\3\2\2\2\u01ab\u01ad\3\2\2\2\u01ac\u01a3\3\2\2\2\u01ac"+
		"\u01ad\3\2\2\2\u01ad\u01ae\3\2\2\2\u01ae\u01b0\7\u01aa\2\2\u01af\u019d"+
		"\3\2\2\2\u01af\u01b0\3\2\2\2\u01b0\u01c4\3\2\2\2\u01b1\u01b5\7\u01a9\2"+
		"\2\u01b2\u01b3\5.\30\2\u01b3\u01b4\b\31\1\2\u01b4\u01b6\3\2\2\2\u01b5"+
		"\u01b2\3\2\2\2\u01b5\u01b6\3\2\2\2\u01b6\u01c0\3\2\2\2\u01b7\u01b8\7\65"+
		"\2\2\u01b8\u01b9\5.\30\2\u01b9\u01be\b\31\1\2\u01ba\u01bb\7\65\2\2\u01bb"+
		"\u01bc\5> \2\u01bc\u01bd\b\31\1\2\u01bd\u01bf\3\2\2\2\u01be\u01ba\3\2"+
		"\2\2\u01be\u01bf\3\2\2\2\u01bf\u01c1\3\2\2\2\u01c0\u01b7\3\2\2\2\u01c0"+
		"\u01c1\3\2\2\2\u01c1\u01c2\3\2\2\2\u01c2\u01c4\7\u01aa\2\2\u01c3\u019b"+
		"\3\2\2\2\u01c3\u01b1\3\2\2\2\u01c4\u01c5\3\2\2\2\u01c5\u01c6\b\31\1\2"+
		"\u01c6\61\3\2\2\2\u01c7\u01c8\t\35\2\2\u01c8\u01c9\b\32\1\2\u01c9\63\3"+
		"\2\2\2\u01ca\u01cb\t\36\2\2\u01cb\u01cc\b\33\1\2\u01cc\65\3\2\2\2\u01cd"+
		"\u01ce\t\37\2\2\u01ce\u01cf\b\34\1\2\u01cf\67\3\2\2\2\u01d0\u01d1\t \2"+
		"\2\u01d1\u01d2\b\35\1\2\u01d29\3\2\2\2\u01d3\u01d4\t!\2\2\u01d4\u01d5"+
		"\b\36\1\2\u01d5;\3\2\2\2\u01d6\u01d7\t\"\2\2\u01d7\u01d8\b\37\1\2\u01d8"+
		"=\3\2\2\2\u01d9\u01da\7\u022d\2\2\u01da\u01e0\b \1\2\u01db\u01dc\7\u022b"+
		"\2\2\u01dc\u01e0\b \1\2\u01dd\u01de\7\u022c\2\2\u01de\u01e0\b \1\2\u01df"+
		"\u01d9\3\2\2\2\u01df\u01db\3\2\2\2\u01df\u01dd\3\2\2\2\u01e0?\3\2\2\2"+
		"\u01e1\u01e2\7\u0221\2\2\u01e2\u01e3\5> \2\u01e3\u01e4\b!\1\2\u01e4A\3"+
		"\2\2\2\u01e5\u01fc\7\u0222\2\2\u01e6\u01e7\5> \2\u01e7\u01e8\b\"\1\2\u01e8"+
		"\u01fd\3\2\2\2\u01e9\u01ea\7\u0223\2\2\u01ea\u01eb\5> \2\u01eb\u01ec\7"+
		"\u01a8\2\2\u01ec\u01f7\b\"\1\2\u01ed\u01ee\7\u0224\2\2\u01ee\u01f8\b\""+
		"\1\2\u01ef\u01f0\7\u0225\2\2\u01f0\u01f8\b\"\1\2\u01f1\u01f2\7\u0226\2"+
		"\2\u01f2\u01f8\b\"\1\2\u01f3\u01f4\7\u0227\2\2\u01f4\u01f8\b\"\1\2\u01f5"+
		"\u01f6\7\u0228\2\2\u01f6\u01f8\b\"\1\2\u01f7\u01ed\3\2\2\2\u01f7\u01ef"+
		"\3\2\2\2\u01f7\u01f1\3\2\2\2\u01f7\u01f3\3\2\2\2\u01f7\u01f5\3\2\2\2\u01f8"+
		"\u01f9\3\2\2\2\u01f9\u01fa\b\"\1\2\u01fa\u01fb\7\u0229\2\2\u01fb\u01fd"+
		"\3\2\2\2\u01fc\u01e6\3\2\2\2\u01fc\u01e9\3\2\2\2\u01fdC\3\2\2\2+GJPSW"+
		"Zq\u0080\u0082\u0091\u0093\u009f\u00a1\u00ad\u00af\u00bb\u00bd\u00c9\u00cb"+
		"\u00cd\u00fb\u0115\u0135\u0149\u0157\u0165\u0173\u018d\u0194\u019b\u01a1"+
		"\u01aa\u01ac\u01af\u01b5\u01be\u01c0\u01c3\u01df\u01f7\u01fc";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
