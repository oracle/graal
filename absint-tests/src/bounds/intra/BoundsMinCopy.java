public class BoundsMinCopy {

  public static void main(String[] args) {
    int[] src = new int[7];
    int[] dst = new int[5];
    for (int i = 0; i < src.length; i++) {
        src[i] = i * 2;
    }

    int n = Math.min(src.length, dst.length);
    for (int i = 0; i < n; i++) {
      dst[i] = src[i]; 
    }
  }
}
