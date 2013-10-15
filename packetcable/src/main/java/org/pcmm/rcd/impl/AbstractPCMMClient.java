/**
 @header@
 */
package org.pcmm.rcd.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

// import org.junit.Assert;
import org.pcmm.objects.MMVersionInfo;
import org.pcmm.rcd.IPCMMClient;
import org.umu.cops.stack.COPSException;
import org.umu.cops.stack.COPSMsg;
import org.umu.cops.stack.COPSTransceiver;

/**
 *
 * default implementation for {@link IPCMMClient}
 *
 *
 */
public class AbstractPCMMClient implements IPCMMClient {

    protected Logger logger = Logger.getLogger(getClass().getName());
    /**
     * socket used to communicated with server.
     */
    private Socket socket;

    private String clientHanlde;

    private MMVersionInfo versionInfo;

    public AbstractPCMMClient() {
        logger.setLevel(Level.ALL);
    }

    /*
     * (non-Javadoc)
     *
     * @see pcmm.rcd.IPCMMClient#sendRequest(pcmm.messages.IMessage)
     */
    public void sendRequest(COPSMsg requestMessage) {
	/** XXX
        Assert.assertNotNull("Client is not connected", isConnected());
        Assert.assertNotNull("Message is Null", requestMessage);
	*/
        try {
            // logger.info("Sending message type : " +
            // requestMessage.getHeader());
            COPSTransceiver.sendMsg(requestMessage, getSocket());
        } catch (IOException e) {
            logger.severe(e.getMessage());
        } catch (COPSException e) {
            logger.severe(e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.rcd.IPCMMClient#readMessage()
     */
    public COPSMsg readMessage() {
	/** XXX
        Assert.assertNotNull("Client is not connected", isConnected());
	*/
        try {
            COPSMsg recvdMsg = COPSTransceiver.receiveMsg(getSocket());
            // logger.info("received message : " + recvdMsg.getHeader());
            return recvdMsg;
        } catch (IOException e) {
            logger.severe(e.getMessage());
        } catch (COPSException e) {
            logger.severe(e.getMessage());
        }
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see pcmm.rcd.IPCMMClient#tryConnect(java.lang.String, int)
     */
    public boolean tryConnect(String address, int port) {
        try {
            InetAddress addr = InetAddress.getByName(address);
            tryConnect(addr, port);
        } catch (UnknownHostException e) {
            logger.severe(e.getMessage());
            return false;
        }
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see pcmm.rcd.IPCMMClient#tryConnect(java.net.InetAddress, int)
     */
    public boolean tryConnect(InetAddress address, int port) {
        try {
            setSocket(new Socket(address, port));
        } catch (IOException e) {
            logger.severe(e.getMessage());
            return false;
        }
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see pcmm.rcd.IPCMMClient#disconnect()
     */
    public boolean disconnect() {
        if (isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.severe(e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * @return the socket
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * @param socket
     *            the socket to set
     */
    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.rcd.IPCMMClient#isConnected()
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    /**
     * @return the versionInfo
     */
    public MMVersionInfo getVersionInfo() {
        return versionInfo;
    }

    /**
     * @param versionInfo
     *            the versionInfo to set
     */
    public void setVersionInfo(MMVersionInfo versionInfo) {
        this.versionInfo = versionInfo;
    }

    @Override
    public String getClientHandle() {
        return clientHanlde;
    }

    @Override
    public void setClientHandle(String handle) {
        this.clientHanlde = handle;
    }

}
