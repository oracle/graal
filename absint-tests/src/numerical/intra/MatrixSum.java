public class MatrixSum {
    private static int val;
    public static void main(String[] args) {
        int[][] m = new int[3][4];
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                m[i][j] = i + j;
                sum += m[i][j];
            }
        }
        val = sum;
    }
}
