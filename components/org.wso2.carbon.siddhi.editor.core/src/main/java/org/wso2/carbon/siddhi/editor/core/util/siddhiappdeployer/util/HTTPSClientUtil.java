/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.siddhi.editor.core.util.siddhiappdeployer.util;

import feign.Response;
import org.wso2.carbon.siddhi.editor.core.exception.SiddhiAppDeployerServiceStubException;

/**
 * Contains HTTPS client related methods
 */
public class HTTPSClientUtil {
    private static final String PROTOCOL = "https";

    /**
     * Avoids Instantiation
     */
    private HTTPSClientUtil() {
    }

    /**
     * Generates an HTTPS URL with the given hostAndPort
     *
     * @param hostAndPort Host and Port of the Worker node in {Host}:{Port} format
     * @return HTTPS URL
     */
    private static String generateURL(String hostAndPort) {
        return PROTOCOL + "://" + hostAndPort;
    }

    /**
     * Produces a Response after doing a PUT request
     *
     * @param hostAndPort Host and Port of the Worker node in {Host}:{Port} format
     * @param username    Username
     * @param password    Password
     * @param payload     Payload
     * @return Feign Response object
     * @throws SiddhiAppDeployerServiceStubException Error occurred within SiddhiAppDeployerServiceStub
     */
    public static Response doPutRequest(String hostAndPort, String username, String password, String payload)
            throws SiddhiAppDeployerServiceStubException {
        return SiddhiAppDeployerFactory.getSiddhiAppDeployerHttpsClient(generateURL(hostAndPort), username, password)
                .doPutRequest(payload);
    }
}
