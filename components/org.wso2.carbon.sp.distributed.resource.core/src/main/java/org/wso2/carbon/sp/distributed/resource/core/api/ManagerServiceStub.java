/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.sp.distributed.resource.core.api;

import feign.Headers;
import feign.RequestLine;
import feign.Response;
import org.wso2.carbon.sp.distributed.resource.core.bean.NodeConfig;

/**
 * feign client for sending the request.
 */

public interface ManagerServiceStub {
    
    @RequestLine("POST /resourceManager/heartbeat")
    @Headers("Content-Type: application/json")
    Response sendHeartBeat(String nodeConfig);
}
