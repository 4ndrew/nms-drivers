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
package org.aap.nms.driver.client.abstraction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.valabs.odisp.common.Message;
import org.valabs.odisp.common.StandartODObject;
import org.valabs.stdmsg.ODObjectLoadedMessage;
import org.valabs.stdobj.translator.Translator;

import com.novel.nms.client.components.DeviceInfo;
import com.novel.nms.client.devices.GenericDevice;
import com.novel.nms.client.tcpclient.NetManager;
import com.novel.nms.messages.ClearAlarmsMessage;
import com.novel.nms.messages.DevPollReplyMessage;
import com.novel.nms.messages.LogEventMessage;
import com.novel.nms.messages.client.DeviceGetConnectorsMessage;
import com.novel.nms.messages.client.DeviceGetConnectorsReplyMessage;
import com.novel.nms.messages.client.DeviceRequestMessage;
import com.novel.nms.messages.client.DeviceRequestReplyMessage;
import com.novel.nms.messages.client.DeviceUpdateMessage;
import com.novel.nms.messages.client.DeviceUpdateReplyMessage;

/**
 * Abstract version of the driver.
 * 
 * @author Andrew Porokhin
 */
public abstract class AbstractDriverUI extends StandartODObject {
  /** Dependencies of the Abstract Driver UI. */
  final String[] DEPEDENCIES = new String [] {
        "dispatcher",
        Translator.class.getName()
      };
  
  /** Resource :: translator. */
  private Translator translator;

  /**
   * Default constructor for abstract class, copy of the
   * {@link StandartODObject#StandartODObject(String, String, String, String)} in common
   * 
   * @param newName
   * @param fullName
   * @param version
   * @param copyright
   */
  public AbstractDriverUI(String newName, String fullName, String version,
      String copyright) {
    super(newName, fullName, version, copyright);
  }
  
  /**
   * Translate.
   * 
   * @param key
   * @param defValue
   * @return
   */
  public String translate(String key, String defValue) {
    return ((translator != null) ? translator.getProperty(key, defValue)
            : defValue);
  }
  
  /**
   * 
   * @param msg
   * @return
   */
  public boolean handleCommonMessage(Message msg) {
    logger.entering(getClass().getName(), "handleCommonMessage");

    if (ODObjectLoadedMessage.equals(msg)) {
      // Acquire resource objects
      translator = (Translator) dispatcher.getResourceManager()
          .resourceTryAcquire(Translator.class.getName());
      
      registerDeviceTypes();
    } else if (DeviceRequestMessage.equals(msg)) {
      /*
       * NOTE THAT!!! IMPOTANT INFORMATION Any corrent support handler MUST reply
       * on that message with DeviceInfoReplyMessage.
       */
      Message reply = dispatcher.getNewMessage();
      DeviceRequestReplyMessage.setup(reply, msg.getOrigin(), getObjectName(), msg.getId());
      DeviceRequestReplyMessage.setDevice(reply, ((DeviceInfo) DeviceRequestMessage.getDevice(msg)).getName());
      
      dispatcher.send(reply);
    } else if (DeviceUpdateMessage.equals(msg)) {
      /*
       * NOTE THAT!!! IMPOTANT INFORMATION Any corrent support handler MUST reply
       * on that message with DeviceUpdateReplyMessage.
       */
      Message reply = dispatcher.getNewMessage();
      DeviceUpdateReplyMessage.setup(reply, msg.getOrigin(), getObjectName(), msg.getId());
      DeviceUpdateReplyMessage.setDevice(reply, DeviceUpdateMessage.getDevice(msg));
      dispatcher.send(reply);
    } else if (DeviceGetConnectorsMessage.equals(msg)) {
      /*
       * TODO: may be this should be removed.
       */
      Message reply = dispatcher.getNewMessage();
      DeviceGetConnectorsReplyMessage.setup(reply, msg.getOrigin(), getObjectName(), msg.getId());
      DeviceGetConnectorsReplyMessage.setDevice(reply, DeviceGetConnectorsMessage.getDevice(msg));
      DeviceGetConnectorsReplyMessage.setType(reply, DeviceGetConnectorsMessage.getType(msg));
      
      List resultList = new ArrayList();
      
      DeviceGetConnectorsReplyMessage.setConnectors(reply, resultList);
      
      dispatcher.send(reply);
    } else if (DevPollReplyMessage.equals(msg)
        || LogEventMessage.equals(msg)
        || ClearAlarmsMessage.equals(msg)) {
      routeMessageLocal(msg, GenericDevice.DEVICE_MONITOR);
    } else {
      // Indicate unknown driver message request
      return false;
    }
    
    return true;
  }
  
  protected abstract void registerDeviceTypes();
  
  /**
   * 
   * @param msg
   * @param newDestination
   */
  public void routeMessageLocal(Message msg, String newDestination) {
    if (logger.isLoggable(Level.FINEST)) {
      logger.finest("[DBG]: (" + getObjectName() + ") routing message " + msg.getAction() + " to the local receiver: " + newDestination);
    }
    Message routedMessage = msg.cloneMessage();
    routedMessage.setDestination(newDestination);
    routedMessage.setOrigin(getObjectName());
    routedMessage.addField("serverId", new Integer(NetManager.getIndexFromOrigin(msg.getOrigin())));
    routedMessage.setRoutable(false);
    
    // Update envelope messages
    if (routedMessage.getEnvelope() != null) {
      if (logger.isLoggable(Level.FINEST)) {
        logger.finest("Processing envelope of the " + msg.toString());
      }
      
      for (Iterator it = routedMessage.getEnvelope().iterator(); it.hasNext(); ) {
        Message m = (Message) it.next();
        m.addField("serverId", new Integer(NetManager.getIndexFromOrigin(msg.getOrigin())));
      }
    }
    
    if (!routedMessage.isCorrect()) {
      logger.severe("Incorrect message passed to route: " + msg.toString(true));
    }
    
    dispatcher.send(routedMessage);
  }

  /* (non-Javadoc)
   * @see org.valabs.odisp.common.ODObject#getDepends()
   */
  public String[] getDepends() {
    return DEPEDENCIES;
  }

}
