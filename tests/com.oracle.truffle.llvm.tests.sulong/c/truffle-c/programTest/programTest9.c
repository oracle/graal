#include <stdlib.h>

int main() {
  int arraySize = 5;
  int i, j, k;

  int **theArray1;
  theArray1 = (int **)malloc(arraySize * sizeof(int *));
  for (i = 0; i < arraySize; i++)
    theArray1[i] = (int *)malloc(arraySize * sizeof(int));

  int **theArray2;
  theArray2 = (int **)malloc(arraySize * sizeof(int *));
  for (i = 0; i < arraySize; i++)
    theArray2[i] = (int *)malloc(arraySize * sizeof(int));

  int **theArray3;
  theArray3 = (int **)malloc(arraySize * sizeof(int *));
  for (i = 0; i < arraySize; i++)
    theArray3[i] = (int *)malloc(arraySize * sizeof(int));

  for (i = 0; i < arraySize; i++) {
    for (j = 0; j < arraySize; j++) {
      theArray1[i][j] = (i + 1 + j * 2);
      theArray2[i][j] = (j + 1 + i * 2);
      theArray3[i][j] = 0;
    }
  }

  theArray1[3][4] = 666;
  theArray2[2][1] = 667;

  for (i = 0; i < arraySize; i++) {
    for (j = 0; j < arraySize; j++) {
      for (k = 0; k < arraySize; k++) {
        theArray3[i][j] += theArray1[i][k] * theArray2[k][j];
      }
    }
  }

  int sum = 0;

  for (i = 0; i < arraySize; i++) {
    for (j = 0; j < arraySize; j++) {
      sum += theArray3[i][j];
    }
  }

  return sum % 100;
}
