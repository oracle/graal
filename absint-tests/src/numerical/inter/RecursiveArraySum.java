public class RecursiveArraySum {
  static int sum(int[] A, int i) {
    if (i >= A.length) return 0;
    return A[i] + sum(A, i + 1);
  }

  public static void main(String[] args) {
    int[] arr = {1, 2, 3};
    int s = sum(arr, 0);
  }
}
