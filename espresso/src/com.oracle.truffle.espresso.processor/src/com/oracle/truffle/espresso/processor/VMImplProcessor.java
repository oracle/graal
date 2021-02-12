/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.processor;

public class VMImplProcessor extends IntrinsicsProcessor {
    // Processor-specific constants

    private static final String ENV_PACKAGE = "com.oracle.truffle.espresso.vm";

    private static final String VM_IMPL = ENV_PACKAGE + "." + "VmImpl";

    private static final String VM = "VM";
    private static final String VM_NAME = "vm";

    private static final String COLLECTOR = "VMCollector";
    private static final String COLLECTOR_INSTANCE_NAME = "vmCollector";

    public VMImplProcessor() {
        super(VM_NAME, VM, VM_IMPL, ENV_PACKAGE, COLLECTOR, COLLECTOR_INSTANCE_NAME);
    }
}