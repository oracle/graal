/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_ALLOCATION_HPP
#define SHARE_MEMORY_ALLOCATION_HPP

#include "memory/allStatic.hpp"
#include "nmt/memTag.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#include <new>

class outputStream;
class Thread;
class JavaThread;

class AllocFailStrategy {
public:
  enum AllocFailEnum { EXIT_OOM, RETURN_NULL };
};
typedef AllocFailStrategy::AllocFailEnum AllocFailType;

// The virtual machine must never call one of the implicitly declared
// global allocation or deletion functions.  (Such calls may result in
// link-time or run-time errors.)  For convenience and documentation of
// intended use, classes in the virtual machine may be derived from one
// of the following allocation classes, some of which define allocation
// and deletion functions.
// Note: std::malloc and std::free should never called directly.

//
// For objects allocated in the resource area (see resourceArea.hpp).
// - ResourceObj
//
// For objects allocated in the C-heap (managed by: free & malloc and tracked with NMT)
// - CHeapObj
//
// For objects allocated on the stack.
// - StackObj
//
// For classes used as name spaces.
// - AllStatic
//
// For classes in Metaspace (class data)
// - MetaspaceObj
//
// The printable subclasses are used for debugging and define virtual
// member functions for printing. Classes that avoid allocating the
// vtbl entries in the objects should therefore not be the printable
// subclasses.
//
// The following macros and function should be used to allocate memory
// directly in the resource area or in the C-heap, The _OBJ variants
// of the NEW/FREE_C_HEAP macros are used for alloc/dealloc simple
// objects which are not inherited from CHeapObj, note constructor and
// destructor are not called. The preferable way to allocate objects
// is using the new operator.
//
// WARNING: The array variant must only be used for a homogeneous array
// where all objects are of the exact type specified. If subtypes are
// stored in the array then must pay attention to calling destructors
// at needed.
//
// NEW_RESOURCE_ARRAY*
// REALLOC_RESOURCE_ARRAY*
// FREE_RESOURCE_ARRAY*
// NEW_RESOURCE_OBJ*
// NEW_C_HEAP_ARRAY*
// REALLOC_C_HEAP_ARRAY*
// FREE_C_HEAP_ARRAY*
// NEW_C_HEAP_OBJ*
// FREE_C_HEAP_OBJ
//
// char* AllocateHeap(size_t size, MemTag mem_tag, const NativeCallStack& stack, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
// char* AllocateHeap(size_t size, MemTag mem_tag, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
// char* ReallocateHeap(char *old, size_t size, MemTag mem_tag, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
// void FreeHeap(void* p);
//

extern bool NMT_track_callsite;

class NativeCallStack;


char* AllocateHeap(size_t size,
                   MemTag mem_tag,
                   const NativeCallStack& stack,
                   AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
char* AllocateHeap(size_t size,
                   MemTag mem_tag,
                   AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);

char* ReallocateHeap(char *old,
                     size_t size,
                     MemTag mem_tag,
                     AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);

// handles null pointers
void FreeHeap(void* p);

class CHeapObjBase {
 public:
  ALWAYSINLINE void* operator new(size_t size, MemTag mem_tag) {
    return AllocateHeap(size, mem_tag);
  }

  ALWAYSINLINE void* operator new(size_t size,
                                  MemTag mem_tag,
                                  const NativeCallStack& stack) {
    return AllocateHeap(size, mem_tag, stack);
  }

  ALWAYSINLINE void* operator new(size_t size,
                                  MemTag mem_tag,
                                  const std::nothrow_t&,
                                  const NativeCallStack& stack) throw() {
    return AllocateHeap(size, mem_tag, stack, AllocFailStrategy::RETURN_NULL);
  }

  ALWAYSINLINE void* operator new(size_t size,
                                  MemTag mem_tag,
                                  const std::nothrow_t&) throw() {
    return AllocateHeap(size, mem_tag, AllocFailStrategy::RETURN_NULL);
  }

  ALWAYSINLINE void* operator new[](size_t size, MemTag mem_tag) {
    return AllocateHeap(size, mem_tag);
  }

  ALWAYSINLINE void* operator new[](size_t size,
                                    MemTag mem_tag,
                                    const NativeCallStack& stack) {
    return AllocateHeap(size, mem_tag, stack);
  }

  ALWAYSINLINE void* operator new[](size_t size,
                                    MemTag mem_tag,
                                    const std::nothrow_t&,
                                    const NativeCallStack& stack) throw() {
    return AllocateHeap(size, mem_tag, stack, AllocFailStrategy::RETURN_NULL);
  }

  ALWAYSINLINE void* operator new[](size_t size,
                                    MemTag mem_tag,
                                    const std::nothrow_t&) throw() {
    return AllocateHeap(size, mem_tag, AllocFailStrategy::RETURN_NULL);
  }

  void operator delete(void* p)     { FreeHeap(p); }
  void operator delete [] (void* p) { FreeHeap(p); }
};

// Uses the implicitly static new and delete operators of CHeapObjBase
template<MemTag MT>
class CHeapObj {
 public:
  ALWAYSINLINE void* operator new(size_t size) {
    return CHeapObjBase::operator new(size, MT);
  }

  ALWAYSINLINE void* operator new(size_t size,
                                  const NativeCallStack& stack) {
    return CHeapObjBase::operator new(size, MT, stack);
  }

  ALWAYSINLINE void* operator new(size_t size, const std::nothrow_t& nt,
                                  const NativeCallStack& stack) throw() {
    return CHeapObjBase::operator new(size, MT, nt, stack);
  }

  ALWAYSINLINE void* operator new(size_t size, const std::nothrow_t& nt) throw() {
    return CHeapObjBase::operator new(size, MT, nt);
  }

  ALWAYSINLINE void* operator new[](size_t size) {
    return CHeapObjBase::operator new[](size, MT);
  }

  ALWAYSINLINE void* operator new[](size_t size,
                                    const NativeCallStack& stack) {
    return CHeapObjBase::operator new[](size, MT, stack);
  }

  ALWAYSINLINE void* operator new[](size_t size, const std::nothrow_t& nt,
                                    const NativeCallStack& stack) throw() {
    return CHeapObjBase::operator new[](size, MT, nt, stack);
  }

  ALWAYSINLINE void* operator new[](size_t size, const std::nothrow_t& nt) throw() {
    return CHeapObjBase::operator new[](size, MT, nt);
  }

  void operator delete(void* p)     {
    CHeapObjBase::operator delete(p);
  }

  void operator delete [] (void* p) {
    CHeapObjBase::operator delete[](p);
  }
};

// Base class for objects allocated on the stack only.
// Calling new or delete will result in fatal error.

class StackObj {
 public:
  void* operator new(size_t size) = delete;
  void* operator new [](size_t size) = delete;
  void  operator delete(void* p) = delete;
  void  operator delete [](void* p) = delete;
};

#define NEW_C_HEAP_ARRAY(type, size, mem_tag)\
  (type*) (AllocateHeap((size) * sizeof(type), mem_tag))

#define NEW_C_HEAP_ARRAY2_RETURN_NULL(type, size, mem_tag, pc)\
  NEW_C_HEAP_ARRAY3(type, (size), mem_tag, pc, AllocFailStrategy::RETURN_NULL)

#define NEW_C_HEAP_ARRAY_RETURN_NULL(type, size, mem_tag)\
  NEW_C_HEAP_ARRAY2(type, (size), mem_tag, AllocFailStrategy::RETURN_NULL)

#define REALLOC_C_HEAP_ARRAY(type, old, size, mem_tag)\
  (type*) (ReallocateHeap((char*)(old), (size) * sizeof(type), mem_tag))

#define REALLOC_C_HEAP_ARRAY_RETURN_NULL(type, old, size, mem_tag)\
  (type*) (ReallocateHeap((char*)(old), (size) * sizeof(type), mem_tag, AllocFailStrategy::RETURN_NULL))

#define FREE_C_HEAP_ARRAY(type, old) \
  FreeHeap((char*)(old))

// allocate type in heap without calling ctor
#define NEW_C_HEAP_OBJ(type, mem_tag)\
  NEW_C_HEAP_ARRAY(type, 1, mem_tag)

#define NEW_C_HEAP_OBJ_RETURN_NULL(type, mem_tag)\
  NEW_C_HEAP_ARRAY_RETURN_NULL(type, 1, mem_tag)

// deallocate obj of type in heap without calling dtor
#define FREE_C_HEAP_OBJ(objname)\
  FreeHeap((char*)objname);


#endif // SHARE_MEMORY_ALLOCATION_HPP
