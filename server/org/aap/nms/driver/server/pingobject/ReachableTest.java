package org.aap.nms.driver.server.pingobject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.LinkedList;

import org.valabs.odisp.common.Message;

import com.novel.nms.messages.DevPollMessage;
import com.novel.nms.server.devices.common.Device;

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