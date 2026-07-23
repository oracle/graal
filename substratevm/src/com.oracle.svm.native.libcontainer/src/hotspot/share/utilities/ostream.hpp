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

#ifndef SHARE_UTILITIES_OSTREAM_HPP
#define SHARE_UTILITIES_OSTREAM_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"


// Output streams for printing
//
// Printing guidelines:
// Where possible, please use tty->print() and tty->print_cr().
// For product mode VM warnings use warning() which internally uses tty.
// In places where tty is not initialized yet or too much overhead,
// we may use jio_printf:
//     jio_fprintf(defaultStream::output_stream(), "Message");
// This allows for redirection via -XX:+DisplayVMOutputToStdout and
// -XX:+DisplayVMOutputToStderr.

class outputStream : public CHeapObjBase {
  friend class StreamIndentor;


   // Automatic indentation. Returns old autoindent state.
   bool set_autoindent(bool value);

 public:
   void print_raw(const char* str)                { print_raw(str, strlen(str)); }
   void print_raw(const char* str, size_t len);
   virtual void write(const char* str, size_t len) = 0;
};


// for writing to strings; buffer will expand automatically.
// Buffer will always be zero-terminated.
class stringStream : public outputStream {
  DEBUG_ONLY(bool _is_frozen = false);
  char*  _buffer;
  size_t _written;  // Number of characters written, excluding termin. zero
  size_t _capacity;
  const bool _is_fixed;
  char   _small_buffer[48];

  // Grow backing buffer to desired capacity.
  void grow(size_t new_capacity);

  // zero terminate at buffer_pos.
  void zero_terminate();

 public:
  // Create a stringStream using an internal buffer of initially initial_bufsize size;
  // will be enlarged on demand. There is no maximum cap.
  stringStream(size_t initial_capacity = 0);
  ~stringStream();
  virtual void write(const char* c, size_t len);
  // Return number of characters written into buffer, excluding terminating zero and
  // subject to truncation in static buffer mode.
  size_t      size() const { return _written; }
  // Returns internal buffer containing the accumulated string.
  // Returned buffer is only guaranteed to be valid as long as stream is not modified
  const char* base() const { return _buffer; }
  // Freezes stringStream (no further modifications possible) and returns pointer to it.
  // No-op if stream is frozen already.
  // Returns the internal buffer containing the accumulated string.
  const char* freeze() NOT_DEBUG(const) {
    DEBUG_ONLY(_is_frozen = true);
    return _buffer;
  };
};


#endif // SHARE_UTILITIES_OSTREAM_HPP
