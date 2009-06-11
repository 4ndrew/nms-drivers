/**
 * 
 */
package org.aap.nms.driver.server.abstraction;

import java.net.InetAddress;
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
 * Abstract driver.
 * 
 * @author (C) 2009 Andrew Porokhin
 */
public abstract class AbstractDriver extends StandartODObject {
  /** Dependencies of the Abstract Driver UI. */
  final String[] DEPEDENCIES = new String [] {
        "dispatcher",
        Storage.class.getName(),
        DeviceList.class.getName(),
      };
  
  // TODO: change this.
  protected List newObjects;
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
      dispatcher.send(toMap);
      newObjects.addAll(msg.getEnvelope());
    } else if (MapObjectAddedMessage.equals(msg)) {
      List localMessages;
      synchronized (newObjects) {
          localMessages = new ArrayList(newObjects);
          newObjects.clear();
      }
      List objData = (List) msg.getField("0");
      Device newDevice = new Device((String) objData.get(0));
      newDevice.setDriver(getObjectName());
      Iterator it = ((List) objData.get(1)).iterator();
      while (it.hasNext()) {
          String urn = (String) it.next();
          newDevice.addURN(urn);
      }
      deviceList.addDevice(newDevice);
      
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
  
  /**
   * Preload deviceList to our local list.
   */
  protected void loadDeviceList() {
      GetObjectListHelper gohl = new GetObjectListHelper();
      try {
          ((Storage) dispatcher.getResourceManager().resourceAcquire(
                  Storage.class.getName())).executeRequest(gohl);
          List list = gohl.getResult();
          deviceList.clear(getObjectName());
          Iterator it = list.iterator();
          while (it.hasNext()) {
              List objEntry = (List) it.next();
              
              logger.fine(objEntry.toString());
              
              List urn = (List) objEntry.get(1);
              String handler = (String) objEntry.get(2);
              Iterator urn_it = urn.iterator();
              if (getObjectName().startsWith(handler)) {
                  Device dev = new Device((String) objEntry.get(0));
                  dev.setDriver(getObjectName());
                  while (urn_it.hasNext()) {
                      String urnS = (String) urn_it.next();
                      try {
                        // TODO: analyse that
                        // InetAddress.getByName(urnS).toString();
                        dev.addURN(urnS);
                      } catch (Exception e) {
                        dispatcher.getExceptionHandler().signalException(e);
                      }
                  }
                  deviceList.addDevice(dev);
              }
          }
      } catch (Exception e) {
          dispatcher.getExceptionHandler().signalException(e);
      }
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
