import akka.actor.{ActorSystem, Props, ActorRef, Actor}
import akka.dispatch.{Futures, Promise, Future}
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask
import collection.immutable.BitSet
import collection.mutable.Queue

class PresenceService(presence: Presence) extends Actor {
  var asOf = 0
  var restoreInProgress : Option[ActorRef] = None
  override def receive = {
    case op@Get(index) => {
      context.sender ! ReadResponse(presence(index), asOf)
    }
    case op@Put(index, value, meta) => {
      presence.set(index, value)
      asOf += 1
      context.sender ! Ack(meta.id)
      restoreInProgress.map(_ ! ('forward, op))
    }
    case 'healthcheck => context.sender ! 'ok
    case ('getAvailableNodes, sender: ActorRef) => {
      sender !('availableNode, self)
    }

    case 'prepareRestore => {
      if (restoreInProgress isDefined){
        context.sender ! ('rejectRestore, "too busy")
      }else{

        val restore = context.actorOf(Props(new RestoreServer(presence, self)))
        restoreInProgress = Some(restore)
        context.sender ! ('restorePrepared, restore)
      }
    }
    case 'restoreFinished => restoreInProgress = None
    case x => println("Unrecognized message [%s]" format x)
  }
}

class RestoreServer(presence:Presence, presenceService: ActorRef) extends Actor{
  var lastContacted = System.currentTimeMillis()
  var restoringClient : Option[ActorRef] = None

  context.system.scheduler.scheduleOnce(20 seconds, self, 'healthcheck)

  def receive = {
    case ('fetchBlock, bStart, bEnd) => {
      lastContacted = System.currentTimeMillis()
      context.sender ! ('block, bStart, presence.slice(bStart, bEnd))
    }
    case op @ ('forward, _) => restoringClient.map(_ ! op)
    case ('startRestore, client : ActorRef) => {restoringClient = Some(client); context.sender ! 'restoreStarted}
    case 'restoreFinished => {
      presenceService ! 'restoreFinished
      context.stop(self)
    }
    case 'healthcheck => if (System.currentTimeMillis() - lastContacted  > 20000) self ! 'restoreFinished else  context.system.scheduler.scheduleOnce(20 seconds, self, 'healthcheck)
  }
}

class Restore(presence:Presence)(implicit sys:ActorSystem, timeout : Timeout = 1 minute){

  def startRestore(servers:Seq[ActorRef]) : Future[Any] = {
    val restoreStarted = Promise[ActorRef]()

    servers.foreach{ server =>
      implicit val prepareTimeout = Timeout(10 seconds)
      (server ? 'prepareRestore).onSuccess{
        case ('restorePrepared, restoreServer : ActorRef) => if(!restoreStarted.isCompleted) restoreStarted.success(restoreServer) else restoreServer ! 'restoreFinished
      }
    }
    restoreStarted.map{ case restoreServer => sys.actorOf(Props(new RestoreClient(presence, restoreServer))).?('start)(timeout) }
  }



}
class RestoreClient(presence: Presence, restoreServer:ActorRef) extends Actor{
  val pendingOps = Queue[Put]()
  var restoreInitiator : ActorRef = _
  var awaitingBlocks = 0

  def receive = {
    case 'start => {
      restoreInitiator = context.sender
      restoreServer ! 'startRestore
    }
    case 'restoreStarted =>{
      for (slice <- (1 to presence.size).grouped(100000)){
        restoreServer ! ('fetchBlock, slice.head, slice.last)
        awaitingBlocks+=1
      }
    }
    case ('forward, op @ Put(index, value, _)) => pendingOps+=op
    case ('block, start:Int, slice:BitSet) => {
      awaitingBlocks-=1
      presence.overwrite(start, slice)
      if(awaitingBlocks == 0) {
        for( op <- pendingOps){
          presence.set(op.index, op.value)
        }
        restoreInitiator ! 'restoreFinished
        context.stop(self)
      }
    }
  }


}

case class Meta(id:Long)
case class Ack(id:Long)
case class WriteFailed(id:Long)

case class Put(index:Int, value: Boolean, meta: Meta)
case class Get(index:Int)
case class ReadResponse(value:Boolean, asOf:Long)
