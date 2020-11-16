# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

# Note: `while` is used instead of `for`, as Ruby's `for` is syntatic sugar for
# closures: `for i in 1..n; body; end` is the same as `(1..n).each { |i| body }`
def run
  i = 2
  number = 600000
  primes = Array.new(number + 1, 0)

  while i <= number
    primes[i] = i
    i += 1
  end

  i = 2
  while i * i <= number
    if primes[i] != 0
      j = 2
      while j < number
        if primes[i] * j > number
          break
        else
          primes[primes[i] * j] = 0
        end
        j += 1
      end
    end
    i += 1
  end

  count = 0
  c = 2
  while c <= number
    if primes[c] != 0
      count += 1
    end
    c += 1
  end
  return count
end
