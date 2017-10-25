/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.das.jobmanager.core;

import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.das.jobmanager.core.appCreator.DeployableSiddhiQueryGroup;
import org.wso2.carbon.das.jobmanager.core.appCreator.SPSiddhiAppCreator;
import org.wso2.carbon.das.jobmanager.core.bean.DeploymentConfig;
import org.wso2.carbon.das.jobmanager.core.internal.ServiceDataHolder;
import org.wso2.carbon.das.jobmanager.core.topology.SiddhiTopology;
import org.wso2.carbon.das.jobmanager.core.topology.SiddhiTopologyCreatorImpl;
import org.wso2.carbon.das.jobmanager.core.util.TransportStrategy;
import org.wso2.siddhi.core.SiddhiManager;

import java.util.List;

public class SiddhiTopologyCreatorTestCase {
    private static final Logger log = Logger.getLogger(SiddhiTopologyCreatorTestCase.class);

    @BeforeMethod
    public void setUp() {
        DeploymentConfig deploymentConfig = new DeploymentConfig();
        deploymentConfig.setBootstrapURLs("localhost:9092");
        ServiceDataHolder.setDeploymentConfig(deploymentConfig);
    }

    @Test
    public void testSiddhiTopologyCreator() {

        String siddhiApp = "@App:name('TestPlan') \n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/stockQuote', @map(type='xml')) "
                + "Define stream stockStream(symbol string, price float, quantity int, tier string);\n"
                + "@Sink(type='email', @map(type='json'), username='wso2', address='test@wso2.com',password='****',"
                + "host='smtp.gmail.com',subject='Event from SP',to='towso2@gmail.com')\n"
                + "Define stream takingOverStream(symbol string, overtakingSymbol string, avgPrice double);\n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/trigger', @map(type='xml'))\n"
                + "Define stream companyTriggerStream(symbol string);\n"
                + "@Store(type='rdbms', jdbc.url='jdbc:mysql://localhost:3306/cepDB',jdbc.driver.name='', "
                + "username='root', password='****',field.length='symbol:254')\n"
                + "Define table filteredTable (symbol string, price float, quantity int, tier string);\n"
                + "@Store(type='rdbms', jdbc.url='jdbc:mysql://localhost:3306/spDB',jdbc.driver.name='', "
                + "username='root', password='****',field.length='symbol:254')\n"
                + "Define table takingOverTable(symbol string, overtakingSymbol string, avgPrice double);\n"
                + "@info(name = 'query1')@dist(parallel='2', execGroup='001')\n"
                + "From stockStream[price > 100]\n"
                + "Select *\n"
                + "Insert into filteredStockStream;\n"
                + "@info(name='query3')@dist(parallel='2')\n"
                + "Partition with (symbol of filteredStockStream)\n"
                + "begin\n"
                + "From filteredStockStream#window.time(5 min)\n"
                + "Select symbol, avg(price) as avgPrice, quantity\n"
                + "Insert into #avgPriceStream;\n"
                + "From #avgPriceStream#window.time(5 min) as a right outer join companyTriggerStream#window.length"
                + "(1)\n"
                + "On (companyTriggerStream.symbol == a.symbol)\n"
                + "Select a.symbol, a.avgPrice, a.quantity\n"
                + "Insert into triggeredAvgStream;\n"
                + "End;\n"
                + "@info(name='query4')@dist(parallel='1', execGroup='003')\n"
                + "From  a1=triggeredAvgStream,  a2=triggeredAvgStream[a1.avgPrice<a2.avgPrice]\n"
                + "Select a1.symbol, a2.symbol as overtakingSymbol, a2.avgPrice \n"
                + "Insert into takingOverStream;\n"
                + "@info(name='query5')@dist(parallel='4', execGroup='004')\n"
                + "From filteredStockStream\n"
                + "Select *\n"
                + "Insert into filteredTable;\n"
                + "@info(name='query6')@dist(parallel='4', execGroup='004')\n"
                + "From takingOverStream\n"
                + "Select *\n"
                + "Insert into takingOverTable;\n"
                + "@info(name='query7')@dist(parallel='3', execGroup='005')\n"
                + "Partition with (tier of filteredStockStream)\n"
                + "begin\n"
                + "From filteredStockStream#log(tier)\n"
                + "Select *\n"
                + "Insert into dumbstream;\n"
                + "End;\n";

        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);
        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }
    }

    /**
     * Filter query can reside in an execGroup with parallel > 1 and the corresponding stream will have
     * {@link TransportStrategy#ROUND_ROBIN}
     */
    @Test
    public void testFilterQuery() {
        String siddhiApp = "@App:name('TestPlan') "
                + "@Source(type = 'tcp', context='TempStream',"
                + "@map(type='binary')) "
                + "define stream TempStream(deviceID long, roomNo int, temp double); "
                + "@info(name = 'query1') @dist(parallel ='1', execGroup='group1')\n "
                + "from TempStream\n"
                + "select *\n"
                + "insert into TempInternalStream;"
                + "@info(name='query2')@dist(parallel='3',execGroup='005')\n"
                + "from TempInternalStream[(roomNo >= 100 and roomNo < 210) and temp > 40]\n"
                + "select roomNo, temp\n"
                + "insert into HighTempStream;";

        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);
        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("TempInternalStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ROUND_ROBIN);
    }

    /**
     *Window can can reside in an execGroup with parallel > 1 if the used stream is a (Partitioned/Inner) Stream
     */
    @Test
    public void testPartitionWithWindow() {
        String siddhiApp = "@App:name('TestPlan') "
                + "@Source(type = 'tcp', context='TempStream',"
                + "@map(type='binary')) "
                + "define stream TempStream(deviceID long, roomNo int, temp double); "
                + "@info(name = 'query1') @dist(parallel ='1', execGroup='group1')\n "
                + "from TempStream\n"
                + "select *\n"
                + "insert into TempInternalStream;"
                + "@info(name = 'query2') @dist(parallel ='2', execGroup='group2')\n "
                + "partition with ( deviceID of TempInternalStream )\n"
                + "begin\n"
                + "    from TempInternalStream#window.length(10)\n"
                + "    select roomNo, deviceID, max(temp) as maxTemp\n"
                + "    insert into DeviceTempStream\n"
                + "end;";

        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);

        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("TempInternalStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.FIELD_GROUPING);
    }


    /**
     *Sequence can can reside in an execGroup with parallel > 1 if the used stream is a (Partitioned/Inner) Stream
     */
    @Test
    public void testPartitionWithSequence() {
        String siddhiApp = "@App:name('TestPlan') "
                + "@Source(type = 'tcp', context='TempStream',"
                + "@map(type='binary')) "
                + "define stream TempStream(deviceID long, roomNo int, temp double); "
                + "@info(name = 'query1') @dist(parallel ='1', execGroup='group1')\n "
                + "from TempStream\n"
                + "select *\n"
                + "insert into TempInternalStream;"
                + "@info(name ='query2') @dist(execGroup='group2', parallel='2')\n"
                + "partition with ( deviceID of TempInternalStream )\n"
                + "begin\n"
                + "from every e1=TempInternalStream, e2=TempInternalStream[e1.temp <= temp]+, "
                + "e3=TempInternalStream[e2[last].temp > temp]\n"
                + "select e1.temp as initialTemp, e2[last].temp as peakTemp\n"
                + "insert into PeekTempStream;"
                + "end;";

        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);

        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }
    }

    /**
     *Pattern can reside in an execGroup with parallel > 1 if the used stream is a (Partitioned/Inner) Stream
     */
    @Test
    public void testPartitionWithPattern() {
        String siddhiApp = "@App:name('TestPlan') "
                + "@Source(type = 'tcp', context='TempStream',"
                + "@map(type='binary')) "
                + "define stream TempStream(deviceID long, roomNo int, temp double); "
                + "define stream RegulatorStream (deviceID long, roomNo int, tempSet double, isOn bool);\n"
                + "@info(name = 'query1') @dist(execGroup='group1', parallel='1')\n "
                + "from TempStream\n"
                + "select *\n"
                + "insert into TempInternalStream;"
                + "@info(name ='query2') @dist(execGroup='group2', parallel='2')\n"
                + "partition with ( deviceID of TempInternalStream )\n"
                + "begin\n"
                + "from every( e1=RegulatorStream) -> e2=TempInternalStream[e1.roomNo==roomNo]<1:> -> "
                + "e3=RegulatorStream[e1.roomNo==roomNo]\n"
                + "select e1.roomNo, e2[0].temp - e2[last].temp as tempDiff\n"
                + "insert into TempDiffStream;"
                + "end;";
        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);

        Assert.assertTrue(topology.getQueryGroupList().get(0).getInputStreams().containsKey("TempStream"));
        Assert.assertTrue(topology.getQueryGroupList().get(1).getInputStreams().containsKey("RegulatorStream"));
        Assert.assertTrue(topology.getQueryGroupList().get(1).getOutputStreams().containsKey("TempDiffStream"));

        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }
    }


    /**
     * A join can exist with Parallel >1 if the joined stream consists at least one Partitioned Stream
     * The partitioned streams in the join will subscribe with {@link TransportStrategy#FIELD_GROUPING}
     * The unpartitioned streams in the join will subscribe with {@link TransportStrategy#ALL}
     */
    @Test
    public void testJoinWithPartition() {
        String siddhiApp = "@App:name('TestPlan') "
                + "@Source(type = 'tcp', context='TempStream',"
                + "@map(type='binary')) "
                + "define stream TempStream(deviceID long, roomNo int, temp double); "
                + "define stream RegulatorStream(deviceID long, roomNo int, isOn bool);\n"
                + "@info(name = 'query1') @dist(execGroup='group1', parallel='1')\n "
                + "from TempStream\n"
                + "select *\n"
                + "insert into TempInternalStream;"
                + "@info(name ='query2') @dist(execGroup='group2', parallel='5')\n"
                + "partition with ( deviceID of TempInternalStream )\n"
                + "begin\n"
                + "from TempInternalStream[temp > 30.0]#window.time(1 min) as T\n"
                + "  join RegulatorStream[isOn == false]#window.length(1) as R\n"
                + "  on T.roomNo == R.roomNo\n"
                + "select T.roomNo, R.deviceID, 'start' as action\n"
                + "insert into RegulatorActionStream;"
                + "end;";

        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);

        Assert.assertEquals(topology.getQueryGroupList().get(0).getInputStreams().get("TempStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("TempInternalStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.FIELD_GROUPING);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("RegulatorStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("TempInternalStream")
                                    .getSubscriptionStrategy().getOfferedParallelism(), 5);

        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }
    }


    /**
     * A partitioned stream used outside the Partition but inside the same execGroup will have the Subscription
     * strategy of {@link TransportStrategy#FIELD_GROUPING}
     */
    @Test
    public void testPartitionStrategy() {
        String siddhiApp = "@App:name('TestPlan') "
                + "@Source(type = 'tcp', context='TempStream',"
                + "@map(type='binary')) "
                + "define stream TempStream(deviceID long, roomNo int, temp double); "
                + "define stream RegulatorStream(deviceID long, roomNo int, isOn bool);\n"
                + "@info(name = 'query1') @dist(execGroup='group1', parallel='1')\n "
                + "from TempStream\n"
                + "select *\n"
                + "insert into TempInternalStream;"
                + "@info(name='query3') @dist(execGroup='group2' ,parallel='5')\n"
                + "from TempInternalStream[(roomNo >= 100 and roomNo < 210) and temp > 40]\n"
                + "select roomNo, temp\n"
                + "insert into HighTempStream;"
                + "@info(name ='query2') @dist(execGroup='group2', parallel='5')\n"
                + "partition with ( deviceID of TempInternalStream )\n"
                + "begin\n"
                + "from TempInternalStream[temp > 30.0]#window.time(1 min) as T\n"
                + "  join RegulatorStream[isOn == false]#window.length(1) as R\n"
                + "  on T.roomNo == R.roomNo\n"
                + "select T.roomNo, R.deviceID, 'start' as action\n"
                + "insert into RegulatorActionStream;"
                + "end;";


        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);

        Assert.assertEquals(topology.getQueryGroupList().get(0).getInputStreams().get("TempStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("TempInternalStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.FIELD_GROUPING);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("RegulatorStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("TempInternalStream")
                                    .getSubscriptionStrategy().getOfferedParallelism(), 5);

        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }
    }


    /**
     * A stream used by multiple partitions residing in different executionGroups and under same Partition key gets
     * assigned with the maximum parallel value among execGroups.
     */
    @Test
    public void testPartitionMultiSubscription() {

        String siddhiApp = "@App:name('TestPlan') \n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/stockQuote', @map(type='xml'))\n"
                + "Define stream stockStream(symbol string, price float, quantity int, tier string);\n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/trigger', @map(type='xml'))\n"
                + "Define stream companyTriggerStream(symbol string);\n"
                + "@info(name = 'query1')@dist(parallel='1', execGroup='000')\n"
                + "From stockStream[price > 100]\n"
                + "Select *\n"
                + "Insert into filteredStockStream;\n"
                + "@info(name = 'query2')@dist(parallel='1', execGroup='000')\n"
                + "from companyTriggerStream\n"
                + "select *\n"
                + "insert into\n"
                + "companyTriggerInternalStream;\n"
                + "@info(name='query3')@dist(parallel='2',execGroup='001')\n"
                + "Partition with (symbol of filteredStockStream)\n"
                + "begin\n"
                + "From filteredStockStream#window.time(5 min)\n"
                + "Select symbol, avg(price) as avgPrice, quantity\n"
                + "Insert into #avgPriceStream;\n"
                + "From #avgPriceStream#window.time(5 min) as a right outer join companyTriggerInternalStream#window"
                + ".length"
                + "(1)\n"
                + "On (companyTriggerInternalStream.symbol == a.symbol)\n"
                + "Select a.symbol, a.avgPrice, a.quantity\n"
                + "Insert into triggeredAvgStream;\n"
                + "End;\n"
                + "@info(name='query4')@dist(parallel='5', execGroup='002')\n"
                + "Partition with (symbol of filteredStockStream)\n"
                + "begin\n"
                + "From filteredStockStream#log(symbol)\n"
                + "Select *\n"
                + "Insert into dumbstream;\n"
                + "End;";

        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);

        Assert.assertEquals(topology.getQueryGroupList().get(0).getInputStreams().get("stockStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("filteredStockStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.FIELD_GROUPING);
        Assert.assertEquals(topology.getQueryGroupList().get(2).getInputStreams().get("filteredStockStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.FIELD_GROUPING);
        Assert.assertEquals(topology.getQueryGroupList().get(0).getOutputStreams().get("filteredStockStream")
                                    .getPublishingStrategyList().get(0).getParallelism(), 2);
        Assert.assertEquals(topology.getQueryGroupList().get(0).getOutputStreams().get("filteredStockStream")
                                    .getPublishingStrategyList().get(0).getGroupingField(), "symbol");
        Assert.assertEquals(topology.getQueryGroupList().get(0).getOutputStreams().get("filteredStockStream")
                                    .getPublishingStrategyList().get(1).getParallelism(), 5);
        Assert.assertEquals(topology.getQueryGroupList().get(0).getOutputStreams().get("filteredStockStream")
                                    .getPublishingStrategyList().get(1).getGroupingField(), "symbol");

        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }

        Assert.assertEquals(topology.getQueryGroupList().get(0).getSiddhiApp(), "@App:name('${appName}') \n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/trigger', @map(type='xml'))\n"
                + "Define stream companyTriggerStream(symbol string);\n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/stockQuote', @map(type='xml'))\n"
                + "Define stream stockStream(symbol string, price float, quantity int, tier string);\n"
                + "${filteredStockStream}define stream filteredStockStream (symbol string, price float, quantity int,"
                + " tier string);\n"
                + "${companyTriggerInternalStream}define stream companyTriggerInternalStream (symbol string);\n"
                + "@info(name = 'query1')\n"
                + "From stockStream[price > 100]\n"
                + "Select *\n"
                + "Insert into filteredStockStream;\n"
                + "@info(name = 'query2')\n"
                + "from companyTriggerStream\n"
                + "select *\n"
                + "insert into\n"
                + "companyTriggerInternalStream;\n");

        Assert.assertEquals(topology.getQueryGroupList().get(1).getSiddhiApp(), "@App:name('${appName}') \n"
                + "${filteredStockStream}define stream filteredStockStream (symbol string, price float, quantity int,"
                + " tier string);\n"
                + "${companyTriggerInternalStream}define stream companyTriggerInternalStream (symbol string);\n"
                + "${triggeredAvgStream}define stream triggeredAvgStream (symbol string, avgPrice double, quantity "
                + "int);\n"
                + "@info(name='query3')\n"
                + "Partition with (symbol of filteredStockStream)\n"
                + "begin\n"
                + "From filteredStockStream#window.time(5 min)\n"
                + "Select symbol, avg(price) as avgPrice, quantity\n"
                + "Insert into #avgPriceStream;\n"
                + "From #avgPriceStream#window.time(5 min) as a right outer join companyTriggerInternalStream#window"
                + ".length(1)\n"
                + "On (companyTriggerInternalStream.symbol == a.symbol)\n"
                + "Select a.symbol, a.avgPrice, a.quantity\n"
                + "Insert into triggeredAvgStream;\n"
                + "End;\n");
    }


    /**
     * A stream used by multiple partitions residing in different executionGroups and different Partition key gets
     * assigned with the {@link TransportStrategy#FIELD_GROUPING} and corresponding parallelism.
     */
    @Test
    public void testPartitionWithMultiKey() {
        String siddhiApp = "@App:name('TestPlan') \n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/stockQuote', @map(type='xml')) "
                + "Define stream stockStream(symbol string, price float, quantity int, tier string);\n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/trigger', @map(type='xml'))\n"
                + "Define stream companyTriggerStream(symbol string);\n"
                + "@info(name = 'query1')@dist(parallel='1', execGroup='001')\n"
                + "From stockStream[price > 100]\n"
                + "Select *\n"
                + "Insert into filteredStockStream;\n"
                + "@info(name = 'query2')@dist(parallel='1', execGroup='001')\n"
                + "from companyTriggerStream\n"
                + "select *\n"
                + "insert into\n"
                + "companyTriggerInternalStream;\n"
                + "@info(name='query3')@dist(parallel='2',execGroup='002')\n"
                + "Partition with (symbol of filteredStockStream)\n"
                + "begin\n"
                + "From filteredStockStream#window.time(5 min)\n"
                + "Select symbol, avg(price) as avgPrice, quantity\n"
                + "Insert into #avgPriceStream;\n"
                + "From #avgPriceStream#window.time(5 min) as a right outer join companyTriggerInternalStream#window.length"
                + "(1)\n"
                + "On (companyTriggerInternalStream.symbol == a.symbol)\n"
                + "Select a.symbol, a.avgPrice, a.quantity\n"
                + "Insert into triggeredAvgStream;\n"
                + "End;\n"
                + "@info(name='query4')@dist(parallel='2', execGroup='003')\n"
                + "Partition with (tier of filteredStockStream)\n"
                + "begin\n"
                + "From filteredStockStream#log(symbol)\n"
                + "Select *\n"
                + "Insert into dumbstream;\n"
                + "End;\n";
        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);
        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();

        Assert.assertEquals(topology.getQueryGroupList().get(0).getInputStreams().get("stockStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("filteredStockStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.FIELD_GROUPING);
        Assert.assertEquals(topology.getQueryGroupList().get(0).getOutputStreams().get("filteredStockStream")
                                    .getPublishingStrategyList().get(0).getGroupingField(), "symbol");
        Assert.assertEquals(topology.getQueryGroupList().get(0).getOutputStreams().get("filteredStockStream")
                                    .getPublishingStrategyList().get(1).getGroupingField(), "tier");

        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }
    }


    /**
     * user given Sink used in (parallel/multiple execGroups) will get assigned to a all the execGroups after Topology
     * creation
     */
    @Test
    public void testUserDefinedSink() {
        String siddhiApp = "@App:name('TestPlan') \n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/stockQuote', @map(type='xml'))\n"
                + "Define stream stockStream(symbol string, price float, quantity int, tier string);\n"
                + "@Sink(type='email', @map(type='json'), username='wso2', address='test@wso2.com',password='****',"
                + "host='smtp.gmail.com',subject='Event from SP',to='towso2@gmail.com')\n"
                + "Define stream takingOverStream(symbol string, overtakingSymbol string, avgPrice double);\n"
                + "@info(name = 'query1')@dist(parallel='1', execGroup='001')\n"
                + "From stockStream[price > 100]\n"
                + "Select *\n"
                + "Insert into filteredStockStream;\n"
                + "@info(name = 'query2')@dist(parallel='2', execGroup='002')"
                + "Partition with (symbol of filteredStockStream)\n"
                + "begin\n"
                + "From filteredStockStream#window.time(5 min)\n"
                + "Select symbol, tier as overtakingSymbol,avg(price) as avgPrice  \n"
                + "Insert into takingOverStream;\n"
                + "end;\n"
                + "@info(name ='query3')@dist(parallel='2', execGroup='003')\n"
                + "from filteredStockStream [price >250 and price <350]\n"
                + "Select symbol, tier as overtakingSymbol ,avg(price) as avgPrice  \n"
                + "Insert into takingOverStream;\n";

        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);

        Assert.assertTrue(topology.getQueryGroupList().get(1).getOutputStreams().containsKey("takingOverStream"));
        Assert.assertTrue(topology.getQueryGroupList().get(2).getOutputStreams().containsKey("takingOverStream"));

        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }
    }


    /**
     * when a user defined sink stream is used as in an internal source stream, a placeholder corresponding to the
     * streamID will be added to the respective sink so that the placeholder will bridge the stream to the required
     * source.
     */
    @Test
    public void testSinkStreamForSource() {

        String siddhiApp = "@App:name('TestPlan')\n"
                + "@source(type='http',receiver.url='http://localhost:9055/endpoints/stockQuote',@map(type='xml')) \n"
                + "Define stream stockStream(symbol string, price float, quantity int, tier string);\n"
                + "@source(type='http', receiver.url='http://localhost:9055/endpoints/trigger', @map(type='xml'))\n"
                + "Define stream companyTriggerStream(symbol string);\n"
                + "@Sink(type='email', @map(type='json'), username='wso2', address='test@wso2.com',password='****',"
                + "host='smtp.gmail.com',subject='Event from SP',to='towso2@gmail.com')\n"
                + "Define stream takingOverStream(symbol string, overtakingSymbol string, avgPrice double);\n"
                + "@Store(type='rdbms',jdbc.url='jdbc:mysql://localhost:3306/spDB',jdbc.driver.name='', "
                + "username= 'root', password='****',field.length= 'symbol:254')\n"
                + "Define table takingOverTable(symbol string, overtakingSymbol string, avgPrice double);\n"
                + "@info(name = 'query1')@dist(parallel='1', execGroup='001')\n"
                + "From stockStream[price > 100]\n"
                + "Select * Insert into filteredStockStream;\n"
                + "@info(name = 'query2')@dist(parallel='1', execGroup='001')\n"
                + "From companyTriggerStream select * insert into companyTriggerInternalStream;\n"
                + "@info(name='query3') @dist(parallel='3',execGroup='002')\n"
                + "Partition with (symbol of filteredStockStream)\n"
                + "Begin\n"
                + "From filteredStockStream#window.time(5 min)\n"
                + "Select symbol, avg(price) as avgPrice, quantity Insert into #avgPriceStream;\n"
                + "From #avgPriceStream#window.time(5 min) as a right outer join companyTriggerInternalStream#window"
                + ".length(1) "
                + "On (companyTriggerInternalStream.symbol == a.symbol)\n"
                + "Select a.symbol, a.avgPrice, a.quantity Insert into triggeredAvgStream;\n"
                + "End;\n"
                + "@info(name='query4')@dist(parallel='1', execGroup='003')\n"
                + "From  a1=triggeredAvgStream,  a2=triggeredAvgStream[a1.avgPrice<a2.avgPrice]\n"
                + "Select a1.symbol, a2.symbol as overtakingSymbol, a2.avgPrice Insert into "
                + "takingOverStream;\n"
                + "@info(name='query5')@dist(parallel='1', execGroup='004')\n"
                + "From takingOverStream  Select * Insert into takingOverTable;\n";

        SiddhiTopologyCreatorImpl siddhiTopologyCreator = new SiddhiTopologyCreatorImpl();
        SiddhiTopology topology = siddhiTopologyCreator.createTopology(siddhiApp);

        //checking assigned strategies
        Assert.assertEquals(topology.getQueryGroupList().get(0).getInputStreams().get("stockStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(0).getInputStreams().get("companyTriggerStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("filteredStockStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.FIELD_GROUPING);
        Assert.assertEquals(topology.getQueryGroupList().get(1).getInputStreams().get("companyTriggerInternalStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(2).getInputStreams().get("triggeredAvgStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);
        Assert.assertEquals(topology.getQueryGroupList().get(3).getInputStreams().get("takingOverStream")
                                    .getSubscriptionStrategy().getStrategy(), TransportStrategy.ALL);

        Assert.assertEquals(topology.getQueryGroupList().get(2).getOutputStreams().get("takingOverStream")
                                    .getStreamDefinition() + ";", "@Sink(type='email', @map(type='json'), "
                                    + "username='wso2', address='test@wso2.com',password='****',host='smtp.gmail"
                                    + ".com',subject='Event from"
                                    + " SP',to='towso2@gmail.com')\n"
                                    + "${takingOverStream} \n"
                                    + "Define stream takingOverStream(symbol string, overtakingSymbol string, "
                                    + "avgPrice double);");

        SiddhiAppCreator appCreator = new SPSiddhiAppCreator();
        List<DeployableSiddhiQueryGroup> queryGroupList = appCreator.createApps(topology);
        for (DeployableSiddhiQueryGroup group : queryGroupList) {
            for (String query : group.getQueryList()) {
                SiddhiManager siddhiManager = new SiddhiManager();
                siddhiManager.createSiddhiAppRuntime(query);
            }
        }

    }
}