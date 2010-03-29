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
package org.aap.nms.driver.server.abstraction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.valabs.odisp.common.Message;
import org.valabs.odisp.common.StandartODObject;
import org.valabs.stdmsg.ODObjectLoadedMessage;

import com.novel.nms.messages.DeviceGetCurrentLogMessage;
import com.novel.nms.messages.DeviceGetCurrentLogReplyMessage;
import com.novel.nms.messages.DeviceGetCurrentStatusErrorMessage;
import com.novel.nms.messages.DeviceGetCurrentStatusMessage;
import com.novel.nms.messages.DeviceGetCurrentStatusReplyMessage;
import com.novel.nms.messages.LogEventMessage;
import com.novel.nms.messages.MapAddObjectMessage;
import com.novel.nms.messages.MapAddObjectNotifyMessage;
import com.novel.nms.messages.MapDeleteObjectNotifyMessage;
import com.novel.nms.messages.MapObjectAddedErrorMessage;
import com.novel.nms.messages.MapObjectAddedMessage;
import com.novel.nms.server.devices.common.Device;
import com.novel.nms.server.devices.common.DeviceList;
import com.novel.nms.server.storage.Storage;
import com.novel.nms.server.storage.helpers.GetObjectListHelper;

/**
 * Abstract driver implementation. This class designed to simplify
 * new driver development.
 * 
 * @author (C) 2009 Andrew Porokhin
 * @version 1.0.1
 * @source 1.4
 */
@SuppressWarnings("unchecked")
public abstract class AbstractDriver extends StandartODObject {
  /** Dependencies of the Abstract Driver UI. */
  final String[] DEPEDENCIES = new String [] {
        "dispatcher",
        Storage.class.getName(),
        DeviceList.class.getName(),
      };
  
  // TODO: change this.
  protected List newObjects = new ArrayList();
  protected DeviceList deviceList;
  
  /**
   * Default constructor for abstract class, copy of the
   * {@link StandartODObject#StandartODObject(String, String, String, String)} in common
   * 
   * @param newName
   * @param fullName
   * @param version
   * @param copyright
   */
  public AbstractDriver(String newName, String fullName, String version,
      String copyright) {
    super(newName, fullName, version, copyright);
  }
  
  /**
   * 
   * @param msg
   * @return
   */
  public boolean handleCommonMessage(Message msg) {
    logger.entering(getClass().getName(), "handleCommonMessage");
    
    if (ODObjectLoadedMessage.equals(msg)) {
      deviceList = ((DeviceList) dispatcher.getResourceManager()
          .resourceAcquire(DeviceList.class.getName()));
      
      // Load devices into the DeviceList.
      loadDeviceList();
    } else if (MapAddObjectMessage.equals(msg)) {
      logger.fine("Adding new object " + MapAddObjectMessage.getName(msg));
      Message toMap = dispatcher.getNewMessage();
      MapAddObjectMessage.setup(toMap, "map", getObjectName(), msg.getId());
      MapAddObjectMessage.copyFrom(toMap, msg);
      toMap.addField("security", msg.getField("security"));
      synchronized (newObjects) {
        dispatcher.send(toMap);        
        newObjects.addAll(msg.getEnvelope());
      }
    } else if (MapObjectAddedMessage.equals(msg)) {
      List localMessages;
      synchronized (newObjects) {
          localMessages = new ArrayList(newObjects);
          newObjects.clear();
      }
      List objData = (List) msg.getField("0");
      String newDeviceName = (String) objData.get(0);
      List urns = (List) objData.get(1);
      String handler = (String) objData.get(2);
      
      /*
      Device newDevice = new Device(newDeviceName);
      newDevice.setDriver(getObjectName());
      for (Iterator it = urns.iterator(); it.hasNext(); ) {
          String urn = (String) it.next();
          newDevice.addURN(urn);
      }
      deviceList.addDevice(newDevice);
      deviceAdded(newDevice);
      */
      processAddDevice(newDeviceName, handler, urns);
      
      dispatcher.send(localMessages);
      Message notifyMsg = dispatcher.getNewMessage();
      MapAddObjectNotifyMessage.setup(notifyMsg, Message.RECIPIENT_ALL,
              getObjectName(), msg.getId());
      MapAddObjectNotifyMessage.setName(notifyMsg, (String) objData.get(0));
      MapAddObjectNotifyMessage.setURN(notifyMsg, (List) objData.get(1));
      MapAddObjectNotifyMessage.setHandler(notifyMsg, (String) objData.get(2));
      dispatcher.send(notifyMsg);
    } else if (MapObjectAddedErrorMessage.equals(msg)) {
      msg.setDestination(getObjectName() + "-gui");
      msg.setRoutable(true);
      dispatcher.send(msg);
    } else if (DeviceGetCurrentLogMessage.equals(msg)) {
      final String name = DeviceGetCurrentLogMessage.getName(msg);
      final Device dev = deviceList.getDeviceByName(name);
      if (dev != null) {
        Message m = dispatcher.getNewMessage();
        DeviceGetCurrentLogReplyMessage.setup(m, msg.getOrigin(), getObjectName(), msg.getId());
        DeviceGetCurrentLogReplyMessage.setName(m, name);
        DeviceGetCurrentLogReplyMessage.setCurrentLog(m, dev.getCurrentLog());
        dispatcher.send(m);
      } else {
        // TODO: reply with error.
      }
    } else if (DeviceGetCurrentStatusMessage.equals(msg)) {
      Message m = dispatcher.getNewMessage();
      Device dev = deviceList.getDeviceByName(DeviceGetCurrentStatusMessage.getDeviceName(msg));
      if (dev != null) {
          DeviceGetCurrentStatusReplyMessage.setup(m, msg.getOrigin(),
                  getObjectName(), msg.getId());
          DeviceGetCurrentStatusReplyMessage.setDeviceName(m,
                  DeviceGetCurrentStatusMessage.getDeviceName(msg));
          DeviceGetCurrentStatusReplyMessage.setStatus(m, new Long(dev.getStatus()));
      } else if (MapDeleteObjectNotifyMessage.equals(msg)) {
        deviceList.removeDeviceByName(MapDeleteObjectNotifyMessage.getName(msg));
      } else {
          DeviceGetCurrentStatusErrorMessage.setup(m, msg.getOrigin(), getObjectName(), msg.getId());
          DeviceGetCurrentStatusErrorMessage.initAll(m,
                  DeviceGetCurrentStatusMessage.getDeviceName(msg),
          "error.nosuchdevice");
      }
      dispatcher.send(m);
    } else {
      return false;
    }
    
    return true;
  }
  
  protected boolean isAcceptableHandler(String handler) {
    String deviceHandler = getObjectName();
    // TODO: Think about why startsWith, not equals. May be this is a bug.
    return deviceHandler.startsWith(handler);
  }
  
  protected void clearDeviceList() {
    String deviceHandler = getObjectName();
    deviceList.clear(deviceHandler);
  }
  
  /**
   * Preload deviceList to our local list.
   */
  protected void loadDeviceList() {
      GetObjectListHelper gohl = new GetObjectListHelper();
      try {
          ((Storage) dispatcher.getResourceManager().resourceAcquire(
                  Storage.class.getName())).executeRequest(gohl);
          List list = gohl.getResult();
          clearDeviceList();
          Iterator it = list.iterator();
          while (it.hasNext()) {
              List objEntry = (List) it.next();
              
              logger.fine(objEntry.toString());
              
              String deviceName = (String) objEntry.get(0); 
              List urn = (List) objEntry.get(1);
              String handler = (String) objEntry.get(2);
              if (isAcceptableHandler(handler)) {
                  processAddDevice(deviceName, handler, urn);
              }
          }
      } catch (Exception e) {
          dispatcher.getExceptionHandler().signalException(e);
      }
  }

  /**
   * Process adding of the device. This method can be overriden by driver.
   * @param deviceName Device name.
   * @param deviceHandler Device handler.
   * @param urn List of device URN.
   * @return Created device with specified properties.
   */
  protected Device processAddDevice(String deviceName, String deviceHandler, List urn) {
    Device dev = new Device(deviceName);
    dev.setStatus(LogEventMessage.ALARM_POLL_TIMEOUT);
    dev.setDriver(deviceHandler);
    for (Iterator urn_it = urn.iterator(); urn_it.hasNext(); ) {
        String urnS = (String) urn_it.next();
        dev.addURN(urnS);
    }
    deviceList.addDevice(dev);
    deviceAdded(dev);
    
    return dev;
  }
  
  /**
   * Called just after new device added to deviceList.
   * @param device Device.
   */
  protected void deviceAdded(Device device) {
    // Nothing to do
  }

  /* (non-Javadoc)
   * @see org.valabs.odisp.common.ODObject#getDepends()
   */
  public String[] getDepends() {
    return DEPEDENCIES;
  }

  /* (non-Javadoc)
   * @see org.valabs.odisp.common.ODObject#getProviding()
   */
  public String[] getProviding() {
    return null;
  }

}
