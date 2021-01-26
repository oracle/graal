/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/* This benchmark is is derived from Stefan Marr's Are-We-Fast-Yet benchmark
 * suite available at https://github.com/smarr/are-we-fast-yet
 */
'use strict';

var INITIAL_SIZE = 10, INITIAL_CAPACITY = 16;

function Pair(key, val) {
  this.key   = key;
  this.value = val;
}

function Vector(size) {
  this.storage  = new Array(size === undefined ? 50 : size);
  this.firstIdx = 0;
  this.lastIdx  = 0;
}

Vector.with = function (elem) {
  var v = new Vector(1);
  v.append(elem);
  return v;
};

Vector.prototype.at = function (idx) {
  if (idx >= this.storage.length) {
    return null;
  }
  return this.storage[idx];
};

Vector.prototype.atPut = function (idx, val) {
  if (this.idx >= this.storage.length) {
    var newLength = this.storage.length;
    while (newLength <= idx) {
      newLength *= 2;
    }
    this.storage = this.storage.slice();
    this.storage.length = newLength;
  }
  this.storage[idx] = val;
  if (this.lastIdx < idx + 1) {
    this.lastIdx = idx + 1;
  }
};

Vector.prototype.append = function (elem) {
  if (this.lastIdx >= this.storage.length) {
    // Copy storage to comply with rules, but don't extend storage
    var newLength = this.storage.length * 2;
    this.storage = this.storage.slice();
    this.storage.length = newLength;
  }

  this.storage[this.lastIdx] = elem;
  this.lastIdx += 1;
};

Vector.prototype.isEmpty = function () {
  return this.lastIdx === this.firstIdx;
};

Vector.prototype.forEach = function (fn) {
  for (var i = this.firstIdx; i < this.lastIdx; i++) {
    fn(this.storage[i]);
  }
};

Vector.prototype.hasSome = function(fn) {
  for (var i = this.firstIdx; i < this.lastIdx; i++) {
    if (fn(this.storage[i])) {
      return true;
    }
  }
  return false;
};

Vector.prototype.getOne = function (fn) {
  for (var i = this.firstIdx; i < this.lastIdx; i++) {
    var e = this.storage[i];
    if (fn(e)) {
      return e;
    }
  }
  return null;
};

Vector.prototype.removeFirst = function () {
  if (this.isEmpty()) {
    return null;
  }
  this.firstIdx++;
  return this.storage[this.firstIdx - 1];
};

Vector.prototype.remove = function (obj) {
  var newArray = new Array(this.capacity());
  var newLast = 0;
  var found = false;

  this.forEach(function (it) {
    if (it == obj) {
      found = true;
    } else {
      newArray[newLast] = it;
      newLast++;
    }
  });

  this.storage  = newArray;
  this.lastIdx  = newLast;
  this.firstIdx = 0;
  return found;
};

Vector.prototype.removeAll = function () {
  this.firstIdx = 0;
  this.lastIdx = 0;
  this.storage = new Array(this.storage.length);
};

Vector.prototype.size = function () {
  return this.lastIdx - this.firstIdx;
};

Vector.prototype.capacity = function () {
  return this.storage.length;
};

Vector.prototype.sortRange = function (i, j, compare) {
  if (!compare) {
    this.defaultSort(i, j);
  }

  var n = j + 1 - i;
  if (n <= 1) {
    return;
  }

  var di = this.storage[i];
  var dj = this.storage[j];

  if (compare(di, dj)) {
    this.swap(this.storage, i, j);
    var tt = di;
    di = dj;
    dj = tt;
  }

  if (n > 2) {
    var ij = (i + j) / 2;
    var dij = this.storage[ij];

    if (compare(di, dij) <= 0) {
      if (!compare(dij, dj)) {
        this.swap(this.storage, j, ij);
        dij = dj;
      }
    } else {
      this.swap(this.storage, i, ij);
      dij = di;
    }

    if (n > 3) {
      var k = i;
      var l = j - 1;

      while (true) {
        while (k <= l && compare(dij, this.storage[l])) {
          l -= 1;
        }

        k += 1;
        while (k <= l && compare(this.storage[k], dij)) {
          k += 1;
        }

        if (k > l) {
          break;
        }
        this.swap(this.storage, k, l);
      }
      var c = null; // never used
      this.sort(i, l, c);
      this.sort(k, j, c);
    }
  }
};

Vector.prototype.sort = function(compare) {
  if (this.size() > 0) {
    this.sortRange(this.firstIdx, this.lastIdx - 1, compare);
  }
};

function Set(size) {
  this.items = new Vector(size === undefined ? INITIAL_SIZE : size);
}

Set.prototype.size = function () {
  return this.items.size();
};

Set.prototype.forEach = function (fn) {
  this.items.forEach(fn);
};

Set.prototype.hasSome = function (fn) {
  return this.items.hasSome(fn);
};

Set.prototype.getOne = function (fn) {
  return this.items.getOne(fn);
};

Set.prototype.add = function (obj) {
  if (!this.contains(obj)) {
    this.items.append(obj);
  }
};

Set.prototype.contains = function (obj) {
  return this.hasSome(function (e) { return e == obj; } );
};

Set.prototype.removeAll = function () {
  this.items.removeAll();
};

Set.prototype.collect = function (fn) {
  var coll = new Vector();

  this.forEach(function (e) {
    coll.append(fn(e));
  });

  return coll;
};

function IdentitySet(size) {
  Set.call(this, size === undefined ? INITIAL_SIZE : size);
}
IdentitySet.prototype = Object.create(Set.prototype);

IdentitySet.prototype.contains = function (obj) {
  return this.hasSome(function (e) { return e == obj; });
};

function DictEntry(hash, key, value, next) {
  this.hash  = hash;
  this.key   = key;
  this.value = value;
  this.next  = next;
}

DictEntry.prototype.match = function(hash, key) {
  return this.hash == hash && key == this.key;
};

function Dictionary (size) {
  this.buckets = new Array(size === undefined ? INITIAL_CAPACITY : size);
  this.size_ = 0;
}

function hash(key) {
  if (!key) {
    return 0;
  }
  var hash = key.customHash();
  return hash ^ hash >>> 16;
}

Dictionary.prototype.size = function () {
  return this.size_;
};

Dictionary.prototype.isEmpty = function () {
  return this.size_ === 0;
};

Dictionary.prototype.getBucketIdx = function (hash) {
  return (this.buckets.length - 1) & hash;
};

Dictionary.prototype.getBucket = function (hash) {
  return this.buckets[this.getBucketIdx(hash)];
};

Dictionary.prototype.at = function (key) {
  var hash_ = hash(key),
    e = this.getBucket(hash_);

  while (e) {
    if (e.match(hash_, key)) {
      return e.value;
    }
    e = e.next;
  }
  return null;
};

Dictionary.prototype.containsKey = function (key) {
  var hash_ = hash(key),
    e = this.getBucket(hash_);

  while (e) {
    if (e.match(hash_, key)) {
      return true;
    }
    e = e.next;
  }
  return false;
};

Dictionary.prototype.atPut = function (key, value) {
  var hash_ = hash(key),
    i = this.getBucketIdx(hash_),
    current = this.buckets[i];

  if (!current) {
    this.buckets[i] = this.newEntry(key, value, hash_);
    this.size_ += 1;
  } else {
    this.insertBucketEntry(key, value, hash_, current);
  }

  if (this.size_ > this.buckets.length) {
    this.resize();
  }
};

Dictionary.prototype.newEntry = function (key, value, hash) {
  return new DictEntry(hash, key, value, null);
};

Dictionary.prototype.insertBucketEntry = function (key, value, hash, head) {
  var current = head;

  while (true) {
    if (current.match(hash, key)) {
      current.value = value;
      return;
    }
    if (!current.next) {
      this.size_ += 1;
      current.next = this.newEntry(key, value, hash);
      return;
    }
    current = current.next;
  }
};

Dictionary.prototype.resize = function () {
  var oldStorage = this.buckets;
  this.buckets = new Array(oldStorage.length * 2);
  this.transferEntries(oldStorage);
};

Dictionary.prototype.transferEntries = function (oldStorage) {
  for (var i = 0; i < oldStorage.length; ++i) {
    var current = oldStorage[i];
    if (current) {
      oldStorage[i] = null;

      if (!current.next) {
        this.buckets[current.hash & (this.buckets.length - 1)] = current;
      } else {
        this.splitBucket(oldStorage, i, current);
      }
    }
  }
};

Dictionary.prototype.splitBucket = function (oldStorage, i, head) {
  var loHead = null, loTail = null,
    hiHead = null, hiTail = null,
    current = head;

  while (current) {
    if ((current.hash & oldStorage.length) === 0) {
      if (!loTail) {
        loHead = current;
      } else {
        loTail.next = current;
      }
      loTail = current;
    } else {
      if (!hiTail) {
        hiHead = current;
      } else {
        hiTail.next = current;
      }
      hiTail = current;
    }
    current = current.next;
  }

  if (loTail) {
    loTail.next = null;
    this.buckets[i] = loHead;
  }
  if (hiTail) {
    hiTail.next = null;
    this.buckets[i + oldStorage.length] = hiHead;
  }
};

Dictionary.prototype.removeAll = function () {
  this.buckets = new Array(this.buckets.length);
  this.size_ = 0;
};

Dictionary.prototype.getKeys = function () {
  var keys = new Vector(this.size_);
  for (var i = 0; i < this.buckets.length; ++i) {
    var current = this.buckets[i];
    while (current) {
      keys.append(current.key);
      current = current.next;
    }
  }
  return keys;
};

Dictionary.prototype.getValues = function () {
  var values = new Vector(this.size_);
  for (var i = 0; i < this.buckets.length; ++i) {
    var current = this.buckets[i];
    while (current) {
      values.append(current.value);
      current = current.next;
    }
  }
  return values;
};

function DictIdEntry(hash, key, value, next) {
  DictEntry.call(this, hash, key, value, next);
}
DictIdEntry.prototype = Object.create(DictEntry.prototype);

DictIdEntry.prototype.match = function (hash, key) {
  return this.hash == hash && this.key === key;
};

function IdentityDictionary(size) {
  Dictionary.call(this, size === undefined ? INITIAL_CAPACITY : size);
}
IdentityDictionary.prototype = Object.create(Dictionary.prototype);

IdentityDictionary.prototype.newEntry = function (key, value, hash) {
  return new DictIdEntry(hash, key, value, null);
};

function Random() {
  this.seed = 74755;
}

Random.prototype.next = function () {
  this.seed = ((this.seed * 1309) + 13849) & 65535;
  return this.seed;
};

function Planner() {
  this.currentMark = 1;
}

function Sym(hash) {
  this.hash = hash;
}

Sym.prototype.customHash = function () {
  return this.hash;
};

var ABSOLUTE_STRONGEST = new Sym(0),
  REQUIRED             = new Sym(1),
  STRONG_PREFERRED     = new Sym(2),
  PREFERRED            = new Sym(3),
  STRONG_DEFAULT       = new Sym(4),
  DEFAULT              = new Sym(5),
  WEAK_DEFAULT         = new Sym(6),
  ABSOLUTE_WEAKEST     = new Sym(7);

function Plan() {
  Vector.call(this, 15);
}
Plan.prototype = Object.create(Vector.prototype);

Plan.prototype.execute = function () {
  this.forEach(function (c) { c.execute(); });
};

Planner.prototype.newMark = function () {
  this.currentMark += 1;
  return this.currentMark;
};

Planner.prototype.incrementalAdd = function (c) {
  var mark = this.newMark(),
    overridden = c.satisfy(mark, this);

  while (overridden !== null) {
    overridden = overridden.satisfy(mark, this);
  }
};

Planner.prototype.incrementalRemove = function (c) {
  var out = c.getOutput();
  c.markUnsatisfied();
  c.removeFromGraph();

  var unsatisfied = this.removePropagateFrom(out),
    that = this;
  unsatisfied.forEach(function (u) { that.incrementalAdd(u); });
};

Planner.prototype.extractPlanFromConstraints = function (constraints) {
  var sources = new Vector();

  constraints.forEach(function (c) {
    if (c.isInput() && c.isSatisfied()) {
      sources.append(c);
    }
  });

  return this.makePlan(sources);
};

Planner.prototype.makePlan = function (sources) {
  var mark = this.newMark(),
    plan = new Plan(),
    todo = sources;

  while (!todo.isEmpty()) {
    var c = todo.removeFirst();

    if (c.getOutput().mark !== mark && c.inputsKnown(mark)) {
      // not in plan already and eligible for inclusion
      plan.append(c);
      c.getOutput().mark = mark;
      this.addConstraintsConsumingTo(c.getOutput(), todo);
    }
  }
  return plan;
};

Planner.prototype.propagateFrom = function (v) {
  var todo = new Vector();
  this.addConstraintsConsumingTo(v, todo);

  while (!todo.isEmpty()) {
    var c = todo.removeFirst();
    c.execute();
    this.addConstraintsConsumingTo(c.getOutput(), todo);
  }
};

Planner.prototype.addConstraintsConsumingTo = function (v, coll) {
  var determiningC = v.determinedBy;

  v.constraints.forEach(function (c) {
    if (c !== determiningC && c.isSatisfied()) {
      coll.append(c);
    }
  });
};

Planner.prototype.addPropagate = function (c, mark) {
  var todo = Vector.with(c);

  while (!todo.isEmpty()) {
    var d = todo.removeFirst();

    if (d.getOutput().mark === mark) {
      this.incrementalRemove(c);
      return false;
    }
    d.recalculate();
    this.addConstraintsConsumingTo(d.getOutput(), todo);
  }
  return true;
};

Planner.prototype.change = function (v, newValue) {
  var editC = new EditConstraint(v, PREFERRED, this),
    editV = Vector.with(editC),
    plan = this.extractPlanFromConstraints(editV);

  for (var i = 0; i < 10; i++) {
    v.value = newValue;
    plan.execute();
  }
  editC.destroyConstraint(this);
};

Planner.prototype.constraintsConsuming = function (v, fn) {
  var determiningC = v.determinedBy;
  v.constraints.forEach(function (c) {
    if (c != determiningC && c.isSatisfied()) {
      fn(c);
    }
  });
};

Planner.prototype.removePropagateFrom = function(out) {
  var unsatisfied = new Vector();

  out.determinedBy = null;
  out.walkStrength = Strength.absoluteWeakest;
  out.stay = true;

  var todo = Vector.with(out);

  while (!todo.isEmpty()) {
    var v = todo.removeFirst();

    v.constraints.forEach(function (c) {
        if (!c.isSatisfied()) { unsatisfied.append(c); }});

    this.constraintsConsuming(v, function (c) {
      c.recalculate();
      todo.append(c.getOutput());
    });
  }

  unsatisfied.sort(function (c1, c2) {
    return c1.strength.stronger(c2.strength); });
  return unsatisfied;
};


Planner.chainTest = function (n) {
  var planner = new Planner(),
    vars = new Array(n + 1),
    i = 0;

  for (i = 0; i < n + 1; i++) {
    vars[i] = new Variable();
  }

  // Build chain of n equality constraints
  for (i = 0; i < n; i++) {
    var v1 = vars[i],
      v2 = vars[i + 1];
    new EqualityConstraint(v1, v2, REQUIRED, planner);
  }

  new StayConstraint(vars[n], STRONG_DEFAULT, planner);

  var editC = new EditConstraint(vars[0], PREFERRED, planner),
    editV = Vector.with(editC),
    plan = planner.extractPlanFromConstraints(editV);

  for (i = 0; i < 100; i++) {
    vars[0].value = i;
    plan.execute();
    if (vars[n].value != i) {
      throw new Error("Chain test failed!");
    }
  }
  editC.destroyConstraint(planner);
};

Planner.projectionTest = function(n) {
  var planner = new Planner(),
    dests  = new Vector(),
    scale  = Variable.value(10),
    offset = Variable.value(1000),

    src = null, dst = null,
    i;

  for (i = 1; i <= n; i++) {
    src = Variable.value(i);
    dst = Variable.value(i);
    dests.append(dst);
    new StayConstraint(src, DEFAULT, planner);
    new ScaleConstraint(src, scale, offset, dst, REQUIRED, planner);
  }

  planner.change(src, 17);
  if (dst.value != 1170) {
    throw new Error("Projection test 1 failed!");
  }

  planner.change(dst, 1050);
  if (src.value != 5) {
    throw new Error("Projection test 2 failed!");
  }

  planner.change(scale, 5);
  for (i = 0; i < n - 1; ++i) {
    if (dests.at(i).value != (i + 1) * 5 + 1000) {
      throw new Error("Projection test 3 failed!");
    }
  }

  planner.change(offset, 2000);
  for (i = 0; i < n - 1; ++i) {
    if (dests.at(i).value != (i + 1) * 5 + 2000) {
      throw new Error("Projection test 4 failed!");
    }
  }
};

function Strength(symbolicValue) {
  this.arithmeticValue = Strength.strengthTable.at(symbolicValue);
}

Strength.prototype.sameAs = function (s) {
  return this.arithmeticValue == s.arithmeticValue;
};

Strength.prototype.stronger = function (s) {
  return this.arithmeticValue < s.arithmeticValue;
};

Strength.prototype.weaker = function (s) {
  return this.arithmeticValue > s.arithmeticValue;
};

Strength.prototype.strongest = function (s) {
  return s.stronger(this) ? s : this;
};

Strength.prototype.weakest = function (s) {
  return s.weaker(this) ? s : this;
};

Strength.of = function (strength) {
  return Strength.strengthConstant.at(strength);
};

function createStrengthTable() {
  var strengthTable = new IdentityDictionary();
  strengthTable.atPut(ABSOLUTE_STRONGEST, -10000);
  strengthTable.atPut(REQUIRED,           -800);
  strengthTable.atPut(STRONG_PREFERRED,   -600);
  strengthTable.atPut(PREFERRED,          -400);
  strengthTable.atPut(STRONG_DEFAULT,     -200);
  strengthTable.atPut(DEFAULT,             0);
  strengthTable.atPut(WEAK_DEFAULT,        500);
  strengthTable.atPut(ABSOLUTE_WEAKEST,    10000);
  return strengthTable;
}

function createStrengthConstants() {
  var strengthConstant = new IdentityDictionary();
  Strength.strengthTable.getKeys().forEach(function (key) {
    strengthConstant.atPut(key, new Strength(key));
  });
  return strengthConstant;
}

Strength.strengthTable     = createStrengthTable();
Strength.strengthConstant  = createStrengthConstants();

Strength.absoluteWeakest   = Strength.of(ABSOLUTE_WEAKEST);
Strength.required          = Strength.of(REQUIRED);

function AbstractConstraint(strengthSym) {
  this.strength = Strength.of(strengthSym);
}

AbstractConstraint.prototype.isInput = function() {
  return false;
};

AbstractConstraint.prototype.addConstraint = function(planner) {
  this.addToGraph();
  planner.incrementalAdd(this);
};

AbstractConstraint.prototype.destroyConstraint = function(planner) {
  if (this.isSatisfied()) {
    planner.incrementalRemove(this);
  }
  this.removeFromGraph();
};

AbstractConstraint.prototype.inputsKnown = function(mark) {
  return !this.inputsHasOne(function (v) {
      return !(v.mark === mark || v.stay || v.determinedBy === null);
  });
};

AbstractConstraint.prototype.satisfy = function (mark, planner) {
  var overridden;
  this.chooseMethod(mark);

  if (this.isSatisfied()) {
    // constraint can be satisfied
    // mark inputs to allow cycle detection in addPropagate
    this.inputsDo(function (i) { i.mark = mark; });

    var out = this.getOutput();
    overridden = out.determinedBy;
    if (overridden !== null) {
      overridden.markUnsatisfied();
    }
    out.determinedBy = this;
    if (!planner.addPropagate(this, mark)) {
      throw new Error("Cycle encountered");
    }
    out.mark = mark;
  } else {
    overridden = null;
    if (this.strength.sameAs(Strength.required)) {
      throw new Error("Could not satisfy a required constraint");
    }
  }
  return overridden;
};

function BinaryConstraint(var1, var2, strength, planner) {
  AbstractConstraint.call(this, strength);
  this.v1 = var1;
  this.v2 = var2;
  this.direction = null;
}
BinaryConstraint.prototype = Object.create(AbstractConstraint.prototype);

BinaryConstraint.prototype.isSatisfied = function () {
  return this.direction !== null;
};

BinaryConstraint.prototype.addToGraph = function () {
  this.v1.addConstraint(this);
  this.v2.addConstraint(this);
  this.direction = null;
};

BinaryConstraint.prototype.removeFromGraph = function () {
  if (this.v1 !== null) {
    this.v1.removeConstraint(this);
  }
  if (this.v2 !== null) {
    this.v2.removeConstraint(this);
  }
  this.direction = null;
};

BinaryConstraint.prototype.chooseMethod = function (mark) {
  if (this.v1.mark === mark) {
    if (this.v2.mark !== mark && this.strength.stronger(this.v2.walkStrength)) {
      this.direction = "forward";
      return this.direction;
    } else {
      this.direction = null;
      return this.direction;
    }
  }

  if (this.v2.mark === mark) {
    if (this.v1.mark !== mark && this.strength.stronger(this.v1.walkStrength)) {
      this.direction = "backward";
      return this.direction;
    } else {
      this.direction = null;
      return this.direction;
    }
  }

  // If we get here, neither variable is marked, so we have a choice.
  if (this.v1.walkStrength.weaker(this.v2.walkStrength)) {
    if (this.strength.stronger(this.v1.walkStrength)) {
      this.direction = "backward";
      return this.direction;
    } else {
      this.direction = null;
      return this.direction;
    }
  } else {
    if (this.strength.stronger(this.v2.walkStrength)) {
      this.direction = "forward";
      return this.direction;
    } else {
      this.direction = null;
      return this.direction;
    }
  }
};

BinaryConstraint.prototype.inputsDo = function (fn) {
  if (this.direction == "forward") {
    fn(this.v1);
  } else {
    fn(this.v2);
  }
};

BinaryConstraint.prototype.inputsHasOne = function (fn) {
  if (this.direction == "forward") {
    return fn(this.v1);
  } else {
    return fn(this.v2);
  }
};

BinaryConstraint.prototype.markUnsatisfied = function () {
  this.direction = null;
};

BinaryConstraint.prototype.getOutput = function () {
  return this.direction == "forward" ? this.v2 : this.v1;
};

BinaryConstraint.prototype.recalculate = function () {
  var ihn, out;

  if (this.direction == "forward") {
    ihn = this.v1; out = this.v2;
  } else {
    ihn = this.v2; out = this.v1;
  }

  out.walkStrength = this.strength.weakest(ihn.walkStrength);
  out.stay = ihn.stay;
  if (out.stay) {
    this.execute();
  }
};

function UnaryConstraint (v, strength, planner) {
  AbstractConstraint.call(this, strength);
  this.output = v;
  this.satisfied = false;

  this.addConstraint(planner);
}
UnaryConstraint.prototype = Object.create(AbstractConstraint.prototype);

UnaryConstraint.prototype.isSatisfied = function () {
  return this.satisfied;
};

UnaryConstraint.prototype.addToGraph = function () {
  this.output.addConstraint(this);
  this.satisfied = false;
};

UnaryConstraint.prototype.removeFromGraph = function () {
  if (this.output !== null) {
    this.output.removeConstraint(this);
  }
  this.satisfied = false;
};

UnaryConstraint.prototype.chooseMethod = function (mark) {
  this.satisfied = this.output.mark != mark &&
    this.strength.stronger(this.output.walkStrength);
  return null;
};

UnaryConstraint.prototype.inputsDo = function (fn) {
  // I have no input variables
};

UnaryConstraint.prototype.inputsHasOne = function (fn) {
  return false;
};

UnaryConstraint.prototype.markUnsatisfied = function () {
  this.satisfied = false;
};

UnaryConstraint.prototype.getOutput = function () {
  return this.output;
};

UnaryConstraint.prototype.recalculate = function () {
  this.output.walkStrength = this.strength;
  this.output.stay = !this.isInput();
  if (this.output.stay) {
    this.execute(); // stay optimization
  }
};

function EditConstraint(v, strength, planner) {
  UnaryConstraint.call(this, v, strength, planner);
}
EditConstraint.prototype = Object.create(UnaryConstraint.prototype);

EditConstraint.prototype.isInput = function () {
  return true;
};

EditConstraint.prototype.execute = function () {};

function EqualityConstraint(var1, var2, strength, planner) {
  BinaryConstraint.call(this, var1, var2, strength, planner);
  this.addConstraint(planner);
}
EqualityConstraint.prototype = Object.create(BinaryConstraint.prototype);

EqualityConstraint.prototype.execute = function () {
  if (this.direction == "forward") {
    this.v2.value = this.v1.value;
  } else {
    this.v1.value = this.v2.value;
  }
};

function ScaleConstraint(src, scale, offset, dest, strength, planner) {
  BinaryConstraint.call(this, src, dest, strength, planner);
  this.scale  = scale;
  this.offset = offset;

  this.addConstraint(planner);
}
ScaleConstraint.prototype = Object.create(BinaryConstraint.prototype);

ScaleConstraint.prototype.addToGraph = function () {
  this.v1.addConstraint(this);
  this.v2.addConstraint(this);
  this.scale.addConstraint(this);
  this.offset.addConstraint(this);
  this.direction = null;
};

ScaleConstraint.prototype.removeFromGraph = function () {
  if (this.v1 !== null) { this.v1.removeConstraint(this); }
  if (this.v2 !== null) { this.v2.removeConstraint(this); }
  if (this.scale  !== null) { this.scale.removeConstraint(this); }
  if (this.offset !== null) { this.offset.removeConstraint(this); }
  this.direction = null;
};

ScaleConstraint.prototype.execute = function () {
  if (this.direction == "forward") {
    this.v2.value = this.v1.value * this.scale.value + this.offset.value;
  } else {
    this.v1.value = (this.v2.value - this.offset.value) / this.scale.value;
  }
};

ScaleConstraint.prototype.inputsDo = function (fn) {
  if (this.direction == "forward") {
    fn(this.v1);
    fn(this.scale);
    fn(this.offset);
  } else {
    fn(this.v2);
    fn(this.scale);
    fn(this.offset);
  }
};

ScaleConstraint.prototype.recalculate = function () {
  var ihn, out;

  if (this.direction == "forward") {
    ihn = this.v1; out = this.v2;
  } else {
    out = this.v1; ihn = this.v2;
  }

  out.walkStrength = this.strength.weakest(ihn.walkStrength);
  out.stay = ihn.stay && this.scale.stay && this.offset.stay;
  if (out.stay) {
    this.execute(); // stay optimization
  }
};

function StayConstraint (v, strength, planner) {
  UnaryConstraint.call(this, v, strength, planner);
}
StayConstraint.prototype = Object.create(UnaryConstraint.prototype);

StayConstraint.prototype.execute = function() {};

function Variable() {
  this.value = 0;
  this.constraints = new Vector(2);
  this.determinedBy = null;
  this.walkStrength = Strength.absoluteWeakest;
  this.stay = true;
  this.mark = 0;
}

Variable.prototype.addConstraint = function (c) {
  this.constraints.append(c);
};

Variable.prototype.removeConstraint = function (c) {
  this.constraints.remove(c);
  if (this.determinedBy == c) {
    this.determinedBy = null;
  }
};

Variable.value = function (aValue) {
  var v = new Variable();
  v.value = aValue;
  return v;
};

function run() {
  var innerIterations = 1000;
  Planner.chainTest(innerIterations);
  Planner.projectionTest(innerIterations);
  return Planner;
};
