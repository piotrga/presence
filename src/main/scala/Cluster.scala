import akka.actor._
import akka.remote.RemoteActorRefProvider
import akka.serialization.{Serialization, JavaSerializer, SerializationExtension}
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask
import collection.mutable
import mutable.SynchronizedSet
import org.jgroups.{JChannel, ReceiverAdapter, Message}

class Cluster(implicit val sys: ActorSystem) {
  val servers = new mutable.HashSet[ActorRef]() with SynchronizedSet[ActorRef]

  startHealthcheck(servers)
  val manager  = sys.actorOf(Props(new Manager()))
  val presenceChanel = new DiscoveryChannel("PresenceCluster")


  sys.scheduler.schedule(0 millis, 1 seconds, new Runnable { def run() { requestAvailableNodesInfo() }})

  private def requestAvailableNodesInfo() {
    val msg: (Symbol, ActorRef) = ('getAvailableNodes, manager)
    presenceChanel.send(msg)
  }


  def startHealthcheck(nodes: Iterable[ActorRef]) {
    nodes.foreach(node => sys.actorOf(Props(new HealthCheck(node, this))))
  }

  class HealthCheck(target: ActorRef, cluster: Cluster) extends Actor {
    context.system.scheduler.scheduleOnce(0 millis, self, 'healthcheck)

    protected def receive = {
      case 'healthcheck => {
        implicit val timeout = Timeout(1 second)
        (target ? 'healthcheck).onComplete {
          case Right('ok) => context.system.scheduler.scheduleOnce(200 millis, self, 'healthcheck)
          case Left(error) => self !('error, error.toString)
          case resp => self !('error, "Unrecognized response [%s]" format resp)
        }
      }
      case ('error, error) => {
        println("Removing [%s] from the cluster. Error [%s]" format(target.path, error))
        cluster.serverNotResponding(target)
        context.stop(self)
      }

    }
  }

  class Manager extends Actor {
    def receive = {
      case ('availableNode, node: ActorRef) => {
        if (!servers.contains(node)){
          servers += node
          startHealthcheck(Set(node))
          println("Available servers [%d]" format servers.size)
        }
      }
      case msg => println("Manager doesn't understand messsage [%s]" format msg)
    }
  }


  def serverNotResponding(server: ActorRef) {
    servers.remove(server)
  }

  def minReads = servers.size / 2 + 1
  def minWrites = servers.size / 2 + 1

  def onlineServers = servers.toSet
}
