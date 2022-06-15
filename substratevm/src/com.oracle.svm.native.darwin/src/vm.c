/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#include<mach/mach.h>
#include<mach/mach_vm.h>

static int is_protected(int prot) {
    return !(prot & (VM_PROT_READ | VM_PROT_WRITE));
}

int vm_compute_stack_guard(void *stack_end) {
    int guard_size = 0;

    mach_vm_address_t address = (mach_vm_address_t) stack_end;
    mach_vm_size_t size = 0;
    vm_region_basic_info_data_64_t info;

    mach_port_t task = mach_task_self();

    do {
        mach_port_t dummyobj;
        mach_msg_type_number_t count = VM_REGION_SUBMAP_INFO_COUNT_64;
        kern_return_t kr = mach_vm_region(task, &address, &size, VM_REGION_BASIC_INFO_64, (vm_region_info_t)&info, &count, &dummyobj);
        if (kr != KERN_SUCCESS) {
            return -1;
        }

        if (is_protected(info.protection)) {
            guard_size += size;
        }
        address += size;
    } while(is_protected(info.protection));

    return guard_size;
}
