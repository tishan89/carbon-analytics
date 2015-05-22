/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.analytics.eventsink;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.deployment.AbstractDeployer;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.analytics.datasource.commons.AnalyticsSchema;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsException;
import org.wso2.carbon.analytics.eventsink.exception.AnalyticsEventStoreException;
import org.wso2.carbon.analytics.eventsink.exception.AnalyticsEventStoreDeploymentException;
import org.wso2.carbon.analytics.eventsink.internal.AnalyticsEventStoreManager;
import org.wso2.carbon.analytics.eventsink.internal.util.AnalyticsEventSinkConstants;
import org.wso2.carbon.analytics.eventsink.internal.util.AnalyticsEventSinkUtil;
import org.wso2.carbon.analytics.eventsink.internal.util.ServiceHolder;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.io.File;

public class AnalyticsEventStoreDeployer extends AbstractDeployer {
    private static Log log = LogFactory.getLog(AnalyticsEventStoreDeployer.class);

    @Override
    public void init(ConfigurationContext configurationContext) {
        File deployementDir = new File(MultitenantUtils.getAxis2RepositoryPath(CarbonContext.getThreadLocalCarbonContext().
                getTenantId())+ File.separator+ AnalyticsEventSinkConstants.DEPLOYMENT_DIR_NAME);
        if (!deployementDir.exists()){
            deployementDir.mkdir();
        }
    }

    public void deploy(DeploymentFileData deploymentFileData) throws DeploymentException {
        int tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
        try {
            AnalyticsEventStore eventStoreConfiguration = AnalyticsEventStoreManager.getInstance()
                    .getAnalyticsEventStore(deploymentFileData.getFile());
            if (AnalyticsEventSinkUtil.getAnalyticsEventStoreName(deploymentFileData.getName()).
                    equals(eventStoreConfiguration.getName())) {
                addEventStore(tenantId, eventStoreConfiguration);
            } else {
                throw new AnalyticsEventStoreDeploymentException("Invalid configuration provided! File name: " +
                        AnalyticsEventSinkUtil.getAnalyticsEventStoreName(deploymentFileData.getName() + " should be " +
                                "matched with deduced table name : " + eventStoreConfiguration.getName() + " for the streams"));
            }
        } catch (AnalyticsEventStoreException e) {
            String errMsg = "Error while deploying file : " + deploymentFileData.getName() + " for tenant id : " + tenantId;
            log.error(errMsg, e);
            throw new AnalyticsEventStoreDeploymentException(errMsg, e);
        }
    }

    private void addEventStore(int tenantId, AnalyticsEventStore eventStore)
            throws AnalyticsEventStoreException {
        try {
            AnalyticsEventStoreManager.getInstance().addEventStoreConfiguration(tenantId, eventStore);
            ServiceHolder.getAnalyticsDataService().createTable(tenantId, eventStore.getName());
            ServiceHolder.getAnalyticsDataService().setTableSchema(tenantId, eventStore.getName(),
                    AnalyticsEventSinkUtil.getAnalyticsSchema(eventStore.getAnalyticsTableSchema()));
            for (String streamId : eventStore.getEventSource().getStreamIds()) {
                ServiceHolder.getAnalyticsEventStreamListener().subscribeForStream(tenantId, streamId);
            }
        } catch (AnalyticsException e) {
            throw new AnalyticsEventStoreException("Error while creating the table Or setting the " +
                    "schema for table :" + eventStore.getName(), e);
        }
    }

    public void undeploy(String fileName) throws DeploymentException {
        int tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
        String eventStoreName = AnalyticsEventSinkUtil.getAnalyticsEventStoreName(fileName);
        AnalyticsEventStore existingEventStore = AnalyticsEventStoreManager.getInstance().removeEventStoreConfiguration(tenantId,
                eventStoreName);
        if (existingEventStore != null) {
            try {
                for (String streamId : existingEventStore.getEventSource().getStreamIds()) {
                    ServiceHolder.getAnalyticsEventStreamListener().unsubscribeFromStream(tenantId, streamId);
                }
                ServiceHolder.getAnalyticsDataService().
                        setTableSchema(tenantId, eventStoreName, new AnalyticsSchema());
            } catch (AnalyticsException e) {
                throw new AnalyticsEventStoreDeploymentException("Error while setting empty schema for the table :"
                        + eventStoreName, e);
            }
        }
    }

    @Override
    public void setDirectory(String s) {

    }

    @Override
    public void setExtension(String s) {

    }
}
