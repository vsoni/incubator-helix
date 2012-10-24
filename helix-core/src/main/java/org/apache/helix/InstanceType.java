/**
 * Copyright (C) 2012 LinkedIn Inc <opensource@linkedin.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.helix;

/**
 * CONTROLLER:     cluster managing component is a controller
 * PARTICIPANT:    participate in the cluster state changes
 * SPECTATOR:      interested in the state changes in the cluster
 * CONTROLLER_PARTICIPANT:
 *  special participant that competes for the leader of CONTROLLER_CLUSTER
 *  used in cluster controller of distributed mode {@HelixControllerMain}
 *
 */
public enum InstanceType
{
  CONTROLLER,
  PARTICIPANT,
  SPECTATOR,
  CONTROLLER_PARTICIPANT,
  ADMINISTRATOR
}