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

import org.apache.helix.TestHelper;
import org.apache.helix.TestHelper.StartCMResult;
import org.apache.helix.controller.HelixControllerMain;
import org.apache.helix.tools.ClusterStateVerifier;
import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestStandAloneCMMain extends ZkStandAloneCMTestBase {
  private static Logger logger = Logger.getLogger(TestStandAloneCMMain.class);

  @Test()
  public void testStandAloneCMMain() throws Exception {
    logger.info("RUN testStandAloneCMMain() at " + new Date(System.currentTimeMillis()));

    for (int i = 1; i <= 2; i++) {
      String controllerName = "controller_" + i;
      StartCMResult startResult =
          TestHelper.startController(CLUSTER_NAME, controllerName, ZK_ADDR,
              HelixControllerMain.STANDALONE);
      _startCMResultMap.put(controllerName, startResult);
    }

    stopCurrentLeader(_zkClient, CLUSTER_NAME, _startCMResultMap);
    boolean result =
        ClusterStateVerifier.verifyByPolling(new ClusterStateVerifier.BestPossAndExtViewZkVerifier(
            ZK_ADDR, CLUSTER_NAME));
    Assert.assertTrue(result);

    logger.info("STOP testStandAloneCMMain() at " + new Date(System.currentTimeMillis()));
  }

}
