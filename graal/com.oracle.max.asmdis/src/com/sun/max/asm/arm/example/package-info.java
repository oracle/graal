/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
/**
 * This package presents an example of how to generate an assembler for a subset of the instructions in an ISA
 * specification. This example is in the context of the ARM ISA specification but the mechanism can be just as easily
 * applied to any other ISA.
 * <p>
 * The approach is to use an interface that specifies a subset of the methods generated in the complete assembler for
 * the ISA in question. The interface could be written by hand or produced using the "extract interface" refactoring
 * available in many modern Java IDEs. In this example, we used Eclipse to extract the
 * {@link ExampleARMAssemblerSpecification} interface from a random subset of the methods in {@link ARMRawAssembler} and
 * {@link ARMLabelAssembler}. Next, the class into which the assembler methods will be generated must be written.
 * The only constraint on this class (which respect to generation of the assembler methods) is that it has these
 * delimiter lines some where within its declaration:
 * <pre>
 * // START GENERATED RAW ASSEMBLER METHODS
 * // END GENERATED RAW ASSEMBLER METHODS
 * 
 * // START GENERATED LABEL ASSEMBLER METHODS
 * // END GENERATED LABEL ASSEMBLER METHODS
 * </pre>
 * In this example, the {@link ExampleARMAssembler} serves this purpose.
 * <p>
 * Lastly, the {@linkplain ARMAssemblerGenerator ARM assembler generator}
 * needs to be run with program arguments specifying which class to update and what
 * interface specifies the ISA subset. In this example, the execution of the
 * generator is done programatically by {@link ExampleARMAssemblerSpecification.Generator}.
 */
package com.sun.max.asm.arm.example;

