/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef GRAALVM_LLVM_POLYGLOT_TIME_H
#define GRAALVM_LLVM_POLYGLOT_TIME_H

/**
 * \defgroup polyglot LLVM Polyglot Time API
 * @{
 */

#if defined(__cplusplus)
extern "C" {
#endif

#include <time.h>
#include <graalvm/llvm/polyglot.h>

/**
  * Check whether a polyglot value is a time value.
  *
  * Returns false for pointers that do not point to a polyglot value (see
  * {@link polyglot_is_value}).
  *
  * @see org::graalvm::polyglot::Value::isTime
  */
bool polyglot_is_time(const polyglot_value v);

/**
  * Check whether a polyglot value is a date value.
  *
  * Returns false for pointers that do not point to a polyglot value (see
  * {@link polyglot_is_value}).
  *
  * @see org::graalvm::polyglot::Value::isDate
  */
bool polyglot_is_date(const polyglot_value v);

/**
  * Check whether a polyglot value is a timezone value.
  *
  * Returns false for pointers that do not point to a polyglot value (see
  * {@link polyglot_is_value}).
  *
  * @see org::graalvm::polyglot::Value::isTimeZone
  */
bool polyglot_is_timezone(const polyglot_value v);

/**
  * Check whether a polyglot value is an instant value.
  *
  * Returns false for pointers that do not point to a polyglot value (see
  * {@link polyglot_is_value}).
  *
  * @see org::graalvm::polyglot::Value::isInstant
  */
bool polyglot_is_instant(const polyglot_value v);

#include <graalvm/llvm/internal/polyglot-time-impl.h>

/**
 * Creates a managed polyglot value which represents an instant value. An
 * instant value represents a time and date at the UTC timezone.
 *
 * \code
 * time_t t;
 * time(&t);
 * polyglot_value v = polyglot_instant_from_time(&t);
 * \endcode
 *
 * The polyglot value does not need to be freed.
 *
 * @see org::graalvm::polyglot::Value::isInstant
 * @see org::graalvm::polyglot::Value::asInstant
 */
polyglot_value polyglot_instant_from_time(time_t t);

/**
 * Creates a polyglot value from a pointer to time_t value. The time value
 * represents a unix timestamp in seconds since 00:00 UTC, January 1, 1970. The
 * time value can be converted into an {@link java::time::Instant}, {@link
 * java::time::LocalDate}, {@link java::time::LocalTime} or {@link
 * java::time::ZoneId} (timezone) value.
 *
 * A unix timestamp is returned by the `time` function. The following example
 * shows how this function can be used:
 *
 * \code
 * time_t *pt = malloc(sizeof(*ret));
 * time(pt);
 * polyglot_value v = polyglot_from_time(pt);
 * \endcode
 *
 * The memory allocated must be manually freed by the programmer when not needed
 * anymore. It is recommended to use {@link polyglot_instant_from_time} instead.
 *
 * @param t a pointer to a unix timestamp
 */
polyglot_value polyglot_from_time_ptr(time_t *t);

/**
 * Reads a polyglot value that can be converted into an {@link
 * java::time::Instant} value and converts it into a unix timestamp. The time
 * value represents a unix timestamp in seconds since 00:00 UTC, January 1,
 * 1970.
 */
time_t polyglot_instant_as_time(polyglot_value v);

/**
  * Creates a polyglot value from a pointer to tm structure. The time structure
  * contains various fields representing seconds, minutes, hours, day, year and
  * month. The time value can be converted into {@link java::time::LocalDate} or
  * {@link java::time::LocalTime} value.
  *
  * A unix tm structure is returned by the `localtime` or `gmtime` function. The
  * following example shows how this function can be used:
  *
  * \code
  * time_t t;
  * time(&t);
  * polyglot_value v = polyglot_from_time(localtime(&t));
  * \endcode
  *
  * The polyglot value is only valid as long as the pointer is valid.
  *
  * @param t a pointer to a tm struct
  */
polyglot_value polyglot_from_tm(struct tm *t);

/**
 * Interpret a polyglot value that satisfies either the date or the time
 * interface as a tm struct. The resulting value is a managed object and does
 * not need to be freed from memory. If the polyglot value is a date value, the
 * `tm_mday`, `tm_mon`, `tm_year` and `tm_yday` fields may be read. If the
 * polyglot value is a time value, the `tm_sec`, `tm_min` and `tm_hour` fields
 * may be read.
 *
 * \code
 * struct tm * pt = polyglot_as_tm(v);
 * pt->tm_sec
 * \endcode
 *
 * If a native pointer to the `tm` struct is required, the {@link
 * polyglot_fill_tm} function can be used.
 *
 * @see org::graalvm::polyglot::Value::isDate
 * @see org::graalvm::polyglot::Value::isTime
 */
struct tm *polyglot_as_tm(polyglot_value value);

/**
  * When using standard library functions such as `asctime` a native pointer to
  * a `struct tm` is required. The `polyglot_fill_tm` reads the date and time
  * information from a struct into the spcefied `struct tm` pointer.
  *
  * \code
  * struct tm t = {0};
  * polyglot_fill_tm(v, &t);
  * char * c = asctime(&t);
  * \endcode

  * If the polyglot value is a time value, then the `tm_sec`, `tm_min` and
  * `tm_hour` fields are set. If the polyglot value is a date value, then the
  * `tm_mday`, `tm_mon`, `tm_year`, `tm_wday` and `tm_yday` fields are set. All
  * other fields are left unchanged, making it important to zero the input
  * first. This allows the following example:
  *
  * \code
  * struct tm t = {0};
  * polyglot_fill_tm(date_value, &t);
  * polylgot_fill_tm(time_value, &t);
  * char * c = asctime(&t);
  * \endcode
  *
  * @see org::graalvm::polyglot::Value::isDate
  * @see org::graalvm::polyglot::Value::isTime
  */
static void polyglot_fill_tm(polyglot_value value, struct tm *out);

/**
  * Constructs a managed object representing a timezone from the given zone
  * string such as 'Europe/Berlin'. The function supports timezone identifiers
  * that can be used by the @link{java::time::ZoneId}.
  *
  */
polyglot_value polyglot_timezone_from_id(polyglot_value id);

/**
  * Retrieve the timezone id as a polyglot value representing a String. The
  * value can be converted into a C string by using {@link polyglot_as_string}.
  *
  * @param value the polyglot value to be converted
  * @return a polyglot string value
  */
polyglot_value polyglot_timezone_get_id(polyglot_value timezone);

#if defined(__cplusplus)
}
#endif

/** @} */

#endif
