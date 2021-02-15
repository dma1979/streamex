# skipLast(long n)
### Implementation hints
* [ ]Надо чтобы в SIZED не было буфера вообще и в последовательном, и в параллельном случаях, а в SIZED|SUBSIZED идеально параллелизовалось
* В SIZED просто параллелить придётся в poor man режиме, как делает AbstractSpliterator, отдавая массивы в префикс
* [x] skipLast(1) можно превратить в pairMap((a, b) -> a). Там вся эта жесть сделана, и это действительно будет быстро даже без SIZED
* Если размер не известен вообще, стоит держать кольцевой буфер. Можно ArrayDeque, можно свой собственный.
* Но стоит подумать про ситуацию когда параметр skipLast очень большой. Он может оказаться больше, чем число элементов в стриме
* Если размер известен, то буфер не нужен, и заранее точно известно, когда остановиться.
  * А если размер неизвестен, то надо выяснить. Если даже огромное число подали, но весь стрим в память влезет, тогда никаких проблем, ничего не выдаётся на выход.
* Для реализации необходим свой Spliterator и может даже не один, отдельно для SIZED, отдельно нет
  * CloneableSpliterator можно попробовать взять в качестве баового для сплитератора
### Original idea
I don't see that skipLast itself is useful enough to add it to the library directly.
* If your source is sized (collection, array, etc.), you may easily express it via `limit(collection.size()-n)`
  (which will be actually much more optimal as it will short-circuit and don't use intermediate memory).

* If you want to skip just one element, you can use existing `.mapLast(x -> null).nonNull()`
  assuming that the original stream contains no null values, otherwise you can use some other special value). So there's actually very limited cases when skipLast is cannot be expressed using existing operations. And I haven't seen a real problem which would actually need this method.

* The big problem is that it does not parallelize normally (well unless the source is SIZED/SUBSIZED).
  For features which cannot be parallelized there must be very strong reason to add them to the library.
  I have one such operation, namely `headTail`: it allows you to express almost every other intermediate operation within just a few lines.
  Here's the example which shows how to implement `skipLast` using `headTail` which is much shorter than your code.
  If you need such operation, just add the corresponding static method into your code.

When PR#10 will be implemented you will even be able to chain this call:
```java
StreamEx.of(source).chain(s -> skipLast(s, n)).map(...) // do something else
```

### Current pull request status
[View pull request](https://github.com/amaembo/streamex/pull/156)

#### Details
Please add StreamEx.skipLast(long n). This method is similar to skip(long n) except this method returns a stream consisting of the initial elements of this stream
after discarding the last n elements of the stream. If this stream contains fewer than n elements then an empty stream will be returned.

#### Feedback(Implementation plan)
* use poor-man parallelism for general case (via AbstractSpliterator and friends), though it's possible to do better,

* [ ] Ignoring parallelism completely is not an option to the library:
  the author expects to see SIZED/sequential and SIZED/SUBSIZED/parallel cases to be handled specially(probably via separate spliterators).
    * one/util/streamex/AbstractStreamEx.java:349
```java
        @Override
        public Spliterator<T> trySplit() {
            return null;
        }
```
* [ ] By creating an anonymous class the outer `this` is captured which is unnecessary.
    * one/util/streamex/AbstractStreamEx.java:333
```java
        BlockingDeque<T> buffer = new LinkedBlockingDeque<>(n + 1);
        Spliterator<T> source = this.spliterator();

        return supply(StreamEx.of(new Spliterator<T>() {
```
* [ ] Could return negative size here which has no meaning:
    * one/util/streamex/AbstractStreamEx.java:355
```java
        @Override
        public long estimateSize() {
            return source.estimateSize() - n;
        }
```
* [ ] Delegate comparator in case if original spliterator is sorted.
    * one/util/streamex/AbstractStreamEx.java:359
```java
        @Override
        public int characteristics() {
            return source.characteristics();
        }
```  
* [ ] not optimized solution:
  Note(!) that an optimized solution could be provided if the stream is SIZED/sequential or SIZED/SUBSIZED/parallel.
  In this case you don't need buffering at all as you know exactly how many elements are left.
  Also why concurrent buffer if you just refuse to parallelize? ArrayDeque (or simply Object[] array) would suffice and would be much faster.
    * one/util/streamex/AbstractStreamEx.java:330
```java
        if (n == 0)
            return supply(this);

        BlockingDeque<T> buffer = new LinkedBlockingDeque<>(n + 1);
```  

* [ ] A forEachRemaining method must be provided as it optimizes the common case (non-short-circuiting stream).
  In specific cases it could be much faster and memory friendly than tryAdvance.
    * one/util/streamex/AbstractStreamEx.java:359
```java
            @Override
            public int characteristics() {
                return source.characteristics();
            }
```
* [ ] Unnecessary boxing is absolutely not an option. You could just create an array of n elements.
    * one/util/streamex/DoubleStreamEx.java:605
```java
        if (n == 0)
            return this;

        BlockingDeque<Double> buffer = new LinkedBlockingDeque<>(n + 1);
```    

#### Original draft source code
Note that this implementation has a problem `skipLast(Stream.of(1,2,3), Integer.MAX_VALUE-1)` should return empty stream,
but it will likely to die with OutOfMemoryError.
```java
public static <T> Stream<T> skipLast(Stream<T> stream, int n)
{
   Spliterator<T> iter;
   Stream<T> result;

   iter   = stream.spliterator();
   iter   = new SkipLast<>(iter, n);
   result = StreamSupport.stream(iter, false);

   return(result);
}

private static class SkipLast<T> extends Spliterators.AbstractSpliterator<T> implements Consumer<T>
{
   private final Spliterator<T> m_input;
   private final ArrayDeque<T>  m_queue;
   private final int            m_n;

   public AllButLastN(Spliterator<T> input, int n)
   {
      super(Math.max(input.estimateSize() - n, 0), 0);

      m_input = input;
      m_n     = n;
      m_queue = new ArrayDeque<>(n + 1);
   }

   @Override
   public boolean tryAdvance(Consumer<? super T> action)
   {
      T value;

      while (m_queue.size() <= m_n)
         if (!m_input.tryAdvance(this))
            return(false);

      value = m_queue.pollFirst();

      action.accept(value);

      return(true);
   }

   @Override
   public void accept(T value)
   {
      m_queue.addLast(value);
   }
}

```
