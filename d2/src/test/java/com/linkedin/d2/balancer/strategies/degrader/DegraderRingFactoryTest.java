/*
   Copyright (c) 2016 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.d2.balancer.strategies.degrader;


import com.linkedin.d2.balancer.util.hashing.ConsistentHashRing.Point;
import com.linkedin.d2.balancer.util.hashing.Ring;
import com.linkedin.d2.balancer.util.partitions.DefaultPartitionAccessor;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Random;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;


public class DegraderRingFactoryTest
{
  private static final int DEFAULT_PARTITION_ID = DefaultPartitionAccessor.DEFAULT_PARTITION_ID;
  private static final int DEFAULT_CONSISTENT_HASH_VERSION = 1;

  public static void main(String[] args) throws URISyntaxException,
          InterruptedException
  {
    DegraderRingFactoryTest test = new DegraderRingFactoryTest();

    test.testPointsCleanUp();
  }

  private Map<String, Integer> buildPointsMap(int numOfPoints)
  {
    Map<String, Integer> newMap = new HashMap<>();

    String baseUri = "http://test.linkedin.com:";
    for (int i=0; i < numOfPoints; ++i)
    {
      newMap.put(baseUri + 1000 + i, 100);
    }
    return newMap;
  }

  @Test(groups = { "small", "back-end" })
  public void testPointsCleanUp()
          throws URISyntaxException
  {
    Map<String, Integer> pointsMp = buildPointsMap(6);

    PointBasedConsistentHashRingFactory<String> ringFactory = new PointBasedConsistentHashRingFactory<>(new DegraderLoadBalancerStrategyConfig(1L));
    Ring<String>  ring = ringFactory.createRing(pointsMp);
    assertNotNull(ring.get(1000));

    pointsMp.remove("http://test.linkedin.com:10001");
    pointsMp.remove("http://test.linkedin.com:10003");

    ring = ringFactory.createRing(pointsMp);
    assertNotNull(ring.get(1000));
    // factory should keep all the points -- the default MinUnusedEntry = 3
    Map<String, List<Point<String>>> pointsMap = ringFactory.getPointsMap();
    assertEquals(pointsMap.size(), 6);

    pointsMp.remove("http://test.linkedin.com:10004");
    pointsMp.remove("http://test.linkedin.com:10005");
    ring = ringFactory.createRing(pointsMp);
    assertNotNull(ring.get(1000));

    // factory should clean up and build new points because unused entry == 3
    pointsMap = ringFactory.getPointsMap();
    assertEquals(pointsMap.size(), 2);
  }

  @Test(groups = { "small", "back-end" })
  public void testPointsCleanUpLarge()
      throws URISyntaxException
  {
    Map<String, Integer> pointsMp = buildPointsMap(19);

    PointBasedConsistentHashRingFactory<String> ringFactory = new PointBasedConsistentHashRingFactory<>(new DegraderLoadBalancerStrategyConfig(1L));
    Ring<String>  ring = ringFactory.createRing(pointsMp);
    assertNotNull(ring.get(1000));

    pointsMp.remove("http://test.linkedin.com:10001");
    pointsMp.remove("http://test.linkedin.com:10003");
    pointsMp.remove("http://test.linkedin.com:10006");

    ring = ringFactory.createRing(pointsMp);
    assertNotNull(ring.get(1000));
    // factory should keep all the points
    Map<String, List<Point<String>>> pointsMap = ringFactory.getPointsMap();
    assertEquals(pointsMap.size(), 19);

    pointsMp.remove("http://test.linkedin.com:10009");
    ring = ringFactory.createRing(pointsMp);
    assertNotNull(ring.get(1000));

    // factory should clean up and build new points
    pointsMap = ringFactory.getPointsMap();
    assertEquals(pointsMap.size(), 15);
  }

  @Test(groups = { "small", "back-end" })
  public void testRandomChangePoints()
      throws URISyntaxException
  {
    int pointNum = 5;
    int loopNum = 100;
    Map<String, Integer> pointsMp = buildPointsMap(pointNum);
    Map<String, Integer> maxPoints = new HashMap<>(pointNum);
    Random random = new Random();

    for (String uri : pointsMp.keySet()) {
      maxPoints.put(uri, 100);
    }

    PointBasedConsistentHashRingFactory<String> ringFactory = new PointBasedConsistentHashRingFactory<>(new DegraderLoadBalancerStrategyConfig(1L));
    Ring<String>  ring = ringFactory.createRing(pointsMp);
    assertNotNull(ring.get(1000));

    for (int i = 0; i < loopNum; ++i) {
      // new point list
      for (String uri : pointsMp.keySet()) {
        int newPoints = random.nextInt(200);
        if (newPoints == 0) {
          continue;
        }
        pointsMp.put(uri, newPoints);
        if (newPoints > maxPoints.get(uri)) {
          maxPoints.put(uri, ((newPoints + 3) / 4) * 4);
        }
      }
      ring = ringFactory.createRing(pointsMp);
      assertNotNull(ring.get(1000));
      Map<String, List<Point<String>>> pointList = ringFactory.getPointsMap();
      for (String uri : pointsMp.keySet()) {
        assertEquals ((int)maxPoints.get(uri), pointList.get(uri).size());
      }
    }
  }
}

