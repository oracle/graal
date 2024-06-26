/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

#include <gdbJITCompilationInterface.h>

#include <stdlib.h>
#include <assert.h>


/* Make sure to specify the version statically, because the
   debugger may check the version before we can set it.  */
struct jit_descriptor __jit_debug_descriptor = { 1, 0, 0, 0 };


struct jit_code_entry *register_jit_code(const char *addr, uint64_t size)
{
    /* Create new jit_code_entry */
    struct jit_code_entry *const entry = malloc(sizeof(*entry));
    entry->symfile_addr = addr;
    entry->symfile_size = size;

	/* Insert entry at head of the list. */
	struct jit_code_entry *const next_entry = __jit_debug_descriptor.first_entry;
	entry->prev_entry = NULL;
	entry->next_entry = next_entry;

	if (next_entry != NULL) {
	    next_entry->prev_entry = entry;
        }

	/* Notify GDB.  */
	__jit_debug_descriptor.action_flag = JIT_REGISTER_FN;
	__jit_debug_descriptor.first_entry = entry;
	__jit_debug_descriptor.relevant_entry = entry;
	__jit_debug_register_code();

    return entry;
}


void unregister_jit_code(struct jit_code_entry *const entry)
{
    struct jit_code_entry *const prev_entry = entry->prev_entry;
    struct jit_code_entry *const next_entry = entry->next_entry;

    /* Fix prev and next in list */
    if (next_entry != NULL) {
	next_entry->prev_entry = prev_entry;
    }

    if (prev_entry != NULL) {
	prev_entry->next_entry = next_entry;
    } else {
	assert(__jit_debug_descriptor.first_entry == entry);
	__jit_debug_descriptor.first_entry = next_entry;
    }

    /* Notify GDB.  */
    __jit_debug_descriptor.action_flag = JIT_UNREGISTER_FN;
    __jit_debug_descriptor.relevant_entry = entry;
    __jit_debug_register_code();

    free(entry);
}
