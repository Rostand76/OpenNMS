//
// Copyright (C) 2002-2003 Sortova Consulting Group, Inc.  All rights reserved.
// Parts Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.sortova.com/
//
//
// Tab Size = 8
//
//
package org.opennms.netmgt.poller;

import java.lang.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.nio.channels.SocketChannel;
import org.opennms.netmgt.utils.SocketChannelUtil;

import java.net.InetAddress;
import java.net.ConnectException;
import java.net.NoRouteToHostException;

import java.util.Map;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;

import org.opennms.netmgt.utils.ParameterMap;

/**
 * <P>This class is designed to be used by the service poller
 * framework to test the availability of the Citrix service on 
 * remote interfaces. The class implements the ServiceMonitor
 * interface that allows it to be used along with other
 * plug-ins by the service poller framework.</P>
 *
 * @author <A HREF="mailto:tarus@opennms.org">Tarus Balog</A>
 * @author <A HREF="mailto:jason@opennms.org">Jason</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</A>
 *
 *
 */
final class CitrixMonitor
        extends IPv4LatencyMonitor
{
	/** 
	 * Default FTP port.
	 */
	private static final int 	DEFAULT_PORT 		= 1494;

	/** 
	 * Default retries.
	 */
	private static final int 	DEFAULT_RETRY 		= 0;

	/** 
	 * Default timeout. Specifies how long (in milliseconds) to block waiting
	 * for data from the monitored interface.
	 */
	private static final int 	DEFAULT_TIMEOUT 	= 3000; // 3 second timeout on read()

	/**
	 * <P>Poll the specified address for Citrix service availability</P>
	 *
	 * <P>During the poll an attempt is made to connect on the specified
	 * port (by default  port 1494).  If the connection request is
	 * successful, the banner line generated by the interface is parsed
	 * and if the extracted return code indicates that we are talking to
	 * an Citrix server ('ICA' appears in the response) we set the
	 * service status to SERVICE_AVAILABLE and return.</P>
	 * @param iface		The network interface to test the service on.
	 * @param parameters	The package parameters (timeout, retry, etc...) to be 
	 *  used for this poll.
	 *
	 * @return The availibility of the interface and if a transition event
	 * 	should be supressed.
	 *
	 */
	public int poll(NetworkInterface iface, Map parameters, org.opennms.netmgt.config.poller.Package pkg) 
	{
		// check the interface type
		//
		if(iface.getType() != NetworkInterface.TYPE_IPV4)
			throw new NetworkInterfaceNotSupportedException("Unsupported interface type, only TYPE_IPV4 currently supported");


		// Get the category logger
		//
		Category log = ThreadCategory.getInstance(getClass());

		// get the parameters
		//
		int retry   = ParameterMap.getKeyedInteger(parameters, "retry", DEFAULT_RETRY);
		int port    = ParameterMap.getKeyedInteger(parameters, "port", DEFAULT_PORT);
		int timeout = ParameterMap.getKeyedInteger(parameters, "timeout", DEFAULT_TIMEOUT);
                String rrdPath = ParameterMap.getKeyedString(parameters, "rrd-repository", null);
                String dsName = ParameterMap.getKeyedString(parameters, "ds-name", null);

                if (rrdPath == null)
                {
                        log.info("poll: RRD repository not specified in parameters, latency data will not be stored.");
                }
                if (dsName == null)
                {
                        dsName = DS_NAME;
                }

		//don't let the user set the timeout to 0, an infinite loop will occur if the server is down
		if (timeout==0)
			timeout=10;
		
		// Extract the address
		//
		InetAddress ipv4Addr = (InetAddress)iface.getAddress();
		String host = ipv4Addr.getHostAddress();
		
		if (log.isDebugEnabled())
			log.debug("CitrixMonitor.poll: Polling interface: " + host + " timeout: " + timeout + " retry: " + retry);
		
		int serviceStatus = ServiceMonitor.SERVICE_UNAVAILABLE;
                long responseTime = -1;

		for (int attempts=0; attempts <= retry && serviceStatus != ServiceMonitor.SERVICE_AVAILABLE; attempts++)
		{
                        SocketChannel sChannel = null;
			try
			{
				// create a connected socket
				//
                                long sentTime = System.currentTimeMillis();

                                sChannel = SocketChannelUtil.getConnectedSocketChannel(ipv4Addr, port, timeout);
                                if (sChannel == null)
                                {
                                        log.debug("CitrixMonitor: did not connect to host within timeout: " + timeout +" attempt: " + attempts);
                                        continue;
                                }
                                log.debug("CitrixMonitor: connected to host: " + host + " on port: " + port);

				// We're connected, so upgrade status to unresponsive

				// Allocate a line reader
				//
                                BufferedReader reader = new BufferedReader(new InputStreamReader(sChannel.socket().getInputStream()));

				StringBuffer buffer = new StringBuffer();
				
				// Not an infinite loop...socket timeout will break this out
				// of the loop if "ICA" string is never read.
				//
				while (serviceStatus!=ServiceMonitor.SERVICE_AVAILABLE)
				{
					buffer.append((char)reader.read());
					if (buffer.toString().indexOf("ICA")>-1)
					{
						serviceStatus = ServiceMonitor.SERVICE_AVAILABLE;
		                                responseTime = System.currentTimeMillis() - sentTime;
                	                        if (responseTime >= 0 && rrdPath != null)
						{
                                        		try
                                        		{
		                                        	this.updateRRD(m_rrdInterface, rrdPath, ipv4Addr, dsName, responseTime, pkg);
                                        		}
                                        		catch(RuntimeException rex)
                                        		{
                                                		log.debug("There was a problem writing the RRD:" + rex);
                                        		}
						}
					}
					else
					{
						serviceStatus = ServiceMonitor.SERVICE_UNAVAILABLE;
					}
				}
			}
			catch(ConnectException cE)
			{
				// Connection refused!!  Continue to retry.
				//
				cE.fillInStackTrace();
				log.debug("CitrixPlugin: connection refused by host " + host, cE);
				serviceStatus = ServiceMonitor.SERVICE_UNAVAILABLE;
			}
			catch(NoRouteToHostException e)
			{
				// No route to host!!  No need to perform retries.
				e.fillInStackTrace();
				log.info("CitrixPlugin: Unable to test host " + host + ", no route available", e);
				serviceStatus = ServiceMonitor.SERVICE_UNAVAILABLE;
				break;
			}
			catch(InterruptedIOException e)
			{
				// no logging necessary, this is "expected" behavior
				//
				serviceStatus = ServiceMonitor.SERVICE_UNAVAILABLE;
			}
			catch(IOException e)
			{
				log.info("CitrixPlugin: Error communicating with host " + host, e);
				serviceStatus = ServiceMonitor.SERVICE_UNAVAILABLE;
			}
                        catch(InterruptedException e)                                
                        {                                                             
                                log.warn("CitrixMonitor: Thread interrupted while connecting to host " + host, e); 
                                serviceStatus = ServiceMonitor.SERVICE_UNAVAILABLE;
                                break;                                                
			}
			catch(Throwable t)
			{
				log.warn("CitrixPlugin: Undeclared throwable exception caught contacting host " + host, t);
				serviceStatus = ServiceMonitor.SERVICE_UNAVAILABLE;
			}
			finally
			{
				try
				{
                                        if(sChannel != null)
                                        {
                                                if (sChannel.socket() != null)
                                                        sChannel.socket().close();
                                                sChannel.close();
                                                sChannel = null;
                                        }
				}
				catch(IOException e) { }
			}
		}
	
		//
		// return the status of the service
		//
		return serviceStatus;
	}

}

