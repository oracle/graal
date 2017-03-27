class someClass { };

int foo(int a) {
   if(a == 0 ) {
      throw someClass();
   }
   return a;
}

int main(int argc, char* argv[]) {
	try {
		foo(1);
		return 0;
	} catch (const char* msg) {
		return 1;
	} catch (long value) {
		return 2;
	} catch (int* value) {
		return 3;
	} catch (someClass value) {
		return 4;
	}
    
}