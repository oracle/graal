/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_LOGGING_LOG_HPP
#define SHARE_LOGGING_LOG_HPP

#include "utilities/debug.hpp"

//
// Logging macros
//
// Usage:
//   log_<level>(<comma separated log tags>)(<printf-style log arguments>);
// e.g.
//   log_debug(logging)("message %d", i);
//
// Note that these macros will not evaluate the arguments unless the logging is enabled.
//

#if defined(LOG_LEVEL) && defined(PRINT_WARNINGS)

#define Error 1
#define Warning 2
#define Info 3
#define Debug 4
#define Trace 5

#define log_error(...)   (!log_is_enabled(Error,   __VA_ARGS__)) ? (void)0 : warning
#define log_warning(...) (!log_is_enabled(Warning, __VA_ARGS__)) ? (void)0 : warning
#define log_info(...)    (!log_is_enabled(Info,    __VA_ARGS__)) ? (void)0 : warning
#define log_debug(...)   (!log_is_enabled(Debug,   __VA_ARGS__)) ? (void)0 : warning
#define log_trace(...)   (!log_is_enabled(Trace,   __VA_ARGS__)) ? (void)0 : warning

#define log_is_enabled(level, ...) (level <= LOG_LEVEL)

#else
#define log_error(...)   DUMMY_ARGUMENT_CONSUMER
#define log_warning(...) DUMMY_ARGUMENT_CONSUMER
#define log_info(...)    DUMMY_ARGUMENT_CONSUMER
#define log_debug(...)   DUMMY_ARGUMENT_CONSUMER
#define log_trace(...)   DUMMY_ARGUMENT_CONSUMER

#define DUMMY_ARGUMENT_CONSUMER(...)

// Convenience macro to test if the logging is enabled on the specified level for given tags.
#define log_is_enabled(level, ...) false

#endif // LOG_LEVEL

#endif // SHARE_LOGGING_LOG_HPP
