#include <stdio.h>

int arr[] = {1,2,42,3};

int foo(int a) {

   if(a == 0 ) {
      throw arr;
   }
   return a;
}

int main() {
	try {
		foo(0);
		return 0;
	} catch (int* a) {
		printf("%i\n", a[2]);
		return a[2];
	}

}