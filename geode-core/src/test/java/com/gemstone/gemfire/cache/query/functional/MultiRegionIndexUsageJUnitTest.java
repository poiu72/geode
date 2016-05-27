/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * MultiRegionIndexUsageJUnitTest.java
 *
 * Created on October 3, 2005, 1:20 PM
 */
package com.gemstone.gemfire.cache.query.functional;

import com.gemstone.gemfire.cache.*;
import com.gemstone.gemfire.cache.query.*;
import com.gemstone.gemfire.cache.query.data.*;
import com.gemstone.gemfire.cache.query.internal.QueryObserverAdapter;
import com.gemstone.gemfire.cache.query.internal.QueryObserverHolder;
import com.gemstone.gemfire.cache.query.types.StructType;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.test.junit.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.*;

import static com.gemstone.gemfire.distributed.SystemConfigurationProperties.MCAST_PORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

//TODO:TEST clean this up and add assertions
/**
 */
@Category(IntegrationTest.class)
public class MultiRegionIndexUsageJUnitTest {

  private static final String indexName = "queryTest";

  private Cache cache;
  private Region region1;
  private Region region2;
  private Region region3;
  private Index index;
  private DistributedSystem ds;
  private QueryService qs;

  private StructType resType1 = null;
  private StructType resType2 = null;

  private int resSize1 = 0;
  private int resSize2 = 0;

  private Iterator itert1 = null;
  private Iterator itert2 = null;

  private Set set1 = null;
  private Set set2 = null;

  // ////////////// queries ////////////////
  private static final String queries[] = {
      // Query 1
      "SELECT DISTINCT * FROM /Countries c, c.states s, s.districts d,"
          + " d.villages v, d.cities ct WHERE v.name = 'MAHARASHTRA_VILLAGE1'",
      // Query 2
      "SELECT DISTINCT * FROM /Countries c, c.states s, s.districts d, d.villages v,"
          + " d.cities ct WHERE v.name='MAHARASHTRA_VILLAGE1' AND ct.name = 'PUNE'",
      // Query 3
      "SELECT DISTINCT * FROM /Countries c, c.states s, s.districts d, d.villages v, "
          + "d.cities ct WHERE ct.name = 'PUNE' AND s.name = 'MAHARASHTRA'",
      // Query 4a & 4b
      "SELECT DISTINCT * FROM /Countries c WHERE c.name = 'INDIA'",
      "SELECT DISTINCT * FROM /Countries c, c.states s, s.districts d, d.cities ct, d.villages v WHERE c.name = 'INDIA'",
      // Query 5
      "SELECT DISTINCT * FROM /Countries c, c.states s, s.districts d WHERE d.name = 'PUNEDIST' AND s.name = 'GUJARAT'",
      // Query 6
      "SELECT DISTINCT * FROM /Countries c, c.states s, s.districts d, d.cities ct WHERE ct.name = 'MUMBAI'",
      // Query 7
      "SELECT DISTINCT c.name, s.name, d.name, ct.name FROM /Countries c, c.states s, s.districts d, d.cities ct WHERE ct.name = 'MUMBAI' OR ct.name = 'CHENNAI'",
      // Query 8
      "SELECT DISTINCT c.name, s.name FROM /Countries c, c.states s, s.districts d, d.cities ct WHERE ct.name = 'MUMBAI' OR s.name = 'GUJARAT'",
      // Query 9a & 9b
      "SELECT DISTINCT c.name, s.name, ct.name FROM /Countries c, c.states s, (SELECT DISTINCT * FROM "
          + "/Countries c, c.states s, s.districts d, d.cities ct WHERE s.name = 'PUNJAB') itr1, "
          + "s.districts d, d.cities ct WHERE ct.name = 'CHANDIGARH'",
      "SELECT DISTINCT c.name, s.name, ct.name FROM /Countries c, c.states s, s.districts d,"
          + " d.cities ct WHERE ct.name = (SELECT DISTINCT ct.name FROM /Countries c, c.states s, "
          + "s.districts d, d.cities ct WHERE s.name = 'MAHARASHTRA' AND ct.name = 'PUNE')",
      // Query 10
      "SELECT DISTINCT c.name, s.name, ct.name FROM /Countries c, c.states s, s.districts d, "
          + "d.cities ct, d.getVillages() v WHERE v.getName() = 'PUNJAB_VILLAGE1'",
      // Query 11
      "SELECT DISTINCT s.name, s.getDistricts(), ct.getName() FROM /Countries c, c.getStates() s, "
          + "s.getDistricts() d, d.getCities() ct WHERE ct.getName() = 'PUNE' OR ct.name = 'CHANDIGARH' "
          + "OR s.getName() = 'GUJARAT'",
      // Query 12
      "SELECT DISTINCT d.getName(), d.getCities(), d.getVillages() FROM /Countries c, "
          + "c.states s, s.districts d WHERE d.name = 'MUMBAIDIST'",

  };

  @Before
  public void setUp() throws Exception {
    Properties props = new Properties();
    props.setProperty(MCAST_PORT, "0");
    ds = DistributedSystem.connect(props);
    cache = CacheFactory.create(ds);
    /* create region containing Country objects */
    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.DISTRIBUTED_ACK);
    factory.setValueConstraint(Country.class);

    region1 = cache.createRegion("Countries1", factory.create());
    region2 = cache.createRegion("Countries2", factory.create());
    region3 = cache.createRegion("Countries3", factory.create());

    populateData();
  }// end of setUp

  @After
  public void tearDown() throws Exception {
    if (ds != null) {
      ds.disconnect();
    }
  }// end of tearDown

  @Test
  public void testChangedFormClauseOrder1() throws Exception {
    CacheUtils
        .log("------------- testChangedFormClauseOrder1 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR003
    String sqlStr = "SELECT DISTINCT * FROM "
        + "/Countries1 c1, c1.states s1, s1.districts d1, d1.villages v1, d1.cities ct1, "
        + "/Countries2 c2, c2.states s2, s2.districts d2, d2.villages v2, d2.cities ct2 "
        + "WHERE v1.name = 'MAHARASHTRA_VILLAGE1' AND ct2.name = 'MUMBAI' ";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      CacheUtils.log("0000000000000000000000000000");
      q = qs1.newQuery(sqlStr);
      CacheUtils.log("aaaaaaaaaaaaaaaaaa");
      rs[0][0] = (SelectResults) q.execute();
      CacheUtils.log("11111111111111111111111111111");
      createIndex();
      CacheUtils.log("22222222222222222222222");
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      CacheUtils.log("333333333333333333");
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();
      CacheUtils.log("44444444444444444444444444444");
      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }

      Iterator itr = observer.indexesUsed.iterator();
      String temp;
      while (itr.hasNext()) {
        temp = (String) itr.next();
        if (temp.equals("villageName1")) {
          break;
        } else if (temp.equals("cityName2")) {
          break;
        } else {
          fail("indices used do not match with those which are expected to be used"
              + "<villageName1> and <cityName2> were expected but found "
              + itr.next());
        }
      }

      // while(itr.hasNext()){
      // assertIndexDetailsEquals("villageName1", itr.next().toString());
      // assertIndexDetailsEquals("cityName2", itr.next().toString());
      // }

      CacheUtils.log("5555555555555555555555555555");
      areResultsMatching(rs, new String[] { sqlStr });

      CacheUtils
          .log("------------- testChangedFormClauseOrder1 end------------- ");

    } catch (Exception e) {
      e.printStackTrace();
      fail(q.getQueryString());
    }

  }// end of test

  //@Test
  public void _testChangedFormClauseOrder2() throws Exception {
    CacheUtils
        .log("------------- testChangedFormClauseOrder2 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR008
    String sqlStr = "SELECT DISTINCT * FROM /Countries1 c1, c1.states s1, s1.districts d1, d1.villages v1, d1.cities ct1, "
        + "/Countries2 c2, c2.states s2, s2.districts d2, d2.villages v2, d2.cities ct2, "
        + "/Countries3 c3, c3.states s3, s3.districts d3, d3.villages v3, d3.cities ct3 "
        + " WHERE v1.name='MAHARASHTRA_VILLAGE1' AND ct3.name = 'PUNE'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }

      Iterator itr = observer.indexesUsed.iterator();
      String temp;

      while (itr.hasNext()) {
        temp = (String) itr.next();
        if (temp.equals("villageName1")) {
          break;
        } else if (temp.equals("cityName3")) {
          break;
        } else {
          fail("indices used do not match with those which are expected to be used"
              + "<villageName1> and <cityName3> were expected but found "
              + itr.next());
        }
      }

      // assertIndexDetailsEquals("villageName1", itr.next().toString());
      // assertIndexDetailsEquals("cityName3", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });

      CacheUtils
          .log("------------- testChangedFormClauseOrder2 end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testSelectBestIndex1() throws Exception {
    CacheUtils.log("------------- testSelectBestIndex1 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR010
    String sqlStr = "SELECT DISTINCT * FROM /Countries1 c1, /Countries2 c2 WHERE c1.name = 'INDIA' AND c2.name = 'ISRAEL'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      assertEquals("countryNameB", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });

      CacheUtils.log("------------- testSelectBestIndex1 end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testSelectBestIndex2() throws Exception {
    CacheUtils.log("------------- testSelectBestIndex2 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR011
    String sqlStr = "SELECT DISTINCT * FROM /Countries1 c1, c1.states s1, s1.districts d1, d1.cities ct1, d1.villages v1, "
        + "/Countries2 c2, c2.states s2, s2.districts d2, d2.cities ct2, d2.villages v2 "
        + "WHERE c1.name = 'INDIA' AND c2.name = 'ISRAEL'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      assertEquals("countryNameA", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });
      CacheUtils.log("------------- testSelectBestIndex2 end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      // fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testProjectionAttr1() throws Exception {
    CacheUtils.log("------------- testProjectionAttr1 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR012
    String sqlStr = "SELECT DISTINCT * FROM /Countries1 c1, c1.states s1, s1.districts d1, "
        + "/Countries2 c2, c2.states s2, s2.districts d2, "
        + "/Countries3 c3, c3.states s3, s3.districts d3 "
        + "WHERE d3.name = 'PUNEDIST' AND s2.name = 'GUJARAT'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      String temp;

      while (itr.hasNext()) {
        temp = (String) itr.next();
        if (temp.equals("districtName3")) {
          break;
        } else if (temp.equals("stateName2")) {
          break;
        } else {
          fail("indices used do not match with those which are expected to be used"
              + "<districtName3> and <stateName2> were expected but found "
              + itr.next());
        }
      }

      // assertIndexDetailsEquals("districtName3", itr.next().toString());
      // assertIndexDetailsEquals("stateName2", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });

      CacheUtils.log("------------- testProjectionAttr1 end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      // fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testCutDown1() throws Exception {
    CacheUtils.log("------------- testCutDown1 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR014
    String sqlStr = "SELECT DISTINCT c1.name, s1.name, d2.name, ct2.name "
        + "FROM /Countries1 c1, c1.states s1, s1.districts d1, d1.cities ct1,"
        + "/Countries2 c2, c2.states s2, s2.districts d2, d2.cities ct2"
        + " WHERE ct1.name = 'MUMBAI' OR ct2.name = 'CHENNAI'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      String temp;

      while (itr.hasNext()) {
        temp = (String) itr.next();
        if (temp.equals("cityName1")) {
          break;
        } else if (temp.equals("cityName2")) {
          break;
        } else {
          fail("indices used do not match with those which are expected to be used"
              + "<cityName1> and <cityName2> were expected but found "
              + itr.next());
        }
      }

      // assertIndexDetailsEquals("cityName1", itr.next().toString());
      // assertIndexDetailsEquals("cityName2", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });
      CacheUtils.log("------------- testCutDown1 end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testSelectAsFromClause() throws Exception {
    CacheUtils.log("------------- testSelectAsFromClause start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR016
    String sqlStr = "SELECT DISTINCT c1.name, s1.name, ct1.name FROM /Countries1 c1, c1.states s1, (SELECT DISTINCT * FROM "
        + "/Countries2 c2, c2.states s2, s2.districts d2, d2.cities ct2 WHERE s2.name = 'PUNJAB') itr1, "
        + "s1.districts d1, d1.cities ct1 WHERE ct1.name = 'CHANDIGARH'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      String temp;

      while (itr.hasNext()) {
        temp = (String) itr.next();
        if (temp.equals("stateName2")) {
          break;
        } else if (temp.equals("cityName1")) {
          break;
        } else {
          fail("indices used do not match with those which are expected to be used"
              + "<stateName2> and <cityName1> were expected but found "
              + itr.next());
        }
      }

      // assertIndexDetailsEquals("stateName2", itr.next().toString());
      // assertIndexDetailsEquals("cityName1", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });
      CacheUtils.log("------------- testSelectAsFromClause end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testSelectAsWhereClause() throws Exception {
    CacheUtils.log("------------- testSelectAsWhereClause start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR017
    String sqlStr = "SELECT DISTINCT c1.name, s1.name, ct1.name FROM /Countries1 c1, c1.states s1, s1.districts d1,"
        + " d1.cities ct1 WHERE ct1.name = element (SELECT DISTINCT ct3.name FROM /Countries3 c3, c3.states s3, "
        + "s3.districts d3, d3.cities ct3 WHERE s3.name = 'MAHARASHTRA' AND ct3.name = 'PUNE')";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      String temp;

      while (itr.hasNext()) {
        temp = (String) itr.next();
        if (temp.equals("cityName3")) {
          break;
        } else if (temp.equals("cityName1")) {
          break;
        } else {
          fail("indices used do not match with those which are expected to be used"
              + "<cityName3> and <cityName1> were expected but found "
              + itr.next());
        }
      }

      // assertIndexDetailsEquals("cityName3", itr.next().toString());
      // assertIndexDetailsEquals("cityName1", itr.next().toString());
      // assertIndexDetailsEquals("stateName1", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });
      CacheUtils.log("------------- testSelectAsWhereClause end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      // fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testFunctionUse1() throws Exception {
    CacheUtils.log("------------- testFunctionUse1 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR018
    String sqlStr = "SELECT DISTINCT c1.name, s1.name, ct1.name FROM /Countries1 c1, c1.states s1, s1.districts d1, "
        + "d1.cities ct1, d1.getVillages() v1 WHERE v1.getName() = 'PUNJAB_VILLAGE1'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      assertEquals("villageName1", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });
      CacheUtils.log("------------- testFunctionUse1 end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      // fail(q.getQueryString());
    }

  }// end of test

  //@Test
  public void _testFunctionUse2() throws Exception {
    CacheUtils.log("------------- testFunctionUse2 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR019
    String sqlStr = "SELECT DISTINCT s.name, s.getDistricts(), ct.getName() FROM /Countries1 c, c.states s, "
        + "s.getDistricts() d, d.getCities() ct WHERE ct.name = 'PUNE' OR ct.name = 'CHANDIGARH' "
        + "OR s.name = 'GUJARAT'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      String temp;

      while (itr.hasNext()) {
        temp = (String) itr.next();
        if (temp.equals("cityName1")) {
          break;
        } else if (temp.equals("stateName1")) {
          break;
        } else {
          fail("indices used do not match with those which are expected to be used"
              + "<villageName1> and <cityName2> were expected but found "
              + itr.next());
        }
      }

      // assertIndexDetailsEquals("cityName", itr.next().toString());
      // assertIndexDetailsEquals("stateName", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });
      CacheUtils.log("------------- testFunctionUse2 end------------- ");

    } catch (Exception e) {
      e.printStackTrace();
      fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testFunctionUse3() throws Exception {
    CacheUtils.log("------------- testFunctionUse3 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR020
    String sqlStr = "SELECT DISTINCT d.getName(), d.getCities(), d.getVillages() FROM /Countries3 c, "
        + "c.states s, s.districts d WHERE d.name = 'MUMBAIDIST'";

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      if (!observer.isIndexesUsed) {
        fail("------------ INDEX IS NOT USED FOR THE QUERY:: "
            + q.getQueryString());
      }
      Iterator itr = observer.indexesUsed.iterator();
      assertEquals("districtName3", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });
      CacheUtils.log("------------- testFunctionUse3 end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      fail(q.getQueryString());
    }

  }// end of test

  @Test
  public void testFunctionUse4() throws Exception {
    CacheUtils.log("------------- testFunctionUse4 start------------- ");
    SelectResults rs[][] = new SelectResults[1][2];
    // Test Case No. IUMR020
    String sqlStr = "SELECT DISTINCT * FROM /Countries1 c1, /Countries3 c3, c1.states s1, c3.states s3, "
        + "s1.districts d1, s3.getDistrictsWithSameName(d1) d3 WHERE c1.name = 'INDIA' OR c3.name = 'ISRAEL' "
        + "OR d3.name = 'MUMBAIDIST' OR d3.name = 'PUNEDIST'";
    // s3.getDistrictsWithSameName(d1) d3
    // s3.districts d3

    // query execution without Index.
    Query q = null;
    try {
      QueryService qs1 = cache.getQueryService();
      q = qs1.newQuery(sqlStr);
      rs[0][0] = (SelectResults) q.execute();

      createIndex();
      QueryService qs2 = cache.getQueryService();// ????
      q = qs2.newQuery(sqlStr);
      QueryObserverImpl observer = new QueryObserverImpl();
      QueryObserverHolder.setInstance(observer);
      rs[0][1] = (SelectResults) q.execute();

      // assertIndexDetailsEquals("districtName3", itr.next().toString());

      areResultsMatching(rs, new String[] { sqlStr });
      CacheUtils.log("------------- testFunctionUse4 end------------- ");
    } catch (Exception e) {
      e.printStackTrace();
      fail(q.getQueryString());
    }

  }// end of test

  private static void areResultsMatching(SelectResults rs[][], String[] queries) {
    StructSetOrResultsSet ssORrs = new StructSetOrResultsSet();
    ssORrs.CompareQueryResultsWithoutAndWithIndexes(rs, 1, queries);

  }// end of areResultsMatching

  // ////////////// function to pupualte data in single region //////////////
  private void populateData() throws Exception {
    /* create villages */
    Village v1 = new Village("MAHARASHTRA_VILLAGE1", 123456);
    Village v2 = new Village("PUNJAB_VILLAGE1", 123789);
    Village v3 = new Village("KERALA_VILLAGE1", 456789);
    // Village v4 = new Village("GUJARAT_VILLAGE1", 123478);
    // Village v5 = new Village("AASAM_VILLAGE1", 783456);
    Set villages = new HashSet();
    villages.add(v1);
    villages.add(v2);
    villages.add(v3); // villages.add(v4); villages.add(v5);

    /* create cities */
    City ct1 = new City("MUMBAI", 123456);
    City ct2 = new City("PUNE", 123789);
    City ct3 = new City("GANDHINAGAR", 456789);
    City ct4 = new City("CHANDIGARH", 123478);
    Set cities = new HashSet();
    cities.add(ct1);
    cities.add(ct2);
    cities.add(ct3);
    cities.add(ct4);

    /* create districts */
    District d1 = new District("MUMBAIDIST", cities, villages);
    District d2 = new District("PUNEDIST", cities, villages);
    // District d3 = new District("GANDHINAGARDIST", cities, villages);
    // District d4 = new District("CHANDIGARHDIST", cities, villages);
    Set districts = new HashSet();
    districts.add(d1);
    districts.add(d2); // districts.add(d3); districts.add(d4);

    /* create states */
    State s1 = new State("MAHARASHTRA", "west", districts);
    State s2 = new State("PUNJAB", "north", districts);
    State s3 = new State("GUJARAT", "west", districts);
    // State s4 = new State("KERALA", "south", districts);
    // State s5 = new State("AASAM", "east", districts);
    Set states = new HashSet();
    states.add(s1);
    states.add(s2);
    states.add(s3); // states.add(s4); states.add(s5);

    /* create countries */
    Country c1 = new Country("INDIA", "asia", states);
    Country c2 = new Country("ISRAEL", "africa", states);
    Country c3 = new Country("CANADA", "america", states);
    Country c4 = new Country("AUSTRALIA", "australia", states);
    Country c5 = new Country("MALAYSIA", "asia", states);

    for (int i = 0; i < 5; i++) {
      int temp;
      temp = i % 5;
      switch (temp) {
      case 1:
        region1.put(new Integer(i), c1);
        region2.put(new Integer(i), c1);
        region3.put(new Integer(i), c1);
        break;

      case 2:
        region1.put(new Integer(i), c2);
        region2.put(new Integer(i), c2);
        region3.put(new Integer(i), c2);
        break;

      case 3:
        region1.put(new Integer(i), c3);
        region2.put(new Integer(i), c3);
        region3.put(new Integer(i), c3);
        break;

      case 4:
        region1.put(new Integer(i), c4);
        region2.put(new Integer(i), c4);
        region3.put(new Integer(i), c4);
        break;

      case 0:
        region1.put(new Integer(i), c5);
        region2.put(new Integer(i), c5);
        region3.put(new Integer(i), c5);
        break;

      default:
        CacheUtils.log("Nothing to add in region for: " + temp);
        break;

      }// end of switch
    }// end of for

  }// end of populateData

  // //////////////// function to create index ///////////////////
  private void createIndex() throws Exception {
    QueryService qs;
    qs = cache.getQueryService();
    /* Indices on region1 */
    qs.createIndex("villageName1", IndexType.FUNCTIONAL, "v.name",
        "/Countries1 c, c.states s, s.districts d, d.cities ct, d.villages v");
    qs.createIndex("cityName1", IndexType.FUNCTIONAL, "ct.name",
        "/Countries1 c, c.states s, s.districts d, d.cities ct, d.villages v");
    qs.createIndex("countryNameA", IndexType.FUNCTIONAL, "c.name",
        "/Countries1 c, c.states s, s.districts d, d.cities ct, d.villages v");
    qs.createIndex("countryNameB", IndexType.FUNCTIONAL, "c.name",
        "/Countries1 c");

    /* Indices on region2 */
    qs.createIndex("stateName2", IndexType.FUNCTIONAL, "s.name",
        "/Countries2 c, c.states s, s.districts d, d.cities ct, d.villages v");
    qs.createIndex("cityName2", IndexType.FUNCTIONAL, "ct.name",
        "/Countries2 c, c.states s, s.districts d, d.cities ct, d.villages v");

    /* Indices on region3 */
    qs.createIndex("districtName3", IndexType.FUNCTIONAL, "d.name",
        "/Countries3 c, c.states s, s.districts d, d.cities ct, d.villages v");
    qs.createIndex("villageName3", IndexType.FUNCTIONAL, "v.name",
        "/Countries3 c, c.states s, s.districts d, d.cities ct, d.villages v");
    qs.createIndex("cityName3", IndexType.FUNCTIONAL, "ct.name",
        "/Countries3 c, c.states s, s.districts d, d.cities ct, d.villages v");

  }// end of createIndex

  private static class QueryObserverImpl extends QueryObserverAdapter {
    boolean isIndexesUsed = false;
    ArrayList indexesUsed = new ArrayList();
    String indexName;

    public void beforeIndexLookup(Index index, int oper, Object key) {
      indexName = index.getName();
      indexesUsed.add(index.getName());
    }

    public void afterIndexLookup(Collection results) {
      if (results != null) {
        isIndexesUsed = true;
      }
    }
  }// end of QueryObserverImpl

}// end of the class
