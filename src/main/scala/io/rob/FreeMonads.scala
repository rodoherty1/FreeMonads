package io.rob

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import cats.free.Free
import cats.free.Free.liftF
import cats.{Id, ~>}

sealed trait StartupActionA[A] extends Product with Serializable

case object StartCluster extends StartupActionA[Unit]
case object StartEventActorShard extends StartupActionA[ActorRef]
case class StartKafka(ref: ActorRef) extends StartupActionA[Option[ActorRef]]

object Constructors {
  type StartupAction[A] = Free[StartupActionA,A]

  def startCluster: StartupAction[Unit] = liftF[StartupActionA, Unit](StartCluster)
  def startEventActorShard: StartupAction[ActorRef] = liftF[StartupActionA, ActorRef](StartEventActorShard)
  def startKafka(ref: ActorRef): StartupAction[Option[ActorRef]] = liftF[StartupActionA, Option[ActorRef]](StartKafka(ref))
}

object Program {
  import Constructors._

  def run: StartupAction[Option[ActorRef]] = for {
    _          <- startCluster
    eventActor <- startEventActorShard
    kafkaActor <- startKafka(eventActor)
  } yield kafkaActor
}


object Interpreter extends (StartupActionA ~> Id) {

  class MyActor extends Actor {
    override def receive: Receive = {
      case _ => ()
    }
  }

  implicit val system = ActorSystem()

  override def apply[A](fa: StartupActionA[A]): Id[A] = fa match {
    case StartCluster =>
      println("Starting up the Akka Cluster").asInstanceOf[A]
    case StartEventActorShard =>
      system.actorOf(Props(new MyActor()), "MyActor").asInstanceOf[A]
    case StartKafka(ref) =>
      Some(ref).asInstanceOf[A]
  }
}


object MyApp extends App {
  println(Program.run.foldMap(Interpreter).toString)
}



