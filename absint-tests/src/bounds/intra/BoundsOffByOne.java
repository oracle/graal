public class BoundsOffByOne {
  public static void main(String[] args) {
    int[] a = new int[5];
    for (int i = 0; i < a.length; i++) a[i] = i;

    int last = 0;
    if (a.length > 0) {
      last = a[a.length - 1]; 
    }
    // if (last == -1) System.out.println("impossible");
  }
}
