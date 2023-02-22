package com.oracle.graal.pointsto.typestate;

import java.util.BitSet;
import com.oracle.graal.pointsto.typestate.SmallBitSet;

public class FastBitSet {
  public FastBitSet(BitSet other) {
    cardinality = other.cardinality();
    // System.out.printf("cardinality %d\n", cardinality);
    if (isSmall()) {
      set = new SmallBitSet(other);
    } else {
      set = other;
    }
  }

  public BitSet asBitSet() {
    if (isSmall()) {
      return ((SmallBitSet)this.set).asBitSet();
    }
    return (BitSet)set;
  }

  public boolean get(int idx) {
    if (isSmall()) {
      return ((SmallBitSet)this.set).get(idx);
    }
    return ((BitSet)set).get(idx);
  }

  public int nextSetBit() {
    return nextSetBit(0);
  }

  public int nextSetBit(int fromIndex) {
    if (isSmall()) {
      return ((SmallBitSet)this.set).nextSetBit(fromIndex);
    }
    return ((BitSet)set).nextSetBit(fromIndex);
  }

  private boolean isSmall() {
    return false;
    // return cardinality <= SmallBitSet.MAX_CARDINALITY;
  }

  int cardinality;
  Object set;
}
