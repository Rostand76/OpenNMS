/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2005-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.provision.detector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.UnknownHostException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.spring.BeanUtils;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.snmp.annotations.JUnitSnmpAgent;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.provision.detector.snmp.Win32ServiceDetector;
import org.opennms.netmgt.provision.detector.snmp.Win32ServiceDetectorFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
		"classpath:/META-INF/opennms/applicationContext-proxy-snmp.xml",
		"classpath:/META-INF/opennms/detectors.xml"
})
@JUnitSnmpAgent(host=Win32ServiceDetectorTest.TEST_IP_ADDRESS, resource="classpath:org/opennms/netmgt/provision/detector/windows2003.properties")
public class Win32ServiceDetectorTest implements InitializingBean {
    static final String TEST_IP_ADDRESS = "192.0.2.1";

    private Win32ServiceDetector m_detector;
    
    @Autowired
    private Win32ServiceDetectorFactory m_detectorFactory;

    @Override
    public void afterPropertiesSet() throws Exception {
        BeanUtils.assertAutowiring(this);
    }

    @Before
    public void setUp() throws InterruptedException {
        MockLogAppender.setupLogging();
        m_detector = m_detectorFactory.createDetector();
        m_detector.setRetries(2);
        m_detector.setTimeout(5000);
        m_detector.setWin32ServiceName("VMware Tools Service");
        m_detector.setRuntimeAttributes(m_detectorFactory.getRuntimeAttributes(null, InetAddressUtils.addr(TEST_IP_ADDRESS), null));
    }
    
    @Test(timeout=20000)
    public void testDetectorSuccessful() throws UnknownHostException{
        assertTrue(m_detector.isServiceDetected(InetAddressUtils.addr(TEST_IP_ADDRESS)));
    }

    @Test(timeout=20000)
    public void testDetectorFail() throws UnknownHostException{
        m_detector.setWin32ServiceName("This service does not exist");
        assertFalse(m_detector.isServiceDetected(InetAddressUtils.addr(TEST_IP_ADDRESS)));
    }
}
