/**
 * 
 */
package org.aap.nms.performance.server;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;

import org.doomdark.uuid.UUID;
import org.valabs.odisp.common.Message;
import org.valabs.odisp.common.StandartODObject;
import org.valabs.stdmsg.ODObjectLoadedMessage;

import com.novel.nms.messages.LogEventMessage;
import com.novel.nms.server.devices.common.Device;

/**
 * This class developed to measure performance of logEvent store
 * process.
 *
 * @author (C) 2008 Andrew Porokhin <andrew.porokhin@gmail.com>
 * @version 1.0
 */
public class LogEventGenerator extends StandartODObject implements Runnable {
    private static final String NAME = "loggenerator";
    private static final String FULLNAME = "Log Event Generator";
    private static final String VERSION = "0.0";
    private static final String COPYRIGHT = "(C) 2008 Andrew Porokhin";
    private static final String DEVICE_NAME = null;
    
    /** Report about progress every 10 second. */
    private static final long REPORT_TIME = 10000;
    /** Worker thread. */
    private Thread worker;

    public LogEventGenerator() {
        super(NAME, FULLNAME, VERSION, COPYRIGHT);
    }

    public void handleMessage(Message msg) {
        if (ODObjectLoadedMessage.equals(msg)) {
            worker = new Thread(this, "EventGeneratorThread");
            worker.start();
        }
    }
    
    public int cleanUp(int type) {
        worker.interrupt();
        return super.cleanUp(type);
    }

    /* (non-Javadoc)
     * @see org.valabs.odisp.common.ODObject#getDepends()
     */
    public String[] getDepends() {
        String[] result = { "dispatcher",
                com.novel.nms.server.storage.Storage.class.getName(),
                com.novel.nms.server.devices.common.DeviceList.class.getName(),
        };
        return result;
    }

    /* (non-Javadoc)
     * @see org.valabs.odisp.common.ODObject#getProviding()
     */
    public String[] getProviding() {
        String[] result = { NAME };
        return result;
    }

    public void run() {
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        long logEventSent = 0;
        long logEventSentReport = 0;
        
        long initialTime = c.getTimeInMillis();
        long currentTime = initialTime;
        long lastReportTime = currentTime;
        logger.info("Event generator thread started.");
        while (!Thread.interrupted()) {
            currentTime = c.getTimeInMillis();
            Long date = new Long(currentTime / 1000);
            Message m = dispatcher.getNewMessage();
            LogEventMessage.setup(m, "nmslog", getObjectName(), UUID.getNullUUID());
            LogEventMessage.setName(m, DEVICE_NAME);
            LogEventMessage.setSource(m, DEVICE_NAME);
            LogEventMessage.setDate(m, date);
            LogEventMessage.setLDate(m, date);
            LogEventMessage.setIndex(m, Device.POLL_TIMEOUT_EVENT_IDX);
            LogEventMessage.setEvent(m, LogEventMessage.ALARM_POLL_TIMEOUT);
            LogEventMessage.setObject(m, "");
            LogEventMessage.setPC(m, "Poll timeout");
            LogEventMessage.setAI(m, "");
            LogEventMessage.setEquipStatus(m, LogEventMessage.ALARM_POLL_TIMEOUT);
            m.setRoutable(false);
            dispatcher.send(m);
            logEventSent++;
            logEventSentReport++;
            
            if (logger.isLoggable(Level.INFO)) {
                if (currentTime - lastReportTime > REPORT_TIME) {
                    logger.info("Messages: " + logEventSentReport + " in " + (currentTime - lastReportTime) + " ms");
                    logger.info("Rate aprox.: " + (logEventSentReport/(currentTime - lastReportTime)/1000) + " log per second");
                    
                    logger.info("Total messages: " + logEventSent + " in " + (currentTime - initialTime) + " ms");
                    logger.info("Global rate aprox.: " + (logEventSent/(currentTime - initialTime)/1000) + " log per second");
                    
                    logEventSentReport = 0;
                    lastReportTime = currentTime;
                }
            }
        }
        logger.info("Event generator thread stopped.");
    }

}
