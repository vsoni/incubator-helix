package org.apache.helix.integration;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.helix.NotificationContext;
import org.apache.helix.TestHelper;
import org.apache.helix.controller.HelixControllerMain;
import org.apache.helix.mock.participant.MockParticipant;
import org.apache.helix.mock.participant.MockTransition;
import org.apache.helix.model.Message;
import org.apache.helix.tools.ClusterStateVerifier;
import org.apache.helix.tools.ClusterStateVerifier.BestPossAndExtViewZkVerifier;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestRestartParticipant extends ZkIntegrationTestBase {
  public class KillOtherTransition extends MockTransition {
    final AtomicReference<MockParticipant> _other;

    public KillOtherTransition(MockParticipant other) {
      _other = new AtomicReference<MockParticipant>(other);
    }

    @Override
    public void doTransition(Message message, NotificationContext context) {
      MockParticipant other = _other.getAndSet(null);
      if (other != null) {
        System.err.println("Kill " + other.getInstanceName()
            + ". Interrupted exceptions are IGNORABLE");
        other.syncStop();
      }
    }
  }

  @Test()
  public void testRestartParticipant() throws Exception {
    // Logger.getRootLogger().setLevel(Level.INFO);
    System.out.println("START testRestartParticipant at " + new Date(System.currentTimeMillis()));

    String clusterName = getShortClassName();
    MockParticipant[] participants = new MockParticipant[5];

    TestHelper.setupCluster(clusterName, ZK_ADDR, 12918, // participant port
        "localhost", // participant name prefix
        "TestDB", // resource name prefix
        1, // resources
        10, // partitions per resource
        5, // number of nodes
        3, // replicas
        "MasterSlave", true); // do rebalance

    TestHelper
        .startController(clusterName, "controller_0", ZK_ADDR, HelixControllerMain.STANDALONE);
    // start participants
    for (int i = 0; i < 5; i++) {
      String instanceName = "localhost_" + (12918 + i);

      if (i == 4) {
        participants[i] =
            new MockParticipant(clusterName, instanceName, ZK_ADDR, new KillOtherTransition(
                participants[0]));
      } else {
        participants[i] = new MockParticipant(clusterName, instanceName, ZK_ADDR, null);
        // Thread.sleep(100);
      }

      participants[i].syncStart();
    }

    boolean result =
        ClusterStateVerifier.verifyByZkCallback(new BestPossAndExtViewZkVerifier(ZK_ADDR,
            clusterName));
    Assert.assertTrue(result);

    // restart
    Thread.sleep(500);
    MockParticipant participant =
        new MockParticipant(participants[0].getClusterName(), participants[0].getInstanceName(),
            ZK_ADDR, null);
    System.err.println("Restart " + participant.getInstanceName());
    participant.syncStart();
    result =
        ClusterStateVerifier.verifyByZkCallback(new BestPossAndExtViewZkVerifier(ZK_ADDR,
            clusterName));
    Assert.assertTrue(result);

    System.out.println("START testRestartParticipant at " + new Date(System.currentTimeMillis()));

  }
}
