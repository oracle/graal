#include <stdio.h>
class someClass { };

int foo(int a) {
   if(a == 0 ) {
      throw "Null!";
   }
   return a;
}

int main() {
	try {
		foo(1);
		return 0;
	} catch (const char* msg) {
		printf("%s\n", msg);
		return 1;
	} catch (long value) {
		return 2;
	} catch (int* value) {
		return 3;
	} catch (someClass value) {
		return 4;
	}

}