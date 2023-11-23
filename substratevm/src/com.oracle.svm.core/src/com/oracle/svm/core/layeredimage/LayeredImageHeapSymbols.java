/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.layeredimage;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;

import jdk.graal.compiler.word.Word;

/**
 * Stores the heap symbols for the application layer. This is a temporary solution and should be
 * fixed with GR-53995.
 */
public class LayeredImageHeapSymbols {
    public static final String SECOND_IMAGE_HEAP_BEGIN_SYMBOL_NAME = "__svm_second_heap_begin";
    public static final String SECOND_IMAGE_HEAP_END_SYMBOL_NAME = "__svm_second_heap_end";
    public static final String SECOND_IMAGE_HEAP_RELOCATABLE_BEGIN_SYMBOL_NAME = "__svm_second_heap_relocatable_begin";
    public static final String SECOND_IMAGE_HEAP_RELOCATABLE_END_SYMBOL_NAME = "__svm_second_heap_relocatable_end";
    public static final String SECOND_IMAGE_HEAP_A_RELOCATABLE_POINTER_SYMBOL_NAME = "__svm_second_heap_a_relocatable_pointer";
    public static final String SECOND_IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME = "__svm_second_heap_writable_begin";
    public static final String SECOND_IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME = "__svm_second_heap_writable_end";

    public static final CGlobalData<Word> SECOND_IMAGE_HEAP_BEGIN = CGlobalDataFactory.forSymbol(SECOND_IMAGE_HEAP_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> SECOND_IMAGE_HEAP_END = CGlobalDataFactory.forSymbol(SECOND_IMAGE_HEAP_END_SYMBOL_NAME);
    public static final CGlobalData<Word> SECOND_IMAGE_HEAP_RELOCATABLE_BEGIN = CGlobalDataFactory.forSymbol(SECOND_IMAGE_HEAP_RELOCATABLE_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> SECOND_IMAGE_HEAP_RELOCATABLE_END = CGlobalDataFactory.forSymbol(SECOND_IMAGE_HEAP_RELOCATABLE_END_SYMBOL_NAME);
    public static final CGlobalData<Word> SECOND_IMAGE_HEAP_A_RELOCATABLE_POINTER = CGlobalDataFactory.forSymbol(SECOND_IMAGE_HEAP_A_RELOCATABLE_POINTER_SYMBOL_NAME);
    public static final CGlobalData<Word> SECOND_IMAGE_HEAP_WRITABLE_BEGIN = CGlobalDataFactory.forSymbol(SECOND_IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> SECOND_IMAGE_HEAP_WRITABLE_END = CGlobalDataFactory.forSymbol(SECOND_IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME);
}
