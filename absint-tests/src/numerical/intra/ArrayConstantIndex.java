/*
 Expected invariants:
 - reading arr[2] should be exact if arr constant initialized
*/
public class ArrayConstantIndex {
    private static int res;
    public static void main(String[] args) {
        int[] arr = new int[] {10,20,30,40};
        res = arr[2];
    }
}

