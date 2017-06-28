#include <stdio.h>

int foo(int a) {
   if(a == 0 ) {
      throw 42;
   }
   return a;
}

int main() {
	try {
		foo(0);
		return 0;
	} catch (int a) {
		printf("%i\n", a);
		return a;
	}

}