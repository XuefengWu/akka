/**
 *  Copyright (C) 2009-2011 Scalable Solutions AB <http://scalablesolutions.se>
 */

package akka.cluster.routing.roundrobin_2_replicas

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll

import org.apache.bookkeeper.client.{ BookKeeper, BKException }
import BKException._

import akka.cluster._
import akka.actor._
import akka.actor.Actor._
import akka.config.Config

object RoundRobin2ReplicasMultiJvmSpec {
  val NrOfNodes = 3

  class HelloWorld extends Actor with Serializable {
    def receive = {
      case "Hello" ⇒
        println("Received message on [" + Config.nodename + "]")
        self.reply("World from node [" + Config.nodename + "]")
    }
  }
}

class RoundRobin2ReplicasMultiJvmNode1 extends WordSpec with MustMatchers with BeforeAndAfterAll {
  import RoundRobin2ReplicasMultiJvmSpec._

  private var bookKeeper: BookKeeper = _
  private var localBookKeeper: LocalBookKeeper = _

  "A cluster" must {

    "create clustered actor, get a 'local' actor on 'home' node and a 'ref' to actor on remote node" in {
      System.getProperty("akka.cluster.nodename", "") must be("node1")
      System.getProperty("akka.cluster.port", "") must be("9991")

      Cluster.barrier("start-node1", NrOfNodes) {
        Cluster.node.start()
      }

      Cluster.barrier("start-node2", NrOfNodes) {}

      Cluster.barrier("start-node3", NrOfNodes) {}

      Cluster.barrier("get-ref-to-actor-on-node2", NrOfNodes) {}

      Cluster.barrier("send-message-from-node2-to-replicas", NrOfNodes) {}

      Cluster.node.shutdown()
    }
  }

  override def beforeAll() = {
    Cluster.startLocalCluster()
    LocalBookKeeperEnsemble.start()
  }

  override def afterAll() = {
    Cluster.shutdownLocalCluster()
    TransactionLog.shutdown()
    LocalBookKeeperEnsemble.shutdown()
  }
}

class RoundRobin2ReplicasMultiJvmNode2 extends WordSpec with MustMatchers {
  import RoundRobin2ReplicasMultiJvmSpec._

  "A cluster" must {

    "create clustered actor, get a 'local' actor on 'home' node and a 'ref' to actor on remote node" in {
      System.getProperty("akka.cluster.nodename", "") must be("node2")
      System.getProperty("akka.cluster.port", "") must be("9992")

      Cluster.barrier("start-node1", NrOfNodes) {}

      Cluster.barrier("start-node2", NrOfNodes) {
        Cluster.node.start()
      }

      Cluster.barrier("start-node3", NrOfNodes) {}

      var hello: ActorRef = null
      Cluster.barrier("get-ref-to-actor-on-node2", NrOfNodes) {
        hello = Actor.actorOf[HelloWorld]("service-hello")
        hello must not equal (null)
        hello.address must equal("service-hello")
        hello.isInstanceOf[ClusterActorRef] must be(true)
      }

      Cluster.barrier("send-message-from-node2-to-replicas", NrOfNodes) {
        hello must not equal (null)

        val replies = collection.mutable.Map.empty[String, Int]
        def count(reply: String) = {
          if (replies.get(reply).isEmpty) replies.put(reply, 1)
          else replies.put(reply, replies(reply) + 1)
        }

        count((hello ? "Hello").as[String].getOrElse(fail("Should have recieved reply from node1")))
        count((hello ? "Hello").as[String].getOrElse(fail("Should have recieved reply from node3")))
        count((hello ? "Hello").as[String].getOrElse(fail("Should have recieved reply from node1")))
        count((hello ? "Hello").as[String].getOrElse(fail("Should have recieved reply from node3")))
        count((hello ? "Hello").as[String].getOrElse(fail("Should have recieved reply from node1")))
        count((hello ? "Hello").as[String].getOrElse(fail("Should have recieved reply from node3")))
        count((hello ? "Hello").as[String].getOrElse(fail("Should have recieved reply from node1")))
        count((hello ? "Hello").as[String].getOrElse(fail("Should have recieved reply from node3")))

        replies("World from node [node1]") must equal(4)
        replies("World from node [node3]") must equal(4)
      }

      Cluster.node.shutdown()
    }
  }
}

class RoundRobin2ReplicasMultiJvmNode3 extends WordSpec with MustMatchers {
  import RoundRobin2ReplicasMultiJvmSpec._

  "A cluster" must {

    "create clustered actor, get a 'local' actor on 'home' node and a 'ref' to actor on remote node" in {
      System.getProperty("akka.cluster.nodename", "") must be("node3")
      System.getProperty("akka.cluster.port", "") must be("9993")

      Cluster.barrier("start-node1", NrOfNodes) {}

      Cluster.barrier("start-node2", NrOfNodes) {}

      Cluster.barrier("start-node3", NrOfNodes) {
        Cluster.node.start()
      }

      Cluster.barrier("get-ref-to-actor-on-node2", NrOfNodes) {}

      Cluster.barrier("send-message-from-node2-to-replicas", NrOfNodes) {}

      Cluster.node.shutdown()
    }
  }
}
