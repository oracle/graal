#include <stdio.h>

int val = 5;

int foo(int a) {

   if(a == 0 ) {
      throw &val;
   }
   return a;
}

int main() {
	try {
		foo(0);
		return 0;
	} catch (int* a) {
		printf("%i\n", *a);
		return *a;
	}

}