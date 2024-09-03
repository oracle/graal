/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#ifndef NATIVE_IMAGE
#include "runtime/timer.hpp"
#endif // !NATIVE_IMAGE
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#ifndef NATIVE_IMAGE

namespace svm_container {

DEBUG_ONLY(class ResourceMark;)

} // namespace svm_container

#endif // !NATIVE_IMAGE

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


namespace svm_container {

class outputStream : public CHeapObjBase {
#ifndef NATIVE_IMAGE
 private:
   NONCOPYABLE(outputStream);
   int _indentation; // current indentation
   bool _autoindent; // if true, every line starts with indentation

 protected:
   int _position;    // visual position on the current line
   uint64_t _precount; // number of chars output, less than _position
   TimeStamp _stamp; // for time stamps
   char* _scratch;   // internal scratch buffer for printf
   size_t _scratch_len; // size of internal scratch buffer

  // Returns whether a newline was seen or not
   bool update_position(const char* s, size_t len);

  // Processes the given format string and the supplied arguments
  // to produce a formatted string in the supplied buffer. Returns
  // the formatted string (in the buffer). If the formatted string
  // would be longer than the buffer, it is truncated.
  //
  // If the format string is a plain string (no format specifiers)
  // or is exactly "%s" to print a supplied argument string, then
  // the buffer is ignored, and we return the string directly.
  // However, if `add_cr` is true then we have to copy the string
  // into the buffer, which risks truncation if the string is too long.
  //
  // The `result_len` reference is always set to the length of the returned string.
  //
  // If add_cr is true then the cr will always be placed in the buffer (buffer minimum size is 2).
  //
  // In a debug build, if truncation occurs a VM warning is issued.
   static const char* do_vsnprintf(char* buffer, size_t buflen,
                                   const char* format, va_list ap,
                                   bool add_cr,
                                   size_t& result_len)  ATTRIBUTE_PRINTF(3, 0);

   // calls do_vsnprintf and writes output to stream; uses an on-stack buffer.
   void do_vsnprintf_and_write_with_automatic_buffer(const char* format, va_list ap, bool add_cr) ATTRIBUTE_PRINTF(2, 0);
   // calls do_vsnprintf and writes output to stream; uses the user-provided buffer;
   void do_vsnprintf_and_write_with_scratch_buffer(const char* format, va_list ap, bool add_cr) ATTRIBUTE_PRINTF(2, 0);
   // calls do_vsnprintf, then writes output to stream.
   void do_vsnprintf_and_write(const char* format, va_list ap, bool add_cr) ATTRIBUTE_PRINTF(2, 0);
#endif // !NATIVE_IMAGE

 public:
#ifndef NATIVE_IMAGE
   class TestSupport;  // Unit test support

   // creation
   outputStream(bool has_time_stamps = false);

   // indentation
   outputStream& indent();
   void inc() { _indentation++; };
   void dec() { _indentation--; };
   void inc(int n) { _indentation += n; };
   void dec(int n) { _indentation -= n; };
   int  indentation() const    { return _indentation; }
   void set_indentation(int i) { _indentation = i;    }
   int fill_to(int col);
   void move_to(int col, int slop = 6, int min_space = 2);

   // Automatic indentation:
   // If autoindent mode is on, the following APIs will automatically indent
   // line starts depending on the current indentation level:
   // print(), print_cr(), print_raw(), print_raw_cr()
   // Other APIs are unaffected
   // Returns old autoindent state.
   bool set_autoindent(bool value);

   // sizing
   int position() const { return _position; }
   julong count() const { return _precount + _position; }
   void set_count(julong count) { _precount = count - _position; }
   void set_position(int pos)   { _position = pos; }

   // printing
   // Note that (v)print_cr forces the use of internal buffering to allow
   // appending of the "cr". This can lead to truncation if the buffer is
   // too small.

   void print(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
   void print_cr(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
   void vprint(const char *format, va_list argptr) ATTRIBUTE_PRINTF(2, 0);
   void vprint_cr(const char* format, va_list argptr) ATTRIBUTE_PRINTF(2, 0);
#endif // !NATIVE_IMAGE
   void print_raw(const char* str)                { print_raw(str, strlen(str)); }
   void print_raw(const char* str, size_t len);
#ifndef NATIVE_IMAGE
   void print_raw_cr(const char* str)             { print_raw(str); cr(); }
   void print_raw_cr(const char* str, size_t len) { print_raw(str, len); cr(); }
   void print_data(void* data, size_t len, bool with_ascii, bool rel_addr=true);
   void put(char ch);
   void sp(int count = 1);
   void cr();
   void cr_indent();
   void bol() { if (_position > 0)  cr(); }


   // Time stamp
   TimeStamp& time_stamp() { return _stamp; }
   void stamp();
   void stamp(bool guard, const char* prefix, const char* suffix);
   void stamp(bool guard) {
     stamp(guard, "", ": ");
   }
   // Date stamp
   void date_stamp(bool guard, const char* prefix, const char* suffix);
   // A simplified call that includes a suffix of ": "
   void date_stamp(bool guard) {
     date_stamp(guard, "", ": ");
   }

   // portable printing of 64 bit integers
   void print_jlong(jlong value);
   void print_julong(julong value);

   // flushing
   virtual void flush() {}
#endif // !NATIVE_IMAGE
   virtual void write(const char* str, size_t len) = 0;
#ifndef NATIVE_IMAGE
   virtual void rotate_log(bool force, outputStream* out = nullptr) {} // GC log rotation
   virtual ~outputStream() {}   // close properly on deletion

   // Caller may specify their own scratch buffer to use for printing; otherwise,
   // an automatic buffer on the stack (with O_BUFLEN len) is used.
   void set_scratch_buffer(char* p, size_t len) { _scratch = p; _scratch_len = len; }

   void dec_cr() { dec(); cr(); }
   void inc_cr() { inc(); cr(); }
#endif // !NATIVE_IMAGE
};

#ifndef NATIVE_IMAGE
// standard output
// ANSI C++ name collision
extern outputStream* tty;           // tty output

class streamIndentor : public StackObj {
  outputStream* const _str;
  const int _amount;
  NONCOPYABLE(streamIndentor);
public:
  streamIndentor(outputStream* str, int amt = 2) : _str(str), _amount(amt) {
    _str->inc(_amount);
  }
  ~streamIndentor() { _str->dec(_amount); }
};

class StreamAutoIndentor : public StackObj {
  outputStream* const _os;
  const bool _old;
  NONCOPYABLE(StreamAutoIndentor);
 public:
  StreamAutoIndentor(outputStream* os) :
    _os(os), _old(os->set_autoindent(true)) {}
  ~StreamAutoIndentor() { _os->set_autoindent(_old); }
};

// advisory locking for the shared tty stream:
class ttyLocker: StackObj {
  friend class ttyUnlocker;
 private:
  intx _holder;

 public:
  static intx  hold_tty();                // returns a "holder" token
  static void  release_tty(intx holder);  // must witness same token
  static bool  release_tty_if_locked();   // returns true if lock was released
  static void  break_tty_lock_for_safepoint(intx holder);

  ttyLocker()  { _holder = hold_tty(); }
  ~ttyLocker() { release_tty(_holder); }
};

// Release the tty lock if it's held and reacquire it if it was
// locked.  Used to avoid lock ordering problems.
class ttyUnlocker: StackObj {
 private:
  bool _was_locked;
 public:
  ttyUnlocker()  {
    _was_locked = ttyLocker::release_tty_if_locked();
  }
  ~ttyUnlocker() {
    if (_was_locked) {
      ttyLocker::hold_tty();
    }
  }
};
#endif // !NATIVE_IMAGE

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
#ifndef NATIVE_IMAGE
  // Creates a stringStream using a caller-provided buffer. Will truncate silently if
  // it overflows.
  stringStream(char* fixed_buffer, size_t fixed_buffer_size);
#endif // !NATIVE_IMAGE
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
#ifndef NATIVE_IMAGE
  void  reset();
  bool is_empty() const { return _buffer[0] == '\0'; }
  // Copy to a resource, or C-heap, array as requested
  char* as_string(bool c_heap = false) const;
#endif // !NATIVE_IMAGE
};

#ifndef NATIVE_IMAGE
class fileStream : public outputStream {
 protected:
  FILE* _file;
  bool  _need_close;
 public:
  fileStream() { _file = nullptr; _need_close = false; }
  fileStream(const char* file_name);
  fileStream(const char* file_name, const char* opentype);
  fileStream(FILE* file, bool need_close = false) { _file = file; _need_close = need_close; }
  ~fileStream();
  bool is_open() const { return _file != nullptr; }
  virtual void write(const char* c, size_t len);
  // unlike other classes in this file, fileStream can perform input as well as output
  size_t read(void* data, size_t size) {
    if (_file == nullptr)  return 0;
    return ::fread(data, 1, size, _file);
  }
  size_t read(void *data, size_t size, size_t count) {
    return read(data, size * count);
  }
  void close() {
    if (_file == nullptr || !_need_close)  return;
    fclose(_file);
    _need_close = false;
  }
  long fileSize();
  void flush();
};

// unlike fileStream, fdStream does unbuffered I/O by calling
// open() and write() directly. It is async-safe, but output
// from multiple thread may be mixed together. Used by fatal
// error handler.
class fdStream : public outputStream {
 protected:
  int  _fd;
  static fdStream _stdout_stream;
  static fdStream _stderr_stream;
 public:
  fdStream(int fd = -1) : _fd(fd) { }
  bool is_open() const { return _fd != -1; }
  void set_fd(int fd) { _fd = fd; }
  int fd() const { return _fd; }
  virtual void write(const char* c, size_t len);
  void flush() {};

  // predefined streams for unbuffered IO to stdout, stderr
  static fdStream* stdout_stream() { return &_stdout_stream; }
  static fdStream* stderr_stream() { return &_stderr_stream; }
};

// A /dev/null equivalent stream
class nullStream : public outputStream {
public:
  void write(const char* c, size_t len) {}
  void flush() {};
};

void ostream_init();
void ostream_init_log();
void ostream_exit();
void ostream_abort();
const char* make_log_name(const char* log_name, const char* force_directory);

// In the non-fixed buffer case an underlying buffer will be created and
// managed in C heap. Not MT-safe.
class bufferedStream : public outputStream {
 protected:
  char*  buffer;
  size_t buffer_pos;
  size_t buffer_max;
  size_t buffer_length;
  bool   truncated;
 public:
  bufferedStream(size_t initial_bufsize = 256, size_t bufmax = 1024*1024*10);
  ~bufferedStream();
  virtual void write(const char* c, size_t len);
  size_t      size() { return buffer_pos; }
  const char* base() { return buffer; }
  void  reset() { buffer_pos = 0; _precount = 0; _position = 0; }
  char* as_string();
};

#define O_BUFLEN 2000   // max size of output of individual print() methods

#ifndef PRODUCT

class networkStream : public bufferedStream {

  private:
    int _socket;

  public:
    networkStream();
    ~networkStream();

    bool connect(const char *host, short port);
    bool is_open() const { return _socket != -1; }
    ssize_t read(char *buf, size_t len);
    void close();
    virtual void flush();
};

#endif
#endif // !NATIVE_IMAGE


} // namespace svm_container

#endif // SHARE_UTILITIES_OSTREAM_HPP
