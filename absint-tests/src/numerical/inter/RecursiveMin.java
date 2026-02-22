public class RecursiveMin {
  static int min(int[] A, int i) {
    if (i == A.length - 1) return A[i];
    int m = min(A, i + 1);
    return (A[i] < m) ? A[i] : m;
  }

  public static void main(String[] args) {
    int[] arr = {3, 1, 4};
    int m = min(arr, 0);
  }
}
