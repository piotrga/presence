class DiscoveryChannel(groupName: String) {

  val config = """
<config xmlns="urn:org:jgroups"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="urn:org:jgroups http://www.jgroups.org/schema/JGroups-3.0.xsd">

      <TCP bind_port="17800" bind_addr="localhost" />
      <TCPPING level="debug" timeout="3000" initial_hosts="localhost[17800],localhost[17801]" port_range="1" break_on_coord_rsp="true"/>
      <VERIFY_SUSPECT timeout="1500"  />
      <pbcast.NAKACK use_mcast_xmit="false" retransmit_timeout="300,600,1200,2400,4800" discard_delivered_msgs="true"/>
      <pbcast.STABLE stability_delay="1000" desired_avg_gossip="50000" max_bytes="400000"/>
      <pbcast.GMS print_local_addr="true" join_timeout="3000" view_bundling="true"/>
</config>
"""


  val channel = new JChannel(new ByteArrayInputStream(config.getBytes))
  channel.connect(groupName)

  def registerListener(listener: ActorRef)(implicit sys: ActorSystem) {
    channel.setReceiver(new ReceiverAdapter() {
      override def receive(msg: Message) {
        JavaSerializer.currentSystem.withValue(sys.asInstanceOf[ExtendedActorSystem]) {
          System.out.println("received msg from " + msg.getSrc() + ": " + msg.getObject())
          listener ! msg.getObject
        }
      }
    })

  }

  def send(message: Any)(implicit sys: ActorSystem) {
    JavaSerializer.currentSystem.withValue(sys.asInstanceOf[ExtendedActorSystem]) {
      Serialization.currentTransportAddress.withValue(sys.asInstanceOf[ExtendedActorSystem].provider.asInstanceOf[RemoteActorRefProvider].transport.address) {
        channel.send(new Message(null, null, message))
      }
    }
  }

}
