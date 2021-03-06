
/*
 *    AsTeRICS - Assistive Technology Rapid Integration and Construction Set
 * 
 * 
 *        d8888      88888888888       8888888b.  8888888 .d8888b.   .d8888b. 
 *       d88888          888           888   Y88b   888  d88P  Y88b d88P  Y88b
 *      d88P888          888           888    888   888  888    888 Y88b.     
 *     d88P 888 .d8888b  888   .d88b.  888   d88P   888  888         "Y888b.  
 *    d88P  888 88K      888  d8P  Y8b 8888888P"    888  888            "Y88b.
 *   d88P   888 "Y8888b. 888  88888888 888 T88b     888  888    888       "888
 *  d8888888888      X88 888  Y8b.     888  T88b    888  Y88b  d88P Y88b  d88P
 * d88P     888  88888P' 888   "Y8888  888   T88b 8888888 "Y8888P"   "Y8888P" 
 *
 *
 *                    homepage: http://www.asterics.org 
 *
 *    This project has been partly funded by the European Commission, 
 *                      Grant Agreement Number 247730
 *  
 *  
 *         Dual License: MIT or GPL v3.0 with "CLASSPATH" exception
 *         (please refer to the folder LICENSE)
 * 
 */

package eu.asterics.component.actuator.remotemouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Logger;

import eu.asterics.mw.cimcommunication.CIMPortController;
import eu.asterics.mw.cimcommunication.CIMPortManager;
import eu.asterics.mw.cimcommunication.CIMProtocolPacket;
import eu.asterics.mw.data.ConversionUtils;
import eu.asterics.mw.model.runtime.AbstractRuntimeComponentInstance;
import eu.asterics.mw.model.runtime.IRuntimeEventListenerPort;
import eu.asterics.mw.model.runtime.IRuntimeInputPort;
import eu.asterics.mw.model.runtime.IRuntimeOutputPort;
import eu.asterics.mw.model.runtime.impl.DefaultRuntimeInputPort;
import eu.asterics.mw.services.AstericsErrorHandling;

/**
 * RemoteMouseInstance interfaces with HID actuator USB-dongle to emulate a
 * mouse on a remote PC via USB HID
 * 
 * 
 * @author Christoph Veigl [christoph.veigl@technikum-wien.at] Date: Nov 3, 2010
 *         Time: 02:22:08 PM
 */
public class RemoteMouseInstance extends AbstractRuntimeComponentInstance {

    private CIMPortController port = null;

    private final String ELP_LEFTCLICK_NAME = "leftClick";
    private final String ELP_MIDDLECLICK_NAME = "middleClick";
    private final String ELP_RIGHTCLICK_NAME = "rightClick";
    private final String ELP_DBLCLICK_NAME = "doubleClick";
    private final String ELP_DRAGPRESS_NAME = "dragPress";
    private final String ELP_DRAGRELEASE_NAME = "dragRelease";
    private final String ELP_WHEELUP_NAME = "wheelUp";
    private final String ELP_WHEELDOWN_NAME = "wheelDown";
    private final String ELP_NEXTCLICKRIGHT_NAME = "nextClickRight";
    private final String ELP_NEXTCLICKDOUBLE_NAME = "nextClickDouble";
    private final String ELP_NEXTCLICKMIDDLE_NAME = "nextClickMiddle";
    private final String ELP_NEXTCLICKDRAG_NAME = "nextClickDrag";
    private final String ELP_NEXTCLICKRELEASE_NAME = "nextClickRelease";
    private final String ELP_CENTER_NAME = "center";
    private final String ELP_ACTIVATE_NAME = "activate";
    private final String ELP_DEACTIVATE_NAME = "deactivate";
    private final String ELP_TOGGLE_NAME = "toggle";

    private final int MOUSEBUTTON_LEFT = 1;
    private final int MOUSEBUTTON_RIGHT = 2;
    private final int MOUSEBUTTON_MIDDLE = 4;

    private final int CLK_LEFT = 0;
    private final int CLK_RIGHT = 1;
    private final int CLK_DOUBLE = 2;
    private final int CLK_MIDDLE = 3;
    private final int CLK_DRAG = 4;
    private final int CLK_DRAGRELEASE = 5;

    private static final long REFRESH_INTERVAL = 25;

    private static final short HID_ACTUATOR_CIM_ID = 0x0101;
    private static final short HID_FEATURE_MOUSE_XY_REL = 0x01;
    private static final short HID_FEATURE_MOUSE_BTN_STATE = 0x02;
    private static final short HID_FEATURE_MOUSE_WHEEL = 0x03;

    private boolean propAbsolutePosition = false;
    private double propCenterX = 300;
    private double propCenterY = 150;
    private String propUniqueID = "not used";

    private int nextClick = CLK_LEFT;
    private double diffX = 0, diffY = 0;
    private double yOld = 0;
    private double xOld = 0;

    private boolean enableMouseAction = true;
    private long lastUpdate = 0;

    /**
     * The class constructor.
     */
    public RemoteMouseInstance() {
        // empty constructor - needed for OSGi service factory operations
    }

    /**
     * returns an Input Port.
     * 
     * @param portID
     *            the name of the port
     * @return the input port or null if not found
     */
    @Override
    public IRuntimeInputPort getInputPort(String portID) {
        if ("mouseX".equalsIgnoreCase(portID)) {
            return ipMouseX;
        } else if ("mouseY".equalsIgnoreCase(portID)) {
            return ipMouseY;
        } else if ("action".equalsIgnoreCase(portID)) {
            return ipAction;
        }

        return null;
    }

    /**
     * returns an Output Port.
     * 
     * @param portID
     *            the name of the port
     * @return the output port or null if not found
     */
    @Override
    public IRuntimeOutputPort getOutputPort(String portID) {
        return null;
    }

    /**
     * returns an Event Listener Port.
     * 
     * @param portID
     *            the name of the port
     * @return the event listener port or null if not found
     */
    @Override
    public IRuntimeEventListenerPort getEventListenerPort(String eventPortID) {
        if (ELP_LEFTCLICK_NAME.equalsIgnoreCase(eventPortID)) {
            return elpLeftClick;
        } else if (ELP_MIDDLECLICK_NAME.equalsIgnoreCase(eventPortID)) {
            return elpMiddleClick;
        } else if (ELP_RIGHTCLICK_NAME.equalsIgnoreCase(eventPortID)) {
            return elpRightClick;
        } else if (ELP_DBLCLICK_NAME.equalsIgnoreCase(eventPortID)) {
            return elpDoubleClick;
        } else if (ELP_DRAGPRESS_NAME.equalsIgnoreCase(eventPortID)) {
            return elpDragClick;
        } else if (ELP_DRAGRELEASE_NAME.equalsIgnoreCase(eventPortID)) {
            return elpDragRelease;
        } else if (ELP_WHEELUP_NAME.equalsIgnoreCase(eventPortID)) {
            return elpWheelUp;
        } else if (ELP_WHEELDOWN_NAME.equalsIgnoreCase(eventPortID)) {
            return elpWheelDown;
        } else if (ELP_NEXTCLICKRIGHT_NAME.equalsIgnoreCase(eventPortID)) {
            return elpNextClickRight;
        } else if (ELP_NEXTCLICKDOUBLE_NAME.equalsIgnoreCase(eventPortID)) {
            return elpNextClickDouble;
        } else if (ELP_NEXTCLICKMIDDLE_NAME.equalsIgnoreCase(eventPortID)) {
            return elpNextClickMiddle;
        } else if (ELP_NEXTCLICKDRAG_NAME.equalsIgnoreCase(eventPortID)) {
            return elpNextClickDrag;
        } else if (ELP_NEXTCLICKRELEASE_NAME.equalsIgnoreCase(eventPortID)) {
            return elpNextClickRelease;
        } else if (ELP_CENTER_NAME.equalsIgnoreCase(eventPortID)) {
            return elpCenter;
        } else if (ELP_ACTIVATE_NAME.equalsIgnoreCase(eventPortID)) {
            return elpActivate;
        } else if (ELP_DEACTIVATE_NAME.equalsIgnoreCase(eventPortID)) {
            return elpDeactivate;
        } else if (ELP_TOGGLE_NAME.equalsIgnoreCase(eventPortID)) {
            return elpToggle;
        }
        return null;
    }

    /**
     * returns the value of the given property.
     * 
     * @param propertyName
     *            the name of the property
     * @return the property value or null if not found
     */
    @Override
    public Object getRuntimePropertyValue(String propertyName) {
        if ("absolutePosition".equalsIgnoreCase(propertyName)) {
            return propAbsolutePosition;
        }
        if ("centerX".equalsIgnoreCase(propertyName)) {
            return propCenterX;
        }
        if ("centerY".equalsIgnoreCase(propertyName)) {
            return propCenterY;
        }
        if ("uniqueID".equalsIgnoreCase(propertyName)) {
            return propUniqueID;
        }

        return null;
    }

    /**
     * sets a new value for the given property.
     * 
     * @param propertyName
     *            the name of the property
     * @param newValue
     *            the desired property value
     */
    @Override
    public Object setRuntimePropertyValue(String propertyName, Object newValue) {
        if ("absolutePosition".equalsIgnoreCase(propertyName)) {
            final Object oldValue = propAbsolutePosition;

            if ("true".equalsIgnoreCase((String) newValue)) {
                propAbsolutePosition = true;
            } else if ("false".equalsIgnoreCase((String) newValue)) {
                propAbsolutePosition = false;
            }
            return oldValue;
        } else if ("centerX".equalsIgnoreCase(propertyName)) {
            final Object oldValue = propCenterX;

            if (newValue != null) {
                try {
                    propCenterX = Integer.parseInt(newValue.toString());
                } catch (NumberFormatException nfe) {
                    throw new RuntimeException("Invalid property value for " + propertyName + ": " + newValue);
                }
            }

            return oldValue;
        } else if ("centerY".equalsIgnoreCase(propertyName)) {
            final Object oldValue = propCenterY;

            if (newValue != null) {
                try {
                    propCenterY = Integer.parseInt(newValue.toString());
                } catch (NumberFormatException nfe) {
                    throw new RuntimeException("Invalid property value for " + propertyName + ": " + newValue);
                }
            }

            return oldValue;
        } else if ("uniqueID".equalsIgnoreCase(propertyName)) {
            final Object oldValue = propUniqueID;
            propUniqueID = (String) newValue;
            CIMPortController tempPort = openCIM(HID_ACTUATOR_CIM_ID, propUniqueID);
            if (tempPort != null) {
                port = tempPort;
                if ((!propUniqueID.equals("")) && (!propUniqueID.equals("not used"))) {
                    for (int i = 0; i < 4; i++) {
                        CIMPortManager.getInstance().sendPacket(port, null,
                                CIMProtocolPacket.FEATURE_UNIQUE_SERIAL_NUMBER,
                                CIMProtocolPacket.COMMAND_REQUEST_READ_FEATURE, false);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
            return oldValue;
        }
        return null;
    }

    public CIMPortController openCIM(short CIMID, String uniqueID) {
        if ("not used".equalsIgnoreCase(propUniqueID) || (propUniqueID == "")) {
            return (CIMPortManager.getInstance().getConnection(HID_ACTUATOR_CIM_ID));
        } else {
            Long id;
            try {
                id = Long.parseLong(propUniqueID);
                return (CIMPortManager.getInstance().getConnection(HID_ACTUATOR_CIM_ID, id));
            } catch (Exception e) {
                return (null);
            }
        }
    }

    /**
     * Returns all the filenames inside the path folder data/music and
     * data/sounds
     */
    @Override
    public List<String> getRuntimePropertyList(String key) {
        List<String> res = new ArrayList<String>();
        if (key.compareToIgnoreCase("uniqueID") == 0) {
            res.add("not used");
            Vector<Long> ids;
            ids = CIMPortManager.getInstance().getUniqueIdentifiersofCIMs(HID_ACTUATOR_CIM_ID);
            if (ids != null) {
                for (Long l : ids) {
                    res.add(l.toString());
                    // System.out.println(" found unique ID: "+l.toString());
                }
            }
        }
        return res;
    }

    synchronized private void sendXY(int sendX, int sendY) {
        // System.out.println ("SendX="+sendX);

        // send packet
        byte[] b = new byte[4];
        b[0] = (byte) (sendX & 0xff);
        b[1] = (byte) ((sendX >> 8) & 0xff);
        b[2] = (byte) (sendY & 0xff);
        b[3] = (byte) ((sendY >> 8) & 0xff);

        if (port != null) {
            // AstericsErrorHandling.instance.reportInfo(this,
            // String.format("sending packet: diffX: %x, diffY: %x, byteArray:
            // %x, %x, %x, %x", diffX, diffY, b[0], b[1], b[2], b[3]));
            CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_XY_REL,
                    CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
        }
    }

    synchronized private void centerXY() {
        boolean saveState = enableMouseAction;

        enableMouseAction = false;

        diffX = 0;
        diffY = 0;
        sendXY(-5000, -5000);

        sendXY((int) propCenterX, (int) propCenterY);

        enableMouseAction = saveState;
    }

    /**
     * process the coordinate input for the HId mouse if necessary, large
     * movements (>127) are translated in to several steps. if selected, the
     * mouse action is initiated only if both (x and y) coordinates have been
     * received.
     */
    synchronized private void processRelativeInput(double dx, double dy) {
        diffX += dx;
        diffY += dy;

        if (System.currentTimeMillis() - lastUpdate > REFRESH_INTERVAL) {
            /*
             * switch (centerXState) { case 1: diffX=-5000;diffY=-5000;
             * input1Arrived=true;input2Arrived=true; centerXState++; break;
             * case 2: diffX=propCenterX; diffY=propCenterY; centerXState=0;
             * input1Arrived=true;input2Arrived=true; break; }
             */

            lastUpdate = System.currentTimeMillis();

            sendXY((int) diffX, (int) diffY);

            diffX -= (int) diffX;
            diffY -= (int) diffY;

        }
    }

    /**
     * Input Port for receiving mouse x coordinates.
     */
    private final IRuntimeInputPort ipMouseX = new DefaultRuntimeInputPort() {

        @Override
        synchronized public void receiveData(byte[] data) {
            if (enableMouseAction == true) {
                double inX = ConversionUtils.doubleFromBytes(data);

                if (propAbsolutePosition == false) // input is relative movement
                { // send as it is
                    processRelativeInput(inX, 0);
                } else // input is absolute position
                { // -> transform to relative movement
                    processRelativeInput(inX - xOld, 0);
                    xOld = inX;
                }
            }
        }
    };

    /**
     * Input Port for receiving mouse y coordinates.
     */
    private final IRuntimeInputPort ipMouseY = new DefaultRuntimeInputPort() {

        @Override
        synchronized public void receiveData(byte[] data) {
            if (enableMouseAction == true) {
                double inY = ConversionUtils.doubleFromBytes(data);

                if (propAbsolutePosition == false) // input is relative movement
                { // send as it is
                    processRelativeInput(0, inY);
                } else // input is absolute position
                {
                    processRelativeInput(0, inY - yOld);
                    yOld = inY;
                }
            }
        }
    };

    /**
     * Input Port for receiving mouse action commands. supported commands are:
     * 
     * @MOUSE:nextclick,right next left click will cause a right click
     * @MOUSE:nextclick,double next left click will cause a double click
     * @MOUSE:nextclick,middle next left click will cause a middle click
     * @MOUSE:nextclick,drag next left click will cause a drag click
     * @MOUSE:nextclick,release next left click will release the mouse button
     * @MOUSE:action,enable mouse action is enabled
     * @MOUSE:action,disable mouse action is disabled
     * @MOUSE:action,toggle mouse action is inverted
     */
    private final IRuntimeInputPort ipAction = new DefaultRuntimeInputPort() {
        @Override
        public void receiveData(byte[] data) {
            String text = ConversionUtils.stringFromBytes(data);

            if (text.startsWith("@MOUSE:")) {
                try {
                    StringTokenizer st = new StringTokenizer(text.substring(7), ", ");
                    String command = st.nextToken();
                    if (command.equalsIgnoreCase("nextclick")) {
                        String clickType = st.nextToken();
                        if (clickType.equalsIgnoreCase("right")) {
                            nextClick = CLK_RIGHT;
                        } else if (clickType.equalsIgnoreCase("double")) {
                            nextClick = CLK_DOUBLE;
                        } else if (clickType.equalsIgnoreCase("middle")) {
                            nextClick = CLK_MIDDLE;
                        } else if (clickType.equalsIgnoreCase("drag")) {
                            nextClick = CLK_DRAG;
                        } else if (clickType.equalsIgnoreCase("release")) {
                            nextClick = CLK_DRAGRELEASE;
                        } else {
                            nextClick = CLK_LEFT;
                        }
                    } else if (command.equalsIgnoreCase("action")) {
                        String actionType = st.nextToken();
                        if (actionType.equalsIgnoreCase("enable")) {
                            enableMouseAction = true;
                        } else if (actionType.equalsIgnoreCase("disable")) {
                            enableMouseAction = false;
                        } else if (actionType.equalsIgnoreCase("toggle")) {
                            enableMouseAction = !enableMouseAction;
                        }
                    }

                } catch (Exception e) {
                    Logger.getAnonymousLogger().severe(e.toString());
                }
            }
        }

    };

    /**
     * Event Listener Port for left click.
     */
    final IRuntimeEventListenerPort elpLeftClick = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            if (enableMouseAction == true) {
                if (nextClick == CLK_LEFT) {
                    byte[] b = new byte[1];

                    if (port != null) {
                        b[0] = MOUSEBUTTON_LEFT;
                        CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                                CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);

                        b[0] = 0;
                        CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                                CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                    }
                } else if (nextClick == CLK_RIGHT) {
                    elpRightClick.receiveEvent(null);
                } else if (nextClick == CLK_DOUBLE) {
                    elpDoubleClick.receiveEvent(null);
                } else if (nextClick == CLK_MIDDLE) {
                    elpMiddleClick.receiveEvent(null);
                } else if (nextClick == CLK_DRAG) {
                    elpDragClick.receiveEvent(null);
                } else if (nextClick == CLK_DRAGRELEASE) {
                    elpDragRelease.receiveEvent(null);
                }
                nextClick = CLK_LEFT;
            }
        }
    };

    /**
     * Event Listener Port for right click.
     */
    final IRuntimeEventListenerPort elpRightClick = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            if (enableMouseAction == true) {
                byte[] b = new byte[1];
                if (port != null) {
                    b[0] = MOUSEBUTTON_RIGHT;
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);

                    b[0] = 0;
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                }
            }
        }
    };

    /**
     * Event Listener Port for middle click.
     */
    final IRuntimeEventListenerPort elpMiddleClick = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            if (enableMouseAction == true) {
                byte[] b = new byte[1];
                if (port != null) {
                    b[0] = MOUSEBUTTON_MIDDLE;
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);

                    b[0] = 0;
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                }
            }
        }
    };

    /**
     * Event Listener Port for drag click.
     */
    final IRuntimeEventListenerPort elpDragClick = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            if (enableMouseAction == true) {
                byte[] b = new byte[1];
                b[0] = MOUSEBUTTON_LEFT;
                if (port != null) {
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                }
            }
        }
    };

    /**
     * Event Listener Port for button release.
     */
    final IRuntimeEventListenerPort elpDragRelease = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            if (enableMouseAction == true) {
                byte[] b = new byte[1];
                b[0] = 0;
                if (port != null) {
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                }
            }
        }
    };

    /**
     * Event Listener Port for double click.
     */
    final IRuntimeEventListenerPort elpDoubleClick = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            if (enableMouseAction == true) {
                byte[] b = new byte[1];
                if (port != null) {
                    b[0] = MOUSEBUTTON_LEFT;
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);

                    b[0] = 0;
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                    b[0] = MOUSEBUTTON_LEFT;
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);

                    b[0] = 0;
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_BTN_STATE,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                }
            }
        }
    };

    /**
     * Event Listener Port for wheel up.
     */
    final IRuntimeEventListenerPort elpWheelUp = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            if (enableMouseAction == true) {
                byte[] b = new byte[1];
                b[0] = 1; // one step positive direction

                if (port != null) {
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_WHEEL,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                }
            }
        }
    };

    /**
     * Event Listener Port for wheel down.
     */
    final IRuntimeEventListenerPort elpWheelDown = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            if (enableMouseAction == true) {
                byte[] b = new byte[1];
                b[0] = -1; // one step negative direction

                if (port != null) {
                    CIMPortManager.getInstance().sendPacket(port, b, HID_FEATURE_MOUSE_WHEEL,
                            CIMProtocolPacket.COMMAND_REQUEST_WRITE_FEATURE, false);
                }
            }
        }
    };

    /**
     * Event Listener Port for mouse center command.
     */
    final IRuntimeEventListenerPort elpCenter = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            centerXY();
        }
    };

    /**
     * Event Listener Port for NextClickRight.
     */
    final IRuntimeEventListenerPort elpNextClickRight = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            nextClick = CLK_RIGHT;
        }
    };

    /**
     * Event Listener Port for NextClickDouble.
     */
    final IRuntimeEventListenerPort elpNextClickDouble = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            nextClick = CLK_DOUBLE;
        }
    };

    /**
     * Event Listener Port for NextClickMiddle.
     */
    final IRuntimeEventListenerPort elpNextClickMiddle = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            nextClick = CLK_MIDDLE;
        }
    };

    /**
     * Event Listener Port for NextClickDrag.
     */
    final IRuntimeEventListenerPort elpNextClickDrag = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            nextClick = CLK_DRAG;
        }
    };

    /**
     * Event Listener Port for NextClickRelease.
     */
    final IRuntimeEventListenerPort elpNextClickRelease = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            nextClick = CLK_DRAGRELEASE;
        }
    };

    /**
     * Event Listener Port for activate mouse action.
     */
    final IRuntimeEventListenerPort elpActivate = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            enableMouseAction = true;
        }
    };
    /**
     * Event Listener Port for deactivate mouse action.
     */
    final IRuntimeEventListenerPort elpDeactivate = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            enableMouseAction = false;
        }
    };
    /**
     * Event Listener Port for toggle mouse action.
     */
    final IRuntimeEventListenerPort elpToggle = new IRuntimeEventListenerPort() {
        @Override
        public void receiveEvent(final String data) {
            enableMouseAction = !enableMouseAction;
        }
    };

    @Override
    public void syncedValuesReceived(HashMap<String, byte[]> dataRow) {

        double inX = 0;
        double inY = 0;

        for (String s : dataRow.keySet()) {

            byte[] data = dataRow.get(s);
            if (s.equals("mouseX")) {
                inX = ConversionUtils.doubleFromBytes(data);
            }
            if (s.equals("mouseY")) {
                inY = ConversionUtils.doubleFromBytes(data);
            }
        }

        if (enableMouseAction == true) {
            if (propAbsolutePosition == false) // input is relative movement
            { // send as it is
                processRelativeInput(inX, inY);
            } else // input is absolute position
            { // -> transform to relative movement
                processRelativeInput(inX - xOld, inY - yOld);
                xOld = inX;
                yOld = inY;
            }
        }
    }

    /**
     * called when model is started.
     */
    @Override
    public void start() {
        nextClick = CLK_LEFT;
        lastUpdate = 0;

        port = openCIM(HID_ACTUATOR_CIM_ID, propUniqueID);
        if (port != null) {
            centerXY();
            AstericsErrorHandling.instance.reportInfo(this, "RemoteMouse Instance (ID " + propUniqueID + ") started.");
        } else {
            AstericsErrorHandling.instance.reportError(this,
                    "Could not find RemoteMouse. Please verify that the HID actuator USB dongle is connected to the remote system and correctly installed on this computer.");
        }

        super.start();
    }

    /**
     * called when model is paused.
     */
    @Override
    public void pause() {
        super.pause();
    }

    /**
     * called when model is resumed.
     */
    @Override
    public void resume() {
        super.resume();
    }

    /**
     * called when model is stopped.
     */
    @Override
    public void stop() {
        port = null;
        super.stop();
        AstericsErrorHandling.instance.reportInfo(this, "RemoteMouseInstance stopped");
    }
}