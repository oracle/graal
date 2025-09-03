/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInlineAssemblyRootNode;

import org.graalvm.shadowed.org.antlr.v4.runtime.atn.*;
import org.graalvm.shadowed.org.antlr.v4.runtime.dfa.DFA;
import org.graalvm.shadowed.org.antlr.v4.runtime.*;
import org.graalvm.shadowed.org.antlr.v4.runtime.misc.*;
import org.graalvm.shadowed.org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "this-escape"})
public class InlineAssemblyParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

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
		T__551=552, T__552=553, IDENT=554, BIN_NUMBER=555, HEX_NUMBER=556, NUMBER=557, 
		WS=558, COMMENT=559, LINE_COMMENT=560;
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
			null, "'\"'", "';'", "'\\n'", "'rep'", "'repz'", "'repe'", "'repne'", 
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
			"'seto'", "'setp'", "'setpe'", "'setpo'", "'sets'", "'setz'", "'fstcw'", 
			"'push'", "'pop'", "'cmpxchg8b'", "'cmpxchg16b'", "'fnstcw'", "'movb'", 
			"'xaddb'", "'xchgb'", "'adcb'", "'addb'", "'cmpb'", "'sbbb'", "'subb'", 
			"'andb'", "'orb'", "'xorb'", "'rclb'", "'rcrb'", "'rolb'", "'rorb'", 
			"'salb'", "'sarb'", "'shlb'", "'shrb'", "'testb'", "'cmpxchgb'", "'cmovaw'", 
			"'cmovaew'", "'cmovbw'", "'cmovbew'", "'cmovcw'", "'cmovew'", "'cmovgw'", 
			"'cmovgew'", "'cmovlw'", "'cmovlew'", "'cmovnaw'", "'cmovnaew'", "'cmovnbw'", 
			"'cmovnbew'", "'cmovncw'", "'cmovnew'", "'cmovngw'", "'cmovngew'", "'cmovnlw'", 
			"'cmovnlew'", "'cmovnow'", "'cmovnpw'", "'cmovnsw'", "'cmovnzw'", "'cmovow'", 
			"'cmovpw'", "'cmovpew'", "'cmovpow'", "'cmovsw'", "'cmovzw'", "'cmpxchgw'", 
			"'movw'", "'xaddw'", "'xchgw'", "'adcw'", "'addw'", "'cmpw'", "'sbbw'", 
			"'subw'", "'andw'", "'orw'", "'xorw'", "'testw'", "'bsfw'", "'bsrw'", 
			"'btw'", "'btcw'", "'btrw'", "'btsw'", "'rclw'", "'rcrw'", "'rolw'", 
			"'rorw'", "'salw'", "'sarw'", "'shlw'", "'shrw'", "'movsbw'", "'movzbw'", 
			"'cmoval'", "'cmovael'", "'cmovbl'", "'cmovbel'", "'cmovcl'", "'cmovel'", 
			"'cmovgl'", "'cmovgel'", "'cmovll'", "'cmovlel'", "'cmovnal'", "'cmovnael'", 
			"'cmovnbl'", "'cmovnbel'", "'cmovncl'", "'cmovnel'", "'cmovngl'", "'cmovngel'", 
			"'cmovnll'", "'cmovnlel'", "'cmovnol'", "'cmovnpl'", "'cmovnsl'", "'cmovnzl'", 
			"'cmovol'", "'cmovpl'", "'cmovpel'", "'cmovpol'", "'cmovsl'", "'cmovzl'", 
			"'cmpxchgl'", "'movl'", "'xaddl'", "'xchgl'", "'adcl'", "'addl'", "'cmpl'", 
			"'sbbl'", "'subl'", "'andl'", "'orl'", "'xorl'", "'testl'", "'bsfl'", 
			"'bsrl'", "'btl'", "'btcl'", "'btrl'", "'btsl'", "'rcll'", "'rcrl'", 
			"'roll'", "'rorl'", "'sall'", "'sarl'", "'shll'", "'shrl'", "'movsbl'", 
			"'movswl'", "'movzbl'", "'movzwl'", "'cmovaq'", "'cmovaeq'", "'cmovbq'", 
			"'cmovbeq'", "'cmovcq'", "'cmoveq'", "'cmovgq'", "'cmovgeq'", "'cmovlq'", 
			"'cmovleq'", "'cmovnaq'", "'cmovnaeq'", "'cmovnbq'", "'cmovnbeq'", "'cmovncq'", 
			"'cmovneq'", "'cmovngq'", "'cmovngeq'", "'cmovnlq'", "'cmovnleq'", "'cmovnoq'", 
			"'cmovnpq'", "'cmovnsq'", "'cmovnzq'", "'cmovoq'", "'cmovpq'", "'cmovpeq'", 
			"'cmovpoq'", "'cmovsq'", "'cmovzq'", "'cmpxchgq'", "'movq'", "'xaddq'", 
			"'xchgq'", "'adcq'", "'addq'", "'cmpq'", "'sbbq'", "'subq'", "'andq'", 
			"'orq'", "'xorq'", "'testq'", "'bsfq'", "'bsrq'", "'btq'", "'btcq'", 
			"'btrq'", "'btsq'", "'rclq'", "'rcrq'", "'rolq'", "'rorq'", "'salq'", 
			"'sarq'", "'shlq'", "'shrq'", "'movsbq'", "'movzbq'", "'movswq'", "'movzwq'", 
			"'movslq'", "'cmova'", "'cmovae'", "'cmovb'", "'cmovbe'", "'cmovc'", 
			"'cmove'", "'cmovg'", "'cmovge'", "'cmovl'", "'cmovle'", "'cmovna'", 
			"'cmovnae'", "'cmovnb'", "'cmovnbe'", "'cmovnc'", "'cmovne'", "'cmovng'", 
			"'cmovnge'", "'cmovnl'", "'cmovnle'", "'cmovno'", "'cmovnp'", "'cmovns'", 
			"'cmovnz'", "'cmovo'", "'cmovp'", "'cmovpe'", "'cmovpo'", "'cmovs'", 
			"'cmovz'", "'cmpxchg'", "'pmovmskb'", "'mov'", "'xadd'", "'xchg'", "'adc'", 
			"'add'", "'cmp'", "'div'", "'mul'", "'sbb'", "'sub'", "'and'", "'or'", 
			"'xor'", "'rcl'", "'rcr'", "'rol'", "'ror'", "'sal'", "'sar'", "'shl'", 
			"'shr'", "'lea'", "'bsf'", "'bsr'", "':'", "'('", "')'", "'%ah'", "'%al'", 
			"'%bh'", "'%bl'", "'%ch'", "'%cl'", "'%dh'", "'%dl'", "'%r0l'", "'%r1l'", 
			"'%r2l'", "'%r3l'", "'%r4l'", "'%r5l'", "'%r6l'", "'%r7l'", "'%r8l'", 
			"'%r9l'", "'%r10l'", "'%r11l'", "'%r12l'", "'%r13l'", "'%r14l'", "'%r15l'", 
			"'%ax'", "'%bx'", "'%cx'", "'%dx'", "'%si'", "'%di'", "'%bp'", "'%sp'", 
			"'%r0w'", "'%r1w'", "'%r2w'", "'%r3w'", "'%r4w'", "'%r5w'", "'%r6w'", 
			"'%r7w'", "'%r8w'", "'%r9w'", "'%r10w'", "'%r11w'", "'%r12w'", "'%r13w'", 
			"'%r14w'", "'%r15w'", "'%eax'", "'%ebx'", "'%ecx'", "'%edx'", "'%esi'", 
			"'%edi'", "'%ebp'", "'%esp'", "'%r0d'", "'%r1d'", "'%r2d'", "'%r3d'", 
			"'%r4d'", "'%r5d'", "'%r6d'", "'%r7d'", "'%r8d'", "'%r9d'", "'%r10d'", 
			"'%r11d'", "'%r12d'", "'%r13d'", "'%r14d'", "'%r15d'", "'%rax'", "'%rbx'", 
			"'%rcx'", "'%rdx'", "'%rsp'", "'%rbp'", "'%rsi'", "'%rdi'", "'%r0'", 
			"'%r1'", "'%r2'", "'%r3'", "'%r4'", "'%r5'", "'%r6'", "'%r7'", "'%r8'", 
			"'%r9'", "'%r10'", "'%r11'", "'%r12'", "'%r13'", "'%r14'", "'%r15'", 
			"'%xmm0'", "'%xmm1'", "'%xmm2'", "'%xmm3'", "'%xmm4'", "'%xmm5'", "'%xmm6'", 
			"'%xmm7'", "'%xmm8'", "'%xmm9'", "'%xmm10'", "'%xmm11'", "'%xmm12'", 
			"'%xmm13'", "'%xmm14'", "'%xmm15'", "'%cs'", "'%ds'", "'%es'", "'%fs'", 
			"'%gs'", "'%ss'", "'$$'", "'$'", "'{'", "'b'", "'h'", "'w'", "'k'", "'q'", 
			"'}'"
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
			null, null, "IDENT", "BIN_NUMBER", "HEX_NUMBER", "NUMBER", "WS", "COMMENT", 
			"LINE_COMMENT"
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

	public static LLVMInlineAssemblyRootNode parseInlineAssembly(String asmSnippet, AsmFactory factory) {
	    InlineAssemblyLexer lexer = new InlineAssemblyLexer(CharStreams.fromString(asmSnippet));
	    InlineAssemblyParser parser = new InlineAssemblyParser(new CommonTokenStream(lexer));
	    lexer.removeErrorListeners();
	    parser.removeErrorListeners();
	    BailoutErrorListener listener = new BailoutErrorListener(asmSnippet);
	    lexer.addErrorListener(listener);
	    parser.addErrorListener(listener);
	    parser.snippet = asmSnippet;
	    parser.factory = factory;
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(91);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -2251799813685264L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & -1L) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & -1L) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & -1L) != 0) || ((((_la - 256)) & ~0x3f) == 0 && ((1L << (_la - 256)) & -1L) != 0) || ((((_la - 320)) & ~0x3f) == 0 && ((1L << (_la - 320)) & -1L) != 0) || ((((_la - 384)) & ~0x3f) == 0 && ((1L << (_la - 384)) & 1099511627775L) != 0)) {
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
				case T__421:
				case T__422:
					{
					 factory.setPrefix(null); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(74);
				assembly_instruction();
				setState(88);
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
					setState(81);
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
						setState(78);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
						case 1:
							{
							setState(77);
							match(T__1);
							}
							break;
						}
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
					case T__421:
					case T__422:
						{
						 factory.setPrefix(null); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(84);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -2251799813686272L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & -1L) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & -1L) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & -1L) != 0) || ((((_la - 256)) & ~0x3f) == 0 && ((1L << (_la - 256)) & -1L) != 0) || ((((_la - 320)) & ~0x3f) == 0 && ((1L << (_la - 320)) & -1L) != 0) || ((((_la - 384)) & ~0x3f) == 0 && ((1L << (_la - 384)) & 1099511627775L) != 0)) {
						{
						setState(83);
						assembly_instruction();
						}
					}

					}
					}
					setState(90);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(93);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(96);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1008L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(114);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__49:
				{
				setState(99);
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
				setState(100);
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
				setState(101);
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
				setState(102);
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
				setState(103);
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
				setState(104);
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
			case T__162:
			case T__163:
				{
				setState(105);
				unary_op();
				}
				break;
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
				{
				setState(106);
				binary_op8();
				}
				break;
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
				{
				setState(107);
				binary_op16();
				}
				break;
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
				{
				setState(108);
				binary_op32();
				}
				break;
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
				{
				setState(109);
				binary_op64();
				}
				break;
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
			case T__421:
			case T__422:
				{
				setState(110);
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
				setState(111);
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
				setState(112);
				jump();
				}
				break;
			case T__9:
				{
				setState(113);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(116);
			match(T__9);
			setState(117);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(120);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1125899906840576L) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(121);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(123);
			_localctx.op = match(T__49);
			setState(124);
			_localctx.low_order_bits = number();
			setState(131);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__50) {
				{
				setState(125);
				match(T__50);
				setState(126);
				_localctx.padding_byte = number();
				setState(129);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__50) {
					{
					setState(127);
					match(T__50);
					setState(128);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(133);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 52)) & ~0x3f) == 0 && ((1L << (_la - 52)) & 268435455L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(206);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__79:
			case T__80:
				{
				setState(136);
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
				setState(137);
				_localctx.a1 = operand8();
				setState(148);
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
					setState(139);
					match(T__50);
					setState(140);
					_localctx.b1 = operand8();
					setState(146);
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
						setState(142);
						match(T__50);
						setState(143);
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
				setState(150);
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
				setState(151);
				_localctx.a2 = operand16();
				setState(162);
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
					setState(153);
					match(T__50);
					setState(154);
					_localctx.b2 = operand16();
					setState(160);
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
						setState(156);
						match(T__50);
						setState(157);
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
				setState(164);
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
				setState(165);
				_localctx.a3 = operand32();
				setState(176);
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
					setState(167);
					match(T__50);
					setState(168);
					_localctx.b3 = operand32();
					setState(174);
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
						setState(170);
						match(T__50);
						setState(171);
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
				setState(178);
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
				setState(179);
				_localctx.a4 = operand64();
				setState(190);
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
					setState(181);
					match(T__50);
					setState(182);
					_localctx.b4 = operand64();
					setState(188);
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
						setState(184);
						match(T__50);
						setState(185);
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
				setState(192);
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
				setState(193);
				_localctx.a5 = operand();
				setState(204);
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
					setState(195);
					match(T__50);
					setState(196);
					_localctx.b5 = operand();
					setState(202);
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
						setState(198);
						match(T__50);
						setState(199);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(208);
			((Unary_op8Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 90)) & ~0x3f) == 0 && ((1L << (_la - 90)) & 63L) != 0)) ) {
				((Unary_op8Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(209);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(212);
			((Unary_op16Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 96)) & ~0x3f) == 0 && ((1L << (_la - 96)) & 255L) != 0)) ) {
				((Unary_op16Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(213);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(216);
			((Unary_op32Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 104)) & ~0x3f) == 0 && ((1L << (_la - 104)) & 511L) != 0)) ) {
				((Unary_op32Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(217);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(220);
			((Unary_op64Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 113)) & ~0x3f) == 0 && ((1L << (_la - 113)) & 511L) != 0)) ) {
				((Unary_op64Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(221);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(224);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 122)) & ~0x3f) == 0 && ((1L << (_la - 122)) & 8796093022207L) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(225);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(228);
			((Binary_op8Context)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 165)) & ~0x3f) == 0 && ((1L << (_la - 165)) & 2097151L) != 0)) ) {
				((Binary_op8Context)_localctx).op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(229);
			((Binary_op8Context)_localctx).a = operand8();
			setState(230);
			match(T__50);
			setState(231);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(252);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
				enterOuterAlt(_localctx, 1);
				{
				setState(234);
				((Binary_op16Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 186)) & ~0x3f) == 0 && ((1L << (_la - 186)) & 562949953421311L) != 0)) ) {
					((Binary_op16Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(235);
				((Binary_op16Context)_localctx).a1 = operand16();
				setState(236);
				match(T__50);
				setState(237);
				((Binary_op16Context)_localctx).b1 = operand16();
				 factory.createBinaryOperation(((Binary_op16Context)_localctx).op1.getText(), ((Binary_op16Context)_localctx).a1.op, ((Binary_op16Context)_localctx).b1.op); 
				}
				break;
			case T__234:
			case T__235:
			case T__236:
			case T__237:
			case T__238:
			case T__239:
			case T__240:
			case T__241:
				enterOuterAlt(_localctx, 2);
				{
				setState(240);
				((Binary_op16Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 235)) & ~0x3f) == 0 && ((1L << (_la - 235)) & 255L) != 0)) ) {
					((Binary_op16Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(241);
				((Binary_op16Context)_localctx).a2 = operand8();
				setState(242);
				match(T__50);
				setState(243);
				((Binary_op16Context)_localctx).b2 = operand16();
				 factory.createBinaryOperation(((Binary_op16Context)_localctx).op2.getText(), ((Binary_op16Context)_localctx).a2.op, ((Binary_op16Context)_localctx).b2.op); 
				}
				break;
			case T__242:
			case T__243:
				enterOuterAlt(_localctx, 3);
				{
				setState(246);
				((Binary_op16Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__242 || _la==T__243) ) {
					((Binary_op16Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(247);
				((Binary_op16Context)_localctx).a3 = operand8();
				setState(248);
				match(T__50);
				setState(249);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(278);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
				enterOuterAlt(_localctx, 1);
				{
				setState(254);
				((Binary_op32Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 245)) & ~0x3f) == 0 && ((1L << (_la - 245)) & 562949953421311L) != 0)) ) {
					((Binary_op32Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(255);
				((Binary_op32Context)_localctx).a1 = operand32();
				setState(256);
				match(T__50);
				setState(257);
				((Binary_op32Context)_localctx).b1 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op1.getText(), ((Binary_op32Context)_localctx).a1.op, ((Binary_op32Context)_localctx).b1.op); 
				}
				break;
			case T__293:
			case T__294:
			case T__295:
			case T__296:
			case T__297:
			case T__298:
			case T__299:
			case T__300:
				enterOuterAlt(_localctx, 2);
				{
				setState(260);
				((Binary_op32Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 294)) & ~0x3f) == 0 && ((1L << (_la - 294)) & 255L) != 0)) ) {
					((Binary_op32Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(261);
				((Binary_op32Context)_localctx).a2 = operand8();
				setState(262);
				match(T__50);
				setState(263);
				((Binary_op32Context)_localctx).b2 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op2.getText(), ((Binary_op32Context)_localctx).a2.op, ((Binary_op32Context)_localctx).b2.op); 
				}
				break;
			case T__301:
			case T__302:
				enterOuterAlt(_localctx, 3);
				{
				setState(266);
				((Binary_op32Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__301 || _la==T__302) ) {
					((Binary_op32Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(267);
				((Binary_op32Context)_localctx).a3 = operand8();
				setState(268);
				match(T__50);
				setState(269);
				((Binary_op32Context)_localctx).b3 = operand32();
				 factory.createBinaryOperation(((Binary_op32Context)_localctx).op3.getText(), ((Binary_op32Context)_localctx).a3.op, ((Binary_op32Context)_localctx).b3.op); 
				}
				break;
			case T__303:
			case T__304:
				enterOuterAlt(_localctx, 4);
				{
				setState(272);
				((Binary_op32Context)_localctx).op4 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__303 || _la==T__304) ) {
					((Binary_op32Context)_localctx).op4 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(273);
				((Binary_op32Context)_localctx).a4 = operand16();
				setState(274);
				match(T__50);
				setState(275);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(310);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
				enterOuterAlt(_localctx, 1);
				{
				setState(280);
				((Binary_op64Context)_localctx).op1 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 306)) & ~0x3f) == 0 && ((1L << (_la - 306)) & 562949953421311L) != 0)) ) {
					((Binary_op64Context)_localctx).op1 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(281);
				((Binary_op64Context)_localctx).a1 = operand64();
				setState(282);
				match(T__50);
				setState(283);
				((Binary_op64Context)_localctx).b1 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op1.getText(), ((Binary_op64Context)_localctx).a1.op, ((Binary_op64Context)_localctx).b1.op); 
				}
				break;
			case T__354:
			case T__355:
			case T__356:
			case T__357:
			case T__358:
			case T__359:
			case T__360:
			case T__361:
				enterOuterAlt(_localctx, 2);
				{
				setState(286);
				((Binary_op64Context)_localctx).op2 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 355)) & ~0x3f) == 0 && ((1L << (_la - 355)) & 255L) != 0)) ) {
					((Binary_op64Context)_localctx).op2 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(287);
				((Binary_op64Context)_localctx).a2 = operand8();
				setState(288);
				match(T__50);
				setState(289);
				((Binary_op64Context)_localctx).b2 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op2.getText(), ((Binary_op64Context)_localctx).a2.op, ((Binary_op64Context)_localctx).b2.op); 
				}
				break;
			case T__362:
			case T__363:
				enterOuterAlt(_localctx, 3);
				{
				setState(292);
				((Binary_op64Context)_localctx).op3 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__362 || _la==T__363) ) {
					((Binary_op64Context)_localctx).op3 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(293);
				((Binary_op64Context)_localctx).a3 = operand8();
				setState(294);
				match(T__50);
				setState(295);
				((Binary_op64Context)_localctx).b3 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op3.getText(), ((Binary_op64Context)_localctx).a3.op, ((Binary_op64Context)_localctx).b3.op); 
				}
				break;
			case T__364:
			case T__365:
				enterOuterAlt(_localctx, 4);
				{
				setState(298);
				((Binary_op64Context)_localctx).op4 = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__364 || _la==T__365) ) {
					((Binary_op64Context)_localctx).op4 = _errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(299);
				((Binary_op64Context)_localctx).a4 = operand16();
				setState(300);
				match(T__50);
				setState(301);
				((Binary_op64Context)_localctx).b4 = operand64();
				 factory.createBinaryOperation(((Binary_op64Context)_localctx).op4.getText(), ((Binary_op64Context)_localctx).a4.op, ((Binary_op64Context)_localctx).b4.op); 
				}
				break;
			case T__366:
				enterOuterAlt(_localctx, 5);
				{
				setState(304);
				((Binary_op64Context)_localctx).op5 = match(T__366);
				setState(305);
				((Binary_op64Context)_localctx).a5 = operand32();
				setState(306);
				match(T__50);
				setState(307);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(312);
			_localctx.op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 368)) & ~0x3f) == 0 && ((1L << (_la - 368)) & 72057594037927935L) != 0)) ) {
				_localctx.op = _errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(313);
			_localctx.a = operand();
			setState(314);
			match(T__50);
			setState(315);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(330);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__448:
			case T__449:
				{
				setState(318);
				((Operand8Context)_localctx).register8 = register8();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).register8.op; 
				}
				break;
			case T__424:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case T__542:
			case T__543:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(321);
				((Operand8Context)_localctx).memory_reference = memory_reference();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).memory_reference.op; 
				}
				break;
			case T__544:
				{
				setState(324);
				((Operand8Context)_localctx).immediate = immediate();
				 ((Operand8Context)_localctx).op =  ((Operand8Context)_localctx).immediate.op; 
				}
				break;
			case T__545:
				{
				setState(327);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(344);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__472:
			case T__473:
				{
				setState(332);
				((Operand16Context)_localctx).register16 = register16();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).register16.op; 
				}
				break;
			case T__424:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case T__542:
			case T__543:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(335);
				((Operand16Context)_localctx).memory_reference = memory_reference();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).memory_reference.op; 
				}
				break;
			case T__544:
				{
				setState(338);
				((Operand16Context)_localctx).immediate = immediate();
				 ((Operand16Context)_localctx).op =  ((Operand16Context)_localctx).immediate.op; 
				}
				break;
			case T__545:
				{
				setState(341);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(358);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__496:
			case T__497:
				{
				setState(346);
				((Operand32Context)_localctx).register32 = register32();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).register32.op; 
				}
				break;
			case T__424:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case T__542:
			case T__543:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(349);
				((Operand32Context)_localctx).memory_reference = memory_reference();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).memory_reference.op; 
				}
				break;
			case T__544:
				{
				setState(352);
				((Operand32Context)_localctx).immediate = immediate();
				 ((Operand32Context)_localctx).op =  ((Operand32Context)_localctx).immediate.op; 
				}
				break;
			case T__545:
				{
				setState(355);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(372);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__520:
			case T__521:
				{
				setState(360);
				((Operand64Context)_localctx).register64 = register64();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).register64.op; 
				}
				break;
			case T__424:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case T__542:
			case T__543:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(363);
				((Operand64Context)_localctx).memory_reference = memory_reference();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).memory_reference.op; 
				}
				break;
			case T__544:
				{
				setState(366);
				((Operand64Context)_localctx).immediate = immediate();
				 ((Operand64Context)_localctx).op =  ((Operand64Context)_localctx).immediate.op; 
				}
				break;
			case T__545:
				{
				setState(369);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(398);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
			case T__448:
			case T__449:
				{
				setState(374);
				_localctx.register8 = register8();
				 _localctx.op =  _localctx.register8.op; 
				}
				break;
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
			case T__472:
			case T__473:
				{
				setState(377);
				_localctx.register16 = register16();
				 _localctx.op =  _localctx.register16.op; 
				}
				break;
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
			case T__496:
			case T__497:
				{
				setState(380);
				_localctx.register32 = register32();
				 _localctx.op =  _localctx.register32.op; 
				}
				break;
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
			case T__520:
			case T__521:
				{
				setState(383);
				_localctx.register64 = register64();
				 _localctx.op =  _localctx.register64.op; 
				}
				break;
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
			case T__536:
			case T__537:
				{
				setState(386);
				_localctx.registerXmm = registerXmm();
				 _localctx.op =  _localctx.registerXmm.op; 
				}
				break;
			case T__424:
			case T__538:
			case T__539:
			case T__540:
			case T__541:
			case T__542:
			case T__543:
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(389);
				_localctx.memory_reference = memory_reference();
				 _localctx.op =  _localctx.memory_reference.op; 
				}
				break;
			case T__544:
				{
				setState(392);
				_localctx.immediate = immediate();
				 _localctx.op =  _localctx.immediate.op; 
				}
				break;
			case T__545:
				{
				setState(395);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(405);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 539)) & ~0x3f) == 0 && ((1L << (_la - 539)) & 63L) != 0)) {
				{
				setState(401);
				_localctx.segment_register = segment_register();
				 segment = _localctx.segment_register.reg; 
				setState(403);
				match(T__423);
				}
			}

			setState(452);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENT:
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(412);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case IDENT:
					{
					setState(407);
					_localctx.i = match(IDENT);
					 displacement = _localctx.i.getText(); 
					}
					break;
				case BIN_NUMBER:
				case HEX_NUMBER:
				case NUMBER:
					{
					setState(409);
					_localctx.number = number();
					 displacement = String.valueOf(_localctx.number.n); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(432);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__424) {
					{
					setState(414);
					match(T__424);
					setState(418);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (((((_la - 425)) & ~0x3f) == 0 && ((1L << (_la - 425)) & -3L) != 0) || ((((_la - 489)) & ~0x3f) == 0 && ((1L << (_la - 489)) & 288230376151711743L) != 0) || ((((_la - 554)) & ~0x3f) == 0 && ((1L << (_la - 554)) & 15L) != 0)) {
						{
						setState(415);
						_localctx.operand = operand();
						 base = _localctx.operand.op; 
						}
					}

					setState(429);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__50) {
						{
						setState(420);
						match(T__50);
						setState(421);
						_localctx.operand = operand();
						 offset = _localctx.operand.op; 
						setState(427);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==T__50) {
							{
							setState(423);
							match(T__50);
							setState(424);
							_localctx.number = number();
							 scale = (int) _localctx.number.n; 
							}
						}

						}
					}

					setState(431);
					match(T__425);
					}
				}

				}
				break;
			case T__424:
				{
				setState(434);
				match(T__424);
				setState(438);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 425)) & ~0x3f) == 0 && ((1L << (_la - 425)) & -3L) != 0) || ((((_la - 489)) & ~0x3f) == 0 && ((1L << (_la - 489)) & 288230376151711743L) != 0) || ((((_la - 554)) & ~0x3f) == 0 && ((1L << (_la - 554)) & 15L) != 0)) {
					{
					setState(435);
					_localctx.operand = operand();
					 base = _localctx.operand.op; 
					}
				}

				setState(449);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__50) {
					{
					setState(440);
					match(T__50);
					setState(441);
					_localctx.operand = operand();
					 offset = _localctx.operand.op; 
					setState(447);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__50) {
						{
						setState(443);
						match(T__50);
						setState(444);
						_localctx.number = number();
						 scale = (int) _localctx.number.n; 
						}
					}

					}
				}

				setState(451);
				match(T__425);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(456);
			((Register8Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 427)) & ~0x3f) == 0 && ((1L << (_la - 427)) & 16777215L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(459);
			((Register16Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 451)) & ~0x3f) == 0 && ((1L << (_la - 451)) & 16777215L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(462);
			((Register32Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 475)) & ~0x3f) == 0 && ((1L << (_la - 475)) & 16777215L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(465);
			((Register64Context)_localctx).r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 499)) & ~0x3f) == 0 && ((1L << (_la - 499)) & 16777215L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(468);
			_localctx.r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 523)) & ~0x3f) == 0 && ((1L << (_la - 523)) & 65535L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(471);
			_localctx.r = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 539)) & ~0x3f) == 0 && ((1L << (_la - 539)) & 63L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(480);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER:
				{
				setState(474);
				_localctx.num = match(NUMBER);
				 _localctx.n =  Long.parseLong(_localctx.num.getText(), 10); 
				}
				break;
			case BIN_NUMBER:
				{
				setState(476);
				_localctx.num = match(BIN_NUMBER);
				 _localctx.n =  Long.parseLong(_localctx.num.getText().substring(2), 2); 
				}
				break;
			case HEX_NUMBER:
				{
				setState(478);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(482);
			match(T__544);
			setState(483);
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

	@SuppressWarnings("CheckReturnValue")
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
			setState(486);
			match(T__545);
			setState(509);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BIN_NUMBER:
			case HEX_NUMBER:
			case NUMBER:
				{
				setState(487);
				_localctx.n = number();
				 _localctx.op =  new AsmArgumentOperand((int) _localctx.n.n); 
				}
				break;
			case T__546:
				{
				setState(490);
				match(T__546);
				setState(491);
				_localctx.n = number();
				setState(492);
				match(T__423);
				 int size = -1; int shift = 0; 
				setState(504);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__547:
					{
					setState(494);
					match(T__547);
					 size = 8; 
					}
					break;
				case T__548:
					{
					setState(496);
					match(T__548);
					 size = 8; shift = 8; 
					}
					break;
				case T__549:
					{
					setState(498);
					match(T__549);
					 size = 16; 
					}
					break;
				case T__550:
					{
					setState(500);
					match(T__550);
					 size = 32; 
					}
					break;
				case T__551:
					{
					setState(502);
					match(T__551);
					 size = 64; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 _localctx.op =  new AsmArgumentOperand((int) _localctx.n.n, size, shift); 
				setState(507);
				match(T__552);
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
		"\u0004\u0001\u0230\u0200\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007"+
		"\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007"+
		"\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007"+
		"\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007"+
		"\u001b\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007"+
		"\u001e\u0002\u001f\u0007\u001f\u0002 \u0007 \u0001\u0000\u0001\u0000\u0001"+
		"\u0000\u0003\u0000F\b\u0000\u0001\u0000\u0003\u0000I\b\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0003\u0000O\b\u0000\u0001\u0000"+
		"\u0003\u0000R\b\u0000\u0001\u0000\u0003\u0000U\b\u0000\u0005\u0000W\b"+
		"\u0000\n\u0000\f\u0000Z\t\u0000\u0003\u0000\\\b\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0003\u0002s\b\u0002\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0003\u0005\u0082"+
		"\b\u0005\u0003\u0005\u0084\b\u0005\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u0093\b\u0007"+
		"\u0003\u0007\u0095\b\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0003\u0007\u00a1\b\u0007\u0003\u0007\u00a3\b\u0007\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u00af\b\u0007\u0003\u0007\u00b1"+
		"\b\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u00bd"+
		"\b\u0007\u0003\u0007\u00bf\b\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0003\u0007\u00cb\b\u0007\u0003\u0007\u00cd\b\u0007\u0003"+
		"\u0007\u00cf\b\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0003\u000e\u00fd\b\u000e\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0003\u000f\u0117\b\u000f"+
		"\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0003\u0010\u0137\b\u0010\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011"+
		"\u0001\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0003\u0012\u014b\b\u0012\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0003\u0013\u0159\b\u0013"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014"+
		"\u0003\u0014\u0167\b\u0014\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015"+
		"\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015"+
		"\u0001\u0015\u0001\u0015\u0003\u0015\u0175\b\u0015\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0003\u0016\u018f\b\u0016"+
		"\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0003\u0017"+
		"\u0196\b\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017"+
		"\u0003\u0017\u019d\b\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017"+
		"\u0003\u0017\u01a3\b\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017"+
		"\u0001\u0017\u0001\u0017\u0001\u0017\u0003\u0017\u01ac\b\u0017\u0003\u0017"+
		"\u01ae\b\u0017\u0001\u0017\u0003\u0017\u01b1\b\u0017\u0001\u0017\u0001"+
		"\u0017\u0001\u0017\u0001\u0017\u0003\u0017\u01b7\b\u0017\u0001\u0017\u0001"+
		"\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0003"+
		"\u0017\u01c0\b\u0017\u0003\u0017\u01c2\b\u0017\u0001\u0017\u0003\u0017"+
		"\u01c5\b\u0017\u0001\u0017\u0001\u0017\u0001\u0018\u0001\u0018\u0001\u0018"+
		"\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u001a\u0001\u001a\u0001\u001a"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001c"+
		"\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001e\u0001\u001e\u0001\u001e"+
		"\u0001\u001e\u0001\u001e\u0001\u001e\u0003\u001e\u01e1\b\u001e\u0001\u001f"+
		"\u0001\u001f\u0001\u001f\u0001\u001f\u0001 \u0001 \u0001 \u0001 \u0001"+
		" \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0001"+
		" \u0001 \u0001 \u0001 \u0003 \u01f9\b \u0001 \u0001 \u0001 \u0003 \u01fe"+
		"\b \u0001 \u0000\u0000!\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012"+
		"\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.02468:<>@\u0000!\u0001\u0000"+
		"\u0002\u0003\u0001\u0000\u0004\t\u0001\u0000\u000b1\u0001\u00004O\u0001"+
		"\u0000PQ\u0001\u0000RS\u0001\u0000TU\u0001\u0000VW\u0001\u0000XY\u0001"+
		"\u0000Z_\u0001\u0000`g\u0001\u0000hp\u0001\u0000qy\u0001\u0000z\u00a4"+
		"\u0001\u0000\u00a5\u00b9\u0001\u0000\u00ba\u00ea\u0001\u0000\u00eb\u00f2"+
		"\u0001\u0000\u00f3\u00f4\u0001\u0000\u00f5\u0125\u0001\u0000\u0126\u012d"+
		"\u0001\u0000\u012e\u012f\u0001\u0000\u0130\u0131\u0001\u0000\u0132\u0162"+
		"\u0001\u0000\u0163\u016a\u0001\u0000\u016b\u016c\u0001\u0000\u016d\u016e"+
		"\u0001\u0000\u0170\u01a7\u0001\u0000\u01ab\u01c2\u0001\u0000\u01c3\u01da"+
		"\u0001\u0000\u01db\u01f2\u0001\u0000\u01f3\u020a\u0001\u0000\u020b\u021a"+
		"\u0001\u0000\u021b\u0220\u0230\u0000B\u0001\u0000\u0000\u0000\u0002`\u0001"+
		"\u0000\u0000\u0000\u0004r\u0001\u0000\u0000\u0000\u0006t\u0001\u0000\u0000"+
		"\u0000\bx\u0001\u0000\u0000\u0000\n{\u0001\u0000\u0000\u0000\f\u0085\u0001"+
		"\u0000\u0000\u0000\u000e\u00ce\u0001\u0000\u0000\u0000\u0010\u00d0\u0001"+
		"\u0000\u0000\u0000\u0012\u00d4\u0001\u0000\u0000\u0000\u0014\u00d8\u0001"+
		"\u0000\u0000\u0000\u0016\u00dc\u0001\u0000\u0000\u0000\u0018\u00e0\u0001"+
		"\u0000\u0000\u0000\u001a\u00e4\u0001\u0000\u0000\u0000\u001c\u00fc\u0001"+
		"\u0000\u0000\u0000\u001e\u0116\u0001\u0000\u0000\u0000 \u0136\u0001\u0000"+
		"\u0000\u0000\"\u0138\u0001\u0000\u0000\u0000$\u014a\u0001\u0000\u0000"+
		"\u0000&\u0158\u0001\u0000\u0000\u0000(\u0166\u0001\u0000\u0000\u0000*"+
		"\u0174\u0001\u0000\u0000\u0000,\u018e\u0001\u0000\u0000\u0000.\u0190\u0001"+
		"\u0000\u0000\u00000\u01c8\u0001\u0000\u0000\u00002\u01cb\u0001\u0000\u0000"+
		"\u00004\u01ce\u0001\u0000\u0000\u00006\u01d1\u0001\u0000\u0000\u00008"+
		"\u01d4\u0001\u0000\u0000\u0000:\u01d7\u0001\u0000\u0000\u0000<\u01e0\u0001"+
		"\u0000\u0000\u0000>\u01e2\u0001\u0000\u0000\u0000@\u01e6\u0001\u0000\u0000"+
		"\u0000B[\u0005\u0001\u0000\u0000CE\u0003\u0002\u0001\u0000DF\u0005\u0002"+
		"\u0000\u0000ED\u0001\u0000\u0000\u0000EF\u0001\u0000\u0000\u0000FI\u0001"+
		"\u0000\u0000\u0000GI\u0006\u0000\uffff\uffff\u0000HC\u0001\u0000\u0000"+
		"\u0000HG\u0001\u0000\u0000\u0000IJ\u0001\u0000\u0000\u0000JX\u0003\u0004"+
		"\u0002\u0000KQ\u0007\u0000\u0000\u0000LN\u0003\u0002\u0001\u0000MO\u0005"+
		"\u0002\u0000\u0000NM\u0001\u0000\u0000\u0000NO\u0001\u0000\u0000\u0000"+
		"OR\u0001\u0000\u0000\u0000PR\u0006\u0000\uffff\uffff\u0000QL\u0001\u0000"+
		"\u0000\u0000QP\u0001\u0000\u0000\u0000RT\u0001\u0000\u0000\u0000SU\u0003"+
		"\u0004\u0002\u0000TS\u0001\u0000\u0000\u0000TU\u0001\u0000\u0000\u0000"+
		"UW\u0001\u0000\u0000\u0000VK\u0001\u0000\u0000\u0000WZ\u0001\u0000\u0000"+
		"\u0000XV\u0001\u0000\u0000\u0000XY\u0001\u0000\u0000\u0000Y\\\u0001\u0000"+
		"\u0000\u0000ZX\u0001\u0000\u0000\u0000[H\u0001\u0000\u0000\u0000[\\\u0001"+
		"\u0000\u0000\u0000\\]\u0001\u0000\u0000\u0000]^\u0005\u0001\u0000\u0000"+
		"^_\u0006\u0000\uffff\uffff\u0000_\u0001\u0001\u0000\u0000\u0000`a\u0007"+
		"\u0001\u0000\u0000ab\u0006\u0001\uffff\uffff\u0000b\u0003\u0001\u0000"+
		"\u0000\u0000cs\u0003\n\u0005\u0000ds\u0003\f\u0006\u0000es\u0003\u0010"+
		"\b\u0000fs\u0003\u0012\t\u0000gs\u0003\u0014\n\u0000hs\u0003\u0016\u000b"+
		"\u0000is\u0003\u0018\f\u0000js\u0003\u001a\r\u0000ks\u0003\u001c\u000e"+
		"\u0000ls\u0003\u001e\u000f\u0000ms\u0003 \u0010\u0000ns\u0003\"\u0011"+
		"\u0000os\u0003\u000e\u0007\u0000ps\u0003\b\u0004\u0000qs\u0003\u0006\u0003"+
		"\u0000rc\u0001\u0000\u0000\u0000rd\u0001\u0000\u0000\u0000re\u0001\u0000"+
		"\u0000\u0000rf\u0001\u0000\u0000\u0000rg\u0001\u0000\u0000\u0000rh\u0001"+
		"\u0000\u0000\u0000ri\u0001\u0000\u0000\u0000rj\u0001\u0000\u0000\u0000"+
		"rk\u0001\u0000\u0000\u0000rl\u0001\u0000\u0000\u0000rm\u0001\u0000\u0000"+
		"\u0000rn\u0001\u0000\u0000\u0000ro\u0001\u0000\u0000\u0000rp\u0001\u0000"+
		"\u0000\u0000rq\u0001\u0000\u0000\u0000s\u0005\u0001\u0000\u0000\u0000"+
		"tu\u0005\n\u0000\u0000uv\u0003>\u001f\u0000vw\u0006\u0003\uffff\uffff"+
		"\u0000w\u0007\u0001\u0000\u0000\u0000xy\u0007\u0002\u0000\u0000yz\u0003"+
		"*\u0015\u0000z\t\u0001\u0000\u0000\u0000{|\u00052\u0000\u0000|\u0083\u0003"+
		"<\u001e\u0000}~\u00053\u0000\u0000~\u0081\u0003<\u001e\u0000\u007f\u0080"+
		"\u00053\u0000\u0000\u0080\u0082\u0003<\u001e\u0000\u0081\u007f\u0001\u0000"+
		"\u0000\u0000\u0081\u0082\u0001\u0000\u0000\u0000\u0082\u0084\u0001\u0000"+
		"\u0000\u0000\u0083}\u0001\u0000\u0000\u0000\u0083\u0084\u0001\u0000\u0000"+
		"\u0000\u0084\u000b\u0001\u0000\u0000\u0000\u0085\u0086\u0007\u0003\u0000"+
		"\u0000\u0086\u0087\u0006\u0006\uffff\uffff\u0000\u0087\r\u0001\u0000\u0000"+
		"\u0000\u0088\u0089\u0007\u0004\u0000\u0000\u0089\u0094\u0003$\u0012\u0000"+
		"\u008a\u0095\u0006\u0007\uffff\uffff\u0000\u008b\u008c\u00053\u0000\u0000"+
		"\u008c\u0092\u0003$\u0012\u0000\u008d\u0093\u0006\u0007\uffff\uffff\u0000"+
		"\u008e\u008f\u00053\u0000\u0000\u008f\u0090\u0003$\u0012\u0000\u0090\u0091"+
		"\u0006\u0007\uffff\uffff\u0000\u0091\u0093\u0001\u0000\u0000\u0000\u0092"+
		"\u008d\u0001\u0000\u0000\u0000\u0092\u008e\u0001\u0000\u0000\u0000\u0093"+
		"\u0095\u0001\u0000\u0000\u0000\u0094\u008a\u0001\u0000\u0000\u0000\u0094"+
		"\u008b\u0001\u0000\u0000\u0000\u0095\u00cf\u0001\u0000\u0000\u0000\u0096"+
		"\u0097\u0007\u0005\u0000\u0000\u0097\u00a2\u0003&\u0013\u0000\u0098\u00a3"+
		"\u0006\u0007\uffff\uffff\u0000\u0099\u009a\u00053\u0000\u0000\u009a\u00a0"+
		"\u0003&\u0013\u0000\u009b\u00a1\u0006\u0007\uffff\uffff\u0000\u009c\u009d"+
		"\u00053\u0000\u0000\u009d\u009e\u0003&\u0013\u0000\u009e\u009f\u0006\u0007"+
		"\uffff\uffff\u0000\u009f\u00a1\u0001\u0000\u0000\u0000\u00a0\u009b\u0001"+
		"\u0000\u0000\u0000\u00a0\u009c\u0001\u0000\u0000\u0000\u00a1\u00a3\u0001"+
		"\u0000\u0000\u0000\u00a2\u0098\u0001\u0000\u0000\u0000\u00a2\u0099\u0001"+
		"\u0000\u0000\u0000\u00a3\u00cf\u0001\u0000\u0000\u0000\u00a4\u00a5\u0007"+
		"\u0006\u0000\u0000\u00a5\u00b0\u0003(\u0014\u0000\u00a6\u00b1\u0006\u0007"+
		"\uffff\uffff\u0000\u00a7\u00a8\u00053\u0000\u0000\u00a8\u00ae\u0003(\u0014"+
		"\u0000\u00a9\u00af\u0006\u0007\uffff\uffff\u0000\u00aa\u00ab\u00053\u0000"+
		"\u0000\u00ab\u00ac\u0003(\u0014\u0000\u00ac\u00ad\u0006\u0007\uffff\uffff"+
		"\u0000\u00ad\u00af\u0001\u0000\u0000\u0000\u00ae\u00a9\u0001\u0000\u0000"+
		"\u0000\u00ae\u00aa\u0001\u0000\u0000\u0000\u00af\u00b1\u0001\u0000\u0000"+
		"\u0000\u00b0\u00a6\u0001\u0000\u0000\u0000\u00b0\u00a7\u0001\u0000\u0000"+
		"\u0000\u00b1\u00cf\u0001\u0000\u0000\u0000\u00b2\u00b3\u0007\u0007\u0000"+
		"\u0000\u00b3\u00be\u0003*\u0015\u0000\u00b4\u00bf\u0006\u0007\uffff\uffff"+
		"\u0000\u00b5\u00b6\u00053\u0000\u0000\u00b6\u00bc\u0003*\u0015\u0000\u00b7"+
		"\u00bd\u0006\u0007\uffff\uffff\u0000\u00b8\u00b9\u00053\u0000\u0000\u00b9"+
		"\u00ba\u0003*\u0015\u0000\u00ba\u00bb\u0006\u0007\uffff\uffff\u0000\u00bb"+
		"\u00bd\u0001\u0000\u0000\u0000\u00bc\u00b7\u0001\u0000\u0000\u0000\u00bc"+
		"\u00b8\u0001\u0000\u0000\u0000\u00bd\u00bf\u0001\u0000\u0000\u0000\u00be"+
		"\u00b4\u0001\u0000\u0000\u0000\u00be\u00b5\u0001\u0000\u0000\u0000\u00bf"+
		"\u00cf\u0001\u0000\u0000\u0000\u00c0\u00c1\u0007\b\u0000\u0000\u00c1\u00cc"+
		"\u0003,\u0016\u0000\u00c2\u00cd\u0006\u0007\uffff\uffff\u0000\u00c3\u00c4"+
		"\u00053\u0000\u0000\u00c4\u00ca\u0003,\u0016\u0000\u00c5\u00cb\u0006\u0007"+
		"\uffff\uffff\u0000\u00c6\u00c7\u00053\u0000\u0000\u00c7\u00c8\u0003,\u0016"+
		"\u0000\u00c8\u00c9\u0006\u0007\uffff\uffff\u0000\u00c9\u00cb\u0001\u0000"+
		"\u0000\u0000\u00ca\u00c5\u0001\u0000\u0000\u0000\u00ca\u00c6\u0001\u0000"+
		"\u0000\u0000\u00cb\u00cd\u0001\u0000\u0000\u0000\u00cc\u00c2\u0001\u0000"+
		"\u0000\u0000\u00cc\u00c3\u0001\u0000\u0000\u0000\u00cd\u00cf\u0001\u0000"+
		"\u0000\u0000\u00ce\u0088\u0001\u0000\u0000\u0000\u00ce\u0096\u0001\u0000"+
		"\u0000\u0000\u00ce\u00a4\u0001\u0000\u0000\u0000\u00ce\u00b2\u0001\u0000"+
		"\u0000\u0000\u00ce\u00c0\u0001\u0000\u0000\u0000\u00cf\u000f\u0001\u0000"+
		"\u0000\u0000\u00d0\u00d1\u0007\t\u0000\u0000\u00d1\u00d2\u0003$\u0012"+
		"\u0000\u00d2\u00d3\u0006\b\uffff\uffff\u0000\u00d3\u0011\u0001\u0000\u0000"+
		"\u0000\u00d4\u00d5\u0007\n\u0000\u0000\u00d5\u00d6\u0003&\u0013\u0000"+
		"\u00d6\u00d7\u0006\t\uffff\uffff\u0000\u00d7\u0013\u0001\u0000\u0000\u0000"+
		"\u00d8\u00d9\u0007\u000b\u0000\u0000\u00d9\u00da\u0003(\u0014\u0000\u00da"+
		"\u00db\u0006\n\uffff\uffff\u0000\u00db\u0015\u0001\u0000\u0000\u0000\u00dc"+
		"\u00dd\u0007\f\u0000\u0000\u00dd\u00de\u0003*\u0015\u0000\u00de\u00df"+
		"\u0006\u000b\uffff\uffff\u0000\u00df\u0017\u0001\u0000\u0000\u0000\u00e0"+
		"\u00e1\u0007\r\u0000\u0000\u00e1\u00e2\u0003,\u0016\u0000\u00e2\u00e3"+
		"\u0006\f\uffff\uffff\u0000\u00e3\u0019\u0001\u0000\u0000\u0000\u00e4\u00e5"+
		"\u0007\u000e\u0000\u0000\u00e5\u00e6\u0003$\u0012\u0000\u00e6\u00e7\u0005"+
		"3\u0000\u0000\u00e7\u00e8\u0003$\u0012\u0000\u00e8\u00e9\u0006\r\uffff"+
		"\uffff\u0000\u00e9\u001b\u0001\u0000\u0000\u0000\u00ea\u00eb\u0007\u000f"+
		"\u0000\u0000\u00eb\u00ec\u0003&\u0013\u0000\u00ec\u00ed\u00053\u0000\u0000"+
		"\u00ed\u00ee\u0003&\u0013\u0000\u00ee\u00ef\u0006\u000e\uffff\uffff\u0000"+
		"\u00ef\u00fd\u0001\u0000\u0000\u0000\u00f0\u00f1\u0007\u0010\u0000\u0000"+
		"\u00f1\u00f2\u0003$\u0012\u0000\u00f2\u00f3\u00053\u0000\u0000\u00f3\u00f4"+
		"\u0003&\u0013\u0000\u00f4\u00f5\u0006\u000e\uffff\uffff\u0000\u00f5\u00fd"+
		"\u0001\u0000\u0000\u0000\u00f6\u00f7\u0007\u0011\u0000\u0000\u00f7\u00f8"+
		"\u0003$\u0012\u0000\u00f8\u00f9\u00053\u0000\u0000\u00f9\u00fa\u0003&"+
		"\u0013\u0000\u00fa\u00fb\u0006\u000e\uffff\uffff\u0000\u00fb\u00fd\u0001"+
		"\u0000\u0000\u0000\u00fc\u00ea\u0001\u0000\u0000\u0000\u00fc\u00f0\u0001"+
		"\u0000\u0000\u0000\u00fc\u00f6\u0001\u0000\u0000\u0000\u00fd\u001d\u0001"+
		"\u0000\u0000\u0000\u00fe\u00ff\u0007\u0012\u0000\u0000\u00ff\u0100\u0003"+
		"(\u0014\u0000\u0100\u0101\u00053\u0000\u0000\u0101\u0102\u0003(\u0014"+
		"\u0000\u0102\u0103\u0006\u000f\uffff\uffff\u0000\u0103\u0117\u0001\u0000"+
		"\u0000\u0000\u0104\u0105\u0007\u0013\u0000\u0000\u0105\u0106\u0003$\u0012"+
		"\u0000\u0106\u0107\u00053\u0000\u0000\u0107\u0108\u0003(\u0014\u0000\u0108"+
		"\u0109\u0006\u000f\uffff\uffff\u0000\u0109\u0117\u0001\u0000\u0000\u0000"+
		"\u010a\u010b\u0007\u0014\u0000\u0000\u010b\u010c\u0003$\u0012\u0000\u010c"+
		"\u010d\u00053\u0000\u0000\u010d\u010e\u0003(\u0014\u0000\u010e\u010f\u0006"+
		"\u000f\uffff\uffff\u0000\u010f\u0117\u0001\u0000\u0000\u0000\u0110\u0111"+
		"\u0007\u0015\u0000\u0000\u0111\u0112\u0003&\u0013\u0000\u0112\u0113\u0005"+
		"3\u0000\u0000\u0113\u0114\u0003(\u0014\u0000\u0114\u0115\u0006\u000f\uffff"+
		"\uffff\u0000\u0115\u0117\u0001\u0000\u0000\u0000\u0116\u00fe\u0001\u0000"+
		"\u0000\u0000\u0116\u0104\u0001\u0000\u0000\u0000\u0116\u010a\u0001\u0000"+
		"\u0000\u0000\u0116\u0110\u0001\u0000\u0000\u0000\u0117\u001f\u0001\u0000"+
		"\u0000\u0000\u0118\u0119\u0007\u0016\u0000\u0000\u0119\u011a\u0003*\u0015"+
		"\u0000\u011a\u011b\u00053\u0000\u0000\u011b\u011c\u0003*\u0015\u0000\u011c"+
		"\u011d\u0006\u0010\uffff\uffff\u0000\u011d\u0137\u0001\u0000\u0000\u0000"+
		"\u011e\u011f\u0007\u0017\u0000\u0000\u011f\u0120\u0003$\u0012\u0000\u0120"+
		"\u0121\u00053\u0000\u0000\u0121\u0122\u0003*\u0015\u0000\u0122\u0123\u0006"+
		"\u0010\uffff\uffff\u0000\u0123\u0137\u0001\u0000\u0000\u0000\u0124\u0125"+
		"\u0007\u0018\u0000\u0000\u0125\u0126\u0003$\u0012\u0000\u0126\u0127\u0005"+
		"3\u0000\u0000\u0127\u0128\u0003*\u0015\u0000\u0128\u0129\u0006\u0010\uffff"+
		"\uffff\u0000\u0129\u0137\u0001\u0000\u0000\u0000\u012a\u012b\u0007\u0019"+
		"\u0000\u0000\u012b\u012c\u0003&\u0013\u0000\u012c\u012d\u00053\u0000\u0000"+
		"\u012d\u012e\u0003*\u0015\u0000\u012e\u012f\u0006\u0010\uffff\uffff\u0000"+
		"\u012f\u0137\u0001\u0000\u0000\u0000\u0130\u0131\u0005\u016f\u0000\u0000"+
		"\u0131\u0132\u0003(\u0014\u0000\u0132\u0133\u00053\u0000\u0000\u0133\u0134"+
		"\u0003*\u0015\u0000\u0134\u0135\u0006\u0010\uffff\uffff\u0000\u0135\u0137"+
		"\u0001\u0000\u0000\u0000\u0136\u0118\u0001\u0000\u0000\u0000\u0136\u011e"+
		"\u0001\u0000\u0000\u0000\u0136\u0124\u0001\u0000\u0000\u0000\u0136\u012a"+
		"\u0001\u0000\u0000\u0000\u0136\u0130\u0001\u0000\u0000\u0000\u0137!\u0001"+
		"\u0000\u0000\u0000\u0138\u0139\u0007\u001a\u0000\u0000\u0139\u013a\u0003"+
		",\u0016\u0000\u013a\u013b\u00053\u0000\u0000\u013b\u013c\u0003,\u0016"+
		"\u0000\u013c\u013d\u0006\u0011\uffff\uffff\u0000\u013d#\u0001\u0000\u0000"+
		"\u0000\u013e\u013f\u00030\u0018\u0000\u013f\u0140\u0006\u0012\uffff\uffff"+
		"\u0000\u0140\u014b\u0001\u0000\u0000\u0000\u0141\u0142\u0003.\u0017\u0000"+
		"\u0142\u0143\u0006\u0012\uffff\uffff\u0000\u0143\u014b\u0001\u0000\u0000"+
		"\u0000\u0144\u0145\u0003>\u001f\u0000\u0145\u0146\u0006\u0012\uffff\uffff"+
		"\u0000\u0146\u014b\u0001\u0000\u0000\u0000\u0147\u0148\u0003@ \u0000\u0148"+
		"\u0149\u0006\u0012\uffff\uffff\u0000\u0149\u014b\u0001\u0000\u0000\u0000"+
		"\u014a\u013e\u0001\u0000\u0000\u0000\u014a\u0141\u0001\u0000\u0000\u0000"+
		"\u014a\u0144\u0001\u0000\u0000\u0000\u014a\u0147\u0001\u0000\u0000\u0000"+
		"\u014b%\u0001\u0000\u0000\u0000\u014c\u014d\u00032\u0019\u0000\u014d\u014e"+
		"\u0006\u0013\uffff\uffff\u0000\u014e\u0159\u0001\u0000\u0000\u0000\u014f"+
		"\u0150\u0003.\u0017\u0000\u0150\u0151\u0006\u0013\uffff\uffff\u0000\u0151"+
		"\u0159\u0001\u0000\u0000\u0000\u0152\u0153\u0003>\u001f\u0000\u0153\u0154"+
		"\u0006\u0013\uffff\uffff\u0000\u0154\u0159\u0001\u0000\u0000\u0000\u0155"+
		"\u0156\u0003@ \u0000\u0156\u0157\u0006\u0013\uffff\uffff\u0000\u0157\u0159"+
		"\u0001\u0000\u0000\u0000\u0158\u014c\u0001\u0000\u0000\u0000\u0158\u014f"+
		"\u0001\u0000\u0000\u0000\u0158\u0152\u0001\u0000\u0000\u0000\u0158\u0155"+
		"\u0001\u0000\u0000\u0000\u0159\'\u0001\u0000\u0000\u0000\u015a\u015b\u0003"+
		"4\u001a\u0000\u015b\u015c\u0006\u0014\uffff\uffff\u0000\u015c\u0167\u0001"+
		"\u0000\u0000\u0000\u015d\u015e\u0003.\u0017\u0000\u015e\u015f\u0006\u0014"+
		"\uffff\uffff\u0000\u015f\u0167\u0001\u0000\u0000\u0000\u0160\u0161\u0003"+
		">\u001f\u0000\u0161\u0162\u0006\u0014\uffff\uffff\u0000\u0162\u0167\u0001"+
		"\u0000\u0000\u0000\u0163\u0164\u0003@ \u0000\u0164\u0165\u0006\u0014\uffff"+
		"\uffff\u0000\u0165\u0167\u0001\u0000\u0000\u0000\u0166\u015a\u0001\u0000"+
		"\u0000\u0000\u0166\u015d\u0001\u0000\u0000\u0000\u0166\u0160\u0001\u0000"+
		"\u0000\u0000\u0166\u0163\u0001\u0000\u0000\u0000\u0167)\u0001\u0000\u0000"+
		"\u0000\u0168\u0169\u00036\u001b\u0000\u0169\u016a\u0006\u0015\uffff\uffff"+
		"\u0000\u016a\u0175\u0001\u0000\u0000\u0000\u016b\u016c\u0003.\u0017\u0000"+
		"\u016c\u016d\u0006\u0015\uffff\uffff\u0000\u016d\u0175\u0001\u0000\u0000"+
		"\u0000\u016e\u016f\u0003>\u001f\u0000\u016f\u0170\u0006\u0015\uffff\uffff"+
		"\u0000\u0170\u0175\u0001\u0000\u0000\u0000\u0171\u0172\u0003@ \u0000\u0172"+
		"\u0173\u0006\u0015\uffff\uffff\u0000\u0173\u0175\u0001\u0000\u0000\u0000"+
		"\u0174\u0168\u0001\u0000\u0000\u0000\u0174\u016b\u0001\u0000\u0000\u0000"+
		"\u0174\u016e\u0001\u0000\u0000\u0000\u0174\u0171\u0001\u0000\u0000\u0000"+
		"\u0175+\u0001\u0000\u0000\u0000\u0176\u0177\u00030\u0018\u0000\u0177\u0178"+
		"\u0006\u0016\uffff\uffff\u0000\u0178\u018f\u0001\u0000\u0000\u0000\u0179"+
		"\u017a\u00032\u0019\u0000\u017a\u017b\u0006\u0016\uffff\uffff\u0000\u017b"+
		"\u018f\u0001\u0000\u0000\u0000\u017c\u017d\u00034\u001a\u0000\u017d\u017e"+
		"\u0006\u0016\uffff\uffff\u0000\u017e\u018f\u0001\u0000\u0000\u0000\u017f"+
		"\u0180\u00036\u001b\u0000\u0180\u0181\u0006\u0016\uffff\uffff\u0000\u0181"+
		"\u018f\u0001\u0000\u0000\u0000\u0182\u0183\u00038\u001c\u0000\u0183\u0184"+
		"\u0006\u0016\uffff\uffff\u0000\u0184\u018f\u0001\u0000\u0000\u0000\u0185"+
		"\u0186\u0003.\u0017\u0000\u0186\u0187\u0006\u0016\uffff\uffff\u0000\u0187"+
		"\u018f\u0001\u0000\u0000\u0000\u0188\u0189\u0003>\u001f\u0000\u0189\u018a"+
		"\u0006\u0016\uffff\uffff\u0000\u018a\u018f\u0001\u0000\u0000\u0000\u018b"+
		"\u018c\u0003@ \u0000\u018c\u018d\u0006\u0016\uffff\uffff\u0000\u018d\u018f"+
		"\u0001\u0000\u0000\u0000\u018e\u0176\u0001\u0000\u0000\u0000\u018e\u0179"+
		"\u0001\u0000\u0000\u0000\u018e\u017c\u0001\u0000\u0000\u0000\u018e\u017f"+
		"\u0001\u0000\u0000\u0000\u018e\u0182\u0001\u0000\u0000\u0000\u018e\u0185"+
		"\u0001\u0000\u0000\u0000\u018e\u0188\u0001\u0000\u0000\u0000\u018e\u018b"+
		"\u0001\u0000\u0000\u0000\u018f-\u0001\u0000\u0000\u0000\u0190\u0195\u0006"+
		"\u0017\uffff\uffff\u0000\u0191\u0192\u0003:\u001d\u0000\u0192\u0193\u0006"+
		"\u0017\uffff\uffff\u0000\u0193\u0194\u0005\u01a8\u0000\u0000\u0194\u0196"+
		"\u0001\u0000\u0000\u0000\u0195\u0191\u0001\u0000\u0000\u0000\u0195\u0196"+
		"\u0001\u0000\u0000\u0000\u0196\u01c4\u0001\u0000\u0000\u0000\u0197\u0198"+
		"\u0005\u022a\u0000\u0000\u0198\u019d\u0006\u0017\uffff\uffff\u0000\u0199"+
		"\u019a\u0003<\u001e\u0000\u019a\u019b\u0006\u0017\uffff\uffff\u0000\u019b"+
		"\u019d\u0001\u0000\u0000\u0000\u019c\u0197\u0001\u0000\u0000\u0000\u019c"+
		"\u0199\u0001\u0000\u0000\u0000\u019d\u01b0\u0001\u0000\u0000\u0000\u019e"+
		"\u01a2\u0005\u01a9\u0000\u0000\u019f\u01a0\u0003,\u0016\u0000\u01a0\u01a1"+
		"\u0006\u0017\uffff\uffff\u0000\u01a1\u01a3\u0001\u0000\u0000\u0000\u01a2"+
		"\u019f\u0001\u0000\u0000\u0000\u01a2\u01a3\u0001\u0000\u0000\u0000\u01a3"+
		"\u01ad\u0001\u0000\u0000\u0000\u01a4\u01a5\u00053\u0000\u0000\u01a5\u01a6"+
		"\u0003,\u0016\u0000\u01a6\u01ab\u0006\u0017\uffff\uffff\u0000\u01a7\u01a8"+
		"\u00053\u0000\u0000\u01a8\u01a9\u0003<\u001e\u0000\u01a9\u01aa\u0006\u0017"+
		"\uffff\uffff\u0000\u01aa\u01ac\u0001\u0000\u0000\u0000\u01ab\u01a7\u0001"+
		"\u0000\u0000\u0000\u01ab\u01ac\u0001\u0000\u0000\u0000\u01ac\u01ae\u0001"+
		"\u0000\u0000\u0000\u01ad\u01a4\u0001\u0000\u0000\u0000\u01ad\u01ae\u0001"+
		"\u0000\u0000\u0000\u01ae\u01af\u0001\u0000\u0000\u0000\u01af\u01b1\u0005"+
		"\u01aa\u0000\u0000\u01b0\u019e\u0001\u0000\u0000\u0000\u01b0\u01b1\u0001"+
		"\u0000\u0000\u0000\u01b1\u01c5\u0001\u0000\u0000\u0000\u01b2\u01b6\u0005"+
		"\u01a9\u0000\u0000\u01b3\u01b4\u0003,\u0016\u0000\u01b4\u01b5\u0006\u0017"+
		"\uffff\uffff\u0000\u01b5\u01b7\u0001\u0000\u0000\u0000\u01b6\u01b3\u0001"+
		"\u0000\u0000\u0000\u01b6\u01b7\u0001\u0000\u0000\u0000\u01b7\u01c1\u0001"+
		"\u0000\u0000\u0000\u01b8\u01b9\u00053\u0000\u0000\u01b9\u01ba\u0003,\u0016"+
		"\u0000\u01ba\u01bf\u0006\u0017\uffff\uffff\u0000\u01bb\u01bc\u00053\u0000"+
		"\u0000\u01bc\u01bd\u0003<\u001e\u0000\u01bd\u01be\u0006\u0017\uffff\uffff"+
		"\u0000\u01be\u01c0\u0001\u0000\u0000\u0000\u01bf\u01bb\u0001\u0000\u0000"+
		"\u0000\u01bf\u01c0\u0001\u0000\u0000\u0000\u01c0\u01c2\u0001\u0000\u0000"+
		"\u0000\u01c1\u01b8\u0001\u0000\u0000\u0000\u01c1\u01c2\u0001\u0000\u0000"+
		"\u0000\u01c2\u01c3\u0001\u0000\u0000\u0000\u01c3\u01c5\u0005\u01aa\u0000"+
		"\u0000\u01c4\u019c\u0001\u0000\u0000\u0000\u01c4\u01b2\u0001\u0000\u0000"+
		"\u0000\u01c5\u01c6\u0001\u0000\u0000\u0000\u01c6\u01c7\u0006\u0017\uffff"+
		"\uffff\u0000\u01c7/\u0001\u0000\u0000\u0000\u01c8\u01c9\u0007\u001b\u0000"+
		"\u0000\u01c9\u01ca\u0006\u0018\uffff\uffff\u0000\u01ca1\u0001\u0000\u0000"+
		"\u0000\u01cb\u01cc\u0007\u001c\u0000\u0000\u01cc\u01cd\u0006\u0019\uffff"+
		"\uffff\u0000\u01cd3\u0001\u0000\u0000\u0000\u01ce\u01cf\u0007\u001d\u0000"+
		"\u0000\u01cf\u01d0\u0006\u001a\uffff\uffff\u0000\u01d05\u0001\u0000\u0000"+
		"\u0000\u01d1\u01d2\u0007\u001e\u0000\u0000\u01d2\u01d3\u0006\u001b\uffff"+
		"\uffff\u0000\u01d37\u0001\u0000\u0000\u0000\u01d4\u01d5\u0007\u001f\u0000"+
		"\u0000\u01d5\u01d6\u0006\u001c\uffff\uffff\u0000\u01d69\u0001\u0000\u0000"+
		"\u0000\u01d7\u01d8\u0007 \u0000\u0000\u01d8\u01d9\u0006\u001d\uffff\uffff"+
		"\u0000\u01d9;\u0001\u0000\u0000\u0000\u01da\u01db\u0005\u022d\u0000\u0000"+
		"\u01db\u01e1\u0006\u001e\uffff\uffff\u0000\u01dc\u01dd\u0005\u022b\u0000"+
		"\u0000\u01dd\u01e1\u0006\u001e\uffff\uffff\u0000\u01de\u01df\u0005\u022c"+
		"\u0000\u0000\u01df\u01e1\u0006\u001e\uffff\uffff\u0000\u01e0\u01da\u0001"+
		"\u0000\u0000\u0000\u01e0\u01dc\u0001\u0000\u0000\u0000\u01e0\u01de\u0001"+
		"\u0000\u0000\u0000\u01e1=\u0001\u0000\u0000\u0000\u01e2\u01e3\u0005\u0221"+
		"\u0000\u0000\u01e3\u01e4\u0003<\u001e\u0000\u01e4\u01e5\u0006\u001f\uffff"+
		"\uffff\u0000\u01e5?\u0001\u0000\u0000\u0000\u01e6\u01fd\u0005\u0222\u0000"+
		"\u0000\u01e7\u01e8\u0003<\u001e\u0000\u01e8\u01e9\u0006 \uffff\uffff\u0000"+
		"\u01e9\u01fe\u0001\u0000\u0000\u0000\u01ea\u01eb\u0005\u0223\u0000\u0000"+
		"\u01eb\u01ec\u0003<\u001e\u0000\u01ec\u01ed\u0005\u01a8\u0000\u0000\u01ed"+
		"\u01f8\u0006 \uffff\uffff\u0000\u01ee\u01ef\u0005\u0224\u0000\u0000\u01ef"+
		"\u01f9\u0006 \uffff\uffff\u0000\u01f0\u01f1\u0005\u0225\u0000\u0000\u01f1"+
		"\u01f9\u0006 \uffff\uffff\u0000\u01f2\u01f3\u0005\u0226\u0000\u0000\u01f3"+
		"\u01f9\u0006 \uffff\uffff\u0000\u01f4\u01f5\u0005\u0227\u0000\u0000\u01f5"+
		"\u01f9\u0006 \uffff\uffff\u0000\u01f6\u01f7\u0005\u0228\u0000\u0000\u01f7"+
		"\u01f9\u0006 \uffff\uffff\u0000\u01f8\u01ee\u0001\u0000\u0000\u0000\u01f8"+
		"\u01f0\u0001\u0000\u0000\u0000\u01f8\u01f2\u0001\u0000\u0000\u0000\u01f8"+
		"\u01f4\u0001\u0000\u0000\u0000\u01f8\u01f6\u0001\u0000\u0000\u0000\u01f9"+
		"\u01fa\u0001\u0000\u0000\u0000\u01fa\u01fb\u0006 \uffff\uffff\u0000\u01fb"+
		"\u01fc\u0005\u0229\u0000\u0000\u01fc\u01fe\u0001\u0000\u0000\u0000\u01fd"+
		"\u01e7\u0001\u0000\u0000\u0000\u01fd\u01ea\u0001\u0000\u0000\u0000\u01fe"+
		"A\u0001\u0000\u0000\u0000*EHNQTX[r\u0081\u0083\u0092\u0094\u00a0\u00a2"+
		"\u00ae\u00b0\u00bc\u00be\u00ca\u00cc\u00ce\u00fc\u0116\u0136\u014a\u0158"+
		"\u0166\u0174\u018e\u0195\u019c\u01a2\u01ab\u01ad\u01b0\u01b6\u01bf\u01c1"+
		"\u01c4\u01e0\u01f8\u01fd";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
