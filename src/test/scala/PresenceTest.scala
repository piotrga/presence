import akka.actor._
import akka.remote.RemoteActorRefProvider
import akka.util.{Timeout, Duration}
import annotation.tailrec
import com.typesafe.config.ConfigFactory
import io.Source
import java.io.InputStream
import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec
import util.Random
import akka.util.duration._
import akka.pattern.ask
import scala.collection.mutable


class PresenceTest extends WordSpec with MustMatchers {
  implicit def thread[F](f: => F) : Thread = (new Thread( new Runnable() { def run() { f } } ))

  "Presence" must {
    "set/read presence" in {
      val p = new Presence(10)
      p.set(7, true)
      p(7) must be(true)
      for (i <- 0 to 6) p(i) must be(false)
      for (i <- 8 until 10) p(i) must be(false)
    }

    "sync" in {
      val SIZE = 1000000
      val NODES = 3
      val processes = (1 to NODES).map{x => PresenceServer.startInNewJvm()}
      Runtime.getRuntime.addShutdownHook(processes.foreach(_.destroy()))

      val ITERATIONS = 100000
      implicit val sys = ActorSystem("myApp", ConfigFactory.load.getConfig("PresenceClient"))
//      val servers = mutable.Set(56639, 56642, 56645).map(port => sys.actorFor("akka://PresenceServer@127.0.0.1:%d/user/PresenceService" format port))
//      println("Created servers [%s]" format servers)

      val cluster = new Cluster()
      val cli = new PresenceClient(cluster, readTimeout = 5 second, writeTimeout = 5 second)

      val durations = (for (
        is <- (1 to ITERATIONS).grouped(ITERATIONS/8).toList.par;
        i <- is
      ) yield {
        val value = Random.nextBoolean()
        val key = Random.nextInt(SIZE)

        val duration = time {
          retry(3){
            cli.set(key, value)
            cli(key) must be(value)
          }
        }
        if (i % 100 == 0) println("Duration [%d]" format duration)
//        Thread.sleep(Random.nextInt(50))
        duration
      })
      val avg = durations.sum.toDouble / ITERATIONS
      val throughput = ITERATIONS * 1000 / durations.sum
      println("r/w  AVG[%.1f ms], THROUGHPUT[%d/sec]" format(avg, throughput))
    }
  }

  def time(f: => Unit): Long = {
    val start = System.currentTimeMillis()
    f
    System.currentTimeMillis() - start
  }
  
  @tailrec
  private def retry[T](times:Int = 3)(fn: => T):T ={
    try return fn
    catch{
      case t: Exception => if(times == 0) throw t
    }
    retry(times-1)(fn)
  }
}

class RandomDelayRelay(target: ActorRef, delay: Duration, deviation: Duration) extends Actor {
  def sleep() = Thread.sleep(delay.toMillis + Random.nextInt(deviation.toMillis.toInt))

  protected def receive = {
    case m => {
      sleep()
      val sender = context.sender
      implicit val timeout = Timeout(1 hour)
      (target ? m).onSuccess {
        case x => {
          sleep()
          sender ! x
        }
      }
    }
  }
}






object Client extends App {
  val sys = ActorSystem("PresenceClient", ConfigFactory.load.getConfig("PresenceClient"))
  val actor = sys.actorFor("akka://PresenceServer@127.0.0.1:4444/user/PresenceService")
  println(actor.path)
  implicit val timeout = Timeout(1 second)
  (actor ? Put(10, true, Meta(123))).onComplete {
    case r => println("success" + r)
  }

}