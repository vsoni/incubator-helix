package org.apache.helix.controller.stages;

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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.helix.HelixDataAccessor;
import org.apache.helix.HelixManager;
import org.apache.helix.PropertyKey.Builder;
import org.apache.helix.ZNRecord;
import org.apache.helix.ZkUnitTestBase;
import org.apache.helix.api.accessor.ClusterAccessor;
import org.apache.helix.api.id.ClusterId;
import org.apache.helix.api.id.MessageId;
import org.apache.helix.api.id.PartitionId;
import org.apache.helix.api.id.ResourceId;
import org.apache.helix.controller.pipeline.Pipeline;
import org.apache.helix.manager.zk.ZKHelixDataAccessor;
import org.apache.helix.manager.zk.ZkBaseDataAccessor;
import org.apache.helix.model.ClusterConstraints;
import org.apache.helix.model.ClusterConstraints.ConstraintAttribute;
import org.apache.helix.model.ClusterConstraints.ConstraintType;
import org.apache.helix.model.ConstraintItem;
import org.apache.helix.model.Message;
import org.apache.helix.model.Message.MessageType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestMessageThrottleStage extends ZkUnitTestBase {
  final String _className = getShortClassName();

  @Test
  public void testMsgThrottleBasic() throws Exception {
    String clusterName = "CLUSTER_" + _className + "_basic";
    System.out.println("START " + clusterName + " at " + new Date(System.currentTimeMillis()));

    HelixDataAccessor accessor =
        new ZKHelixDataAccessor(clusterName, new ZkBaseDataAccessor<ZNRecord>(_gZkClient));
    HelixManager manager = new DummyClusterManager(clusterName, accessor);

    // ideal state: node0 is MASTER, node1 is SLAVE
    // replica=2 means 1 master and 1 slave
    setupIdealState(clusterName, new int[] {
        0, 1
    }, new String[] {
      "TestDB"
    }, 1, 2);
    setupLiveInstances(clusterName, new int[] {
        0, 1
    });
    setupStateModel(clusterName);

    ClusterAccessor clusterAccessor = new ClusterAccessor(ClusterId.from(clusterName), accessor);
    clusterAccessor.initClusterStructure();

    ClusterEvent event = new ClusterEvent("testEvent");
    event.addAttribute("helixmanager", manager);

    NewMessageThrottleStage throttleStage = new NewMessageThrottleStage();
    try {
      runStage(event, throttleStage);
      Assert.fail("Should throw exception since DATA_CACHE is null");
    } catch (Exception e) {
      // OK
    }

    Pipeline dataRefresh = new Pipeline();
    dataRefresh.addStage(new NewReadClusterDataStage());
    runPipeline(event, dataRefresh);

    try {
      runStage(event, throttleStage);
      Assert.fail("Should throw exception since RESOURCE is null");
    } catch (Exception e) {
      // OK
    }
    runStage(event, new NewResourceComputationStage());

    try {
      runStage(event, throttleStage);
      Assert.fail("Should throw exception since MESSAGE_SELECT is null");
    } catch (Exception e) {
      // OK
    }
    NewMessageOutput msgSelectOutput = new NewMessageOutput();
    List<Message> selectMessages = new ArrayList<Message>();
    Message msg =
        createMessage(MessageType.STATE_TRANSITION, MessageId.from("msgId-001"), "OFFLINE",
            "SLAVE", "TestDB", "localhost_0");
    selectMessages.add(msg);

    msgSelectOutput.setMessages(ResourceId.from("TestDB"), PartitionId.from("TestDB_0"),
        selectMessages);
    event.addAttribute(AttributeName.MESSAGES_SELECTED.toString(), msgSelectOutput);

    runStage(event, throttleStage);

    NewMessageOutput msgThrottleOutput =
        event.getAttribute(AttributeName.MESSAGES_THROTTLE.toString());
    Assert.assertEquals(
        msgThrottleOutput.getMessages(ResourceId.from("TestDB"), PartitionId.from("TestDB_0"))
            .size(), 1);

    System.out.println("END " + clusterName + " at " + new Date(System.currentTimeMillis()));

  }

  @Test()
  public void testMsgThrottleConstraints() throws Exception {
    String clusterName = "CLUSTER_" + _className + "_constraints";
    System.out.println("START " + clusterName + " at " + new Date(System.currentTimeMillis()));

    HelixDataAccessor accessor =
        new ZKHelixDataAccessor(clusterName, new ZkBaseDataAccessor<ZNRecord>(_gZkClient));
    HelixManager manager = new DummyClusterManager(clusterName, accessor);

    // ideal state: node0 is MASTER, node1 is SLAVE
    // replica=2 means 1 master and 1 slave
    setupIdealState(clusterName, new int[] {
        0, 1
    }, new String[] {
      "TestDB"
    }, 1, 2);
    setupLiveInstances(clusterName, new int[] {
        0, 1
    });
    setupStateModel(clusterName);

    ClusterAccessor clusterAccessor = new ClusterAccessor(ClusterId.from(clusterName), accessor);
    clusterAccessor.initClusterStructure();

    // setup constraints
    ZNRecord record = new ZNRecord(ConstraintType.MESSAGE_CONSTRAINT.toString());

    // constraint0:
    // "MESSAGE_TYPE=STATE_TRANSITION,CONSTRAINT_VALUE=ANY"
    record.setMapField("constraint0", new TreeMap<String, String>());
    record.getMapField("constraint0").put("MESSAGE_TYPE", "STATE_TRANSITION");
    record.getMapField("constraint0").put("CONSTRAINT_VALUE", "ANY");
    ConstraintItem constraint0 = new ConstraintItem(record.getMapField("constraint0"));

    // constraint1:
    // "MESSAGE_TYPE=STATE_TRANSITION,TRANSITION=OFFLINE-SLAVE,CONSTRAINT_VALUE=ANY"
    record.setMapField("constraint1", new TreeMap<String, String>());
    record.getMapField("constraint1").put("MESSAGE_TYPE", "STATE_TRANSITION");
    record.getMapField("constraint1").put("TRANSITION", "OFFLINE-SLAVE");
    record.getMapField("constraint1").put("CONSTRAINT_VALUE", "50");
    ConstraintItem constraint1 = new ConstraintItem(record.getMapField("constraint1"));

    // constraint2:
    // "MESSAGE_TYPE=STATE_TRANSITION,TRANSITION=OFFLINE-SLAVE,INSTANCE=.*,RESOURCE=TestDB,CONSTRAINT_VALUE=2";
    record.setMapField("constraint2", new TreeMap<String, String>());
    record.getMapField("constraint2").put("MESSAGE_TYPE", "STATE_TRANSITION");
    record.getMapField("constraint2").put("TRANSITION", "OFFLINE-SLAVE");
    record.getMapField("constraint2").put("INSTANCE", ".*");
    record.getMapField("constraint2").put("RESOURCE", "TestDB");
    record.getMapField("constraint2").put("CONSTRAINT_VALUE", "2");
    ConstraintItem constraint2 = new ConstraintItem(record.getMapField("constraint2"));

    // constraint3:
    // "MESSAGE_TYPE=STATE_TRANSITION,TRANSITION=OFFLINE-SLAVE,INSTANCE=localhost_12918,RESOURCE=.*,CONSTRAINT_VALUE=1";
    record.setMapField("constraint3", new TreeMap<String, String>());
    record.getMapField("constraint3").put("MESSAGE_TYPE", "STATE_TRANSITION");
    record.getMapField("constraint3").put("TRANSITION", "OFFLINE-SLAVE");
    record.getMapField("constraint3").put("INSTANCE", "localhost_1");
    record.getMapField("constraint3").put("RESOURCE", ".*");
    record.getMapField("constraint3").put("CONSTRAINT_VALUE", "1");
    ConstraintItem constraint3 = new ConstraintItem(record.getMapField("constraint3"));

    // constraint4:
    // "MESSAGE_TYPE=STATE_TRANSITION,TRANSITION=OFFLINE-SLAVE,INSTANCE=.*,RESOURCE=.*,CONSTRAINT_VALUE=10"
    record.setMapField("constraint4", new TreeMap<String, String>());
    record.getMapField("constraint4").put("MESSAGE_TYPE", "STATE_TRANSITION");
    record.getMapField("constraint4").put("TRANSITION", "OFFLINE-SLAVE");
    record.getMapField("constraint4").put("INSTANCE", ".*");
    record.getMapField("constraint4").put("RESOURCE", ".*");
    record.getMapField("constraint4").put("CONSTRAINT_VALUE", "10");
    ConstraintItem constraint4 = new ConstraintItem(record.getMapField("constraint4"));

    // constraint5:
    // "MESSAGE_TYPE=STATE_TRANSITION,TRANSITION=OFFLINE-SLAVE,INSTANCE=localhost_12918,RESOURCE=TestDB,CONSTRAINT_VALUE=5"
    record.setMapField("constraint5", new TreeMap<String, String>());
    record.getMapField("constraint5").put("MESSAGE_TYPE", "STATE_TRANSITION");
    record.getMapField("constraint5").put("TRANSITION", "OFFLINE-SLAVE");
    record.getMapField("constraint5").put("INSTANCE", "localhost_0");
    record.getMapField("constraint5").put("RESOURCE", "TestDB");
    record.getMapField("constraint5").put("CONSTRAINT_VALUE", "3");
    ConstraintItem constraint5 = new ConstraintItem(record.getMapField("constraint5"));

    Builder keyBuilder = accessor.keyBuilder();
    accessor.setProperty(keyBuilder.constraint(ConstraintType.MESSAGE_CONSTRAINT.toString()),
        new ClusterConstraints(record));

    // ClusterConstraints constraint =
    // accessor.getProperty(ClusterConstraints.class,
    // PropertyType.CONFIGS,
    // ConfigScopeProperty.CONSTRAINT.toString(),
    // ConstraintType.MESSAGE_CONSTRAINT.toString());
    ClusterConstraints constraint =
        accessor.getProperty(keyBuilder.constraint(ConstraintType.MESSAGE_CONSTRAINT.toString()));

    NewMessageThrottleStage throttleStage = new NewMessageThrottleStage();

    // test constraintSelection
    // message1: hit contraintSelection rule1 and rule2
    Message msg1 =
        createMessage(MessageType.STATE_TRANSITION, MessageId.from("msgId-001"), "OFFLINE",
            "SLAVE", "TestDB", "localhost_0");

    Map<ConstraintAttribute, String> msgAttr = ClusterConstraints.toConstraintAttributes(msg1);
    Set<ConstraintItem> matches = constraint.match(msgAttr);
    System.out.println(msg1 + " matches(" + matches.size() + "): " + matches);
    Assert.assertEquals(matches.size(), 5);
    Assert.assertTrue(containsConstraint(matches, constraint0));
    Assert.assertTrue(containsConstraint(matches, constraint1));
    Assert.assertTrue(containsConstraint(matches, constraint2));
    Assert.assertTrue(containsConstraint(matches, constraint4));
    Assert.assertTrue(containsConstraint(matches, constraint5));

    matches = throttleStage.selectConstraints(matches, msgAttr);
    System.out.println(msg1 + " matches(" + matches.size() + "): " + matches);
    Assert.assertEquals(matches.size(), 2);
    Assert.assertTrue(containsConstraint(matches, constraint1));
    Assert.assertTrue(containsConstraint(matches, constraint5));

    // message2: hit contraintSelection rule1, rule2, and rule3
    Message msg2 =
        createMessage(MessageType.STATE_TRANSITION, MessageId.from("msgId-002"), "OFFLINE",
            "SLAVE", "TestDB", "localhost_1");

    msgAttr = ClusterConstraints.toConstraintAttributes(msg2);
    matches = constraint.match(msgAttr);
    System.out.println(msg2 + " matches(" + matches.size() + "): " + matches);
    Assert.assertEquals(matches.size(), 5);
    Assert.assertTrue(containsConstraint(matches, constraint0));
    Assert.assertTrue(containsConstraint(matches, constraint1));
    Assert.assertTrue(containsConstraint(matches, constraint2));
    Assert.assertTrue(containsConstraint(matches, constraint3));
    Assert.assertTrue(containsConstraint(matches, constraint4));

    matches = throttleStage.selectConstraints(matches, msgAttr);
    System.out.println(msg2 + " matches(" + matches.size() + "): " + matches);
    Assert.assertEquals(matches.size(), 2);
    Assert.assertTrue(containsConstraint(matches, constraint1));
    Assert.assertTrue(containsConstraint(matches, constraint3));

    // test messageThrottleStage
    ClusterEvent event = new ClusterEvent("testEvent");
    event.addAttribute("helixmanager", manager);

    Pipeline dataRefresh = new Pipeline();
    dataRefresh.addStage(new NewReadClusterDataStage());
    runPipeline(event, dataRefresh);
    runStage(event, new NewResourceComputationStage());
    NewMessageOutput msgSelectOutput = new NewMessageOutput();

    Message msg3 =
        createMessage(MessageType.STATE_TRANSITION, MessageId.from("msgId-003"), "OFFLINE",
            "SLAVE", "TestDB", "localhost_0");

    Message msg4 =
        createMessage(MessageType.STATE_TRANSITION, MessageId.from("msgId-004"), "OFFLINE",
            "SLAVE", "TestDB", "localhost_0");

    Message msg5 =
        createMessage(MessageType.STATE_TRANSITION, MessageId.from("msgId-005"), "OFFLINE",
            "SLAVE", "TestDB", "localhost_0");

    Message msg6 =
        createMessage(MessageType.STATE_TRANSITION, MessageId.from("msgId-006"), "OFFLINE",
            "SLAVE", "TestDB", "localhost_1");

    List<Message> selectMessages = new ArrayList<Message>();
    selectMessages.add(msg1);
    selectMessages.add(msg2);
    selectMessages.add(msg3);
    selectMessages.add(msg4);
    selectMessages.add(msg5); // should be throttled
    selectMessages.add(msg6); // should be throttled

    msgSelectOutput.setMessages(ResourceId.from("TestDB"), PartitionId.from("TestDB_0"),
        selectMessages);
    event.addAttribute(AttributeName.MESSAGES_SELECTED.toString(), msgSelectOutput);

    runStage(event, throttleStage);

    NewMessageOutput msgThrottleOutput =
        event.getAttribute(AttributeName.MESSAGES_THROTTLE.toString());
    List<Message> throttleMessages =
        msgThrottleOutput.getMessages(ResourceId.from("TestDB"), PartitionId.from("TestDB_0"));
    Assert.assertEquals(throttleMessages.size(), 4);
    Assert.assertTrue(throttleMessages.contains(msg1));
    Assert.assertTrue(throttleMessages.contains(msg2));
    Assert.assertTrue(throttleMessages.contains(msg3));
    Assert.assertTrue(throttleMessages.contains(msg4));

    System.out.println("END " + clusterName + " at " + new Date(System.currentTimeMillis()));

  }

  private boolean containsConstraint(Set<ConstraintItem> constraints, ConstraintItem constraint) {
    for (ConstraintItem item : constraints) {
      if (item.toString().equals(constraint.toString())) {
        return true;
      }
    }
    return false;
  }

  // add pending message test case
}
