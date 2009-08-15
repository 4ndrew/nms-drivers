/* NMS-DRIVERS -- Free NMS packages.
 * Copyright (C) 2009 Andrew A. Porohin 
 * 
 * NMS-DRIVERS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 2.1 of the License.
 * 
 * NMS-DRIVERS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with NMS-DRIVERS.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.aap.nms.driver.server.pingobject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Just reachable test.
 * Copylefted from http://www.rgagnon.com/javadetails/java-0093.html.
 * 
 * @author Andrew A. Porokhin
 * @version 1.0
 */
public class ReachableTest {
    public static final int NO_ERROR = 0;   
    public static final int UNREACHABLE_ERROR = -1;
    public static final int UNKNOWN_HOST_ERROR = -2;
    public static final int UNKNOWN_ERROR = -3;
    
    /**
     * Check tcp object reachable via ICMP PING.
     * 
     * @param hostAddress Destination host address (DNS domain string or IP Address.)
     * @return 
     */
    public static int checkReach(String hostAddress) {
        int result = UNKNOWN_ERROR;
        try {
            InetAddress address = InetAddress.getByName(hostAddress);
            if (address.isReachable(10000)) result = 0;
        } catch (UnknownHostException e) {
            result = UNKNOWN_HOST_ERROR;
        } catch (IOException e) {
            result = UNREACHABLE_ERROR;
        }
        return result;
    }
    
    public static final void main(String [] args) {
        System.out.println("Reachable mail.ru: " + checkReach("mail.ru"));
        System.out.println("Reachable localhost: " + checkReach("localhost"));
        System.out.println("Reachable 127.0.0.1: " + checkReach("127.0.0.1"));
        System.out.println("Reachable 10.50.9.9: " + checkReach("10.50.9.9"));
    }
    
}