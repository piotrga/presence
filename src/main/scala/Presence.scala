import akka.actor.{Actor, ActorRef}
import akka.dispatch._
import akka.pattern.ask
import akka.util.duration._
import akka.util.{Duration, Timeout}
import collection.immutable
import collection.mutable.{BitSet, ListBuffer}
import sun.jvm.hotspot.utilities.BitMap
import util.Random


class Presence(val size: Int){

  //TODO: verry slow! (Peter G. 11/03/2012)
  def overwrite(start: Int, slice: immutable.BitSet) = for(i<-start until  slice.size) {memory.update(i, slice(i))}

  def slice(start: Int, end: Int)  = memory.slice(start, end)


  val memory = BitSet(size)

  def apply(index: Int): Boolean = memory(index)
  def set(index: Int, present: Boolean) { memory(index) = present }
}