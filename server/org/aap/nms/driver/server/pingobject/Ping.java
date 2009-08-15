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

import java.util.Calendar;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.logging.Level;

import org.aap.nms.driver.server.abstraction.AbstractDriver;
import org.doomdark.uuid.UUID;
import org.valabs.odisp.common.Message;
import org.valabs.stdmsg.ODObjectLoadedMessage;

import com.novel.nms.messages.DevPollMessage;
import com.novel.nms.messages.DevPollReplyMessage;
import com.novel.nms.messages.LogEventMessage;
import com.novel.nms.server.devices.common.Device;

/**
 * Dummy driver for dummy object that support only icmp ping. 
 * 
 * @author <a href="mailto:andrew.porokhin@gmail.com">Andrew Porokhin</a>
 * @version 0.2
 */
public class Ping extends AbstractDriver {
  /** Name of the object, also handler. */
  public static final String NAME = "ping";

  /** Version of this component. */
  public static final String VERSION = "0.2";

  /** Short module description. */
  public static final String FULLNAME = "NMS Ping Object support";

  /** Short copyright string. */
  public static final String COPYRIGHT = "(c) 2009 Andrew Porokhin";
  /** Pinger object. */
  private Pinger pinger = new Pinger();
  
  /**
   * Just default constructor.
   */
  public Ping() {
      super(NAME, FULLNAME, VERSION, COPYRIGHT);
  }

  /* (non-Javadoc)
   * @see org.valabs.odisp.common.StandartODObject#handleMessage(org.valabs.odisp.common.Message)
   */
  public final void handleMessage(final Message msg) {
      if (ODObjectLoadedMessage.equals(msg)) {
        // Request deviceList
        handleCommonMessage(msg);
        
        pinger.start();
      } else if (DevPollMessage.equals(msg)) {
        pinger.addRequest(msg);
      } else {
        if (!handleCommonMessage(msg) && logger.isLoggable(Level.FINEST)) {
          logger.finest("Message unprocessed by driver: " + msg.toString());
        }
      }
  }

  /* (non-Javadoc)
   * @see org.valabs.odisp.common.ODObject#getProviding()
   */
  public final String[] getProviding() {
      String[] result = { NAME };
      return result;
  }

  /* (non-Javadoc)
   * @see org.valabs.odisp.common.StandartODObject#cleanUp(int)
   */
  public final int cleanUp(final int type) {
    pinger.interrupt();
    return 0;
  }
  
  /*
   * Worker thread for icmp ping requests. It is used to free ODISP
   * sender threads from hang up.
   */
  private class Pinger extends Thread {
    /** Ping queue. */
    LinkedList<Message> queue = new LinkedList<Message>();
    
    public Pinger() {
      setName("Pinger thread: waiting");
      setDaemon(true);
    }
    
    public void run() {
      while (!Thread.interrupted()) {
        Message request = null;
        
        synchronized (queue) {
          if (queue.size() > 0) {
            request = queue.removeFirst();
          }
        }
        
        if (request != null) {
          processMessage(request);
        }
        
        synchronized (queue) {
          if (queue.size() == 0) {
            setName("Pinger thread: waiting");
            try {
              queue.wait(10000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }
      }
    }
    
    private void processMessage(Message msg) {
      String deviceName = DevPollMessage.getDeviceName(msg);
      Device device = deviceList.getDeviceByName(deviceName);
      String urn = (String) device.getURNs().get(0);
      
      setName("Pinger thread: " + urn);
      
      Message mtogui = dispatcher.getNewMessage();
      DevPollReplyMessage.setup(mtogui, NAME + "-gui", getObjectName(), msg.getId());
      DevPollReplyMessage.setDeviceName(mtogui, deviceName);
      
      Message mtopoll = dispatcher.getNewMessage();
      DevPollReplyMessage.setup(mtopoll, "devpoll", getObjectName(), msg.getId());
      DevPollReplyMessage.setDeviceName(mtopoll, deviceName);
      
      switch (ReachableTest.checkReach(urn)) {
      case ReachableTest.NO_ERROR:
          DevPollReplyMessage.setStatus(mtogui, DevPollReplyMessage.ALARM_OK);
          DevPollReplyMessage.setStatus(mtopoll, DevPollReplyMessage.ALARM_OK);
          break;
      case ReachableTest.UNREACHABLE_ERROR:
      case ReachableTest.UNKNOWN_HOST_ERROR:
      default:
          Message m = dispatcher.getNewMessage();
          LogEventMessage.setup(m, "nmslog", getObjectName(), UUID.getNullUUID());
          LogEventMessage.setName(m, deviceName);
          LogEventMessage.setSource(m, deviceName);
          Calendar c = Calendar.getInstance(TimeZone.getDefault());
          LogEventMessage.setDate(m, new Long(c.getTimeInMillis() / 1000));
          LogEventMessage.setLDate(m, new Long(c.getTimeInMillis() / 1000));
          LogEventMessage.setIndex(m, Device.POLL_TIMEOUT_EVENT_IDX);
          LogEventMessage.setEvent(m, LogEventMessage.ALARM_POLL_TIMEOUT);
          LogEventMessage.setObject(m, "");
          LogEventMessage.setPC(m, "Poll timeout");
          LogEventMessage.setAI(m, "");
          LogEventMessage.setEquipStatus(m, LogEventMessage.ALARM_POLL_TIMEOUT);
          device.addToCurrentLog(m);
          m.setRoutable(false);
          dispatcher.send(m);
          
          DevPollReplyMessage.setStatus(mtogui, DevPollReplyMessage.ALARM_POLL_TIMEOUT);
          DevPollReplyMessage.setStatus(mtopoll, DevPollReplyMessage.ALARM_POLL_TIMEOUT);
      }
      
      // setting up current device status
      device.setStatus(DevPollReplyMessage.getStatus(mtopoll));
      
      // multiple sending to Server and UI
      dispatcher.send(mtogui);
      dispatcher.send(mtopoll);
    }
    
    public void addRequest(Message request) {
      synchronized (queue) {
        queue.add(request);
        queue.notifyAll();
      }
    }
  }

}
