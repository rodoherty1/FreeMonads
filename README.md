# FreeMonads

### Why am I looking into Free Monads?
Scala Exchange 2017 had a few talks on Tagless Final which is a pattern for building a DSL in Scala.

* [Adam Warski - Free Monad or Tagless Final? How Not to Commit to a Monad Too Early](https://skillsmatter.com/skillscasts/10958-free-monad-or-tagless-final-how-not-to-commit-to-a-monad-too-early)
* [Michał Płachta - Freestyle, Free & Tagless: Separation of Concerns on Steroids](https://skillsmatter.com/skillscasts/10961-freestyle-free-and-tagless-separation-of-concerns-on-steroids)
* [Luka Jacobowitz - Building a Tagless Final DSL for WebGL in Scala](https://skillsmatter.com/skillscasts/11014-building-a-tagless-final-dsl-for-webgl-in-scala)

Some knowledge of free monads was kinda assumed which meant I had some catching up to do!

---
## Recommend videos on Free Monads
* [Kelley Robinson - Why the free Monad isn't free](https://www.youtube.com/watch?v=U0lK0hnbc4U) - By far the best video!
* [Chris Myers - A Year living Freely](https://www.youtube.com/watch?v=rK53C-xyPWw) - Start watching after 11 minutes
* [Daniel Spiewak - Free as in Monads](https://www.youtube.com/watch?v=aKUQUIHRGec) - This one moves too fast if you're just starting out.
---
Free Monads allow you to define and run a sequence of operations like this ...
```scala
val startupProgram = for {
  _               <- startKamonMonitoring
  kafka           <- startKafka
  akkaCluster     <- startAkkaCluster
} yield (kafka, akkaShardRegion)

val startupProgramInterpreter: ??? = ???  // We'll get into this!

val (kafka, akka) = startupProgram foldmap startupProgramInterpreter

val shutdownProgram = for {
  _ <- stopKamonMonitoring
  _ <- stopKafka(kafka)
  _ <- stopAkkaCluster(akkaCluster)
} yield ()

val shutdownProgramInterpreter: ??? = ???  // We'll get into this!

val shutdownProgram foldmap shutdownProgramInterpreter
```

* What is ```startKamonMonitoring```, ```startKafka``` and ```startAkkaCluster```?
* What is ```startupProgramInterpreter```?
* What is ```foldMap``` used for?
___

## How to create a Free Monad using ```Cats```
First, we define our operations as an **Algebraic Data Type**.
```scala
sealed trait Action[A]
case class StartMonitoring                   extends Action[Unit]
case class StartAkkaCluster                  extends Action[ActorRef]
case class StartKafka(akkaCluster: ActorRef) extends Action[ActorRef]
````
These are just data types and, clearly, no actual work is performed by this ADT.

We refer to the above ADT as our **Algebra**.

Next we lift each instance of our algebra into an instance of ```cats.free.Free```.
```scala
def startKamonMonitoring: Free[StartupAction, Unit] = Free.liftF(StartMonitoring)
def startAkkaCluster: Free[StartupAction, ActorRef] = Free.liftF(StartAkkaCluster)
def startKafka(ref: ActorRef): Free[StartupAction, Option[ActorRef]] = Free.liftF(StartKafka(ref))
```
So what does ```Free.liftF``` do?

```cats.free.Free``` is an abstract class with three subclasses; ```Return```, ```Suspend``` and ```FlatMapped```.

When we called ```Free.liftF(StartMonitoring)```, a new instance of ```Free.Suspend[StartMonitoring, Unit]``` was created.

Similarly, calls to ```Free.liftF(StartAkkaCluster)``` and ```Free.liftF(StartKafka(ref))``` returned a ```Suspend[StartAkkaCluster, ActorRef]``` and ```Suspend[StartKafka, ActorRef]``` respectively.

```cats.free.Free.Suspend``` defines a ```flatMap``` function so it can be used in a for-comprehension as follows:
```scala
val startupProgram = for {
  _               <- startKamonMonitoring
  kafka           <- startKafka
  akkaCluster     <- startAkkaCluster
} yield (kafka, akkaCluster)
```
**Question** - What does the above expression give us?
**Answer** -  It gives us a case class that represents the sequence of operations we wish to run.
We refer to this value as our **Program**.  It is an algebraic representation of the work we wish to perform.

## How to run your Free Monad?
This is where we get some pay-off for all this work we have done so far!

We pass our program into an **Interpreter**.  The interpreter knows how to interpret each component of our program and will perform the work we need to be done.

We run our program like this
```scala
val (kafka, akkaCluster) = startuprogram foldMap Interpreter
```
where ```Interpreter``` is defined as follows.

```scala
object Interpreter extends (Action ~> Id) {
  override def apply[A](fa: Action[A]): Id[A] = fa match {
    case StartMonitoring =>
      kamon.start().asInstanceOf[A]

    case StartAkkaCluster =>
      val akkaCluster = // code that will fire up an Akka Cluster and return an Akka Shard Region
      akkaCluster.asInstanceOf[A]

    case StartKafka(ref) =>
      val kafka = ??? // code that will fire up Kafka and in some way hook it into the Akka Cluster
      kafka.asInstanceOf[A]
  }
}
```
Note that:
* We are pattern matching on instances of ```Action```
* Then we perform the required work for the given ```Action```
* Then we return the result of that work.

This is an **Interpreter** for our **Algebra**.

**Question** - But what is ```Action ~> Id```?
**Answer** - It is a functional structure called a **Natural Transformation** which will convert instances of ```Action``` in instances of ```cats.Id```.

```cats.Id``` is defined in the ```cats``` library as ...
```scala
type Id[A] = A
```
```cats``` provides Monad instances for ```cats.Id``` which means that the value that is generated by our Interpreter (e.g ```ActorRef``` or ```Unit```) can also be returned by the Natural Transformation.

----

## What are the benefits of using a Free Monad?

### Multiple Interpreters
Unit tests may use a different interpreter than the production interpreter.

### Stack Safety
The for-comprehension which creates the program simply creates a recursive data structure.
The program is then run against your chosen interpreter in a tail-recursive loop.
All execution occurs on the heap rather than the stack.

----

## Error Handling
Error Handling doesn't come for free.  ```Either``` needs to be explictly used or else the interpreter will throw an exception.





















