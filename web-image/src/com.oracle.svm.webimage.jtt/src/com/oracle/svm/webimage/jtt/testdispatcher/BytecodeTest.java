/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.webimage.jtt.testdispatcher;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.webimage.jtt.bytecode.BC_aaload;
import com.oracle.svm.webimage.jtt.bytecode.BC_aaload_1;
import com.oracle.svm.webimage.jtt.bytecode.BC_aastore;
import com.oracle.svm.webimage.jtt.bytecode.BC_aload_0;
import com.oracle.svm.webimage.jtt.bytecode.BC_aload_1;
import com.oracle.svm.webimage.jtt.bytecode.BC_aload_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_aload_3;
import com.oracle.svm.webimage.jtt.bytecode.BC_anewarray;
import com.oracle.svm.webimage.jtt.bytecode.BC_areturn;
import com.oracle.svm.webimage.jtt.bytecode.BC_arraylength;
import com.oracle.svm.webimage.jtt.bytecode.BC_athrow;
import com.oracle.svm.webimage.jtt.bytecode.BC_baload;
import com.oracle.svm.webimage.jtt.bytecode.BC_bastore;
import com.oracle.svm.webimage.jtt.bytecode.BC_caload;
import com.oracle.svm.webimage.jtt.bytecode.BC_castore;
import com.oracle.svm.webimage.jtt.bytecode.BC_checkcast01;
import com.oracle.svm.webimage.jtt.bytecode.BC_checkcast02;
import com.oracle.svm.webimage.jtt.bytecode.BC_checkcast03;
import com.oracle.svm.webimage.jtt.bytecode.BC_d2f;
import com.oracle.svm.webimage.jtt.bytecode.BC_d2i01;
import com.oracle.svm.webimage.jtt.bytecode.BC_d2i02;
import com.oracle.svm.webimage.jtt.bytecode.BC_d2l01;
import com.oracle.svm.webimage.jtt.bytecode.BC_d2l02;
import com.oracle.svm.webimage.jtt.bytecode.BC_dadd;
import com.oracle.svm.webimage.jtt.bytecode.BC_daload;
import com.oracle.svm.webimage.jtt.bytecode.BC_dastore;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp01;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp02;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp03;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp04;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp05;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp06;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp07;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp08;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp09;
import com.oracle.svm.webimage.jtt.bytecode.BC_dcmp10;
import com.oracle.svm.webimage.jtt.bytecode.BC_ddiv;
import com.oracle.svm.webimage.jtt.bytecode.BC_dmul;
import com.oracle.svm.webimage.jtt.bytecode.BC_dneg;
import com.oracle.svm.webimage.jtt.bytecode.BC_dneg2;
import com.oracle.svm.webimage.jtt.bytecode.BC_drem;
import com.oracle.svm.webimage.jtt.bytecode.BC_dreturn;
import com.oracle.svm.webimage.jtt.bytecode.BC_dsub;
import com.oracle.svm.webimage.jtt.bytecode.BC_dsub2;
import com.oracle.svm.webimage.jtt.bytecode.BC_f2d;
import com.oracle.svm.webimage.jtt.bytecode.BC_f2i01;
import com.oracle.svm.webimage.jtt.bytecode.BC_f2i02;
import com.oracle.svm.webimage.jtt.bytecode.BC_f2l01;
import com.oracle.svm.webimage.jtt.bytecode.BC_f2l02;
import com.oracle.svm.webimage.jtt.bytecode.BC_fadd;
import com.oracle.svm.webimage.jtt.bytecode.BC_faload;
import com.oracle.svm.webimage.jtt.bytecode.BC_fastore;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp01;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp02;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp03;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp04;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp05;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp06;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp07;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp08;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp09;
import com.oracle.svm.webimage.jtt.bytecode.BC_fcmp10;
import com.oracle.svm.webimage.jtt.bytecode.BC_fdiv;
import com.oracle.svm.webimage.jtt.bytecode.BC_fload;
import com.oracle.svm.webimage.jtt.bytecode.BC_fload_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_fmul;
import com.oracle.svm.webimage.jtt.bytecode.BC_fneg;
import com.oracle.svm.webimage.jtt.bytecode.BC_frem;
import com.oracle.svm.webimage.jtt.bytecode.BC_freturn;
import com.oracle.svm.webimage.jtt.bytecode.BC_fsub;
import com.oracle.svm.webimage.jtt.bytecode.BC_getfield;
import com.oracle.svm.webimage.jtt.bytecode.BC_getstatic_b;
import com.oracle.svm.webimage.jtt.bytecode.BC_getstatic_c;
import com.oracle.svm.webimage.jtt.bytecode.BC_getstatic_d;
import com.oracle.svm.webimage.jtt.bytecode.BC_getstatic_f;
import com.oracle.svm.webimage.jtt.bytecode.BC_getstatic_i;
import com.oracle.svm.webimage.jtt.bytecode.BC_getstatic_l;
import com.oracle.svm.webimage.jtt.bytecode.BC_getstatic_s;
import com.oracle.svm.webimage.jtt.bytecode.BC_getstatic_z;
import com.oracle.svm.webimage.jtt.bytecode.BC_i2b;
import com.oracle.svm.webimage.jtt.bytecode.BC_i2c;
import com.oracle.svm.webimage.jtt.bytecode.BC_i2d;
import com.oracle.svm.webimage.jtt.bytecode.BC_i2f;
import com.oracle.svm.webimage.jtt.bytecode.BC_i2l;
import com.oracle.svm.webimage.jtt.bytecode.BC_i2s;
import com.oracle.svm.webimage.jtt.bytecode.BC_iadd;
import com.oracle.svm.webimage.jtt.bytecode.BC_iadd2;
import com.oracle.svm.webimage.jtt.bytecode.BC_iadd3;
import com.oracle.svm.webimage.jtt.bytecode.BC_iaload;
import com.oracle.svm.webimage.jtt.bytecode.BC_iand;
import com.oracle.svm.webimage.jtt.bytecode.BC_iastore;
import com.oracle.svm.webimage.jtt.bytecode.BC_iconst;
import com.oracle.svm.webimage.jtt.bytecode.BC_idiv;
import com.oracle.svm.webimage.jtt.bytecode.BC_idiv2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifeq;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifeq_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifeq_3;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifge;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifge_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifge_3;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifgt;
import com.oracle.svm.webimage.jtt.bytecode.BC_ificmplt1;
import com.oracle.svm.webimage.jtt.bytecode.BC_ificmplt2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ificmpne1;
import com.oracle.svm.webimage.jtt.bytecode.BC_ificmpne2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifle;
import com.oracle.svm.webimage.jtt.bytecode.BC_iflt;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifne;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifnonnull;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifnonnull_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifnonnull_3;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifnull;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifnull_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ifnull_3;
import com.oracle.svm.webimage.jtt.bytecode.BC_iinc_1;
import com.oracle.svm.webimage.jtt.bytecode.BC_iinc_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_iinc_3;
import com.oracle.svm.webimage.jtt.bytecode.BC_iinc_4;
import com.oracle.svm.webimage.jtt.bytecode.BC_iload_0;
import com.oracle.svm.webimage.jtt.bytecode.BC_iload_0_1;
import com.oracle.svm.webimage.jtt.bytecode.BC_iload_0_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_iload_1;
import com.oracle.svm.webimage.jtt.bytecode.BC_iload_1_1;
import com.oracle.svm.webimage.jtt.bytecode.BC_iload_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_iload_3;
import com.oracle.svm.webimage.jtt.bytecode.BC_imul;
import com.oracle.svm.webimage.jtt.bytecode.BC_ineg;
import com.oracle.svm.webimage.jtt.bytecode.BC_instanceof;
import com.oracle.svm.webimage.jtt.bytecode.BC_invokeinterface;
import com.oracle.svm.webimage.jtt.bytecode.BC_invokespecial;
import com.oracle.svm.webimage.jtt.bytecode.BC_invokespecial2;
import com.oracle.svm.webimage.jtt.bytecode.BC_invokestatic;
import com.oracle.svm.webimage.jtt.bytecode.BC_invokevirtual;
import com.oracle.svm.webimage.jtt.bytecode.BC_ior;
import com.oracle.svm.webimage.jtt.bytecode.BC_irem;
import com.oracle.svm.webimage.jtt.bytecode.BC_irem2;
import com.oracle.svm.webimage.jtt.bytecode.BC_irem3;
import com.oracle.svm.webimage.jtt.bytecode.BC_ireturn;
import com.oracle.svm.webimage.jtt.bytecode.BC_ishl;
import com.oracle.svm.webimage.jtt.bytecode.BC_ishr;
import com.oracle.svm.webimage.jtt.bytecode.BC_isub;
import com.oracle.svm.webimage.jtt.bytecode.BC_iushr;
import com.oracle.svm.webimage.jtt.bytecode.BC_ixor;
import com.oracle.svm.webimage.jtt.bytecode.BC_l2d;
import com.oracle.svm.webimage.jtt.bytecode.BC_l2f;
import com.oracle.svm.webimage.jtt.bytecode.BC_l2i;
import com.oracle.svm.webimage.jtt.bytecode.BC_l2i_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ladd;
import com.oracle.svm.webimage.jtt.bytecode.BC_ladd2;
import com.oracle.svm.webimage.jtt.bytecode.BC_laload;
import com.oracle.svm.webimage.jtt.bytecode.BC_land;
import com.oracle.svm.webimage.jtt.bytecode.BC_lastore;
import com.oracle.svm.webimage.jtt.bytecode.BC_lcmp;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldc_01;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldc_02;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldc_03;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldc_04;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldc_05;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldc_06;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldiv;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldiv2;
import com.oracle.svm.webimage.jtt.bytecode.BC_ldiv3;
import com.oracle.svm.webimage.jtt.bytecode.BC_lload_0;
import com.oracle.svm.webimage.jtt.bytecode.BC_lload_01;
import com.oracle.svm.webimage.jtt.bytecode.BC_lload_1;
import com.oracle.svm.webimage.jtt.bytecode.BC_lload_2;
import com.oracle.svm.webimage.jtt.bytecode.BC_lload_3;
import com.oracle.svm.webimage.jtt.bytecode.BC_lmul;
import com.oracle.svm.webimage.jtt.bytecode.BC_lneg;
import com.oracle.svm.webimage.jtt.bytecode.BC_lookupswitch01;
import com.oracle.svm.webimage.jtt.bytecode.BC_lookupswitch02;
import com.oracle.svm.webimage.jtt.bytecode.BC_lookupswitch03;
import com.oracle.svm.webimage.jtt.bytecode.BC_lookupswitch04;
import com.oracle.svm.webimage.jtt.bytecode.BC_lookupswitch05;
import com.oracle.svm.webimage.jtt.bytecode.BC_lor;
import com.oracle.svm.webimage.jtt.bytecode.BC_lrem;
import com.oracle.svm.webimage.jtt.bytecode.BC_lrem2;
import com.oracle.svm.webimage.jtt.bytecode.BC_lreturn;
import com.oracle.svm.webimage.jtt.bytecode.BC_lshl;
import com.oracle.svm.webimage.jtt.bytecode.BC_lshr;
import com.oracle.svm.webimage.jtt.bytecode.BC_lsub;
import com.oracle.svm.webimage.jtt.bytecode.BC_lushr;
import com.oracle.svm.webimage.jtt.bytecode.BC_lxor;
import com.oracle.svm.webimage.jtt.bytecode.BC_multianewarray01;
import com.oracle.svm.webimage.jtt.bytecode.BC_multianewarray02;
import com.oracle.svm.webimage.jtt.bytecode.BC_multianewarray03;
import com.oracle.svm.webimage.jtt.bytecode.BC_multianewarray04;
import com.oracle.svm.webimage.jtt.bytecode.BC_new;
import com.oracle.svm.webimage.jtt.bytecode.BC_newarray;
import com.oracle.svm.webimage.jtt.bytecode.BC_putfield_01;
import com.oracle.svm.webimage.jtt.bytecode.BC_putfield_02;
import com.oracle.svm.webimage.jtt.bytecode.BC_putfield_03;
import com.oracle.svm.webimage.jtt.bytecode.BC_putfield_04;
import com.oracle.svm.webimage.jtt.bytecode.BC_putstatic;
import com.oracle.svm.webimage.jtt.bytecode.BC_saload;
import com.oracle.svm.webimage.jtt.bytecode.BC_sastore;
import com.oracle.svm.webimage.jtt.bytecode.BC_tableswitch;
import com.oracle.svm.webimage.jtt.bytecode.BC_tableswitch2;
import com.oracle.svm.webimage.jtt.bytecode.BC_tableswitch3;
import com.oracle.svm.webimage.jtt.bytecode.BC_tableswitch4;
import com.oracle.svm.webimage.jtt.bytecode.BC_wide01;
import com.oracle.svm.webimage.jtt.bytecode.BC_wide02;

public class BytecodeTest extends JTTTestDispatcher {

    public static final Class<?>[] testClasses = {
                    /*
                     * Bytecodes that work arrays and references/classes. Ex. getstatic, getfield,
                     * newarray, arraylength..
                     */
                    BC_aaload_1.class, BC_aaload.class, BC_aastore.class,
                    BC_aload_0.class, BC_aload_1.class, BC_aload_2.class, BC_aload_3.class,
                    BC_anewarray.class,
                    BC_areturn.class,
                    BC_arraylength.class,
                    BC_athrow.class,
                    BC_baload.class,
                    BC_bastore.class,
                    BC_caload.class,
                    BC_castore.class,
                    BC_saload.class,
                    BC_sastore.class,
                    BC_instanceof.class,
                    BC_invokeinterface.class,
                    BC_invokespecial.class, BC_invokespecial2.class,
                    BC_invokestatic.class,
                    BC_invokevirtual.class,
                    BC_multianewarray01.class, BC_multianewarray02.class, BC_multianewarray03.class, BC_multianewarray04.class,
                    BC_new.class,
                    BC_newarray.class,
                    BC_getfield.class,
                    BC_getstatic_b.class,
                    BC_getstatic_c.class,
                    BC_getstatic_d.class,
                    BC_getstatic_f.class,
                    BC_getstatic_i.class,
                    BC_getstatic_l.class,
                    BC_getstatic_s.class,
                    BC_getstatic_z.class,
                    BC_checkcast01.class, BC_checkcast02.class, BC_checkcast03.class,
                    BC_putfield_01.class, BC_putfield_02.class, BC_putfield_03.class, BC_putfield_04.class,
                    BC_putstatic.class,

                    /*
                     * Bytecodes that work with doubles and floats.
                     */
                    BC_d2f.class,
                    BC_d2i01.class,
                    BC_d2i02.class,
                    BC_d2l01.class,
                    BC_d2l02.class,
                    BC_dadd.class,
                    BC_daload.class,
                    BC_dastore.class,
                    BC_dcmp01.class, BC_dcmp02.class, BC_dcmp03.class, BC_dcmp04.class, BC_dcmp05.class, BC_dcmp06.class, BC_dcmp07.class, BC_dcmp08.class, BC_dcmp09.class, BC_dcmp10.class,
                    BC_ddiv.class,
                    BC_dmul.class,
                    BC_dneg.class, BC_dneg2.class,
                    BC_drem.class,
                    BC_dreturn.class,
                    BC_dsub.class, BC_dsub2.class,
                    BC_f2d.class,
                    BC_f2i01.class,
                    BC_f2i02.class,
                    BC_f2l01.class,
                    BC_f2l02.class,
                    BC_fadd.class,
                    BC_faload.class,
                    BC_fastore.class,
                    BC_fcmp01.class, BC_fcmp02.class, BC_fcmp03.class, BC_fcmp04.class, BC_fcmp05.class, BC_fcmp06.class, BC_fcmp07.class, BC_fcmp08.class, BC_fcmp09.class, BC_fcmp10.class,
                    BC_fdiv.class,
                    BC_fload.class, BC_fload_2.class,
                    BC_fmul.class,
                    BC_fneg.class,
                    BC_frem.class,
                    BC_freturn.class,
                    BC_fsub.class,
                    BC_wide01.class, BC_wide02.class,

                    /*
                     * Bytecodes that work with jumps and wide instruction.
                     */
                    BC_lookupswitch01.class, BC_lookupswitch02.class, BC_lookupswitch03.class, BC_lookupswitch04.class, BC_lookupswitch05.class,
                    BC_tableswitch.class, BC_tableswitch2.class, BC_tableswitch3.class, BC_tableswitch4.class,
                    BC_ifeq.class, BC_ifeq_2.class, BC_ifeq_3.class,
                    BC_ifge.class, BC_ifge_2.class, BC_ifge_3.class,
                    BC_ifgt.class,
                    BC_ifne.class,
                    BC_iflt.class,
                    BC_ifle.class,
                    BC_ificmplt1.class, BC_ificmplt2.class,
                    BC_ificmpne1.class, BC_ificmpne2.class,
                    BC_ifnonnull.class, BC_ifnonnull_2.class, BC_ifnonnull_3.class,
                    BC_ifnull.class, BC_ifnull_2.class, BC_ifnull_3.class,
                    BC_wide01.class, BC_wide02.class,

                    /*
                     * Bytecodes that work with integers and longs.
                     */
                    BC_i2b.class,
                    BC_i2c.class,
                    BC_i2d.class,
                    BC_i2f.class,
                    BC_i2l.class,
                    BC_i2s.class,
                    BC_iadd.class, BC_iadd2.class, BC_iadd3.class,
                    BC_iand.class,
                    BC_iaload.class,
                    BC_iastore.class,
                    BC_iconst.class,
                    BC_idiv.class, BC_idiv2.class,
                    BC_iinc_1.class, BC_iinc_2.class, BC_iinc_3.class, BC_iinc_4.class,
                    BC_iload_1.class, BC_iload_2.class, BC_iload_3.class, BC_iload_1_1.class, BC_iload_0.class, BC_iload_0_1.class, BC_iload_0_2.class,
                    BC_imul.class,
                    BC_ineg.class,
                    BC_ior.class,
                    BC_irem.class, BC_irem2.class, BC_irem3.class,
                    BC_ireturn.class,
                    BC_ishl.class,
                    BC_ishr.class,
                    BC_isub.class,
                    BC_iushr.class,
                    BC_ixor.class,
                    BC_l2d.class,
                    BC_l2f.class,
                    BC_l2i.class, BC_l2i_2.class,
                    BC_ladd.class,
                    BC_ladd2.class,
                    BC_laload.class,
                    BC_land.class,
                    BC_lastore.class,
                    BC_lcmp.class,
                    BC_ldc_01.class, BC_ldc_02.class, BC_ldc_03.class, BC_ldc_04.class, BC_ldc_05.class, BC_ldc_06.class,
                    BC_ldiv.class, BC_ldiv2.class, BC_ldiv3.class,
                    BC_lload_0.class, BC_lload_01.class, BC_lload_1.class, BC_lload_2.class, BC_lload_3.class,
                    BC_lmul.class,
                    BC_lneg.class,
                    BC_lor.class,
                    BC_lrem.class, BC_lrem2.class,
                    BC_lreturn.class,
                    BC_lshl.class,
                    BC_lshr.class,
                    BC_lsub.class,
                    BC_lushr.class,
                    BC_lxor.class,
    };

    @NeverInline(value = "Test")
    public static void main(String[] args) throws ReflectiveOperationException {
        runClass(args[0], new String[0]);
    }

    public static Class<?>[] findTestClasses() {
        return testClasses;
    }

    public static final class SetupReflectiveTests implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            if (SubstrateOptions.Class.getValue().equals(BytecodeTest.class.getName())) {
                registerReflectionClasses(findTestClasses());
            }
        }
    }
}
