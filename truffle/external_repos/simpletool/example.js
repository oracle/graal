/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
var N = 2000;
var EXPECTED = 17393;

function Natural() {
    x = 2;
    return {
        'next' : function() { return x++; }
    };
}

function Filter(number, filter) {
    var self = this;
    this.number = number;
    this.filter = filter;
    this.accept = function(n) {
      var filter = self;
      for (;;) {
          if (n % filter.number === 0) {
              return false;
          }
          filter = filter.filter;
          if (filter === null) {
              break;
          }
      }
      return true;
    };
    return this;
}

function Primes(natural) {
    var self = this;
    this.natural = natural;
    this.filter = null;
    this.next = function() {
        for (;;) {
            var n = self.natural.next();
            if (self.filter === null || self.filter.accept(n)) {
                self.filter = new Filter(n, self.filter);
                return n;
            }
        }
    };
}

var holdsAFunctionThatIsNeverCalled = function(natural) {
    var self = this;
    this.natural = natural;
    this.filter = null;
    this.next = function() {
        for (;;) {
            var n = self.natural.next();
            if (self.filter === null || self.filter.accept(n)) {
                self.filter = new Filter(n, self.filter);
                return n;
            }
        }
    };
}

var holdsAFunctionThatIsNeverCalledOneLine = function() {return null;}

function primesMain() {
    var primes = new Primes(Natural());
    var primArray = [];
    for (var i=0;i<=N;i++) { primArray.push(primes.next()); }
    if (primArray[N] != EXPECTED) { throw new Error('wrong prime found: ' + primArray[N]); }
}
primesMain();
