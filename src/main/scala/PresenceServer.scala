import akka.actor._
import akka.remote.RemoteActorRefProvider
import akka.serialization.{Serialization, JavaSerializer}
import com.typesafe.config.ConfigFactory
import io.Source
import java.io.{ByteArrayInputStream, InputStream}
import java.util.logging.{Level, LogManager}
import org.jgroups.Channel._
import org.jgroups.{Message, ReceiverAdapter, JChannel}
import util.Random

object PresenceServer extends App {
  implicit val sys = ActorSystem("PresenceServer", ConfigFactory.load.getConfig("PresenceServer"))

  delayedInit {
    val addr = sys.asInstanceOf[ActorSystemImpl].provider.asInstanceOf[RemoteActorRefProvider].transport.address
    val actor = sys.actorOf(Props(new PresenceService(new Presence(1000000))), "PresenceService")
    val channel = new DiscoveryChannel("PresenceCluster")
    channel.registerListener(actor)

    println("STARTED @ [%d]" format addr.port.get)
    sys.awaitTermination()
  }

  def startInNewJvm() = {
    val separator = System.getProperty("file.separator");
    val classpath = System.getProperty("java.class.path");
    val path = System.getProperty("java.home") + separator + "bin" + separator + "java";
    val processBuilder = new ProcessBuilder(path, "-cp", classpath, "PresenceServer");


    val process: Process = processBuilder.start()
    println("Started [%s]" format process)
    process
  }
}

