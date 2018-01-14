package io.rob

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import cats.free.Free
import cats.{Id, ~>}

sealed trait StartupAction[A] extends Product with Serializable

case object StartKamonMonitoring extends StartupAction[Unit]
case object StartAkkaCluster extends StartupAction[ActorRef]
case class StartKafka(ref: ActorRef) extends StartupAction[Option[ActorRef]]

object Constructors {
  type StartupActionF[A] = Free[StartupAction,A]

  def startKamonMonitoring: Free[StartupAction, Unit] = Free.liftF(StartKamonMonitoring)
  def startAkkaCluster: Free[StartupAction, ActorRef] = Free.liftF(StartAkkaCluster)
  def startKafka(ref: ActorRef): Free[StartupAction, Option[ActorRef]] = Free.liftF(StartKafka(ref))
}

object Program {
  import Constructors._

  def run: Free[StartupAction, Option[ActorRef]] = for {
    _           <- startKamonMonitoring
    akkaCluster <- startAkkaCluster
    kafkaActor  <- startKafka(akkaCluster)
  } yield kafkaActor
}


object Interpreter extends (StartupAction ~> Id) {

  class MyActor extends Actor {
    override def receive: Receive = {
      case _ => ()
    }
  }

  implicit val system: ActorSystem = ActorSystem()

  override def apply[A](fa: StartupAction[A]): Id[A] = fa match {
    case StartKamonMonitoring =>
      println("Starting up the Akka Cluster").asInstanceOf[A]
    case StartAkkaCluster =>
      system.actorOf(Props(new MyActor()), "MyActor").asInstanceOf[A]
    case StartKafka(ref) =>
      Some(ref).asInstanceOf[A]
  }
}


object MyApp extends App {
  println(Program.run.foldMap(Interpreter).toString)
}



