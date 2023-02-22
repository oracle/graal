package com.oracle.graal.pointsto.typestate;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;

// This bitset does not publicly support modification after it has been
// created.
public class SmallBitSet {
  public SmallBitSet(BitSet other) {
    assert other.cardinality() <= MAX_CARDINALITY;
    Arrays.fill(set, UNSET);
    for (int i = other.nextSetBit(0); i >= 0; i = other.nextSetBit(i+1)) {
    //  set(i);
      set[cardinality++] = i;
    }
    // We rely on having the bits in sorted order in the array so that the
    // iterator can easily return bits in increasing order. We do not need to
    // continually maintain this invariant; the bitset cannot be modified after
    // it is created.
    // Arrays.sort(this.set, 0, cardinality);
  }

  public boolean get(int bit) {
    assert bit >= 0: "get() bit must be non-negative";
    for (int item : set) {
      if (item == bit) {
        return true;
      }
    }
    return false;
  }

  // private void set(int bit) {
  //   assert bit >= 0: "set() bit must be non-negative";
  //   assert cardinality < MAX_CARDINALITY : "Not enough space for new bit in set";
  //   // Avoid duplicating information in case there is a hole before our bit.
  //   for (int item : set) {
  //     if (item == bit) {
  //       return;
  //     }
  //   }
  //   set[cardinality++] = bit;
  //   // for (int i = 0; i < MAX_CARDINALITY; i++) {
  //   //   assert set[i] != bit : "Duplicate item in input set";
  //   //   if (set[i] == UNSET) {
  //   //     set[i] = bit;
  //   //     cardinality++;
  //   //     return;
  //   //   }
  //   // }
  //   // assert false : "Could not find empty space for new item";
  // }

  // private void clear(int bit) {
  //   assert bit >= 0: "clear() index must be non-negative";
  //   for (int i = 0; i < MAX_CARDINALITY; i++) {
  //     if (set[i] == bit) {
  //       set[i] = UNSET;
  //       cardinality--;
  //       return;
  //     }
  //   }
  //   assert false : "Could not find bit in bitset";
  // }

  public int cardinality() {
    return cardinality;
  }

  // This is O(n^2) where n is MAX_CARDINALITY, so it's actually O(1).
  public boolean isSuperSet(SmallBitSet other) {
    for (int item : other.set) {
      if (item != UNSET && !get(item)) {
        return false;
      }
    }
    return true;
  }

  public BitSet asBitSet() {
    BitSet result = new BitSet();
    for (int item : set) {
      if (item != UNSET) {
        result.set(item);
      }
    }
    return result;
  }

  public int nextSetBit(int fromIndex) {
    if (fromIndex < 0 || fromIndex >= cardinality) {
      throw new IndexOutOfBoundsException();
    }
    for (int i = fromIndex; i < cardinality; i++) {
      if (set[i] != UNSET) {
        return i;
      }
    }
    return -1;
  }

  public String toString() {
    String result = "{";
    String sep = "";
    for (int item : set) {
      if (item != UNSET) {
        result += sep;
        result += String.valueOf(item);
      }
      sep = ", ";
    }
    result += "}";
    return result;
  }

  public String repr() {
    String result = "{";
    String sep = "";
    for (int i = 0; i < MAX_CARDINALITY; i++) {
      result += sep;
      result += String.valueOf(set[i]);
      sep = ", ";
    }
    result += "}";
    return result;
  }

  public static final int UNSET = -1;
  public static final int MAX_CARDINALITY = 10;
  int set[] = new int[MAX_CARDINALITY];
  int cardinality = 0;
}
