/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.viadee.bpm.vPAV.spring;

import de.viadee.bpm.vPAV.ProcessApplicationValidator;
import de.viadee.bpm.vPAV.RuntimeConfig;
import de.viadee.bpm.vPAV.constants.ConfigConstants;
import de.viadee.bpm.vPAV.processing.model.data.CheckerIssue;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collection;
import java.util.Properties;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { SayHelloDelegate.class })
public class SpringTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    public void validateModel() {
        RuntimeConfig.getInstance().setTest(true);
        Properties properties = new Properties();
        properties.put("basepath", ConfigConstants.getInstance().getBasepath() + "spring/");
        ConfigConstants.getInstance().setProperties(properties);
        Collection<CheckerIssue> issues = ProcessApplicationValidator.findModelInconsistencies(ctx);
        Assert.assertEquals(1, issues.size());
        Assert.assertEquals("UnkownVariable", issues.iterator().next().getVariable());
    }
}