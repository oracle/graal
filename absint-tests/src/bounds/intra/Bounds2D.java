// Intra-procedural: 2D array with per-row lengths, classic nested loops
public class Bounds2D {
  public static void main(String[] args) {
    int rows = 3;
    int cols = 4;
    int[][] m = new int[rows][cols];
    int sum = 0;
    for (int i = 0; i < m.length; i++) {
      for (int j = 0; j < m[i].length; j++) {
        m[i][j] = i + j;
        sum += m[i][j];
      }
    }
    if (sum == -1) System.out.println("impossible");
  }
}
