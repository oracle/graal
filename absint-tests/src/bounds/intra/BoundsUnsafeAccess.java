public class BoundsUnsafeAccess {
  public static void main(String[] args) {
    int[] a = new int[5];
    int x = 0;
    int i = 5; 
    if (args.length == 42) {
      x = a[i];
    }
  }
}
