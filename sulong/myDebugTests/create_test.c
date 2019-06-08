#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

void* print_message_function(void *ptr);
void* test_func1();
void* test_func2();
int main()
{
  pthread_t thread1, thread2;
  const char* message1 = "Thread 1";
  const char* message2 = "Thread 2";
  int iret1, iret2;
  iret1 = 0;
  iret1 = pthread_create(&thread1, NULL, test_func1, (void*) message1);
  if(iret1)
  {
    fprintf(stderr, "Error - pthread_create() return code: %d\n", iret1);
    exit(EXIT_FAILURE);
  }
  iret2 = 0;
  iret2 = pthread_create(&thread2, NULL, test_func2, (void*) message2);
  if(iret2)
  {
    fprintf(stderr, "Error - pthread_create() return code: %d\n", iret2);
    exit(EXIT_FAILURE);
  }
  printf("%d \n", thread1);
  printf("%d \n", thread2);

  // printf("pthread_create() for thread 1 returns: %d\n", iret1);
  // printf("pthread_create() for thread 2 returns: %d\n", iret2);
  // printf("pthread_create() thread1 is now: %d\n", thread1);
  // printf("pthread_create() thread2 is now: %d\n", thread2);  
  // printf("pthread_my_test() returns: %d\n", pthread_my_test());
  int val1 = 5;
  int val2 = 15;
  void* i = &val1;
  void* j = &val2;

  // einfach nur für gcc:
  // void* i;
  // void* j;

  //i = &val1;
  //j = &val2;
  int join1 = pthread_join(thread1, &i);
  int join2 = pthread_join(thread2, &j);
  printf("join1 value is %d\n", *(int*)i);
  printf("join2 value is %d\n", *(int*)j);
  exit(EXIT_SUCCESS);
}

void* print_message_function(void* ptr)
{
  char* message;
  message = (char*) ptr;
  printf("%s \n", message);
  int* ret = malloc(sizeof(int));
  *ret = 35;
  pthread_exit(ret);  
  return 0;
}

void* test_func1(void* ptr)
{
  char* message;
  message = (char*) ptr;
  printf("%s \n", message);
  int i = 1;
  int* sum = malloc(sizeof(int));
  *sum = 0;
  while (i <= 1000000)
  {
    *sum += i++;
  }
  printf("f1 %d\n", *sum);
  pthread_exit(sum);
  return 0;
}

void* test_func2(void* ptr)
{
  char* message;
  message = (char*) ptr;
  printf("%s \n", message);
  int j = 51;
  int* sum = malloc(sizeof(int));
  *sum = 0;
  while (j <= 1000000)
  {
    *sum += j++;
  }
  printf("f2 %d\n", *sum);
  pthread_exit(sum);
  return 0;
}

