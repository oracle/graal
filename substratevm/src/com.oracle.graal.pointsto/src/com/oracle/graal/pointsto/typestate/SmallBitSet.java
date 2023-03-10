package com.oracle.graal.pointsto.typestate;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.TreeSet;

// This bitset does not support modification after it has been created.
public class SmallBitSet {
    public SmallBitSet(BitSet other) {
        assert other.cardinality() <= MAX_CARDINALITY;
        for (int i = other.nextSetBit(0); i >= 0; i = other.nextSetBit(i+1)) {
            set[cardinality++] = i;
        }
        assert cardinality == other.cardinality();
        // Arrays.fill(set, UNSET, cardinality, MAX_CARDINALITY);
    }

    public boolean get(int bit) {
        assert bit >= 0: "get() bit must be non-negative";
        for (int i = 0; i < cardinality(); i++) {
            if (set[i] == bit) {
                return true;
            }
        }
        return false;
    }

    public int cardinality() {
        return cardinality;
    }

    // // This is O(n^2) where n is MAX_CARDINALITY, so it's actually O(1).
    // public boolean isSuperSet(SmallBitSet other) {
    //     for (int i = 0; i < other.cardinality(); i++) {
    //         if (!get(other.set[i])) {
    //             return false;
    //         }
    //     }
    //     return true;
    // }


    private int lastSetBit() {
      assert cardinality > 0 : "SmallBitSet should never be empty";
      return nextSetBit(cardinality-1);
    }

    public BitSet asBitSet() {
        BitSet result = new BitSet(set[cardinality-1]+1);
        for (int i = 0; i < cardinality(); i++) {
            result.set(set[i]);
        }
        // Clone will trim to size
        return (BitSet) result.clone();
    }

    public int nextSetBit(int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = 0; i < cardinality(); i++) {
          int current = set[i];
          assert current != UNSET : "found UNSET in valid section";
          if (current >= fromIndex) {
            return current;
          }
        }
        return UNSET;
        // int result = set[fromIndex];
        // assert result != UNSET : "Should not have UNSET inside valid section of bitset";
        // return result;
    }

    public String toString() {
        String result = "{";
        String sep = "";
        for (int i = 0; i < cardinality(); i++) {
            result += sep;
            result += String.valueOf(set[i]);
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
