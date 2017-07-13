#include <stdio.h>

int factorial (int x, int y){
  if (x==0) {
    return y;
  } else {
    return factorial(x-1,y*x);
  }
}

int main(void)
{
  return factorial(5, 10) % 256;
}
