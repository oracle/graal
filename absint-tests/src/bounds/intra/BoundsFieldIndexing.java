// Intra-procedural: indexing with final field constant
public class BoundsFieldIndexing {
  private static final int IDX = 3;

  public static void main(String[] args) {
    int[] a = new int[6];
    a[IDX] = 11; // constant index within bounds
    int v = a[IDX];
    if (v == 0) System.out.println("impossible");
  }
}
