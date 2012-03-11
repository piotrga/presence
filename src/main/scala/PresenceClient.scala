import akka.dispatch.{Await, ExecutionContext, Future, DefaultPromise}
import akka.util.{Timeout, Duration}
import akka.util.duration._
import akka.pattern.ask
import collection.mutable.ListBuffer

class PresenceClient(cluster: Cluster, readTimeout: Duration = 500 millis, writeTimeout: Duration = 1000 millis)(implicit executionContext: ExecutionContext) {

  import cluster._

  def apply(index: Int) = {
    implicit val timeout = Timeout(readTimeout)
    val futures = onlineServers.map(s => (s ? Get(index)).mapTo[ReadResponse])
    val responses = Await.result(new FirstSuccessesPromise(futures, minReads), readTimeout)
    if (responses.map(_.asOf).toSet.size > 1) println("Responses [%s]" format responses)
    responses.maxBy(_.asOf).value
  }

  def set(index: Int, value: Boolean) {
    implicit val timeout = Timeout(writeTimeout)
    val futures = onlineServers.map(s => (s ? Put(index, value, Meta(1))).mapTo[Ack])
    Await.result(new FirstSuccessesPromise(futures, minWrites), writeTimeout)
  }

  class FirstSuccessesPromise[T](futures: Traversable[Future[T]], requiredSuccesses: Int) extends DefaultPromise[List[T]]() {
    //    require(futures.size >= requiredSuccesses)
    private val results = new BoundCapacityBuffer[T](requiredSuccesses)
    futures.foreach(future => future.onSuccess {
      case result => if (results.append(result)) this.success(results.toList)
    })

    def finished = results.toList
  }

}


class BoundCapacityBuffer[T](expectedValues: Int){
  val buffer = new ListBuffer[T]()
  buffer.sizeHint(expectedValues)

  def append(v:T) = synchronized {
    if(buffer.length < expectedValues) {
      buffer.append(v)
      (buffer.length == expectedValues)
    } else false
  }

  def toList = synchronized{buffer.toList}
}