// code from https://gist.github.com/rtv/4989304

#include <pthread.h>
#include <stdio.h>
#include <unistd.h>
#include <assert.h>


/* Compile like this:
 gcc --std=c99 -lpthread cond.c -o cond
*/

const size_t NUMTHREADS = 20;

/* a global count of the number of threads finished working. It will
   be protected by mutex and changes to it will be signalled to the
   main thread via cond */

int done = 0;
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond = PTHREAD_COND_INITIALIZER;

/* Note: error checking on pthread_X calls ommitted for clarity - you
   should always check the return values in real code. */

/* Note: passing the thread id via a void pointer is cheap and easy,
 * but the code assumes pointers and long ints are the same size
 * (probably 64bits), which is a little hacky. */

void* ThreadEntry( void* id )
{
  const int myid = (long)id; // force the pointer to be a 64bit integer
  
  const int workloops = 5;
  for( int i=0; i<workloops; i++ )
    {
      printf( "[thread %d] working (%d/%d)\n", myid, i, workloops );
      sleep(1); // simulate doing some costly work
    }
  
  // we're going to manipulate done and use the cond, so we need the mutex
  pthread_mutex_lock( &mutex );

  // increase the count of threads that have finished their work.
  done++;
  printf( "[thread %d] done is now %d. Signalling cond.\n", myid, done );

  // wait up the main thread (if it is sleeping) to test the value of done  
  pthread_cond_signal( &cond ); 
  pthread_mutex_unlock( & mutex );

  return NULL;
}

int main( int argc, char** argv )
{
  puts( "[thread main] starting" );

  pthread_t threads[NUMTHREADS];

  for( int t=0; t<NUMTHREADS; t++ )
    pthread_create( &threads[t], NULL, ThreadEntry, (void*)(long)t );

  // we're going to test "done" so we need the mutex for safety
  pthread_mutex_lock( &mutex );

  // are the other threads still busy?
  while( done < NUMTHREADS )
    {
      printf( "[thread main] done is %d which is < %d so waiting on cond\n", 
	      done, (int)NUMTHREADS );
      
      /* block this thread until another thread signals cond. While
	 blocked, the mutex is released, then re-aquired before this
	 thread is woken up and the call returns. */ 
      pthread_cond_wait( & cond, & mutex ); 
      
      puts( "[thread main] wake - cond was signalled." ); 
      
      /* we go around the loop with the lock held */
    }
  
  printf( "[thread main] done == %d so everyone is done\n", (int)NUMTHREADS );
  
  pthread_mutex_unlock( & mutex );
  
  return 0;
}

