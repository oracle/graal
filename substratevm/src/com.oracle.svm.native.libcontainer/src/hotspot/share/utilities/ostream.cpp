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

#include "memory/allocation.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
void outputStream::print_raw(const char* str, size_t len) {
  write(str, len);
}

stringStream::stringStream(size_t initial_capacity) :
  outputStream(),
  _buffer(_small_buffer),
  _written(0),
  _capacity(sizeof(_small_buffer)),
  _is_fixed(false)
{
  if (initial_capacity > _capacity) {
    grow(initial_capacity);
  }
  zero_terminate();
}


// Grow backing buffer to desired capacity. Don't call for fixed buffers
void stringStream::grow(size_t new_capacity) {
  assert(!_is_fixed, "Don't call for caller provided buffers");
  assert(new_capacity > _capacity, "Sanity");
  assert(new_capacity > sizeof(_small_buffer), "Sanity");
  if (_buffer == _small_buffer) {
    _buffer = NEW_C_HEAP_ARRAY(char, new_capacity, mtInternal);
    _capacity = new_capacity;
    if (_written > 0) {
      ::memcpy(_buffer, _small_buffer, _written);
    }
    zero_terminate();
  } else {
    _buffer = REALLOC_C_HEAP_ARRAY(char, _buffer, new_capacity, mtInternal);
    _capacity = new_capacity;
  }
}

void stringStream::write(const char* s, size_t len) {
  assert(_is_frozen == false, "Modification forbidden");
  assert(_capacity >= _written + 1, "Sanity");
  if (len == 0) {
    return;
  }
  const size_t reasonable_max_len = 1 * G;
  if (len >= reasonable_max_len) {
    assert(false, "bad length? (%zu)", len);
    return;
  }
  size_t write_len = 0;
  if (_is_fixed) {
    write_len = MIN2(len, _capacity - _written - 1);
  } else {
    write_len = len;
    size_t needed = _written + len + 1;
    if (needed > _capacity) {
      grow(MAX2(needed, _capacity * 2));
    }
  }
  assert(_written + write_len + 1 <= _capacity, "stringStream oob");
  if (write_len > 0) {
    ::memcpy(_buffer + _written, s, write_len);
    _written += write_len;
    zero_terminate();
  }

}

void stringStream::zero_terminate() {
  assert(_buffer != nullptr &&
         _written < _capacity, "sanity");
  _buffer[_written] = '\0';
}


stringStream::~stringStream() {
  if (!_is_fixed && _buffer != _small_buffer) {
    FREE_C_HEAP_ARRAY(char, _buffer);
  }
}

