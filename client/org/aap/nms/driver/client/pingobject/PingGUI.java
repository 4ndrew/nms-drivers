package org.aap.nms.driver.client.pingobject;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.ImageIcon;

import org.doomdark.uuid.UUID;
import org.valabs.odisp.common.Message;
import org.valabs.odisp.common.StandartODObject;
import org.valabs.stdmsg.ODObjectLoadedMessage;
import org.valabs.stdobj.translator.Translator;

import com.novel.nms.client.components.DeviceInfo;
import com.novel.nms.client.devices.DeviceType;
import com.novel.nms.client.devices.GenericDevice;
import com.novel.nms.client.tcpclient.NetManager;
import com.novel.nms.common.util.NMSVersion;
import com.novel.nms.messages.ClearAlarmsMessage;
import com.novel.nms.messages.DevPollReplyMessage;
import com.novel.nms.messages.LogEventMessage;
import com.novel.nms.messages.MapAddObjectMessage;
import com.novel.nms.messages.MapAddObjectNotifyMessage;
import com.novel.nms.messages.client.DeviceAddObjectMessage;
import com.novel.nms.messages.client.DeviceAddObjectReplyMessage;
import com.novel.nms.messages.client.DeviceRequestMessage;
import com.novel.nms.messages.client.DeviceRequestReplyMessage;
import com.novel.nms.messages.client.RegisterTypeMessage;

/**
 * Just a ping object.
 * 
 * If you make a choice by trusting your feelings instead of your logic,
 * and you succeed, then, logically, your logic was illogical.
 * 
 * @author <a href="andrew.porokhin@gmail.com">Andrew A. Porokhin</a>
 * @version 1.0
 */
public class PingGUI extends StandartODObject {
    /** Device handler */
    public static final String HANDLER = "ping";
    /** Device name. */
    public static final String NAME = HANDLER + "-gui";
    /** Module version. */
    public static final String VERSION = NMSVersion.VERSION;
    /** Module description. */
    public static final String FULLNAME = "ping.name";
    /** All right reserved. */
    public static final String COPYRIGHT = "ping.copyright";
    /** Short description of the module. */
    public static final String SHORTNAME = "ping.shortName";
    
    /** Field to store request id. */
    public static final String MSG_ID_FIELD = "requestMsgId";
    /** Field to store coordinate of creator. */
    public static final String MSG_ORIG_FIELD = "requestMsgOrig";

    /** Processes for that handler. */
    private List processes = new LinkedList();
    /** Icon for ping object. */
    public static ImageIcon deviceIcon = null;
    /** Translation resource for this object. */
    private Translator translator;
    /** Devicelist resource. */
//    private DeviceList deviceList;
    
    /**
     * Конструктор.
     */
    public PingGUI() {
        super(NAME, FULLNAME, VERSION, COPYRIGHT);
        
        deviceIcon = new ImageIcon(getClass().getResource("/resources/client/default_ping.gif"));
    }
    
    /**
     * Translate key.
     * 
     * @param key Key to translate.
     * @param defValue Default translation.
     */
    public String translate(String key, String defValue) {
        return ((translator != null) ? translator.getProperty(key, defValue)
                : defValue);
    }
    
    /*
     * (non-Javadoc)
     * @see org.valabs.odisp.common.StandartODObject#handleMessage(org.valabs.odisp.common.Message)
     */
    public void handleMessage(Message msg) {
        if (ODObjectLoadedMessage.equals(msg)) {
            logger.finest("[DBG]: (" + NAME + ") initializing process started.");
            
            // Acquire resource objects
            translator = (Translator) dispatcher.getResourceManager()
                .resourceTryAcquire(Translator.class.getName());
            // TODO: if this doesn't required at all -- remove it. Keep code clean.
            /*deviceList = (DeviceList) dispatcher.getResourceManager()
                .resourceTryAcquire(DeviceList.class.getName());*/
            
            // Register new device type: simple ping object.
            DeviceType devicePing = new DeviceType();
            devicePing.setHandler(HANDLER);
            devicePing.setHasChild(false);
            devicePing.setIcon(deviceIcon);
            devicePing.setRealName(translate(SHORTNAME, "PING"));
            
            // Send register message.
            Message registerMessage = dispatcher.getNewMessage();
            RegisterTypeMessage.setup(registerMessage, GenericDevice.DEVICE_SERVICE,
                    getObjectName(), UUID.getNullUUID());
            RegisterTypeMessage.setHandler(registerMessage, HANDLER);
            RegisterTypeMessage.setDeviceType(registerMessage, devicePing);
            
            dispatcher.send(registerMessage);
        } else if (DevPollReplyMessage.equals(msg)) {
            Message route_msg = msg.cloneMessage();
            route_msg.addField("serverId",
                    new Integer(NetManager.getIndexFromOrigin(msg.getOrigin())));
            route_msg.setDestination(GenericDevice.DEVICE_MONITOR);
            route_msg.setRoutable(false);
            dispatcher.send(route_msg);
        } else if (DeviceRequestMessage.equals(msg)) {
            /*
             * NOTE THAT!!! IMPOTANT INFORMATION Any corrent support handler MUST reply
             * on that message with DeviceInfoReplyMessage.
             */
            Message reply = dispatcher.getNewMessage();
            DeviceRequestReplyMessage.setup(reply, msg.getOrigin(), getObjectName(),
                    msg.getId());
            DeviceRequestReplyMessage.setDevice(reply, ((DeviceInfo) DeviceRequestMessage.getDevice(msg)).getName());
            
            dispatcher.send(reply);
        } else if (LogEventMessage.equals(msg)) {
            logger.finest("[DBG]: (" + NAME + ") log_event received");
            Message logmsg = dispatcher.getNewMessage();
            LogEventMessage.setup(logmsg, GenericDevice.DEVICE_MONITOR,
                    getObjectName(), msg.getReplyTo());
            LogEventMessage.copyFrom(logmsg, msg);
            logmsg.addField("serverId", new Integer(NetManager.getIndexFromOrigin(msg
                    .getOrigin())));
            logmsg.setRoutable(false);
            dispatcher.send(logmsg);
        } else if (ClearAlarmsMessage.equals(msg)) {
            // Clear alarms that produced by this object.
            Message route_log = dispatcher.getNewMessage();
            ClearAlarmsMessage.setup(route_log, GenericDevice.DEVICE_MONITOR, getObjectName(), msg.getReplyTo());
            route_log.setRoutable(false);
            route_log.addField("serverId", new Integer(NetManager.getIndexFromOrigin(msg.getOrigin())));
            ClearAlarmsMessage.copyFrom(route_log, msg);
            Iterator it = msg.getEnvelope().iterator();
            while (it.hasNext()) {
                Message el = (Message) it.next();
                el.addField("serverId", new Integer(NetManager.getIndexFromOrigin(msg.getOrigin())));
                route_log.addToEnvelope(el);
            }
            dispatcher.send(route_log);
        } else if (DeviceAddObjectMessage.equals(msg)) {
          // Add device to the map.
          DeviceInfo newInfo = (DeviceInfo) DeviceAddObjectMessage.getDevice(msg);
          
          newInfo.setProperty(MSG_ID_FIELD, msg.getId());
          newInfo.setProperty(MSG_ORIG_FIELD, msg.getOrigin());
          
          Message addmsg = dispatcher.getNewMessage();
          MapAddObjectMessage.setup(addmsg, NetManager.makeDestination(newInfo
                  .getHandler(), NetManager
                  .makeOrigin(newInfo.getConnectionIndex())), getObjectName(), UUID
                  .getNullUUID());
          MapAddObjectMessage.setName(addmsg, newInfo.getName());
          MapAddObjectMessage.setHandler(addmsg, newInfo.getHandler());
          MapAddObjectMessage.setURN(addmsg, newInfo.getURN());
          MapAddObjectMessage.setCity(addmsg, (String) newInfo.getProperty(DeviceInfo.CITY));
          MapAddObjectMessage.setSuffix(addmsg, (String) newInfo.getProperty(DeviceInfo.SUFFIX));
          MapAddObjectMessage.setOwner(addmsg, (String) newInfo.getProperty(DeviceInfo.OWNER));
          MapAddObjectMessage.setCustomer(addmsg, (String) newInfo.getProperty(DeviceInfo.CUSTOMER));
          MapAddObjectMessage.setPlacement(addmsg, (String) newInfo.getProperty(DeviceInfo.PLACEMENT));
          MapAddObjectMessage.setComment(addmsg, (String) newInfo.getProperty(DeviceInfo.COMMENTS));

          if (!addmsg.isCorrect()) {
            logger.severe("Message AddObject is incorrect");
          }

          // copy additional fields.
          Iterator it = msg.getEnvelope().iterator();
          while (it.hasNext()) {
            addmsg.addToEnvelope((Message) it.next());
          }
          synchronized (processes) {
            processes.add(newInfo);
          }

          dispatcher.send(addmsg);
        } else if (MapAddObjectNotifyMessage.equals(msg)) {
            // This message received from map and inform us that 
            // add object process complited.
            boolean found = false;
            synchronized (processes) {
                DeviceInfo devInfo = null;
                for (Iterator it = processes.iterator(); it.hasNext(); ) {
                    devInfo = (DeviceInfo) it.next();
                    if (devInfo.getName().equals(MapAddObjectNotifyMessage.getName(msg))
                            && devInfo.getHandler().equals(
                                    MapAddObjectNotifyMessage.getHandler(msg))
                                    && devInfo.getConnectionIndex() == NetManager
                                    .getIndexFromOrigin(msg.getOrigin())) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    processes.remove(devInfo);
                    
                    Message reply = dispatcher.getNewMessage();
                    DeviceAddObjectReplyMessage.setup(
                            reply,
                            (String) devInfo.getProperty(MSG_ORIG_FIELD),
                            getObjectName(),
                            (UUID) devInfo.getProperty(MSG_ID_FIELD));
                    DeviceAddObjectReplyMessage.setDevice(reply, devInfo);
                    
                    // Remove temporarily fields
                    devInfo.setProperty(MSG_ID_FIELD, null);
                    devInfo.setProperty(MSG_ORIG_FIELD, null);

                    // Send result to originator
                    dispatcher.send(reply);
                    
                    /*
                     * I think that code is deprecated.
                     * 
                    Message enumObj = dispatcher.getNewMessage();
                    MapEnumerateObjectsReplyMessage.setup(enumObj, "map-gui",
                            NetManager.makeDestination("map", msg.getOrigin()), UUID.getNullUUID());
                    
                    List newObj = new ArrayList();
                    newObj.add(MapAddObjectNotifyMessage.getName(msg));
                    newObj.add(MapAddObjectNotifyMessage.getURN(msg));
                    newObj.add(MapAddObjectNotifyMessage.getHandler(msg));
                    newObj.add(new Integer(0));
                    
                    enumObj.addField("0", newObj);
                    dispatcher.send(enumObj);*/
                } else if (MapAddObjectNotifyMessage.getHandler(msg).equals(HANDLER)) {
                    logger.info("Object not found, may be it is not my object");
                }
            } // synchronized (processes)
        }
    }

    /*
     * (non-Javadoc)
     * @see org.valabs.odisp.common.ODObject#getDepends()
     */
    public String[] getDepends() {
        String depends[] = { "dispatcher",
                com.novel.nms.client.components.info.InfoGUI.NAME,
                com.novel.nms.client.components.map.MapGUI.NAME
        };
        return depends;
    }

    /*
     * (non-Javadoc)
     * @see org.valabs.odisp.common.ODObject#getProviding()
     */
    public String[] getProviding() {
        String providing[] = { NAME };
        return providing;
    }
    
}
