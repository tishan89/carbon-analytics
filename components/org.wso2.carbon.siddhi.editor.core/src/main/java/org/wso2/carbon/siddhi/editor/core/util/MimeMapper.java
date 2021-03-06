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

package org.wso2.carbon.siddhi.editor.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.siddhi.editor.core.exception.MimeMappingException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This class lazily loads the mime-map.properties file
 * and maps file extension to mime type using the file.
 */
public class MimeMapper {

    private static final Logger log = LoggerFactory.getLogger(MimeMapper.class);
    private static Properties mimeMap = null;

    private static void loadMimeMap() throws IOException {
        mimeMap = new Properties();
        InputStream inputStream = MimeMapper.class.getClassLoader()
                .getResourceAsStream("mime-map.properties");
        if (inputStream != null) {
            mimeMap.load(inputStream);
            try {
                inputStream.close();
            } catch (IOException e) {
                log.warn("Could not close input stream", e);
            }
        }
    }

    public static String getMimeType(String extension) throws MimeMappingException {
        try {
            if (mimeMap == null) {
                loadMimeMap();
            }
        } catch (IOException e) {
            throw new MimeMappingException("Could not load mime map", e);
        }
        String mimeType = mimeMap.getProperty(extension);
        if (mimeType == null) {
            throw new MimeMappingException("Could not find mime type");
        }
        return mimeType;
    }

}
