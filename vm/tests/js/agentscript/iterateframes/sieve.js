/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

function Natural() {
    this.x = 2;
};
Natural.prototype.next = function() {
    return this.x++;
};

function Filter(number) {
    this.number = number;
    this.next = null;
    this.last = this;
}
Filter.prototype.acceptAndAdd = function(n) {
  var filter = this;
  var sqrt = Math.sqrt(n);
  for (;;) {
      if (n % filter.number === 0) {
          return false;
      }
      if (filter.number > sqrt) {
          break;
      }
      filter = filter.next;
  }
  var newFilter = new Filter(n);
  this.last.next = newFilter;
  this.last = newFilter;
  return true;
};

function Primes(natural) {
    this.natural = natural;
    this.filter = null;
}
Primes.prototype.next = function() {
    for (;;) {
        var n = this.natural.next();
        if (this.filter === null) {
            this.filter = new Filter(n);
            return n;
        }
        if (this.filter.acceptAndAdd(n)) {
            return n;
        }
    }
};

function measure(prntCnt, upto) {
    var primes = new Primes(new Natural());

    var start = new Date().getTime();
    var cnt = 0;
    var res = -1;
    for (;;) {
        res = primes.next();
        cnt++;
        if (cnt % prntCnt === 0) {
            log("Computed " + cnt + " primes in " + (new Date().getTime() - start) + " ms. Last one is " + res);
            prntCnt *= 2;
        }
        if (upto && cnt >= upto) {
            break;
        }
    }
    return new Date().getTime() - start;
}

var log = typeof console !== 'undefined' ? console.log : (
    typeof println !== 'undefined' ? println : print
);
if (typeof count === 'undefined') {
    var count = 256 * 256;
    if (typeof process !== 'undefined' && process.argv.length === 3) {
      count = new Number(process.argv[2]);
    }
}
if (typeof setTimeout === 'undefined') {
    var pending = [];
    setTimeout = function(r) {
        var process = pending.length === 0;
        pending.push(r);
        if (process) {
            while (pending.length > 0) {
                pending[0]();
                pending.shift();
            }
        }
    };
}

function oneRound() {
    log("Hundred thousand prime numbers in " + measure(97, 100000) + " ms");
    if (count-- > 0) {
        setTimeout(oneRound, 50);
    }
}
setTimeout(oneRound, 50);
