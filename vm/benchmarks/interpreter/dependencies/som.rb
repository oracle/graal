# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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


# This benchmark is is derived from Stefan Marr's Are-We-Fast-Yet benchmark
# suite available at https://github.com/smarr/are-we-fast-yet
INITIAL_SIZE = 10
INITIAL_CAPACITY = 16

class Pair
  attr_accessor :key, :value

  def initialize(key, value)
    @key   = key
    @value = value
  end
end

class Vector
  def self.with(elem)
    new_vector = new(1)
    new_vector.append(elem)
    new_vector
  end

  def initialize(size = 50)
    @storage   = Array.new(size)
    @first_idx = 0
    @last_idx  = 0
  end

  def at(idx)
    return nil if idx >= @storage.length

    @storage[idx]
  end

  def at_put(idx, val)
    if idx >= @storage.length
      new_length = @storage.length
      new_length *= 2 while new_length <= idx

      new_storage = Array.new(new_length)
      @storage.each_index do |i|
        new_storage[i] = @storage[i]
      end
      @storage = new_storage
    end
    @storage[idx] = val

    @last_idx = idx + 1 if @last_idx < idx + 1
  end

  def append(elem)
    if @last_idx >= @storage.size
      # Need to expand capacity first
      new_storage = Array.new(2 * @storage.size)
      @storage.each_index do |i|
        new_storage[i] = @storage[i]
      end
      @storage = new_storage
    end

    @storage[@last_idx] = elem
    @last_idx += 1
    self
  end

  def empty?
    @last_idx == @first_idx
  end

  def each
    (@first_idx..(@last_idx - 1)).each do |i|
      yield @storage[i]
    end
  end

  def has_some
    (@first_idx..(@last_idx - 1)).each do |i|
      return true if yield @storage[i]
    end
    false
  end

  def get_one
    (@first_idx..(@last_idx - 1)).each do |i|
      e = @storage[i]
      return e if yield e
    end
    nil
  end

  def remove_first
    return nil if empty?

    @first_idx += 1
    @storage[@first_idx - 1]
  end

  def remove(obj)
    new_array = Array.new(capacity)
    new_last = 0
    found = false

    each do |it|
      if it.equal? obj
        found = true
      else
        new_array[new_last] = it
        new_last += 1
      end
    end

    @storage  = new_array
    @last_idx = new_last
    @first_idx = 0
    found
  end

  def remove_all
    @first_idx = 0
    @last_idx  = 0
    @storage = Array.new(@storage.size)
  end

  def size
    @last_idx - @first_idx
  end

  def capacity
    @storage.size
  end

  def sort(&block)
    # Make the argument, block, be the criterion for ordering elements of
    # the receiver.
    # Sort blocks with side effects may not work right.
    sort_range(@first_idx, @last_idx - 1, &block) if size > 0
  end

  def sort_range(i, j)
    # Sort elements i through j of self to be non-descending
    # according to sortBlock.
    default_sort(i, j) unless block_given?

    # The prefix d means the data at that index.

    n = j + 1 - i
    return self if n <= 1 # Nothing to sort

    # Sort di, dj
    di = @storage[i]
    dj = @storage[j]

    # i.e., should di precede dj?
    unless yield di, dj
      @storage.swap(i, j)
      tt = di
      di = dj
      dj = tt
    end

    # NOTE: For DeltaBlue, this is never reached.
    if n > 2 # More than two elements.
      ij  = ((i + j) / 2).floor  # ij is the midpoint of i and j.
      dij = @storage[ij]         # Sort di,dij,dj.  Make dij be their median.

      if yield di, dij           # i.e. should di precede dij?
        unless yield dij, dj     # i.e., should dij precede dj?
          @storage.swap(j, ij)
          dij = dj
        end
      else                       # i.e. di should come after dij
        @storage.swap(i, ij)
        dij = di
      end

      if n > 3 # More than three elements.
        # Find k>i and l<j such that dk,dij,dl are in reverse order.
        # Swap k and l.  Repeat this procedure until k and l pass each other.
        k = i
        l = j - 1

        while (
          # i.e. while dl succeeds dij
          l -= 1 while k <= l && (yield dij, @storage[l])

          k += 1
          # i.e. while dij succeeds dk
          k += 1 while k <= l && (yield @storage[k], dij)
          k <= l)
          @storage.swap(k, l)
        end

        # Now l < k (either 1 or 2 less), and di through dl are all
        # less than or equal to dk through dj.  Sort those two segments.

        sort_range(i, l, &block)
        sort_range(k, j, &block)
      end
    end
  end
end

class Set
  def initialize(size = INITIAL_SIZE)
    @items = Vector.new(size)
  end

  def size
    @items.size
  end

  def each(&block)
    @items.each(&block)
  end

  def has_some(&block)
    @items.has_some(&block)
  end

  def get_one(&block)
    @items.get_one(&block)
  end

  def add(obj)
    @items.append(obj) unless contains(obj)
  end

  def collect
    coll = Vector.new
    each { |e| coll.append(yield e) }
    coll
  end

  def contains(obj)
    has_some { |it| it == obj }
  end
end

class IdentitySet < Set
  def contains(obj)
    has_some { |it| it.equal? obj }
  end
end

class Entry
  attr_reader :hash, :key
  attr_accessor :value, :next

  def initialize(hash, key, value, next_)
    @hash  = hash
    @key   = key
    @value = value
    @next  = next_
  end

  def match(hash, key)
    @hash == hash && @key == key
  end
end

class Dictionary
  attr_reader :size

  def initialize(size = INITIAL_CAPACITY)
    @buckets = Array.new(size)
    @size    = 0
  end

  def hash(key)
    return 0 unless key

    hash = key.custom_hash
    hash ^ hash >> 16
  end

  def empty?
    @size == 0
  end

  def get_bucket_idx(hash)
    (@buckets.size - 1) & hash
  end

  def get_bucket(hash)
    @buckets[get_bucket_idx(hash)]
  end

  def at(key)
    hash = hash(key)
    e = get_bucket(hash)

    while e
      return e.value if e.match(hash, key)
      e = e.next
    end
    nil
  end

  def contains_key(key)
    hash = hash(key)
    e = get_bucket(hash)

    while e
      return true if e.match(hash, key)
      e = e.next
    end
    false
  end

  def at_put(key, value)
    hash = hash(key)
    i = get_bucket_idx(hash)
    current = @buckets[i]

    unless current
      @buckets[i] = new_entry(key, value, hash)
      @size += 1
    else
      insert_bucket_entry(key, value, hash, current)
    end

    resize if @size > @buckets.size
  end

  def new_entry(key, value, hash)
    Entry.new(hash, key, value, nil)
  end

  def insert_bucket_entry(key, value, hash, head)
    current = head

    loop do
      if current.match(hash, key)
        current.value = value
        return
      end
      unless current.next
        @size += 1
        current.next = new_entry(key, value, hash)
        return
      end
      current = current.next
    end
  end

  def resize
    old_storage = @buckets
    @buckets = Array.new(old_storage.size * 2)
    transfer_entries(old_storage)
  end

  def transfer_entries(old_storage)
    old_storage.each_with_index do |current, i|
      if current
        old_storage[i] = nil

        unless current.next
          @buckets[current.hash & (@buckets.size - 1)] = current
        else
          split_bucket(old_storage, i, current)
        end
      end
    end
  end

  def split_bucket(old_storage, i, head)
    lo_head = nil, lo_tail = nil
    hi_head = nil, hi_tail = nil
    current = head

    while current
      if (current.hash & old_storage.size) == 0
        unless lo_tail
          lo_head = current
        else
          lo_tail.next = current
        end
        lo_tail = current
      else
        unless hi_tail
          hi_head = current
        else
          hi_tail.next = current
        end
        hi_tail = current
      end
      current = current.next
    end

    if lo_tail
      lo_tail.next = nil
      @buckets[i] = lo_head
    end
    if hi_tail
      hi_tail.next = nil
      @buckets[i + old_storage.size] = hi_head
    end
  end

  def remove_all
    @buckets = Array.new(@buckets.size)
    @size = 0
  end

  def keys
    keys = Vector.new(@size)
    @buckets.each_index do |i|
      current = @buckets[i]
      while current
        keys.append(current.key)
        current = current.next
      end
    end
    keys
  end

  def values
    vals = Vector.new(@size)
    @buckets.each_index do |i|
      current = @buckets[i]
      while current
        vals.append(current.value)
        current = current.next
      end
    end
    vals
  end
end

class IdEntry < Entry
  def match(hash, key)
    @hash == hash && (@key.equal? key)
  end
end

class IdentityDictionary < Dictionary
  def new_entry(key, value, hash)
    IdEntry.new(hash, key, value, nil)
  end
end

class Random
  def initialize
    @seed = 74_755
  end

  def next
    @seed = ((@seed * 1_309) + 13_849) & 65_535
  end
end